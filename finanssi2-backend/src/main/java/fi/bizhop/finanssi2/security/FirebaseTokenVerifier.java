package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Verifies Firebase ID tokens, for both HTTP requests and websocket connections */
@Component
public class FirebaseTokenVerifier {
    static final String BEARER_PREFIX = "Bearer ";

    /** Verifies the token in an {@code Authorization: Bearer <token>} header value; empty when missing, invalid or expired */
    public Optional<FirebaseToken> verifyAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(FirebaseAuth.getInstance().verifyIdToken(authorizationHeader.substring(BEARER_PREFIX.length())));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
