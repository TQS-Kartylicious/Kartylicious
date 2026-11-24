package pt.ua.kartylicious.bookings;

class BookingException extends RuntimeException {
  BookingException(String message) {
    super(message);
  }
}

class BookingValidationException extends BookingException {
  BookingValidationException(String message) {
    super(message);
  }
}

class KartUnavailableException extends BookingException {
  KartUnavailableException() {
    super("The kart is no longer available.");
  }
}

class DepositAuthorizationException extends BookingException {
  DepositAuthorizationException() {
    super("Security deposit authorization failed.");
  }
}
