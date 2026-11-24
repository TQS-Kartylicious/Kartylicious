package pt.ua.kartylicious.e2e.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import io.cucumber.java.Before;

public class DatabaseResetHooks {

  @Before(value = "@KARTY-35 or @KARTY-36 or @KARTY-37", order = 0)
  public void resetFleetState() throws Exception {
    Map<String, String> dotenv = loadDotenv();
    String db = getConfig("POSTGRES_DB", dotenv, "kartylicious");
    String user = getConfig("POSTGRES_USER", dotenv, "kartylicious");
    String pass = getConfig("POSTGRES_PASSWORD", dotenv, "kartylicious");
    String url =
        System.getenv()
            .getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/" + db);

    try (Connection conn = DriverManager.getConnection(url, user, pass);
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

  private static String getConfig(String key, Map<String, String> dotenv, String fallback) {
    String env = System.getenv(key);
    if (env != null && !env.isBlank()) {
      return env;
    }
    return dotenv.getOrDefault(key, fallback);
  }

  private static Map<String, String> loadDotenv() {
    Map<String, String> values = new HashMap<>();
    for (Path path : new Path[] {Path.of("../.env"), Path.of(".env")}) {
      if (Files.isRegularFile(path)) {
        try {
          for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
              continue;
            }
            int idx = trimmed.indexOf('=');
            String k = trimmed.substring(0, idx).trim();
            String v = trimmed.substring(idx + 1).trim().replaceAll("^[\"']|[\"']$", "");
            values.put(k, v);
          }
        } catch (Exception ignored) {
        }
        break;
      }
    }
    return values;
  }
}