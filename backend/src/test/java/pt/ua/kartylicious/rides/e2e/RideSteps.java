package pt.ua.kartylicious.rides.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.RequestOptions;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import pt.ua.kartylicious.e2e.support.E2eDatabaseSupport;
import pt.ua.kartylicious.e2e.support.E2eSupport;
import pt.ua.kartylicious.e2e.support.PlaywrightContext;

public class RideSteps {

  private static final String PASSWORD = "strong-pass-123";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
  private String riderEmail;

  private Page page() {
    return PlaywrightContext.page();
  }

  private APIResponse secondRideResponse;

  @Given("I am logged in and viewing the hub directory for rides")
  public void registerAndOpenHubDirectory() {
    String token = token();
    page().navigate(E2eSupport.baseUrl() + "/register");
    page().getByLabel("Username").fill("ride-e2e-" + token);
    riderEmail = "ride-e2e-" + token + "@example.com";
    page().getByLabel("Email").fill(riderEmail);
    page().getByLabel("Password").fill(PASSWORD);
    page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account")).click();
    page().waitForURL("**/dashboard");
    assertThat(
        page().getByRole(
            AriaRole.HEADING, new Page.GetByRoleOptions().setName("Find your mini-kart")))
        .isVisible();
  }

  @When("I reload the ride dashboard")
  public void reloadDashboard() {
    page().reload();
  }

  @When("I log out and back in as the rider")
  public void logBackIn() {
    page().getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Logout").setExact(true)).click();
    page().waitForURL("**/login");
    page().getByLabel("Email").fill(riderEmail);
    page().getByLabel("Password").fill(PASSWORD);
    page().getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in").setExact(true)).click();
    page().waitForURL("**/dashboard");
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
  public void reserveKart(String kartId) throws Exception {
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
    makeReservationCurrent(kartId);
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

  @Then("the Start Ride action for the {string} mini-kart is disabled")
  public void startRideIsDisabled(String kartId) {
    assertThat(kartCard(kartId).getByRole(
        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Start Ride").setExact(true)))
        .isDisabled();
  }

  @Then("the active ride is still shown for the {string} mini-kart")
  public void activeRideIsShown(String kartId) {
    assertThat(page().getByRole(AriaRole.STATUS).getByText(kartId)).isVisible();
    assertThat(page().getByRole(AriaRole.TIME)).isVisible();
    assertThat(page().getByText("No active trips.", new Page.GetByTextOptions().setExact(true)))
        .hasCount(0);
  }

  @When("I request to start another ride on the {string} mini-kart through the API")
  public void requestSecondRide(String kartId) {
    var token = page().evaluate("sessionStorage.getItem('kartylicious.auth.token')");
    secondRideResponse = page().request().post(E2eSupport.baseUrl() + "/api/rides",
        RequestOptions.create().setHeader("Authorization", "Bearer " + token)
            .setData(Map.of("hubId", "central-square", "kartId", kartId)));
  }

  @Then("I cannot start a ride on the {string} mini-kart")
  public void cannotStartRide(String kartId) {
    assertThat(
            kartCard(kartId)
                .getByRole(
                    AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName("Start Ride").setExact(true)))
        .hasCount(0);
  }

  @Given("I have started a ride on the {string} mini-kart at the {string} municipal hub")
  public void startFirstRide(String kartId, String hubName) {
    selectHub(hubName);
    Locator startButton =
        kartCard(kartId)
            .getByRole(
                AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Start Ride").setExact(true));
    startButton.waitFor();
    startButton.click();
    assertThat(page().getByRole(AriaRole.STATUS).getByText(kartId)).isVisible();
  }

  @Then("the ride is started for the {string} mini-kart")
  public void rideStarted(String kartId) {
    assertThat(page().getByRole(AriaRole.STATUS).getByText(kartId)).isVisible();
  }

  @Then("my trip timer is running")
  public void tripTimerIsRunning() {
    assertThat(page().getByRole(AriaRole.TIME)).isVisible();
  }

  @Given("latest telemetry reports the {string} mini-kart at the {string} municipal hub")
  public void reportLatestTelemetry(String kartId, String hubName) throws Exception {
    try (var connection = E2eDatabaseSupport.openConnection();
        var statement =
            connection.prepareStatement(
                "update karts set telemetry_hub_id = ?, telemetry_recorded_at = current_timestamp "
                    + "where id = ?")) {
      statement.setString(1, hubId(hubName));
      statement.setString(2, kartId);
      statement.executeUpdate();
    }
  }

  @When("I end the active ride at the {string} municipal hub")
  public void endActiveRide(String hubName) {
    page().getByLabel("Destination hub").selectOption(hubId(hubName));
    page()
        .getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("End Ride").setExact(true))
        .click();
  }

  @Given("the {string} municipal hub has no free capacity")
  public void hubHasNoFreeCapacity(String hubName) throws Exception {
    try (var connection = E2eDatabaseSupport.openConnection();
        var statement =
            connection.prepareStatement(
                "update hubs set capacity = (select count(*) from karts "
                    + "where hub_id = ? and status <> 'IN_USE') where id = ?")) {
      var hubId = hubId(hubName);
      statement.setString(1, hubId);
      statement.setString(2, hubId);
      statement.executeUpdate();
    }
  }

  @Then("the ride is completed")
  public void rideIsCompleted() {
    assertThat(
            page()
                .getByRole(
                    AriaRole.STATUS,
                    new Page.GetByRoleOptions().setName("Ride completed")))
        .isVisible();
  }

  @Then("the final charge is displayed")
  public void finalChargeIsDisplayed() {
    assertThat(page().getByText(Pattern.compile("Final charge: €\\d+\\.\\d{2}"))).isVisible();
  }

  @Then("my active ride remains in progress")
  public void activeRideRemainsInProgress() {
    assertThat(page().getByRole(AriaRole.TIME)).isVisible();
  }

  @Then("the API rejects the second ride with {string}")
  public void secondRideIsRejected(String message) {
    org.assertj.core.api.Assertions.assertThat(secondRideResponse.status()).isEqualTo(409);
    org.assertj.core.api.Assertions.assertThat(secondRideResponse.text()).contains(message);
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

  private String hubId(String hubName) {
    return switch (hubName) {
      case "Central Square" -> "central-square";
      case "Riverside Park" -> "riverside-park";
      case "University Gardens" -> "university-gardens";
      default -> throw new IllegalArgumentException("Unknown hub: " + hubName);
    };
  }

  private void makeReservationCurrent(String kartId) throws Exception {
    try (var connection = E2eDatabaseSupport.openConnection();
        var statement =
            connection.prepareStatement(
                "update bookings set starts_at = current_timestamp - interval '1 minute', "
                    + "ends_at = current_timestamp + interval '30 minutes' "
                    + "where kart_id = ? and status = 'ACTIVE'")) {
      statement.setString(1, kartId);
      statement.executeUpdate();
    }
  }

  private static String token() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}
