package pt.ua.kartylicious.e2e.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

public final class E2eDatabaseSupport {

  private E2eDatabaseSupport() {}

  public static Connection openConnection() throws Exception {
    var dotenv = loadDotenv();
    var database = getConfig("POSTGRES_DB", dotenv, "kartylicious");
    var user = getConfig("POSTGRES_USER", dotenv, "kartylicious");
    var password = getConfig("POSTGRES_PASSWORD", dotenv, "change-me");
    var url =
        System.getenv()
            .getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/" + database);
    return DriverManager.getConnection(url, user, password);
  }

  private static String getConfig(String key, Map<String, String> dotenv, String fallback) {
    var environmentValue = System.getenv(key);
    if (environmentValue != null && !environmentValue.isBlank()) {
      return environmentValue;
    }
    return dotenv.getOrDefault(key, fallback);
  }

  private static Map<String, String> loadDotenv() {
    Map<String, String> values = new HashMap<>();
    for (var path : new Path[] {Path.of("../.env"), Path.of(".env")}) {
      if (Files.isRegularFile(path)) {
        try {
          for (var line : Files.readAllLines(path)) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
              continue;
            }
            var separator = trimmed.indexOf('=');
            var key = trimmed.substring(0, separator).trim();
            var value =
                trimmed.substring(separator + 1).trim().replaceAll("^[\"']|[\"']$", "");
            values.put(key, value);
          }
        } catch (Exception ignored) {
          // Missing optional dotenv values fall back to the test defaults.
        }
        break;
      }
    }
    return values;
  }
}
