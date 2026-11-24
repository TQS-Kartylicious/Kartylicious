package pt.ua.kartylicious.bookings.e2e;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class BookingSteps {

  private static final String PASSWORD = "strong-pass-123";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private Playwright playwright;
  private Browser browser;
  private Page page;

  @Before("@KARTY-36")
  public void startBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    page = browser.newPage();
  }

  @After("@KARTY-36")
  public void closeBrowser() {
    if (page != null) {
      page.close();
    }
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  @Given("I am logged in and viewing the hub directory for booking")
  public void registerAndOpenHubDirectory() {
    String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    page.navigate(baseUrl() + "/register");
    page.getByLabel("Username").fill("booking-e2e-" + token);
    page.getByLabel("Email").fill("booking-e2e-" + token + "@example.com");
    page.getByLabel("Password").fill(PASSWORD);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account")).click();
    page.waitForURL("**/dashboard");
    PlaywrightAssertions.assertThat(
            page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Active trips")))
        .isVisible();
  }

  @Given("the {string} mini-kart is available at the {string} municipal hub")
  public void selectAvailableKart(String kartId, String hubName) {
    page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Municipal hubs"))
        .getByRole(AriaRole.BUTTON)
        .filter(new Locator.FilterOptions().setHasText(hubName))
        .first()
        .click();
    PlaywrightAssertions.assertThat(
            kartCard(kartId).getByText("Available", new Locator.GetByTextOptions().setExact(true)))
        .isVisible();
  }

  @When("I select the {string} mini-kart")
  public void selectKart(String kartId) {
    kartCard(kartId)
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Select kart"))
        .click();
  }

  @When("I choose a future start time and a duration of {int} minutes")
  public void chooseBookingWindow(int duration) {
    String start = LocalDateTime.now().plusHours(2).format(DATE_TIME_FORMATTER);
    page.getByLabel("Start time").fill(start);
    page.getByLabel("Duration (minutes)").fill(String.valueOf(duration));
  }

  @When("I confirm the booking")
  public void confirmBooking() {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Confirm booking")).click();
  }

  @Then("the booking page shows {string}")
  public void seeMessage(String message) {
    PlaywrightAssertions.assertThat(
            page.getByText(message, new Page.GetByTextOptions().setExact(true)))
        .isVisible();
  }

  @Then("the {string} mini-kart has status {string}")
  public void kartHasStatus(String kartId, String status) {
    PlaywrightAssertions.assertThat(kartCard(kartId).getByText(status,
            new Locator.GetByTextOptions().setExact(true)))
        .isVisible();
  }

  @Then("my active trips include the {string} mini-kart")
  public void activeTripsIncludeKart(String kartId) {
    Locator activeTrips = page.getByRole(AriaRole.REGION,
        new Page.GetByRoleOptions().setName("Active trips"));
    PlaywrightAssertions.assertThat(activeTrips.getByText(kartId,
        new Locator.GetByTextOptions().setExact(true))).isVisible();
    PlaywrightAssertions.assertThat(activeTrips.getByText("Reserved",
        new Locator.GetByTextOptions().setExact(true))).isVisible();
  }

  private Locator kartCard(String kartId) {
    return page.locator(".kart-card").filter(new Locator.FilterOptions().setHasText(kartId));
  }

  private static String baseUrl() {
    String configured = System.getProperty("e2e.baseUrl", System.getenv("E2E_BASE_URL"));
    String value = configured == null || configured.isBlank()
        ? "http://localhost:3000"
        : configured;
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }
}
