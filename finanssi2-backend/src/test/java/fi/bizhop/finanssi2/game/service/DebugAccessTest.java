package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameMode;
import fi.bizhop.finanssi2.security.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DebugAccessTest {
    @Test
    void firebaseIdentityRetainsVerifiedClaimForRestAndStomp() {
        var token = org.mockito.Mockito.mock(com.google.firebase.auth.FirebaseToken.class);
        org.mockito.Mockito.when(token.getUid()).thenReturn("a");
        org.mockito.Mockito.when(token.getEmail()).thenReturn("owner@example.com");
        org.mockito.Mockito.when(token.isEmailVerified()).thenReturn(true);
        var restUser = User.fromToken(token);
        var stompUser = new fi.bizhop.finanssi2.security.FirebaseAuthenticationToken(token).user();
        assertEquals(restUser, stompUser);
        assertTrue(new DebugAccess("owner@example.com").allowed(stompUser));
        org.mockito.Mockito.when(token.isEmailVerified()).thenReturn(false);
        assertFalse(new DebugAccess("owner@example.com").allowed(User.fromToken(token)));
    }

    @Test
    void requiresExactVerifiedConfiguredEmail() {
        var access = new DebugAccess(" Owner@Example.com , second@example.com ");
        assertTrue(access.allowed(new User("a", " owner@EXAMPLE.com ", "Owner", null, true)));
        assertFalse(access.allowed(new User("a", "owner@example.com", "Owner", null)));
        assertFalse(access.allowed(new User("a", null, "Owner", null, true)));
        assertFalse(access.allowed(new User("a", "owner+alias@example.com", "Owner", null, true)));
        assertFalse(new DebugAccess("").allowed(new User("a", "owner@example.com", "Owner", null, true)));
        var game = new Game(GameMode.DEBUG);
        game.setCreator("a");
        assertThrows(NotAllowedException.class,
                () -> access.requireOwner(game, new User("b", "second@example.com", "Other", null, true)));
    }
}
