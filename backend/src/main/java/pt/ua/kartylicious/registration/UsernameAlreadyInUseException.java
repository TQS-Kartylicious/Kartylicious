package pt.ua.kartylicious.registration;

public class UsernameAlreadyInUseException extends RuntimeException {

  public UsernameAlreadyInUseException() {
    super("The username is already in use");
  }
}
