package pt.ua.kartylicious.auth;

public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Incorrect email or password.");
  }
}
