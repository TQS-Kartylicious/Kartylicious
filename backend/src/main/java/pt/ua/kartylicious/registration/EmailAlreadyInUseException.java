package pt.ua.kartylicious.registration;

public class EmailAlreadyInUseException extends RuntimeException {

  public EmailAlreadyInUseException() {
    super("The email is already in use");
  }
}
