package pt.ua.kartylicious.admin;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AdminExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(
      MethodArgumentNotValidException exception) {
    var errors =
        exception.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    error -> error.getField(),
                    error -> error.getDefaultMessage(),
                    (firstMessage, ignored) -> firstMessage));
    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler({
      AdminExceptions.EmailAlreadyInUseException.class,
      AdminExceptions.UsernameAlreadyInUseException.class,
      AdminExceptions.ActiveRideOrReservationException.class,
      AdminExceptions.LastActiveAdminException.class,
      AdminExceptions.InvalidRoleException.class,
      AdminExceptions.InvalidStatusException.class
  })
  public ResponseEntity<Map<String, String>> handleConflict(RuntimeException exception) {
    var status =
        exception instanceof AdminExceptions.InvalidRoleException
            || exception instanceof AdminExceptions.InvalidStatusException
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler(AdminExceptions.UserNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(
      AdminExceptions.UserNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of());
  }
}
