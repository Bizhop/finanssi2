package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebaseTokenVerifierTest {
    final FirebaseAuth auth = mock(FirebaseAuth.class);
    @SuppressWarnings("unchecked")
    final ObjectProvider<FirebaseAuth> provider = mock(ObjectProvider.class);
    final FirebaseTokenVerifier verifier = new FirebaseTokenVerifier(provider);

    @Test
    void validTokenIsReturned() throws Exception {
        var token = mock(FirebaseToken.class);
        when(provider.getIfAvailable()).thenReturn(auth);
        when(auth.verifyIdToken("good")).thenReturn(token);

        assertEquals(token, verifier.verifyAuthorizationHeader("Bearer good").orElseThrow());
    }

    @Test
    void missingOrNonBearerHeadersAreRejectedWithoutCallingFirebase() {
        assertTrue(verifier.verifyAuthorizationHeader(null).isEmpty());
        assertTrue(verifier.verifyAuthorizationHeader("Basic abc").isEmpty());
    }

    @Test
    void invalidTokenKeyFetchFailureAndUnexpectedFailureAllFailClosed() throws Exception {
        when(provider.getIfAvailable()).thenReturn(auth);
        var invalid = mock(FirebaseAuthException.class);
        when(invalid.getAuthErrorCode()).thenReturn(AuthErrorCode.INVALID_ID_TOKEN);
        var keys = mock(FirebaseAuthException.class);
        when(keys.getAuthErrorCode()).thenReturn(AuthErrorCode.CERTIFICATE_FETCH_FAILED);
        when(auth.verifyIdToken("invalid")).thenThrow(invalid);
        when(auth.verifyIdToken("keys")).thenThrow(keys);
        when(auth.verifyIdToken("")).thenThrow(new IllegalArgumentException("idToken must not be null or empty"));
        when(auth.verifyIdToken("broken")).thenThrow(new IllegalStateException("FirebaseApp was deleted"));

        for (var token : new String[] {"invalid", "keys", "", "broken"}) {
            assertTrue(verifier.verifyAuthorizationHeader("Bearer " + token).isEmpty(), token);
        }
    }

    @Test
    void withoutFirebaseConfigurationEveryTokenIsRejected() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertTrue(verifier.verifyAuthorizationHeader("Bearer anything").isEmpty());
    }
}
