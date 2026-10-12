package fi.bizhop.finanssi2.service;

import fi.bizhop.finanssi2.error.ErrorCode;
import fi.bizhop.finanssi2.error.ApiException;
import java.util.Map;
import fi.bizhop.finanssi2.db.ApplicationUser;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The signed-in user's own profile: display name and avatar, versioned so concurrent edits fail instead of overwriting */
@Service
@RequiredArgsConstructor
public class ProfileService {
    static final String VERSION_CONFLICT = "PROFILE_VERSION_CONFLICT";
    static final int MAX_DISPLAY_NAME_LENGTH = 50;

    final ApplicationUserRepository users;
    final DebugAccess debugAccess;

    public record Me(String id, String email, String displayName, @Nullable String avatar, @Nullable String avatarSource, long version, Capabilities capabilities) {}

    public record Capabilities(boolean debugMode) {}

    public Capabilities capabilities(User user) {
        return new Capabilities(debugAccess.allowed(user));
    }

    @Transactional(readOnly = true)
    public Me profile(User user) {
        return view(user, load(user));
    }

    @Transactional
    public Me updateDisplayName(User user, String requestedName, long version) {
        var displayName = requestedName == null ? "" : requestedName.trim();
        if (displayName.isEmpty() || displayName.length() > MAX_DISPLAY_NAME_LENGTH)
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.DISPLAY_NAME_INVALID, "displayName must contain 1–" + MAX_DISPLAY_NAME_LENGTH + " characters",
                    Map.of("max", MAX_DISPLAY_NAME_LENGTH));
        var profile = load(user);
        requireVersion(profile, version);
        profile.updateDisplayName(displayName);
        return flushed(user, profile);
    }

    /** {@code avatar} is the already processed image; decoding it is slow and must not hold a database transaction */
    @Transactional
    public Me setAvatar(User user, long version, String avatar) {
        var profile = load(user);
        requireVersion(profile, version);
        profile.setCustomAvatar(avatar);
        return flushed(user, profile);
    }

    @Transactional
    public Me removeAvatar(User user, long version) {
        var profile = load(user);
        requireVersion(profile, version);
        profile.removeCustomAvatar();
        return flushed(user, profile);
    }

    private ApplicationUser load(User user) {
        return users.findById(UUID.fromString(user.userId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Account profile not found"));
    }

    private static void requireVersion(ApplicationUser profile, long expected) {
        if (profile.getVersion() != expected) throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT, VERSION_CONFLICT);
    }

    private Me flushed(User user, ApplicationUser profile) {
        try {
            users.flush();
        } catch (ObjectOptimisticLockingFailureException stale) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT, VERSION_CONFLICT, Map.of(), stale);
        }
        return view(user, profile);
    }

    private Me view(User user, ApplicationUser profile) {
        var source = profile.getCustomAvatar() != null ? "custom" : profile.getProviderPhotoUrl() != null ? "provider" : null;
        return new Me(user.userId(), profile.getEmail(), profile.getDisplayName(), profile.avatarUrl(), source, profile.getVersion(),
                capabilities(user));
    }
}
