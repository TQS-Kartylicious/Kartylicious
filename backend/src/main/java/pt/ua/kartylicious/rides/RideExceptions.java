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

class RideNotFoundException extends RideException {
  RideNotFoundException() {
    super("Ride not found.");
  }
}

class RideAccessDeniedException extends RideException {
  RideAccessDeniedException() {
    super("Access denied");
  }
}

class RideAlreadyCompletedException extends RideException {
  RideAlreadyCompletedException() {
    super("The ride has already been completed.");
  }
}

class UnauthorizedHubException extends RideException {
  UnauthorizedHubException() {
    super("Park at an authorized hub");
  }
}

class HubFullException extends RideException {
  HubFullException() {
    super("The hub is full");
  }
}

class FinalPaymentException extends RideException {
  FinalPaymentException() {
    super("Final payment failed.");
  }
}

class VehicleLockException extends RideException {
  VehicleLockException() {
    super("The mini-kart could not be locked.");
  }
}

class RideCompletionException extends RideException {
  RideCompletionException() {
    super("The ride could not be completed.");
  }
}
