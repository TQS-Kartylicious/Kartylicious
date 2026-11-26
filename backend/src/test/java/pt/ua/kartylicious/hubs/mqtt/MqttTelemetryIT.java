package pt.ua.kartylicious.hubs.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.faults.FaultReport;
import pt.ua.kartylicious.faults.FaultService;

@SpringBootTest
@Testcontainers
@Tag("BR14")
class MqttTelemetryIT {
  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Container
  private static final GenericContainer<?> BROKER = new GenericContainer<>("eclipse-mosquitto:2")
      .withCopyToContainer(
          Transferable.of("listener 1883\nallow_anonymous true\n"),
          "/mosquitto/config/mosquitto.conf")
      .withExposedPorts(1883);

  @Autowired private JdbcTemplate jdbc;
  @Autowired private BookingRepository bookings;
  @Autowired private FaultService faults;
  private MqttClient publisher;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("fleet.mqtt.enabled", () -> "true");
    registry.add("fleet.mqtt.url", MqttTelemetryIT::brokerUrl);
  }

  @BeforeEach
  void connectPublisher() throws Exception {
    publisher = new MqttClient(brokerUrl(), "telemetry-test-" + UUID.randomUUID(),
        new MemoryPersistence());
    publisher.connect();
    // A retained observation also works if the asynchronous subscriber is still connecting.
    send("K-101", observation(84, "null", true, false, now()), true);
    eventually(() -> count("karts where id = 'K-101' and telemetry_recorded_at is not null") == 1);
  }

  @AfterEach
  void closePublisher() throws Exception {
    publisher.disconnect();
    publisher.close();
  }

  @Test
  @Requirement("KARTY-38")
  void persistsOrderedObservationsWithoutMovingTheKartOrCorruptingAnActiveRide() throws Exception {
    var first = now().minusSeconds(2);
    send("K-104", observation(19, "\"riverside-park\"", false, false, first), false);
    eventually(() -> count("karts where id = 'K-104' and battery_level = 19") == 1);
    send("K-104", observation(100, "null", true, true, first.plusNanos(500)), false);
    send("K-104", observation(99, "null", true, true, first.minusSeconds(1)), false);
    send("K-104", observation(101, "null", true, true, first.plusMillis(100)), false);
    send("K-104", observation(88, "\"unknown-hub\"", true, true, first.plusMillis(200)), false);
    send("K-104", observation(88, "null", true, true, now().plusSeconds(20)), false);
    send("K-104", "{\"batteryLevel\":42}", false);
    send("unknown", observation(88, "null", true, true, first.plusMillis(300)), false);
    send("K-104", observation(21, "\"riverside-park\"", false, false, first.plusSeconds(1)), false);
    eventually(() -> count("karts where id = 'K-104' and battery_level = 21") == 1);
    assertThat(count("karts where id = 'K-104' and hub_id = 'central-square' "
        + "and telemetry_hub_id = 'riverside-park' and status = 'IN_USE' "
        + "and not locked and not critical_fault")).isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-39")
  void batteryRecoveryRespectsReservationsAndCriticalFaultsAreStickyAndDeduplicated()
      throws Exception {
    var first = now().minusSeconds(2);
    bookings.save(new Booking(UUID.randomUUID(), UUID.randomUUID(), "central-square", "K-102",
        now().plusSeconds(600), now().plusSeconds(1200), BookingStatus.ACTIVE));
    send("K-102", observation(19, "null", true, false, first), false);
    eventually(() -> hasStatus("K-102", "LOW_BATTERY"));
    send("K-102", observation(20, "null", true, false, first.plusMillis(100)), false);
    eventually(() -> hasStatus("K-102", "RESERVED"));
    send("K-102", observation(21, "null", true, true, first.plusMillis(200)), false);
    eventually(() -> hasStatus("K-102", "IN_MAINTENANCE"));
    send("K-102", observation(22, "null", true, true, first.plusMillis(300)), false);
    send("K-102", observation(23, "null", true, false, first.plusMillis(400)), false);
    eventually(() -> count("karts where id = 'K-102' and battery_level = 23") == 1);
    assertThat(hasStatus("K-102", "IN_MAINTENANCE")).isTrue();
    assertThat(count("karts where id = 'K-102' and critical_fault")).isEqualTo(1);
    assertThat(count("maintenance_tickets where kart_id = 'K-102' and priority = 'URGENT'"))
        .isEqualTo(1);
    assertThat(count("fault_reports where kart_id = 'K-102' and user_id is null")).isEqualTo(1);

    send("K-302", observation(99, "null", true, true, first), false);
    eventually(() -> count("karts where id = 'K-302' and battery_level = 99") == 1);
    assertThat(hasStatus("K-302", "INACTIVE")).isTrue();
    send("K-303", observation(99, "null", true, false, first), false);
    eventually(() -> count("karts where id = 'K-303' and battery_level = 99") == 1);
    assertThat(count("karts where id = 'K-303' and status = 'IN_MAINTENANCE' and critical_fault"))
        .isEqualTo(1);
    faults.report("K-301", UUID.randomUUID(), FaultReport.Category.CRITICAL, "Manual fault");
    send("K-301", observation(81, "null", true, false, first), false);
    eventually(() -> count("karts where id = 'K-301' and battery_level = 81") == 1);
    assertThat(count("karts where id = 'K-301' and status = 'IN_MAINTENANCE' and critical_fault"))
        .isEqualTo(1);
    assertThat(count("fault_reports where kart_id = 'K-301' and user_id is not null")).isEqualTo(1);
    assertThat(count("maintenance_tickets where kart_id = 'K-301'")).isEqualTo(1);
  }

  private boolean hasStatus(String kartId, String status) {
    return status.equals(jdbc.queryForObject("select status from karts where id = ?", String.class, kartId));
  }

  private static String brokerUrl() {
    return "tcp://" + BROKER.getHost() + ":" + BROKER.getMappedPort(1883);
  }

  private static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private int count(String expression) {
    return jdbc.queryForObject("select count(*) from " + expression, Integer.class);
  }

  private void send(String kartId, String body, boolean retained) throws Exception {
    publisher.publish("kartylicious/karts/" + kartId + "/telemetry",
        body.getBytes(StandardCharsets.UTF_8), 1, retained);
  }

  private static String observation(
      int battery, String hub, boolean locked, boolean critical, Instant observedAt) {
    return """
        {"batteryLevel":%d,"hubId":%s,"locked":%s,"criticalFault":%s,"recordedAt":"%s"}
        """.formatted(battery, hub, locked, critical, observedAt);
  }

  private static void eventually(BooleanSupplier condition) throws Exception {
    var until = System.nanoTime() + java.time.Duration.ofSeconds(8).toNanos();
    while (!condition.getAsBoolean() && System.nanoTime() < until) {
      Thread.sleep(25);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }
}
