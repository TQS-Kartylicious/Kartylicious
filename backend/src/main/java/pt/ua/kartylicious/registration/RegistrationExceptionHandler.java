package pt.ua.kartylicious.registration;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RegistrationExceptionHandler {

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

  @ExceptionHandler(EmailAlreadyInUseException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateEmail(
      EmailAlreadyInUseException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler(UsernameAlreadyInUseException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateUsername(
      UsernameAlreadyInUseException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("message", exception.getMessage()));
  }
}
