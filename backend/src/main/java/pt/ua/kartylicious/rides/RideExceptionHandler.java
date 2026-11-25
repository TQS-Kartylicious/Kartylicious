package pt.ua.kartylicious.rides;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RideExceptionHandler {
  @ExceptionHandler(RideValidationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> validation(RideValidationException exception) {
    return Map.of("message", exception.getMessage());
  }

  @ExceptionHandler({RideKartUnavailableException.class, ActiveRideException.class})
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> conflict(RideException exception) {
    return Map.of("message", exception.getMessage());
  }

  @ExceptionHandler(LowBatteryException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> lowBattery(LowBatteryException exception) {
    return Map.of("message", exception.getMessage());
  }

  @ExceptionHandler(RideDepositAuthorizationException.class)
  @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
  public Map<String, String> deposit(RideDepositAuthorizationException exception) {
    return Map.of("message", exception.getMessage());
  }
}
