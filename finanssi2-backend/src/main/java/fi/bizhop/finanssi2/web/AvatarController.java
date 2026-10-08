package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.game.web.CapabilitiesController;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class AvatarController {
    final ApplicationUserRepository users;
    final DebugAccess debugAccess;
    final AvatarImageProcessor imageProcessor;

    @PutMapping(value = "/api/me/avatar", consumes = "multipart/form-data")
    @Transactional
    public CapabilitiesController.Me upload(@RequestAttribute("user") User user,
            @RequestParam long version, @RequestPart("file") MultipartFile file) {
        var profile = users.findById(java.util.UUID.fromString(user.userId())).orElseThrow();
        requireVersion(profile.getVersion(), version);
        profile.setCustomAvatar(imageProcessor.process(file));
        flushOrConflict();
        return view(user, profile);
    }

    @DeleteMapping("/api/me/avatar")
    @Transactional
    public CapabilitiesController.Me remove(@RequestAttribute("user") User user, @RequestParam long version) {
        var profile = users.findById(java.util.UUID.fromString(user.userId())).orElseThrow();
        requireVersion(profile.getVersion(), version);
        profile.removeCustomAvatar();
        flushOrConflict();
        return view(user, profile);
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT");
    }

    private void flushOrConflict() {
        try { users.flush(); }
        catch (ObjectOptimisticLockingFailureException stale) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT", stale);
        }
    }

    private CapabilitiesController.Me view(User user, fi.bizhop.finanssi2.db.ApplicationUser profile) {
        return new CapabilitiesController.Me(user.userId(), profile.getEmail(), profile.getDisplayName(), profile.effectiveAvatar(),
                profile.getCustomAvatar() != null ? "custom" : profile.getProviderPhotoUrl() != null ? "provider" : null,
                profile.getVersion(), new CapabilitiesController.Capabilities(debugAccess.allowed(user)));
    }
}
