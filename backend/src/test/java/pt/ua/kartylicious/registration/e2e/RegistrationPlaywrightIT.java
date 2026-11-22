package pt.ua.kartylicious.registration.e2e;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.Timeout;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;

@Tag("e2e")
@TestInstance(Lifecycle.PER_CLASS)
class RegistrationPlaywrightIT {

  private Playwright playwright;
  private Browser browser;

  @BeforeAll
  void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  void closeBrowser() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  @Requirement("KARTY-27")
  @Test
  @Timeout(60)
  void validRegistrationCreatesEndUserAndOpensDashboard() {
    var registrationRequests = new AtomicInteger();
    List<Response> registrationResponses = new ArrayList<>();
    String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String email = "e2e-" + token + "@example.com";
    String username = "e2e-" + token;
    String baseUrl = baseUrl();
    try (var page = newTrackingPage(registrationRequests, registrationResponses)) {
      page.navigate(baseUrl);
      fillForm(page, username, email, "strong-pass-123");
      submit(page);

      page.waitForURL("**/dashboard");
      PlaywrightAssertions.assertThat(page)
          .hasURL(baseUrl + "/dashboard");
      PlaywrightAssertions.assertThat(
              page.getByRole(
                  AriaRole.HEADING,
                  new Page.GetByRoleOptions().setName("Welcome, " + username + ".")))
          .isVisible();
      PlaywrightAssertions.assertThat(
              page.getByText("Your End User account is ready. This is your Kartylicious dashboard."))
          .isVisible();
      PlaywrightAssertions.assertThat(page.locator(".kart-sweep")).hasCount(1);
      Assertions.assertThat(registrationRequests.get()).isEqualTo(1);
      Assertions.assertThat(registrationResponses).hasSize(1);
      Assertions.assertThat(registrationResponses.getFirst().status()).isEqualTo(201);
      Assertions.assertThat(registrationResponses.getFirst().text())
          .contains("\"role\":\"END_USER\"");
    }
  }

  @Requirement("KARTY-27")
  @Test
  @Timeout(60)
  void duplicateEmailShowsEmailAlreadyInUse() {
    var registrationRequests = new AtomicInteger();
    List<Response> registrationResponses = new ArrayList<>();
    String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String email = "duplicate-" + token + "@example.com";
    String baseUrl = baseUrl();
    try (var page = newTrackingPage(registrationRequests, registrationResponses)) {
      page.navigate(baseUrl);
      fillForm(page, "first-" + token, email, "strong-pass-123");
      submit(page);

      page.waitForURL("**/dashboard");
      Assertions.assertThat(registrationRequests.get()).isEqualTo(1);

      page.navigate(baseUrl);
      fillForm(page, "duplicate-" + token, email, "strong-pass-123");
      submit(page);

      PlaywrightAssertions.assertThat(page.getByText("The email is already in use")).isVisible();
      Assertions.assertThat(registrationRequests.get()).isEqualTo(2);
      Assertions.assertThat(registrationResponses).hasSize(2);
      Assertions.assertThat(registrationResponses.getLast().status()).isEqualTo(409);
    }
  }

  @Requirement("KARTY-27")
  @Test
  @Timeout(60)
  void invalidEmailIsRejectedWithoutSubmitting() {
    var registrationRequests = new AtomicInteger();
    var page = newTrackingPage(registrationRequests, new ArrayList<>());
    String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    try {
      page.navigate(baseUrl());
      fillForm(page, "invalid-" + token, "not-an-email", "strong-pass-123");
      submit(page);

      PlaywrightAssertions.assertThat(page.getByText("The email format is incorrect")).isVisible();
      Assertions.assertThat(registrationRequests.get()).isZero();
    } finally {
      page.close();
    }
  }

  @Requirement("KARTY-27")
  @Test
  @Timeout(60)
  void shortPasswordIsRejectedWithoutSubmitting() {
    var registrationRequests = new AtomicInteger();
    var page = newTrackingPage(registrationRequests, new ArrayList<>());
    String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    try {
      page.navigate(baseUrl());
      fillForm(page, "short-password-" + token, "short-" + token + "@example.com", "short");

      submit(page);

      PlaywrightAssertions.assertThat(
              page.getByText("The password must be equal or longer than 8 characters"))
          .isVisible();
      Assertions.assertThat(registrationRequests.get()).isZero();
    } finally {
      page.close();
    }
  }

  private Page newTrackingPage(
      AtomicInteger registrationRequests, List<Response> registrationResponses) {
    var page = browser.newPage();
    page.onRequest(
        request -> {
          if (isRegistrationRequest(request.method(), request.url())) {
            registrationRequests.incrementAndGet();
          }
        });
    page.onResponse(
        response -> {
          if (isRegistrationRequest(response.request().method(), response.url())) {
            registrationResponses.add(response);
          }
        });
    return page;
  }

  private static String baseUrl() {
    String configured = System.getProperty("e2e.baseUrl", System.getenv("E2E_BASE_URL"));
    String baseUrl =
        configured == null || configured.isBlank() ? "http://localhost:3000" : configured;
    return baseUrl.replaceAll("/+$", "");
  }

  private static boolean isRegistrationRequest(String method, String url) {
    return "POST".equals(method) && url.endsWith("/api/registration");
  }

  private static void fillForm(Page page, String username, String email, String password) {
    page.getByLabel("Username").fill(username);
    page.getByLabel("Email").fill(email);
    page.getByLabel("Password").fill(password);
  }

  private static void submit(Page page) {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account")).click();
  }
}
