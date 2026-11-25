package pt.ua.kartylicious.e2e.support;

import io.cucumber.java.Before;
import java.sql.Statement;

public class DatabaseResetHooks {

  @Before(value = "@KARTY-35 or @KARTY-36 or @KARTY-37", order = 0)
  public void resetFleetState() throws Exception {
    try (var conn = E2eDatabaseSupport.openConnection();
        Statement stmt = conn.createStatement()) {
      stmt.executeUpdate("DELETE FROM rides");
      stmt.executeUpdate("DELETE FROM bookings");
      stmt.executeUpdate(
          "UPDATE karts SET status = 'AVAILABLE' WHERE id IN ('K-101', 'K-102', 'K-301')");
      stmt.executeUpdate("UPDATE karts SET status = 'LOW_BATTERY' WHERE id = 'K-103'");
      stmt.executeUpdate("UPDATE karts SET status = 'IN_USE' WHERE id = 'K-104'");
      stmt.executeUpdate("UPDATE karts SET status = 'RESERVED' WHERE id = 'K-105'");
      stmt.executeUpdate(
          "UPDATE karts SET status = 'IN_MAINTENANCE' WHERE id IN ('K-106', 'K-303')");
      stmt.executeUpdate("UPDATE karts SET status = 'INACTIVE' WHERE id = 'K-302'");
    }
  }
}
