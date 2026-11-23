package pt.ua.kartylicious.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;

class JwtIssuerTest {

  @Requirement("KARTY-34")
  @Test
  void issuedTokenContainsRequiredClaimsAndExpiry() {
    var properties = new JwtProperties();
    properties.setSecret("test-only-secret-not-for-production-0123456789");
    properties.setExpirationMinutes(30);
    var configuration = new JwtConfiguration(properties);
    var encoder = configuration.jwtEncoder();
    var issuer = configuration.jwtTokenIssuer(encoder);
    var decoder = configuration.jwtDecoder();
    var account =
        new UserAccount(
            UUID.fromString("6a62f8c5-f4a4-4c54-827e-4e31b6ef728e"),
            "kartfan",
            "kart@example.com",
            "hashed-password",
            UserRole.END_USER);

    var issued = issuer.issue(account);
    var jwt = decoder.decode(issued.token());

    assertThat(jwt.getSubject()).isEqualTo(account.id().toString());
    assertThat(jwt.getClaimAsString("username")).isEqualTo("kartfan");
    assertThat(jwt.getClaimAsString("role")).isEqualTo("END_USER");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("kartylicious");
    assertThat(jwt.getIssuedAt()).isNotNull();
    assertThat(jwt.getExpiresAt()).isNotNull();
    assertThat(ChronoUnit.SECONDS.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(1800);
    assertThat(issued.expiresInSeconds()).isEqualTo(1800);
  }
}
