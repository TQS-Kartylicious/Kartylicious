package pt.ua.kartylicious.e2e.support;

public final class E2eSupport {

  private E2eSupport() {}

  public static String baseUrl() {
    var configured = System.getProperty("e2e.baseUrl", System.getenv("E2E_BASE_URL"));
    var value = configured == null || configured.isBlank() ? "http://localhost:3000" : configured;
    return removeTrailingSlashes(value);
  }

  public static String environmentOr(String propertyName, String envName, String fallback) {
    var property = System.getProperty(propertyName);
    var environment = System.getenv(envName);
    if (property != null && !property.isBlank()) {
      return property;
    }
    if (environment != null && !environment.isBlank()) {
      return environment;
    }
    return fallback;
  }

  public static String removeTrailingSlashes(String value) {
    var result = value;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
