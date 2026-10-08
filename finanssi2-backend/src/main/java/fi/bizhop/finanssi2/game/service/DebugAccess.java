package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameMode;
import fi.bizhop.finanssi2.security.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DebugAccess {
    private final Set<String> allowedEmails;

    public DebugAccess(@Value("${finanssi2.debug.allowed-emails:}") String emails) {
        allowedEmails = Arrays.stream(emails.split(",")).map(DebugAccess::normalize)
                .filter(email -> !email.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean allowed(User user) {
        return user != null && user.emailVerified() && user.email() != null
                && allowedEmails.contains(normalize(user.email()));
    }

    public void require(User user) {
        if (!allowed(user)) throw new NotAllowedException("Debug access is not enabled for this account");
    }

    public void requireOwner(Game game, User user) {
        require(user);
        if (game.getMode() != GameMode.DEBUG || !game.getCreator().equals(user.userId())) {
            throw new NotAllowedException("Only the debug game owner can access this game");
        }
    }

    public void requireRead(Game game, User user) {
        if (game.getMode() == GameMode.DEBUG) requireOwner(game, user);
    }
}
