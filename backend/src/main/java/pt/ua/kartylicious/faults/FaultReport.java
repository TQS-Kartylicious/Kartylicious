package pt.ua.kartylicious.faults;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record FaultReport(
    UUID id, String kartId, UUID userId, Category category, String description, Instant reportedAt) {
  @JsonProperty
  public String source() {
    return userId == null ? "SYSTEM" : "END_USER";
  }

  public enum Category {
    NORMAL,
    CRITICAL
  }
}
