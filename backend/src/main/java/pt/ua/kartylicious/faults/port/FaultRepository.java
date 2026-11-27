package pt.ua.kartylicious.faults.port;

import java.util.List;
import pt.ua.kartylicious.faults.FaultReport;
import pt.ua.kartylicious.faults.MaintenanceTicket;

public interface FaultRepository {
  void saveReport(FaultReport report);

  void saveTicket(MaintenanceTicket ticket);

  List<FaultReport> findReports();

  List<MaintenanceTicket> findTickets();
}
