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

class BookingNotFoundException extends BookingException {
  BookingNotFoundException() {
    super("Reservation not found.");
  }
}

class BookingAccessDeniedException extends BookingException {
  BookingAccessDeniedException() {
    super("Access denied");
  }
}

class ReservationNotCancellableException extends BookingException {
  ReservationNotCancellableException() {
    super("Only an active, unexpired reservation can be cancelled.");
  }
}
