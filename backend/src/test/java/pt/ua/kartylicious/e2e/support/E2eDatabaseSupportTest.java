package pt.ua.kartylicious.e2e.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class E2eDatabaseSupportTest {
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"jdbc:postgresql://127.0.0.1:5432/kartylicious",
      "jdbc:postgresql://db.example.test:5432/kartylicious_e2e",
      "jdbc:postgresql://localhost:5432/kartylicious_e2e?currentSchema=ordinary"})
  void refusesMissingOrNonFixtureUrls(String url) {
    assertThatThrownBy(() -> E2eDatabaseSupport.requireFixtureConfig(url, "fixture", "synthetic"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("E2E_DB_URL");
  }

  @Test
  void requiresExplicitCredentialsForADisposableDatabase() {
    var url = "jdbc:postgresql://127.0.0.1:5433/kartylicious_e2e";
    assertThatThrownBy(() -> E2eDatabaseSupport.requireFixtureConfig(url, null, "synthetic"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("E2E_DB_USER");
    assertThatThrownBy(() -> E2eDatabaseSupport.requireFixtureConfig(url, "fixture", ""))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("E2E_DB_PASSWORD");
    E2eDatabaseSupport.requireFixtureConfig(url, "fixture", "synthetic");
  }
}
