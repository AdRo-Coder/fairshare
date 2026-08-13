package nz.ac.auckland.se310.fairshare;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@Profile("test")
public class TestSecurityConfig {
  // this exists because login system is currently not set up
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(
            csrf ->
                csrf.disable()) // disables csrf protection so spring security won't require CSRF
        // tokens(some requests may be denied if it is not disabled)
        .authorizeHttpRequests(
            auth ->
                auth.anyRequest()
                    .permitAll()); // allows everyone to access endpoints (no authentication
    // needed)
    return http.build();
  }
}
