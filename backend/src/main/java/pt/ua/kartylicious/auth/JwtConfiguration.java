package pt.ua.kartylicious.auth;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import pt.ua.kartylicious.auth.port.JwtTokenIssuer;
import pt.ua.kartylicious.registration.UserAccount;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

  private final JwtProperties properties;

  public JwtConfiguration(JwtProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void validateProperties() {
    properties.validate();
  }

  @Bean
  JwtDecoder jwtDecoder() {
    var secretKey =
        new SecretKeySpec(
            properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  JwtEncoder jwtEncoder() {
    var secretKey =
        new SecretKeySpec(
            properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return NimbusJwtEncoder.withSecretKey(secretKey).build();
  }

  @Bean
  JwtTokenIssuer jwtTokenIssuer(JwtEncoder encoder) {
    return new JwtTokenIssuer() {
      @Override
      public IssuedToken issue(UserAccount account) {
        var now = Instant.now();
        var expiry =
            now.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);
        var claims =
            JwtClaimsSet.builder()
                .issuer("kartylicious")
                .subject(account.id().toString())
                .issuedAt(now)
                .expiresAt(expiry)
                .claim("username", account.username())
                .claim("role", account.role().name())
                .build();
        var token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new IssuedToken(
            token, properties.getExpirationMinutes() * 60);
      }
    };
  }

  @Bean
  Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
    return jwt -> {
      var role = jwt.getClaimAsString("role");
      if (role == null || role.isBlank()) {
        return List.of();
      }
      return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    };
  }
}
