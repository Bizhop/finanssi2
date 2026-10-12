package fi.bizhop.finanssi2.service;

import fi.bizhop.finanssi2.config.Finanssi2Properties;
import fi.bizhop.finanssi2.db.ApplicationUser;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.security.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import fi.bizhop.finanssi2.error.ApiException;
import fi.bizhop.finanssi2.error.ErrorCode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTest {
    final ApplicationUserRepository users = mock(ApplicationUserRepository.class);
    final DebugAccess debugAccess = new DebugAccess(new Finanssi2Properties(null, null, new Finanssi2Properties.Debug(Set.of("owner@example.com")), null));
    final ProfileService service = new ProfileService(users, debugAccess);
    final ApplicationUser profile = mock(ApplicationUser.class);
    final UUID id = UUID.randomUUID();
    final User user = new User(id.toString(), "owner@example.com", "Owner", null, true, "uid");

    ProfileServiceTest() {
        when(users.findById(id)).thenReturn(Optional.of(profile));
        when(profile.getVersion()).thenReturn(3L);
        when(profile.getEmail()).thenReturn("owner@example.com");
        when(profile.getDisplayName()).thenReturn("Owner");
    }

    private HttpStatus statusOf(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ApiException.class, action::run).status().value());
    }

    @Test
    void profileReportsAvatarSourceAndDebugCapability() {
        when(profile.getProviderPhotoUrl()).thenReturn("https://photos.example/p.jpg");
        when(profile.avatarUrl()).thenReturn("https://photos.example/p.jpg");

        var me = service.profile(user);

        assertEquals("provider", me.avatarSource());
        assertEquals(3L, me.version());
        assertTrue(me.capabilities().debugMode());
        when(profile.getCustomAvatar()).thenReturn("data:image/jpeg;base64,AAAA");
        assertEquals("custom", service.profile(user).avatarSource());
    }

    @Test
    void missingProfileIsNotFound() {
        var stranger = new User(UUID.randomUUID().toString(), "x@example.com", "X", null, true, "uid-x");
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.profile(stranger)));
    }

    @Test
    void displayNameIsTrimmedValidatedAndVersionChecked() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.updateDisplayName(user, "  ", 3)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.updateDisplayName(user, null, 3)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.updateDisplayName(user, "x".repeat(51), 3)));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.updateDisplayName(user, "New name", 2)));

        service.updateDisplayName(user, "  New name ", 3);

        org.mockito.Mockito.verify(profile).updateDisplayName("New name");
    }

    @Test
    void avatarChangesAreVersionChecked() {
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.setAvatar(user, 1, "data:image/jpeg;base64,AAAA")));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.removeAvatar(user, 1)));

        service.setAvatar(user, 3, "data:image/jpeg;base64,AAAA");
        service.removeAvatar(user, 3);

        org.mockito.Mockito.verify(profile).setCustomAvatar("data:image/jpeg;base64,AAAA");
        org.mockito.Mockito.verify(profile).removeCustomAvatar();
    }

    @Test
    void aConcurrentEditSurfacesAsAVersionConflict() {
        doThrow(new ObjectOptimisticLockingFailureException(ApplicationUser.class, id)).when(users).flush();

        var failure = assertThrows(ApiException.class, () -> service.removeAvatar(user, 3));

        assertEquals(HttpStatus.CONFLICT.value(), failure.status().value());
        assertEquals("PROFILE_VERSION_CONFLICT", failure.getMessage());
        assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, failure.code());
        assertNull(profile.getCustomAvatar());
    }
}
