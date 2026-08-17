package nz.ac.auckland.se310.fairshare.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Default security chain. Superseded by {@link DevSecurityConfig} under the "dev"
 * profile, because Spring Security rejects two chains that both match any request.
 *
 * <p>TODO(#NN): no authentication mechanism is configured yet, so every endpoint
 * except /users/register is currently unreachable.
 */
@Configuration
@Profile("!dev")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/users/register").permitAll()
                                .anyRequest().authenticated());
        return http.build();
    }
}