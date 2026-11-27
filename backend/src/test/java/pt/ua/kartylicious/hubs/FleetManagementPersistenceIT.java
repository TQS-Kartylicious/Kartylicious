package pt.ua.kartylicious.hubs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pt.ua.kartylicious.bookings.BookingRequest;
import pt.ua.kartylicious.bookings.BookingService;
import pt.ua.kartylicious.bookings.BookingStatus;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.rides.RideCompletionRequest;
import pt.ua.kartylicious.rides.RideRequest;
import pt.ua.kartylicious.rides.RideService;

@SpringBootTest
@Testcontainers
class FleetManagementPersistenceIT {
  private static final String OK = "OK";
  @Container private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private FleetManagementService fleet;
  @Autowired private HubDirectoryService directory;
  @Autowired private HubRepository hubs;
  @Autowired private FleetTelemetryService telemetry;
  @Autowired private BookingService bookings;
  @Autowired private BookingRepository bookingRepository;
  @Autowired private RideService rides;
  @Autowired private UserAccountRepository users;
  @Autowired private JdbcTemplate jdbc;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Test
  @Requirement({"KARTY-48", "KARTY-49", "KARTY-50"})
  void persistsHubEditAndInactiveRegistrationThenAppliesTelemetryAndActivates() {
    var hub = hub(2);
    var kart = register(hub);
    assertThat(jdbc.queryForObject("select telemetry_recorded_at is null from karts where id = ?",
        Boolean.class, kart.id())).isTrue();
    assertThat(fleet.updateHub(hub.id(), "New name", "New address", 1).id()).isEqualTo(hub.id());
    assertThat(hubs.findById(hub.id()).orElseThrow()).satisfies(saved -> {
      assertThat(saved.name()).isEqualTo("New name");
      assertThat(saved.address()).isEqualTo("New address");
      assertThat(saved.capacity()).isEqualTo(1);
      assertThat(saved.karts()).containsExactly(kart);
    });

    ingest(kart.id(), hub.id(), 19, false);
    assertThatThrownBy(() -> fleet.setKartActive(kart.id(), true)).hasMessageContaining("20%");
    ingest(kart.id(), hub.id(), 20, false);
    assertThat(currentKart(hub)).satisfies(saved -> {
      assertThat(saved.batteryLevel()).isEqualTo(20);
      assertThat(saved.active()).isFalse();
      assertThat(saved.status()).isEqualTo(KartStatus.INACTIVE);
    });
    fleet.setKartActive(kart.id(), true);
    assertThat(currentKart(hub).status()).isEqualTo(KartStatus.AVAILABLE);
    assertThat(directory.getHub(hub.id()).karts().getFirst().selectable()).isTrue();
  }

  @Test
  @Requirement("KARTY-49")
  void onlyOneConcurrentRegistrationCanFillTheFinalParkingSpace() throws Exception {
    var hub = hub(1);
    var outcomes = concurrently(() -> register(hub), () -> register(hub));
    assertThat(outcomes).containsExactlyInAnyOrder(OK, "The hub is full.");
    assertThat(hubs.findById(hub.id()).orElseThrow().occupancy()).isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-49")
  void concurrentDuplicateAcrossHubsCannotOverwriteTheWinningKart() throws Exception {
    var first = hub(1);
    var second = hub(1);
    var id = UUID.randomUUID().toString();
    assertThat(concurrently(
        () -> fleet.registerKart(id, first.id()), () -> fleet.registerKart(id, second.id())))
        .containsExactlyInAnyOrder(OK, "A mini-kart with this identifier already exists.");
    assertThat(jdbc.queryForObject("select count(*) from karts where id = ?", Integer.class, id))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("select hub_id from karts where id = ?", String.class, id))
        .isIn(first.id(), second.id());
  }

  @Test
  @Requirement({"KARTY-48", "KARTY-49"})
  void aCapacityReductionAndRegistrationCannotBothUseTheLastSpace() throws Exception {
    var hub = hub(2);
    register(hub);
    var outcomes = concurrently(() -> reduce(hub), () -> register(hub));
    assertThat(outcomes).isIn(List.of(OK, "The hub is full."),
        List.of("Hub capacity cannot be below current occupancy.", OK));
    var saved = hubs.findById(hub.id()).orElseThrow();
    assertThat(saved.capacity()).isEqualTo(saved.occupancy());
  }

  @Test
  @Requirement({"KARTY-48", "KARTY-38"})
  void aCapacityReductionAndRideReturnCannotBothUseTheLastSpace() throws Exception {
    var destination = hub(2);
    register(destination);
    var origin = hub(1);
    var kart = availableKart(origin);
    var rider = rider();
    var ride = rides.start(new RideRequest(origin.id(), kart.id()), rider);
    ingest(kart.id(), destination.id(), 21, false);

    var outcomes = concurrently(() -> reduce(destination),
        () -> rides.end(ride.id(), new RideCompletionRequest(destination.id()), rider));

    assertThat(outcomes).isIn(List.of(OK, "The hub is full"),
        List.of("Hub capacity cannot be below current occupancy.", OK));
    var saved = hubs.findById(destination.id()).orElseThrow();
    assertThat(saved.capacity()).isEqualTo(saved.occupancy());
    assertThat(rides.activeRide(rider).isPresent()).isEqualTo(!outcomes.get(1).equals(OK));
  }

  @Test
  @Requirement("KARTY-50")
  void deactivationBlocksRentalsWithoutCancellingOrReleasingExistingReservation() {
    var hub = hub(1);
    var kart = availableKart(hub);
    var user = rider();
    var starts = Instant.now().plusSeconds(60);
    var reservation = bookings.book(new BookingRequest(hub.id(), kart.id(), starts,
        starts.plusSeconds(60)), user);

    fleet.setKartActive(kart.id(), false);
    ingest(kart.id(), hub.id(), 90, false);

    assertThat(currentKart(hub).status()).isEqualTo(KartStatus.INACTIVE);
    assertThat(currentKart(hub).active()).isFalse();
    assertThatThrownBy(() -> bookings.book(new BookingRequest(
        hub.id(), kart.id(), starts.plusSeconds(60), starts.plusSeconds(120)), user))
        .hasMessage("The kart is no longer available.");
    assertThatThrownBy(() -> rides.start(new RideRequest(hub.id(), kart.id()), user))
        .hasMessage("The kart is no longer available.");
    fleet.setKartActive(kart.id(), true);
    assertThat(currentKart(hub).status()).isEqualTo(KartStatus.RESERVED);
    assertThat(bookingRepository.findById(reservation.id()).orElseThrow().status())
        .isEqualTo(BookingStatus.ACTIVE);
    assertThat(jdbc.queryForObject("select status from payment_authorizations where operation_id = ?",
        String.class, reservation.id())).isEqualTo("AUTHORIZED");
  }

  @Test
  @Requirement("KARTY-50")
  void aRideStartAndDeactivationCannotBothWin() throws Exception {
    var hub = hub(1);
    var kart = availableKart(hub);
    var rider = rider();
    assertThat(concurrently(() -> fleet.setKartActive(kart.id(), false),
        () -> rides.start(new RideRequest(hub.id(), kart.id()), rider)))
        .isIn(List.of(OK, "The kart is no longer available."),
            List.of("A mini-kart in use cannot be deactivated.", OK));
    assertThat(currentKart(hub).active()).isEqualTo(rides.activeRide(rider).isPresent());
  }

  @Test
  @Requirement("KARTY-50")
  void criticalTelemetryBlocksActivationEvenAfterLaterHealthyReading() {
    var hub = hub(1);
    var kart = register(hub);
    ingest(kart.id(), hub.id(), 21, true);
    ingest(kart.id(), hub.id(), 21, false);
    assertThatThrownBy(() -> fleet.setKartActive(kart.id(), true))
        .hasMessage("Resolve critical faults before activating this mini-kart.");
    assertThat(currentKart(hub).criticalFault()).isTrue();
    assertThat(currentKart(hub).active()).isFalse();
  }

  private List<String> concurrently(Callable<?> first, Callable<?> second) throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstResult = executor.submit(() -> race(ready, start, first));
      var secondResult = executor.submit(() -> race(ready, start, second));
      boolean readyTogether = ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      assertThat(readyTogether).isTrue();
      return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
    }
  }

  private String race(CountDownLatch ready, CountDownLatch start, Callable<?> operation)
      throws Exception {
    ready.countDown();
    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
    try {
      operation.call();
      return OK;
    } catch (RuntimeException exception) {
      return exception.getMessage();
    }
  }

  private Hub hub(int capacity) {
    return fleet.registerHub("Synthetic hub", "Test address", capacity);
  }

  private Hub reduce(Hub hub) {
    return fleet.updateHub(hub.id(), hub.name(), hub.address(), 1);
  }

  private Kart register(Hub hub) {
    return fleet.registerKart(UUID.randomUUID().toString(), hub.id());
  }

  private Kart availableKart(Hub hub) {
    var kart = register(hub);
    ingest(kart.id(), hub.id(), 21, false);
    return fleet.setKartActive(kart.id(), true);
  }

  private Kart currentKart(Hub hub) {
    return hubs.findById(hub.id()).orElseThrow().karts().getFirst();
  }

  private UUID rider() {
    var id = UUID.randomUUID();
    users.save(new UserAccount(id, "fleet-" + id, id + "@example.test", "unused", UserRole.END_USER));
    return id;
  }

  private void ingest(String kartId, String hubId, int battery, boolean fault) {
    telemetry.ingest(new TelemetryObservation(kartId, hubId, battery, false, fault, Instant.now()));
  }
}
