package pt.ua.kartylicious.auth;

public class DeactivatedAccountException extends RuntimeException {

  public DeactivatedAccountException() {
    super("This account is deactivated.");
  }
}
