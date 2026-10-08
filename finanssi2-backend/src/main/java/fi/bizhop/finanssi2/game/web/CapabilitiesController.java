package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequiredArgsConstructor
public class CapabilitiesController {
    final DebugAccess debugAccess;
    final ApplicationUserRepository users;
    public record Me(String id, String email, String displayName, String avatar, String avatarSource, long version, Capabilities capabilities) {}
    public record Capabilities(boolean debugMode) {}

    @GetMapping("/api/me/capabilities")
    public Capabilities capabilities(@RequestAttribute("user") User user) {
        return new Capabilities(debugAccess.allowed(user));
    }

    @GetMapping("/api/me")
    public Me me(@RequestAttribute("user") User user) {
        var profile = users.findById(java.util.UUID.fromString(user.userId())).orElseThrow();
        var avatar = profile.effectiveAvatar();
        var source = profile.getCustomAvatar() != null ? "custom" : profile.getProviderPhotoUrl() != null ? "provider" : null;
        return new Me(user.userId(), profile.getEmail(), profile.getDisplayName(), avatar, source, profile.getVersion(),
                new Capabilities(debugAccess.allowed(user)));
    }

    public record ProfileUpdate(String displayName, long version) {}

    @PutMapping("/api/me/profile")
    @Transactional
    public Me updateProfile(@RequestAttribute("user") User user, @RequestBody ProfileUpdate update) {
        var displayName = update.displayName() == null ? "" : update.displayName().trim();
        if (displayName.isEmpty() || displayName.length() > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName must contain 1–50 characters");
        var profile = users.findById(java.util.UUID.fromString(user.userId())).orElseThrow();
        if (profile.getVersion() != update.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT");
        profile.updateDisplayName(displayName);
        try { users.flush(); }
        catch (org.springframework.orm.ObjectOptimisticLockingFailureException stale) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT", stale);
        }
        return new Me(user.userId(), profile.getEmail(), profile.getDisplayName(), profile.effectiveAvatar(),
                profile.getCustomAvatar() != null ? "custom" : profile.getProviderPhotoUrl() != null ? "provider" : null,
                profile.getVersion(), new Capabilities(debugAccess.allowed(user)));
    }
}
