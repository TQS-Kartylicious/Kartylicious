package pt.ua.kartylicious.admin.port;

import java.util.UUID;

public interface UserActivityPort {

  boolean hasActiveRideOrReservation(UUID userId);
}
