package fi.bizhop.finanssi2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class FirebaseTokenFilter extends AbstractPreAuthenticatedProcessingFilter {
    final FirebaseTokenVerifier tokenVerifier;

    @Override
    protected Object getPreAuthenticatedPrincipal(@NonNull HttpServletRequest request) {
        return null;
    }

    @Override
    protected Object getPreAuthenticatedCredentials(@NonNull HttpServletRequest request) {
        return null;
    }

    @Override
    public void doFilter(@NonNull ServletRequest request, @NonNull ServletResponse response, @NonNull FilterChain chain)
            throws IOException, ServletException {
        var httpRequest = (HttpServletRequest)request;
        tokenVerifier.verifyAuthorizationHeader(httpRequest.getHeader("Authorization")).ifPresentOrElse(
                decodedToken -> {
                    var user = new User(decodedToken.getUid(), decodedToken.getEmail(), decodedToken.getName(), decodedToken.getPicture());
                    request.setAttribute("user", user);
                    SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(decodedToken));
                },
                SecurityContextHolder::clearContext);
        chain.doFilter(request, response);
    }
}

