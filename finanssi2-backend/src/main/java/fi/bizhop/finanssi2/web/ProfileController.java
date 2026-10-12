package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.security.User;
import fi.bizhop.finanssi2.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProfileController {
    final ProfileService profiles;

    public record ProfileUpdate(String displayName, long version) {}

    @GetMapping("/api/me/capabilities")
    public ProfileService.Capabilities capabilities(@RequestAttribute("user") User user) {
        return profiles.capabilities(user);
    }

    @GetMapping("/api/me")
    public ProfileService.Me me(@RequestAttribute("user") User user) {
        return profiles.profile(user);
    }

    @PutMapping("/api/me/profile")
    public ProfileService.Me updateProfile(@RequestAttribute("user") User user, @RequestBody ProfileUpdate update) {
        return profiles.updateDisplayName(user, update.displayName(), update.version());
    }
}
