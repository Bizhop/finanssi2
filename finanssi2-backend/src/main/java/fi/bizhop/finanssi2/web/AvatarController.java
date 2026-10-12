package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.security.User;
import fi.bizhop.finanssi2.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AvatarController {
    final ProfileService profiles;
    final AvatarImageProcessor imageProcessor;

    @PutMapping(value = "/api/me/avatar", consumes = "multipart/form-data")
    public ProfileService.Me upload(@RequestAttribute("user") User user,
            @RequestParam long version, @RequestPart("file") MultipartFile file) {
        // Decoded and re-encoded before the profile transaction opens
        return profiles.setAvatar(user, version, imageProcessor.process(file));
    }

    @DeleteMapping("/api/me/avatar")
    public ProfileService.Me remove(@RequestAttribute("user") User user, @RequestParam long version) {
        return profiles.removeAvatar(user, version);
    }
}
