package fi.bizhop.finanssi2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import java.io.IOException;

public class FirebaseTokenFilter extends AbstractPreAuthenticatedProcessingFilter {
    final FirebaseTokenVerifier tokenVerifier;
    final AuthenticatedUserService authenticatedUserService;

    public FirebaseTokenFilter(FirebaseTokenVerifier tokenVerifier, AuthenticatedUserService authenticatedUserService) {
        this.tokenVerifier = tokenVerifier;
        this.authenticatedUserService = authenticatedUserService;
    }

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
        var token = tokenVerifier.verifyAuthorizationHeader(httpRequest.getHeader("Authorization"));
        if (token.isPresent()) {
            try {
                var resolved = authenticatedUserService.resolve(token.get());
                request.setAttribute("user", resolved);
                SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(token.get(), resolved));
            } catch (AuthenticatedUserService.UnverifiedEmailException e) {
                ((jakarta.servlet.http.HttpServletResponse) response).sendError(403, "EMAIL_VERIFICATION_REQUIRED");
                return;
            } catch (AuthenticatedUserService.AccountLinkConflictException e) {
                ((jakarta.servlet.http.HttpServletResponse) response).sendError(409, "ACCOUNT_LINKING_CONFLICT");
                return;
            }
        } else SecurityContextHolder.clearContext();
        chain.doFilter(request, response);
    }
}
