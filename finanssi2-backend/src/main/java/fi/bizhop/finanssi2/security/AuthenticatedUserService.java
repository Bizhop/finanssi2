package fi.bizhop.finanssi2.security;

import fi.bizhop.finanssi2.db.ApplicationUser;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Locale;

@Service
public class AuthenticatedUserService {
    final ApplicationUserRepository users;
    final TransactionTemplate transactions;
    public AuthenticatedUserService(ApplicationUserRepository users, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public User resolve(com.google.firebase.auth.FirebaseToken token) {
        var email = token.getEmail();
        if (!token.isEmailVerified() || email == null || email.isBlank()) throw new UnverifiedEmailException();
        var normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            return transactions.execute(status -> resolveInTransaction(token, normalized));
        } catch (DataIntegrityViolationException competingInsert) {
            var winner = users.findByFirebaseUid(token.getUid());
            if (winner.isPresent() && winner.get().getEmail().equals(normalized)) return user(winner.get());
            throw new AccountLinkConflictException();
        }
    }

    private User resolveInTransaction(com.google.firebase.auth.FirebaseToken token, String normalized) {
        var existing = users.findByFirebaseUid(token.getUid());
        if (existing.isPresent()) {
            var profile = existing.get();
            if (users.findByEmail(normalized).filter(other -> !other.getId().equals(profile.getId())).isPresent())
                throw new AccountLinkConflictException();
            profile.updateIdentity(normalized, token.getPicture());
            return user(profile);
        }
        if (users.findByEmail(normalized).isPresent()) throw new AccountLinkConflictException();
        var name = token.getName();
        if (name == null || name.isBlank()) name = normalized.substring(0, normalized.indexOf('@'));
        name = name.trim();
        if (name.length() > 50) name = name.substring(0, 50);
        return user(users.saveAndFlush(new ApplicationUser(token.getUid(), normalized, name, token.getPicture())));
    }
    private User user(ApplicationUser profile) {
        return new User(profile.getId().toString(), profile.getEmail(), profile.getDisplayName(), profile.effectiveAvatar(), true, profile.getFirebaseUid());
    }

    public static class UnverifiedEmailException extends RuntimeException {}
    public static class AccountLinkConflictException extends RuntimeException {}
}
