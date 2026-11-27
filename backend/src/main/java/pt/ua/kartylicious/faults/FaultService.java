package pt.ua.kartylicious.faults;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.faults.port.FaultRepository;
import pt.ua.kartylicious.hubs.port.HubRepository;

@Service
public class FaultService {
  private final FaultRepository faults;
  private final HubRepository hubs;
  private final Clock clock;

  public FaultService(FaultRepository faults, HubRepository hubs, Clock clock) {
    this.faults = faults;
    this.hubs = hubs;
    this.clock = clock;
  }

  @Transactional
  public void report(String kartId, UUID userId, FaultReport.Category category, String description) {
    if (description == null || description.isBlank()) {
      throw new InvalidFaultReportException("Describe the issue");
    }
    if (category == null) {
      throw new InvalidFaultReportException("Select a category");
    }
    if (kartId == null || kartId.isBlank()) {
      throw new InvalidFaultReportException("Select a mini-kart");
    }

    boolean critical = category == FaultReport.Category.CRITICAL;
    boolean kartExists =
        critical ? hubs.reportCriticalFault(kartId) : hubs.lockKartById(kartId).isPresent();
    if (!kartExists) {
      throw new FaultKartNotFoundException();
    }

    var now = clock.instant();
    var report = new FaultReport(UUID.randomUUID(), kartId, userId, category, description.strip(), now);
    faults.saveReport(report);
    if (critical) {
      openUrgentTicket(report, now);
    }
  }

  @Transactional
  public void reportCriticalTelemetry(String kartId, Instant observedAt) {
    var report = new FaultReport(
        UUID.randomUUID(), kartId, null, FaultReport.Category.CRITICAL,
        "Critical fault reported by fleet telemetry", observedAt);
    faults.saveReport(report);
    openUrgentTicket(report, clock.instant());
  }

  private void openUrgentTicket(FaultReport report, Instant openedAt) {
    faults.saveTicket(new MaintenanceTicket(
        UUID.randomUUID(), report.id(), report.kartId(), "URGENT", "OPEN", openedAt));
  }

  @Transactional(readOnly = true)
  public List<FaultReport> reports() {
    return faults.findReports();
  }

  @Transactional(readOnly = true)
  public List<MaintenanceTicket> tickets() {
    return faults.findTickets();
  }
}

class InvalidFaultReportException extends RuntimeException {
  InvalidFaultReportException(String message) {
    super(message);
  }
}

class FaultKartNotFoundException extends RuntimeException {
  FaultKartNotFoundException() {
    super("Mini-kart not found.");
  }
}
