package fi.bizhop.finanssi2.security;

import fi.bizhop.finanssi2.db.ApplicationUser;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class AuthenticatedUserService {
    final ApplicationUserRepository users;
    public AuthenticatedUserService(ApplicationUserRepository users) { this.users = users; }

    @Transactional
    public User resolve(com.google.firebase.auth.FirebaseToken token) {
        var email = token.getEmail();
        if (!token.isEmailVerified() || email == null || email.isBlank()) throw new UnverifiedEmailException();
        var normalized = email.trim().toLowerCase(Locale.ROOT);
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
        try {
            return user(users.saveAndFlush(new ApplicationUser(token.getUid(), normalized, name, token.getPicture())));
        } catch (DataIntegrityViolationException conflict) {
            var winner = users.findByFirebaseUid(token.getUid()).orElseThrow(AccountLinkConflictException::new);
            if (!winner.getEmail().equals(normalized)) throw new AccountLinkConflictException();
            return user(winner);
        }
    }
    private User user(ApplicationUser profile) {
        return new User(profile.getId().toString(), profile.getEmail(), profile.getDisplayName(), profile.effectiveAvatar(), true, profile.getFirebaseUid());
    }
    public static class UnverifiedEmailException extends RuntimeException {}
    public static class AccountLinkConflictException extends RuntimeException {}
}
