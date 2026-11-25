package pt.ua.kartylicious.rides;

class RideException extends RuntimeException {
  RideException(String message) {
    super(message);
  }
}

class RideValidationException extends RideException {
  RideValidationException(String message) {
    super(message);
  }
}

class RideKartUnavailableException extends RideException {
  RideKartUnavailableException() {
    super("The kart is no longer available.");
  }
}

class LowBatteryException extends RideException {
  LowBatteryException() {
    super("Kart has low battery.");
  }
}

class ActiveRideException extends RideException {
  ActiveRideException() {
    super("Only one active trip is allowed per user.");
  }
}

class RideDepositAuthorizationException extends RideException {
  RideDepositAuthorizationException() {
    super("Security deposit authorization failed.");
  }
}
