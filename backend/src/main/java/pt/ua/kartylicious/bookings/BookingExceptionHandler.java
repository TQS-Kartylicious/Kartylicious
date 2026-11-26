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

  @ExceptionHandler(BookingNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> notFound(BookingNotFoundException exception) {
    return Map.of("message", exception.getMessage());
  }

  @ExceptionHandler(BookingAccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public Map<String, String> accessDenied(BookingAccessDeniedException exception) {
    return Map.of("message", exception.getMessage());
  }

  @ExceptionHandler(ReservationNotCancellableException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> notCancellable(ReservationNotCancellableException exception) {
    return Map.of("message", exception.getMessage());
  }
}
