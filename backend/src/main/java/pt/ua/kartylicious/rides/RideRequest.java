package pt.ua.kartylicious.rides;

import java.util.UUID;

public record RideRequest(String hubId, String kartId, UUID bookingId) {
  public RideRequest(String hubId, String kartId) {
    this(hubId, kartId, null);
  }
}
