package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ApplicationUser;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class PublicProfileController {
    final ApplicationUserRepository users;

    public record PublicProfile(String id, String displayName, String avatar) {}

    @GetMapping("/api/users")
    public List<PublicProfile> profiles(@RequestParam List<String> ids) {
        if (ids.size() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 100 user ids are allowed");
        var parsed = ids.stream().distinct().map(PublicProfileController::parseId).toList();
        return users.findAllById(parsed).stream().map(PublicProfileController::view).toList();
    }

    @GetMapping("/api/users/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable String id, @RequestParam long v) {
        var profile = users.findById(parseId(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (profile.getVersion() != v || profile.getCustomAvatar() == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var encoded = profile.getCustomAvatar().substring(profile.getCustomAvatar().indexOf(',') + 1);
        var bytes = Base64.getDecoder().decode(encoded);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .eTag("\"avatar-" + profile.getVersion() + "\"").body(bytes);
    }

    private static PublicProfile view(ApplicationUser profile) {
        return new PublicProfile(profile.getId().toString(), profile.getDisplayName(), profile.avatarUrl());
    }

    private static UUID parseId(String id) {
        try { return UUID.fromString(id); }
        catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id");
        }
    }
}
