package pt.ua.kartylicious.faults.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pt.ua.kartylicious.faults.MaintenanceTicket;

@Entity
@Table(name = "maintenance_tickets")
class JpaMaintenanceTicket {
  @Id private UUID id;
  @Column(nullable = false, unique = true) private UUID reportId;
  @Column(nullable = false) private String kartId;
  @Column(nullable = false) private String priority;
  @Column(nullable = false) private String status;
  @Column(nullable = false) private Instant openedAt;

  protected JpaMaintenanceTicket() {}

  JpaMaintenanceTicket(MaintenanceTicket ticket) {
    id = ticket.id();
    reportId = ticket.reportId();
    kartId = ticket.kartId();
    priority = ticket.priority();
    status = ticket.status();
    openedAt = ticket.openedAt();
  }

  MaintenanceTicket toTicket() {
    return new MaintenanceTicket(id, reportId, kartId, priority, status, openedAt);
  }
}
