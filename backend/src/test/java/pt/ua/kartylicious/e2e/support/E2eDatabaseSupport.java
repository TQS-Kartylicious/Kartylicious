package pt.ua.kartylicious.e2e.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;

public final class E2eDatabaseSupport {
  private static final Pattern FIXTURE_URL =
      Pattern.compile("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}/kartylicious_e2e");

  private E2eDatabaseSupport() {}

  public static Connection openConnection() throws SQLException {
    var url = System.getenv("E2E_DB_URL");
    var user = System.getenv("E2E_DB_USER");
    var password = System.getenv("E2E_DB_PASSWORD");
    requireFixtureConfig(url, user, password);
    var connection = DriverManager.getConnection(url, user, password);
    try (var statement = connection.createStatement();
        var result = statement.executeQuery("""
            select shobj_description(oid, 'pg_database') from pg_database
            where datname = current_database() and datname = 'kartylicious_e2e'
            """)) {
      if (!result.next() || !"kartylicious-disposable-e2e".equals(result.getString(1))) {
        throw new IllegalStateException("Refusing to use a database without the E2E fixture marker.");
      }
      return connection;
    } catch (SQLException | RuntimeException exception) {
      try {
        connection.close();
      } catch (SQLException closeFailure) {
        exception.addSuppressed(closeFailure);
      }
      throw exception;
    }
  }

  static void requireFixtureConfig(String url, String user, String password) {
    if (url == null || !FIXTURE_URL.matcher(url).matches()
        || user == null || user.isBlank() || password == null || password.isBlank()) {
      throw new IllegalStateException(
          "Set E2E_DB_URL to the loopback kartylicious_e2e database and supply E2E_DB_USER "
              + "and E2E_DB_PASSWORD from the disposable fixture stack.");
    }
  }
}
