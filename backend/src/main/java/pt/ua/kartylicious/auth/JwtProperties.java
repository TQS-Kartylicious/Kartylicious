package pt.ua.kartylicious.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

  private String secret;
  private long expirationMinutes = 60;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getExpirationMinutes() {
    return expirationMinutes;
  }

  public void setExpirationMinutes(long expirationMinutes) {
    this.expirationMinutes = expirationMinutes;
  }

  public void validate() {
    if (secret == null
        || secret.isBlank()
        || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
          "JWT_SECRET must be set and contain at least 32 bytes");
    }
    if (expirationMinutes <= 0) {
      throw new IllegalStateException("JWT_EXPIRATION_MINUTES must be greater than zero");
    }
  }
}
