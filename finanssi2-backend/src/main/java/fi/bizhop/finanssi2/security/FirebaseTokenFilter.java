package fi.bizhop.finanssi2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

public class FirebaseTokenFilter extends AbstractPreAuthenticatedProcessingFilter {
    static final String GOOGLE_EMAIL_VERIFICATION_PATH = "/api/me/verify-linked-google-email";
    final FirebaseTokenVerifier tokenVerifier;
    final AuthenticatedUserService authenticatedUserService;

    public FirebaseTokenFilter(FirebaseTokenVerifier tokenVerifier, AuthenticatedUserService authenticatedUserService) {
        this.tokenVerifier = tokenVerifier;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Override
    protected @Nullable Object getPreAuthenticatedPrincipal(@NonNull HttpServletRequest request) {
        return null;
    }

    @Override
    protected @Nullable Object getPreAuthenticatedCredentials(@NonNull HttpServletRequest request) {
        return null;
    }

    @Override
    public void doFilter(@NonNull ServletRequest request, @NonNull ServletResponse response, @NonNull FilterChain chain)
            throws IOException, ServletException {
        var httpRequest = (HttpServletRequest)request;
        var token = tokenVerifier.verifyAuthorizationHeader(httpRequest.getHeader("Authorization"));
        if (token.isPresent()) {
            request.setAttribute("firebaseToken", token.get());
            try {
                var resolved = authenticatedUserService.resolve(token.get());
                request.setAttribute("user", resolved);
                SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(token.get(), resolved));
            } catch (AuthenticatedUserService.UnverifiedEmailException e) {
                if (httpRequest.getMethod().equals("POST") && httpRequest.getRequestURI().equals(GOOGLE_EMAIL_VERIFICATION_PATH)) {
                    SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(token.get()));
                    chain.doFilter(request, response);
                    return;
                }
                writeError((HttpServletResponse) response, 403, "EMAIL_VERIFICATION_REQUIRED",
                        "A verified email address is required");
                return;
            } catch (AuthenticatedUserService.AccountLinkConflictException e) {
                writeError((HttpServletResponse) response, 409, "ACCOUNT_LINKING_CONFLICT",
                        "This email address belongs to an account that uses a different sign-in method");
                return;
            }
        } else SecurityContextHolder.clearContext();
        chain.doFilter(request, response);
    }

    /** A ProblemDetail body like the rest of the API, plus a machine-readable {@code code} */
    private static void writeError(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonMapper.shared().writeValueAsString(Map.of(
                "type", "about:blank", "title", HttpStatus.valueOf(status).getReasonPhrase(), "status", status, "detail", detail, "code", code)));
    }
}
