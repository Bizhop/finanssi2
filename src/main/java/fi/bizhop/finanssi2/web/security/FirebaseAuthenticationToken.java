package fi.bizhop.finanssi2.web.security;

import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {
    private final FirebaseToken firebaseToken;

    public FirebaseAuthenticationToken(FirebaseToken firebaseToken) {
        super(null);
        this.firebaseToken = firebaseToken;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return firebaseToken;
    }
}
