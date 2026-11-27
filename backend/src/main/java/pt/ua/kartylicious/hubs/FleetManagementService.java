package pt.ua.kartylicious.hubs;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.hubs.port.HubRepository;

@Service
public class FleetManagementService {
  private static final int MINIMUM_BATTERY = 20;
  private static final int MAXIMUM_TEXT_LENGTH = 255;
  private static final Pattern KART_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
  private static final String DUPLICATE_KART = "A mini-kart with this identifier already exists.";
  private final HubRepository hubs;
  private final BookingRepository bookings;
  private final Clock clock;

  public FleetManagementService(HubRepository hubs, BookingRepository bookings, Clock clock) {
    this.hubs = hubs;
    this.bookings = bookings;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Hub> hubs() {
    return hubs.findAll();
  }

  @Transactional
  public Hub registerHub(String name, String address, Integer capacity) {
    validateHub(name, address, capacity);
    var hub = new Hub(UUID.randomUUID().toString(), name.strip(), address.strip(), capacity, List.of());
    hubs.insertHub(hub);
    return hub;
  }

  @Transactional
  public Hub updateHub(String hubId, String name, String address, Integer capacity) {
    validateHub(name, address, capacity);
    var current = lockHub(hubId);
    if (capacity < current.occupancy()) {
      throw new Conflict("Hub capacity cannot be below current occupancy.");
    }
    var updated = new Hub(hubId, name.strip(), address.strip(), capacity, current.karts());
    hubs.updateHub(updated);
    return updated;
  }

  @Transactional
  public Kart registerKart(String kartId, String hubId) {
    if (kartId == null || !KART_ID.matcher(kartId).matches()) {
      throw new Invalid("Enter a mini-kart identifier of 1–64 letters, digits, hyphens or underscores.");
    }
    var hub = lockHub(hubId);
    if (hubs.kartExists(kartId)) {
      throw new Conflict(DUPLICATE_KART);
    }
    if (hub.occupancy() >= hub.capacity()) {
      throw new Conflict("The hub is full.");
    }
    var kart = new Kart(kartId, kartId, 0, KartStatus.INACTIVE, false, false);
    try {
      hubs.insertKart(hubId, kart);
    } catch (DataIntegrityViolationException exception) {
      // The identifier is the primary key. A concurrent insert at another hub can win.
      throw new Conflict(DUPLICATE_KART, exception);
    }
    return kart;
  }

  @Transactional
  public Kart setKartActive(String kartId, Boolean active) {
    if (active == null) {
      throw new Invalid("The active flag is required.");
    }
    var kart = hubs.lockKartById(kartId).orElseThrow(() -> new Missing("Mini-kart not found."));
    if (kart.status() == KartStatus.RETIRED) {
      throw new Conflict("Retired mini-karts cannot be reactivated.");
    }
    if (!active && kart.status() == KartStatus.IN_USE) {
      throw new Conflict("A mini-kart in use cannot be deactivated.");
    }
    if (active) {
      if (kart.criticalFault()) {
        throw new Conflict("Resolve critical faults before activating this mini-kart.");
      }
      if (kart.status() == KartStatus.IN_MAINTENANCE) {
        throw new Conflict("Complete maintenance before activating this mini-kart.");
      }
      if (kart.batteryLevel() < MINIMUM_BATTERY) {
        throw new Conflict("The battery must be at least 20% to activate this mini-kart.");
      }
    }
    if (kart.active() == active) {
      return kart;
    }
    KartStatus status = KartStatus.INACTIVE;
    if (active) {
      status = bookings.hasUnexpiredReservation(kartId, clock.instant())
          ? KartStatus.RESERVED : KartStatus.AVAILABLE;
    } else if (kart.status() == KartStatus.IN_MAINTENANCE) {
      status = KartStatus.IN_MAINTENANCE;
    }
    hubs.updateKartAvailability(kartId, active, status);
    return new Kart(kartId, kart.name(), kart.batteryLevel(), status, active, kart.criticalFault());
  }

  private Hub lockHub(String hubId) {
    if (hubId == null || hubId.isBlank()) {
      throw new Missing("Invalid hub.");
    }
    return hubs.lockHub(hubId).orElseThrow(() -> new Missing("Invalid hub."));
  }

  private void validateHub(String name, String address, Integer capacity) {
    validateText(name, "Hub name");
    validateText(address, "Hub location");
    if (capacity == null || capacity < 1) {
      throw new Invalid("Hub capacity must be a positive integer.");
    }
  }

  private void validateText(String value, String label) {
    if (value == null || value.isBlank() || value.strip().length() > MAXIMUM_TEXT_LENGTH) {
      throw new Invalid(label + " must contain 1–255 characters.");
    }
  }

  static class Invalid extends RuntimeException {
    Invalid(String message) {
      super(message);
    }
  }

  static class Missing extends RuntimeException {
    Missing(String message) {
      super(message);
    }
  }

  static class Conflict extends RuntimeException {
    Conflict(String message) {
      super(message);
    }

    Conflict(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
