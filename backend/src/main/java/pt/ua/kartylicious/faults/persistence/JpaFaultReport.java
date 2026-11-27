package pt.ua.kartylicious.faults.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pt.ua.kartylicious.faults.FaultReport;

@Entity
@Table(name = "fault_reports")
class JpaFaultReport {
  @Id private UUID id;
  @Column(nullable = false) private String kartId;
  private UUID userId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FaultReport.Category category;
  @Column(nullable = false, columnDefinition = "text") private String description;
  @Column(nullable = false) private Instant reportedAt;

  protected JpaFaultReport() {}

  JpaFaultReport(FaultReport report) {
    id = report.id();
    kartId = report.kartId();
    userId = report.userId();
    category = report.category();
    description = report.description();
    reportedAt = report.reportedAt();
  }

  FaultReport toReport() {
    return new FaultReport(id, kartId, userId, category, description, reportedAt);
  }
}
