package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {
    private final FirebaseToken firebaseToken;
    private final User resolvedUser;

    public FirebaseAuthenticationToken(FirebaseToken firebaseToken) {
        this(firebaseToken, User.fromToken(firebaseToken));
    }

    public FirebaseAuthenticationToken(FirebaseToken firebaseToken, User resolvedUser) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.firebaseToken = firebaseToken;
        this.resolvedUser = resolvedUser;
        setAuthenticated(true);
    }

    public User user() {
        return resolvedUser;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return firebaseToken;
    }

    @Override
    public String getName() {
        return resolvedUser.userId();
    }
}
