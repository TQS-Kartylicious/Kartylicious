package pt.ua.kartylicious.hubs;

public enum KartStatus {
  AVAILABLE,
  LOW_BATTERY,
  INACTIVE,
  IN_USE,
  RESERVED,
  IN_MAINTENANCE,
  RETIRED;

  public String displayName() {
    return switch (this) {
      case AVAILABLE -> "Available";
      case LOW_BATTERY -> "Low Battery";
      case INACTIVE -> "Inactive";
      case IN_USE -> "In Use";
      case RESERVED -> "Reserved";
      case IN_MAINTENANCE -> "In Maintenance";
      case RETIRED -> "Retired";
    };
  }
}
