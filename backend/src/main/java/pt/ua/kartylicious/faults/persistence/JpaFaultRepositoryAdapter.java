package pt.ua.kartylicious.faults.persistence;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;
import pt.ua.kartylicious.faults.FaultReport;
import pt.ua.kartylicious.faults.MaintenanceTicket;
import pt.ua.kartylicious.faults.port.FaultRepository;

@Repository
public class JpaFaultRepositoryAdapter implements FaultRepository {
  private final EntityManager entityManager;

  public JpaFaultRepositoryAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public void saveReport(FaultReport report) {
    entityManager.persist(new JpaFaultReport(report));
  }

  @Override
  public void saveTicket(MaintenanceTicket ticket) {
    entityManager.persist(new JpaMaintenanceTicket(ticket));
  }

  @Override
  public List<FaultReport> findReports() {
    return entityManager
        .createQuery("select r from JpaFaultReport r order by r.reportedAt desc", JpaFaultReport.class)
        .getResultList().stream()
        .map(JpaFaultReport::toReport)
        .toList();
  }

  @Override
  public List<MaintenanceTicket> findTickets() {
    return entityManager
        .createQuery(
            "select t from JpaMaintenanceTicket t order by t.openedAt desc", JpaMaintenanceTicket.class)
        .getResultList().stream()
        .map(JpaMaintenanceTicket::toTicket)
        .toList();
  }
}
