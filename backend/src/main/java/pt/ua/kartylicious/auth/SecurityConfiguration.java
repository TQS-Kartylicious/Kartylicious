package pt.ua.kartylicious.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.UUID;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

  private final UserAccountRepository userAccountRepository;

  public SecurityConfiguration(UserAccountRepository userAccountRepository) {
    this.userAccountRepository = userAccountRepository;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter =
        jwt -> {
          try {
            var account =
                userAccountRepository
                    .findById(UUID.fromString(jwt.getSubject()))
                    .orElseThrow(
                        () -> new InvalidBearerTokenException("Authentication required"));
            if (!account.active()) {
              throw new InvalidBearerTokenException("Authentication required");
            }
            return new JwtAuthenticationToken(
                jwt,
                java.util.List.of(
                    new SimpleGrantedAuthority("ROLE_" + account.role().name())),
                account.username());
          } catch (IllegalArgumentException exception) {
            throw new InvalidBearerTokenException("Authentication required", exception);
          }
        };

    http.csrf(csrf -> csrf.disable()) // NOSONAR - Stateless bearer auth is not cookie/session based.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/api/registration",
                        "/api/auth/login",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resource ->
                resource
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          response.setStatus(401);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response
                              .getWriter()
                              .write("{\"message\":\"Authentication required\"}");
                        }))
        .exceptionHandling(
            exceptions ->
                exceptions.accessDeniedHandler(
                    (request, response, exception) -> {
                      response.setStatus(403);
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      response.getWriter().write("{\"message\":\"Access denied\"}");
                    }));

    return http.build();
  }
}
