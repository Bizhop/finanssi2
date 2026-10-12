package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Verifies Firebase ID tokens, for both HTTP requests and websocket connections */
@Slf4j
@Component
public class FirebaseTokenVerifier {
    static final String BEARER_PREFIX = "Bearer ";
    /** Absent when no Firebase credentials are configured (the test profiles) */
    private final ObjectProvider<FirebaseAuth> firebaseAuth;

    public FirebaseTokenVerifier(ObjectProvider<FirebaseAuth> firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    /** Verifies the token in an {@code Authorization: Bearer <token>} header value; empty when missing, invalid or expired */
    public Optional<FirebaseToken> verifyAuthorizationHeader(@Nullable String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        var auth = firebaseAuth.getIfAvailable();
        if (auth == null) {
            log.warn("Firebase is not configured; rejecting the token");
            return Optional.empty();
        }
        try {
            return Optional.of(auth.verifyIdToken(authorizationHeader.substring(BEARER_PREFIX.length())));
        } catch (FirebaseAuthException e) {
            // A bad token is the client's problem; failing to fetch Google's signing keys is ours
            if (e.getAuthErrorCode() == AuthErrorCode.CERTIFICATE_FETCH_FAILED) log.warn("Could not fetch Firebase signing keys", e);
            else log.debug("Rejected Firebase token: {}", e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            log.debug("Rejected malformed Firebase token: {}", e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Firebase token verification failed unexpectedly", e);
            return Optional.empty();
        }
    }
}
