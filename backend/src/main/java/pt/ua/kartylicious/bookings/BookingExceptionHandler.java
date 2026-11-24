package pt.ua.kartylicious.bookings;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BookingExceptionHandler {
  @ExceptionHandler(BookingValidationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> validation(BookingValidationException exception) {
    return Map.of("message", exception.getMessage());
  }

  @ExceptionHandler(KartUnavailableException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> unavailable(KartUnavailableException exception) {
    return Map.of("message", exception.getMessage());
  }

  @ExceptionHandler(DepositAuthorizationException.class)
  @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
  public Map<String, String> deposit(DepositAuthorizationException exception) {
    return Map.of("message", exception.getMessage());
  }
}
