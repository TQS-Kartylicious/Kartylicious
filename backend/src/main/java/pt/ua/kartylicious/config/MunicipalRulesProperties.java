package pt.ua.kartylicious.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "municipal.rules")
public class MunicipalRulesProperties {

  @Positive private long maximumReservationDurationMinutes = 60;

  public long getMaximumReservationDurationMinutes() {
    return maximumReservationDurationMinutes;
  }

  public void setMaximumReservationDurationMinutes(long maximumReservationDurationMinutes) {
    this.maximumReservationDurationMinutes = maximumReservationDurationMinutes;
  }
}
