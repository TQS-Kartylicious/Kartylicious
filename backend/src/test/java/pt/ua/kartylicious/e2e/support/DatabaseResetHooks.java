package pt.ua.kartylicious.e2e.support;

import io.cucumber.java.Before;
import java.sql.Statement;

public class DatabaseResetHooks {

  @Before(
      value = "@KARTY-35 or @KARTY-36 or @KARTY-37 or @KARTY-38 or @KARTY-39 or @KARTY-40",
      order = 0)
  public void resetFleetState() throws Exception {
    try (var conn = E2eDatabaseSupport.openConnection();
        Statement stmt = conn.createStatement()) {
      stmt.executeUpdate("DELETE FROM payment_settlements");
      stmt.executeUpdate("DELETE FROM payment_authorizations");
      stmt.executeUpdate("DELETE FROM rides");
      stmt.executeUpdate("DELETE FROM bookings");
      stmt.executeUpdate("DELETE FROM maintenance_tickets");
      stmt.executeUpdate("DELETE FROM fault_reports");
      stmt.executeUpdate("UPDATE karts SET critical_fault = (id = 'K-303')");
      stmt.executeUpdate(
          "UPDATE karts SET hub_id = CASE "
              + "WHEN id LIKE 'K-1%' THEN 'central-square' "
              + "WHEN id LIKE 'K-2%' THEN 'riverside-park' "
              + "ELSE 'university-gardens' END");
      stmt.executeUpdate(
          "UPDATE karts SET status = 'AVAILABLE' WHERE id IN ('K-101', 'K-102', 'K-301')");
      stmt.executeUpdate("UPDATE karts SET status = 'LOW_BATTERY' WHERE id = 'K-103'");
      stmt.executeUpdate("UPDATE karts SET status = 'IN_USE' WHERE id = 'K-104'");
      stmt.executeUpdate("UPDATE karts SET status = 'RESERVED' WHERE id = 'K-105'");
      stmt.executeUpdate(
          "UPDATE karts SET status = 'IN_MAINTENANCE' WHERE id IN ('K-106', 'K-303')");
      stmt.executeUpdate("UPDATE karts SET status = 'INACTIVE' WHERE id = 'K-302'");
      stmt.executeUpdate(
          "UPDATE karts SET locked = status <> 'IN_USE', telemetry_hub_id = NULL, "
              + "telemetry_recorded_at = NULL");
      stmt.executeUpdate(
          "UPDATE hubs h SET capacity = "
              + "(SELECT count(*) FROM karts k WHERE k.hub_id = h.id)");
    }
  }
}
