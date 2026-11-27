package pt.ua.kartylicious.hubs.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.RequestOptions;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import java.util.UUID;
import pt.ua.kartylicious.e2e.support.E2eDatabaseSupport;
import pt.ua.kartylicious.e2e.support.E2eSupport;
import pt.ua.kartylicious.e2e.support.PlaywrightContext;
import tools.jackson.databind.json.JsonMapper;

public class OperatorFleetSteps {
  private final String suffix = UUID.randomUUID().toString();
  private final String hubName = "Fleet hub " + suffix;
  private final String newHubName = "Renamed " + hubName;
  private final String kartId = "fleet-" + suffix;
  private String hubId;

  @Given("I am signed in as a fleet operator")
  public void loginAsOperator() {
    var email = "operator-" + suffix + "@example.test";
    var password = "synthetic-fleet-password";
    var adminEmail = E2eSupport.environmentOr(
        "e2e.admin.email", "E2E_ADMIN_EMAIL", "admin@kartylicious.test");
    var adminPassword = E2eSupport.environmentOr(
        "e2e.admin.password", "E2E_ADMIN_PASSWORD", "change-me-admin-pass");
    var client = page().request();
    var login = client.post(E2eSupport.baseUrl() + "/api/auth/login", RequestOptions.create()
        .setData(Map.of("user", adminEmail, "password", adminPassword)));
    org.assertj.core.api.Assertions.assertThat(login.status()).isEqualTo(200);
    var token = JsonMapper.builder().build().readTree(login.text()).get("token").asText();
    var creation = client.post(E2eSupport.baseUrl() + "/api/admin/users", RequestOptions.create()
        .setHeader("Authorization", "Bearer " + token)
        .setData(Map.of("username", "operator-" + suffix, "email", email,
            "password", password, "role", "OPERATOR")));
    org.assertj.core.api.Assertions.assertThat(creation.status()).isEqualTo(201);

    page().navigate(E2eSupport.baseUrl() + "/login");
    page().getByLabel("Email").fill(email);
    page().getByLabel("Password").fill(password);
    page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
    page().waitForURL("**/dashboard");
    assertThat(page().getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Fleet management"))).isVisible();
    assertThat(page().getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Fault reports"))).isVisible();
  }

  @When("I register an operator hub with capacity {int}")
  public void createHub(int capacity) {
    var form = form("Register hub");
    fillHub(form, hubName, "Original location", capacity);
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Register hub")).click();
    fleetMessage("Hub registered.");
    assertThat(hubRow(hubName)).isVisible();
    hubId = option(hubName + " — Original location").getAttribute("value");
    org.assertj.core.api.Assertions.assertThat(hubId).isNotBlank();
  }

  @When("I update the hub name and location with capacity {int}")
  public void editHub(int capacity) {
    hubRow(hubName).getByRole(
        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Edit hub")).click();
    var form = form("Edit hub");
    fillHub(form, newHubName, "Updated location", capacity);
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save hub")).click();
  }

  @When("I register the test mini-kart at that operator hub")
  public void registerKart() {
    submitKart(kartId);
  }

  @When("I register another test mini-kart at that operator hub")
  public void registerAnotherKart() {
    submitKart(kartId + "-other");
  }

  @When("I register two mini-karts at that operator hub")
  public void registerTwoKarts() {
    registerKart();
    assertThat(kartRow()).isVisible();
    registerAnotherKart();
    occupancy(2, 3);
  }

  @When("the test mini-kart receives a fixture observation of {int} percent")
  public void receiveFixture(int battery) throws Exception {
    assertThat(kartRow()).isVisible();
    try (var conn = E2eDatabaseSupport.openConnection();
        var update = conn.prepareStatement("""
            update karts set battery_level = ?, telemetry_recorded_at = current_timestamp,
              telemetry_hub_id = ? where id = ? and hub_id = ?
            """)) {
      update.setInt(1, battery);
      update.setString(2, hubId);
      update.setString(3, kartId);
      update.setString(4, hubId);
      org.assertj.core.api.Assertions.assertThat(update.executeUpdate()).isEqualTo(1);
    }
    page().reload();
    assertThat(kartRow()).containsText(battery + "%");
  }

  @When("I choose {string} for the test mini-kart")
  public void changeAvailability(String action) {
    kartRow().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(action)
        .setExact(true)).click();
  }

  @Then("the fleet management message is {string}")
  public void fleetMessage(String message) {
    assertThat(page().getByRole(AriaRole.STATUS)
        .filter(new Locator.FilterOptions().setHasText(message))).hasText(message);
  }

  @Then("the fleet management error is {string}")
  public void fleetError(String message) {
    assertThat(page().getByRole(AriaRole.ALERT)
        .filter(new Locator.FilterOptions().setHasText(message))).hasText(message);
  }

  @Then("the hub shows {int} of {int} spaces occupied")
  public void occupancy(int occupied, int capacity) {
    assertThat(hubRow(hubName)).containsText(occupied + " / " + capacity + " spaces occupied");
  }

  @Then("the renamed hub keeps its identifier and shows {int} of {int} spaces occupied")
  public void updatedHub(int occupied, int capacity) {
    assertThat(hubRow(newHubName))
        .containsText("Updated location · " + occupied + " / " + capacity + " spaces occupied");
    assertThat(option(newHubName + " — Updated location")).hasAttribute("value", hubId);
    assertThat(hubRow(hubName)).hasCount(0);
  }

  @Then("the test mini-kart shows {int} percent with status {string} and active state {string}")
  public void kartStatus(int battery, String status, String active) {
    assertThat(kartRow()).containsText(battery + "% · " + status + " · " + active);
  }

  @After("@operator-fleet")
  public void removeSyntheticHub() throws Exception {
    try (var conn = E2eDatabaseSupport.openConnection();
        var karts = conn.prepareStatement(
            "delete from karts where hub_id in (select id from hubs where name in (?, ?))");
        var hubs = conn.prepareStatement("delete from hubs where name in (?, ?)")) {
      conn.setAutoCommit(false);
      for (var statement : java.util.List.of(karts, hubs)) {
        statement.setString(1, hubName);
        statement.setString(2, newHubName);
        statement.executeUpdate();
      }
      conn.commit();
    }
  }

  private void fillHub(Locator form, String name, String address, int capacity) {
    form.getByLabel("Hub name").fill(name);
    form.getByLabel("Location").fill(address);
    form.getByLabel("Capacity").fill(Integer.toString(capacity));
  }

  private void submitKart(String identifier) {
    var form = form("Register mini-kart");
    form.getByLabel("Mini-kart identifier").fill(identifier);
    form.getByLabel("Parking hub").selectOption(hubId);
    form.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Register mini-kart"))
        .click();
  }

  private Locator option(String label) {
    return form("Register mini-kart").locator("option")
        .filter(new Locator.FilterOptions().setHasText(label));
  }

  private Locator hubRow(String name) {
    return page().getByRole(AriaRole.ARTICLE, new Page.GetByRoleOptions().setName(name).setExact(true));
  }

  private Locator kartRow() {
    return hubRow(hubName).locator("li[aria-label='" + kartId + "']");
  }

  private Locator form(String name) {
    return page().getByRole(AriaRole.FORM, new Page.GetByRoleOptions().setName(name).setExact(true));
  }

  private Page page() {
    return PlaywrightContext.page();
  }
}
