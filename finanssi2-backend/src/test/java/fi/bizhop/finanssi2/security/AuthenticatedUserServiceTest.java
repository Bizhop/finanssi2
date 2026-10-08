package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.FirebaseToken;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticatedUserServiceTest {
    @Autowired AuthenticatedUserService authenticatedUsers;
    @Autowired ApplicationUserRepository users;

    @BeforeEach
    void clearProfiles() { users.deleteAll(); }

    @Test
    void providerTokensForOneFirebaseUidShareProfileAndKeepChosenMetadata() {
        var google = token("shared-firebase-uid", " Person+tag@Example.COM ", "Chosen Name", "https://one.example/photo", true);
        var password = token("shared-firebase-uid", "person+tag@example.com", "Different provider name", "https://two.example/photo", true);
        var first = authenticatedUsers.resolve(google);
        var second = authenticatedUsers.resolve(password);
        assertEquals(first.userId(), second.userId());
        assertNotEquals(first.userId(), first.firebaseUid());
        assertEquals("person+tag@example.com", first.email());
        assertEquals("Chosen Name", second.name());
        assertEquals("https://one.example/photo", second.photoUrl());
        assertEquals(1, users.count());
    }

    @Test
    void distinctUidsRemainSeparateAndCannotClaimAnExistingEmail() {
        var first = authenticatedUsers.resolve(token("firebase-one", "same@example.com", "One", null, true));
        var other = authenticatedUsers.resolve(token("firebase-two", "other@example.com", "Two", null, true));
        assertNotEquals(first.userId(), other.userId());
        assertThrows(AuthenticatedUserService.AccountLinkConflictException.class,
                () -> authenticatedUsers.resolve(token("firebase-three", "SAME@example.com", "Attacker", null, true)));
        assertEquals(2, users.count());
    }

    @Test
    void concurrentFirstRequestsForOneUidReturnTheSameProfile() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var token = token("concurrent-uid", "concurrent@example.com", "Concurrent", null, true);
            var one = pool.submit(() -> { start.await(); return authenticatedUsers.resolve(token); });
            var two = pool.submit(() -> { start.await(); return authenticatedUsers.resolve(token); });
            start.countDown();
            assertEquals(one.get().userId(), two.get().userId());
        }
        assertEquals(1, users.count());
    }

    @Test
    void missingOrUnverifiedEmailIsRejectedBeforeProfileCreation() {
        assertThrows(AuthenticatedUserService.UnverifiedEmailException.class,
                () -> authenticatedUsers.resolve(token("missing-email", null, "Name", null, true)));
        assertThrows(AuthenticatedUserService.UnverifiedEmailException.class,
                () -> authenticatedUsers.resolve(token("unverified", "pending@example.com", "Name", null, false)));
        assertEquals(0, users.count());
    }

    private static FirebaseToken token(String uid, String email, String name, String picture, boolean verified) {
        var token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.getName()).thenReturn(name);
        when(token.getPicture()).thenReturn(picture);
        when(token.isEmailVerified()).thenReturn(verified);
        return token;
    }
}
