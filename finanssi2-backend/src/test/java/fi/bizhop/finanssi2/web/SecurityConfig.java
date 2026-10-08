package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.security.User;
import jakarta.servlet.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@Profile("test")
public class SecurityConfig {
    /** Request header with a uid; the request then carries a {@link User} as if its Firebase token had been verified */
    public static final String TEST_USER_HEADER = "X-Test-User";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .addFilterBefore(testUserFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static Filter testUserFilter() {
        return (request, response, chain) -> {
            var uid = ((jakarta.servlet.http.HttpServletRequest) request).getHeader(TEST_USER_HEADER);
            if (uid != null) {
                request.setAttribute("user", new User(uid, uid + "@example.com", "Player " + uid, null, true, uid));
            }
            chain.doFilter(request, response);
        };
    }
}
