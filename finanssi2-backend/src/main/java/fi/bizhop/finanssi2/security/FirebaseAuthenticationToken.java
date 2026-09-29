package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {
    private final FirebaseToken firebaseToken;

    public FirebaseAuthenticationToken(FirebaseToken firebaseToken) {
        super(AuthorityUtils.NO_AUTHORITIES);
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

    /** Firebase user id; also the user name of the websocket session */
    @Override
    public String getName() {
        return firebaseToken.getUid();
    }
}
