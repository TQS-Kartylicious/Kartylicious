package pt.ua.kartylicious.auth;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(
      MethodArgumentNotValidException exception) {
    var errors =
        exception.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    error -> "user".equals(error.getField()) ? "email" : error.getField(),
                    error -> error.getDefaultMessage(),
                    (firstMessage, ignored) -> firstMessage));
    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<Map<String, String>> handleInvalidCredentials(
      InvalidCredentialsException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler(DeactivatedAccountException.class)
  public ResponseEntity<Map<String, String>> handleDeactivatedAccount(
      DeactivatedAccountException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("message", exception.getMessage()));
  }
}
