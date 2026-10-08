package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
        var profile = users.findById(java.util.UUID.fromString(user.uid())).orElseThrow();
        var avatar = profile.effectiveAvatar();
        var source = profile.getCustomAvatar() != null ? "custom" : profile.getProviderPhotoUrl() != null ? "provider" : null;
        return new Me(user.uid(), profile.getEmail(), profile.getDisplayName(), avatar, source, profile.getVersion(),
                new Capabilities(debugAccess.allowed(user)));
    }
}
