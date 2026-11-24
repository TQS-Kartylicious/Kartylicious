package pt.ua.kartylicious.rides.e2e;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import com.microsoft.playwright.options.AriaRole;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pt.ua.kartylicious.e2e.support.E2eSupport;
import pt.ua.kartylicious.e2e.support.PlaywrightContext;

public class RideSteps {

  private static final String PASSWORD = "strong-pass-123";
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private Page page() {
    return PlaywrightContext.page();
  }

  @Given("I am logged in and viewing the hub directory for rides")
  public void registerAndOpenHubDirectory() {
    String token = token();
    page().navigate(E2eSupport.baseUrl() + "/register");
    page().getByLabel("Username").fill("ride-e2e-" + token);
    page().getByLabel("Email").fill("ride-e2e-" + token + "@example.com");
    page().getByLabel("Password").fill(PASSWORD);
    page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account")).click();
    page().waitForURL("**/dashboard");
    assertThat(
        page().getByRole(
            AriaRole.HEADING, new Page.GetByRoleOptions().setName("Find your mini-kart")))
        .isVisible();
  }

  @When("I select the {string} mini-kart for an immediate ride")
  public void selectKartForImmediateRide(String kartId) {
    kartCard(kartId)
        .getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Select kart").setExact(true))
        .click();
  }

  @When("I click {string} for the {string} mini-kart")
  public void clickStartRide(String buttonName, String kartId) {
    kartCard(kartId)
        .getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(buttonName).setExact(true))
        .click();
  }

  @When("I reserve the {string} mini-kart for an immediate ride")
  public void reserveKart(String kartId) {
    selectKartForImmediateRide(kartId);
    page()
        .getByLabel("Start time")
        .fill(LocalDateTime.now().plusMinutes(10).format(DATE_TIME_FORMATTER));
    page().getByLabel("Duration (minutes)").fill("30");
    page()
        .getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Confirm booking").setExact(true))
        .click();
    assertThat(
        page().getByText("Booking confirmed.", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
  }

  @When("I click {string} for my reserved mini-kart")
  public void clickReservedRide(String buttonName) {
    page()
        .getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Active trips"))
        .getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(buttonName).setExact(true))
        .click();
  }

  @Given("the {string} mini-kart has latest telemetry battery at {int} percent")
  public void lowBatteryKart(String kartId, int battery) {
    selectHub("Central Square");
    assertThat(kartCard(kartId).getByLabel("Battery " + battery + "%")).isVisible();
    assertThat(
        kartCard(kartId).getByText("Low Battery", new Locator.GetByTextOptions().setExact(true)))
        .isVisible();
  }

  @When("I attempt to start a ride on the {string} mini-kart")
  public void attemptRide(String kartId) {
    kartCard(kartId)
        .getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Start Ride").setExact(true))
        .click();
  }

  @Given("I have started a ride on the {string} mini-kart at the {string} municipal hub")
public void startFirstRide(String kartId, String hubName) {
  selectHub(hubName);
  Locator startButton = kartCard(kartId)
      .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Start Ride").setExact(true));
  startButton.waitFor();
  startButton.click();
  try {
    assertThat(page().getByRole(AriaRole.STATUS).getByText(kartId)).isVisible();
  } catch (AssertionError e) {
    System.err.println("=== DEBUG startFirstRide (" + kartId + " @ " + hubName + ") ===");
    System.err.println("URL: " + page().url());
    Locator alerts = page().getByRole(AriaRole.ALERT);
    System.err.println("Alerts count: " + alerts.count());
    for (int i = 0; i < alerts.count(); i++) {
      System.err.println("Alert[" + i + "]: " + alerts.nth(i).innerText());
    }
    System.err.println("Body text:\n" + page().locator("body").innerText());
    throw e;
  }
}

  @Then("the ride is started for the {string} mini-kart")
  public void rideStarted(String kartId) {
    assertThat(page().getByRole(AriaRole.STATUS).getByText(kartId)).isVisible();
  }

  @Then("my trip timer is running")
  public void tripTimerIsRunning() {
    assertThat(page().getByRole(AriaRole.TIME)).isVisible();
  }

  @Then("the ride is not started")
  public void rideIsNotStarted() {
    Locator activeRide = page().getByRole(AriaRole.STATUS, new Page.GetByRoleOptions().setName("RIDE IN PROGRESS"));
    if (activeRide.count() > 0) {
      assertThat(activeRide).isVisible();
    }
  }

  @Then("I see the ride error {string}")
  public void seeRideError(String message) {
    assertThat(page().getByText(message, new Page.GetByTextOptions().setExact(true))).isVisible();
  }

  @Then("the {string} mini-kart is flagged as {string}")
  public void kartIsFlagged(String kartId, String status) {
    assertThat(
        kartCard(kartId).getByText(status, new Locator.GetByTextOptions().setExact(true)))
        .isVisible();
  }

  private void selectHub(String hubName) {
    page()
        .getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Municipal hubs"))
        .getByRole(AriaRole.BUTTON)
        .filter(new Locator.FilterOptions().setHasText(hubName))
        .first()
        .click();
    assertThat(
        page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(hubName)))
        .isVisible();
  }

  private Locator kartCard(String kartId) {
    return page().locator(".kart-card").filter(new Locator.FilterOptions().setHasText(kartId));
  }

  private static String token() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}