package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.config.Finanssi2Properties;
import fi.bizhop.finanssi2.security.User;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The production filter chain with the Firebase token check swapped for a test header: the same authorization rules (the configured
 * whitelist, public avatars, everything else signed-in only, 401 otherwise), so tests see what an unauthenticated client would.
 */
@Configuration
@EnableWebSecurity
@Profile("test")
public class SecurityConfig {
    /** Request header with a uid; the request then carries a {@link User} as if its Firebase token had been verified */
    public static final String TEST_USER_HEADER = "X-Test-User";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Finanssi2Properties properties) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(properties.endpoints().whitelist().toArray(String[]::new)).permitAll()
                        .requestMatchers("/api/users/*/avatar").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> response.sendError(401)))
                .addFilterBefore(testUserFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static Filter testUserFilter() {
        return (request, response, chain) -> {
            var uid = ((HttpServletRequest) request).getHeader(TEST_USER_HEADER);
            if (uid != null) {
                request.setAttribute("user", new User(uid, uid + "@example.com", "Player " + uid, null, true, uid));
                SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(uid, null, "ROLE_USER"));
            }
            chain.doFilter(request, response);
        };
    }
}
