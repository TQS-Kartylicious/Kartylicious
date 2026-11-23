package pt.ua.kartylicious.admin;

public final class AdminExceptions {

  private AdminExceptions() {}

  public static class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException() {
      super("The email is already in use.");
    }
  }

  public static class UsernameAlreadyInUseException extends RuntimeException {
    public UsernameAlreadyInUseException() {
      super("The username is already in use.");
    }
  }

  public static class InvalidRoleException extends RuntimeException {
    public InvalidRoleException() {
      super("The role is invalid.");
    }
  }

  public static class UserNotFoundException extends RuntimeException {}

  public static class ActiveRideOrReservationException extends RuntimeException {
    public ActiveRideOrReservationException() {
      super("Cannot deactivate a user with an active ride or reservation.");
    }
  }

  public static class LastActiveAdminException extends RuntimeException {
    public LastActiveAdminException() {
      super("At least one active Admin is required.");
    }
  }

  public static class InvalidStatusException extends RuntimeException {
    public InvalidStatusException() {
      super("Only active=false is supported.");
    }
  }
}
