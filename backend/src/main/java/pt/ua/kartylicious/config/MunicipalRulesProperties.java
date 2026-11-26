package pt.ua.kartylicious.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "municipal.rules")
public class MunicipalRulesProperties {

  @Positive private long maximumReservationDurationMinutes = 60;
  @Positive private long maximumRideDurationMinutes = 60;
  @DecimalMin("0.0") private BigDecimal baseFare = new BigDecimal("1.00");
  @DecimalMin("0.0") private BigDecimal perMinuteRate = new BigDecimal("0.25");
  @DecimalMin("0.0") private BigDecimal overtimeFee = new BigDecimal("2.00");

  public long getMaximumReservationDurationMinutes() {
    return maximumReservationDurationMinutes;
  }

  public void setMaximumReservationDurationMinutes(long maximumReservationDurationMinutes) {
    this.maximumReservationDurationMinutes = maximumReservationDurationMinutes;
  }

  public long getMaximumRideDurationMinutes() {
    return maximumRideDurationMinutes;
  }

  public void setMaximumRideDurationMinutes(long maximumRideDurationMinutes) {
    this.maximumRideDurationMinutes = maximumRideDurationMinutes;
  }

  public BigDecimal getBaseFare() {
    return baseFare;
  }

  public void setBaseFare(BigDecimal baseFare) {
    this.baseFare = baseFare;
  }

  public BigDecimal getPerMinuteRate() {
    return perMinuteRate;
  }

  public void setPerMinuteRate(BigDecimal perMinuteRate) {
    this.perMinuteRate = perMinuteRate;
  }

  public BigDecimal getOvertimeFee() {
    return overtimeFee;
  }

  public void setOvertimeFee(BigDecimal overtimeFee) {
    this.overtimeFee = overtimeFee;
  }
}
