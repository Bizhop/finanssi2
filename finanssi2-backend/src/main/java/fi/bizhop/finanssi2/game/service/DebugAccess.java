package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameMode;
import fi.bizhop.finanssi2.security.Emails;
import fi.bizhop.finanssi2.security.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DebugAccess {
    private final Set<String> allowedEmails;

    public DebugAccess(@Value("${finanssi2.debug.allowed-emails:}") String emails) {
        allowedEmails = Arrays.stream(emails.split(",")).map(Emails::normalize)
                .filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    public boolean allowed(User user) {
        return user != null && user.emailVerified() && user.email() != null
                && allowedEmails.contains(Emails.normalize(user.email()));
    }

    public void require(User user) {
        if (!allowed(user)) throw new NotAllowedException("Debug access is not enabled for this account");
    }

    public void requireOwner(Game game, User user) {
        requireOwner(game.getMode(), game.getCreator(), user);
    }

    public void requireOwner(GameMode mode, String creator, User user) {
        require(user);
        if (mode != GameMode.DEBUG || !creator.equals(user.userId())) {
            throw new NotAllowedException("Only the debug game owner can access this game");
        }
    }

    public void requireRead(Game game, User user) {
        if (game.getMode() == GameMode.DEBUG) requireOwner(game, user);
    }
}
