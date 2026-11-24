package pt.ua.kartylicious.hubs.e2e;

import java.util.Map;
import java.util.UUID;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pt.ua.kartylicious.e2e.support.E2eSupport;
import pt.ua.kartylicious.e2e.support.PlaywrightContext;

public class HubDiscoverySteps {

  private static final String PASSWORD = "strong-pass-123";

  private Page page() {
    return PlaywrightContext.page();
  }

  @Given("I am logged in and viewing the hub directory")
  public void registerAndOpenHubDirectory() {
    String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    page().navigate(E2eSupport.baseUrl() + "/register");
    page().getByLabel("Username").fill("hub-e2e-" + token);
    page().getByLabel("Email").fill("hub-e2e-" + token + "@example.com");
    page().getByLabel("Password").fill(PASSWORD);
    page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account")).click();
    page().waitForURL("**/dashboard");
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING, new Page.GetByRoleOptions().setName("Find your mini-kart")))
        .isVisible();
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.NAVIGATION,
                    new Page.GetByRoleOptions().setName("Municipal hubs")))
        .isVisible();
  }

  @When("I select the {string} municipal hub")
  public void selectMunicipalHub(String name) {
    hubButton(name).click();
    PlaywrightAssertions.assertThat(
            page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(name)))
        .isVisible();
  }

  @Then("I see the parked mini-karts with their battery levels and availability statuses")
  public void seeFleetAndState(DataTable karts) {
    var expectedKarts = karts.asMaps(String.class, String.class);
    for (Map<String, String> expected : expectedKarts) {
      Locator card = kartCard(expected.get("id"));
      PlaywrightAssertions.assertThat(
              card.getByRole(
                  AriaRole.HEADING, new Locator.GetByRoleOptions().setName(expected.get("name"))))
          .isVisible();
      PlaywrightAssertions.assertThat(
              card.getByLabel("Battery " + expected.get("battery") + "%"))
          .isVisible();
      PlaywrightAssertions.assertThat(
              card.getByText(
                  expected.get("status"), new Locator.GetByTextOptions().setExact(true)))
          .isVisible();
    }
  }

  @Then("only available karts with at least 20 percent battery can be selected")
  public void verifyKartEligibility(DataTable karts) {
    var expectedKarts = karts.asMaps(String.class, String.class);
    for (Map<String, String> expected : expectedKarts) {
      Locator card = kartCard(expected.get("id"));
      Locator selectButton = selectKartButton(card);
      boolean expectedSelectable = Boolean.parseBoolean(expected.get("selectable"));
      if (expectedSelectable) {
        PlaywrightAssertions.assertThat(selectButton).isEnabled();
      } else {
        PlaywrightAssertions.assertThat(selectButton).isDisabled();
      }
      PlaywrightAssertions.assertThat(
              card.getByLabel("Battery " + expected.get("battery") + "%"))
          .isVisible();
      PlaywrightAssertions.assertThat(
              card.getByText(
                  expected.get("status"), new Locator.GetByTextOptions().setExact(true)))
          .isVisible();
    }
  }

  @Then("I can select an eligible kart for booking")
  public void selectEligibleKart() {
    Locator eligibleKart = kartCard("K-102");
    selectKartButton(eligibleKart).click();
    Locator selectedButton =
        eligibleKart.getByRole(
            AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Selected").setExact(true));
    PlaywrightAssertions.assertThat(selectedButton).isVisible();
    PlaywrightAssertions.assertThat(selectedButton).hasAttribute("aria-pressed", "true");
  }

  @Then("I see {string}")
  public void seeMessage(String message) {
    PlaywrightAssertions.assertThat(
            page().getByText(message, new Page.GetByTextOptions().setExact(true)))
        .isVisible();
  }

  private Locator hubButton(String name) {
    return page()
        .getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Municipal hubs"))
        .getByRole(AriaRole.BUTTON)
        .filter(new Locator.FilterOptions().setHasText(name))
        .first();
  }

  private Locator kartCard(String id) {
    return page().locator(".kart-card").filter(new Locator.FilterOptions().setHasText(id));
  }

  private Locator selectKartButton(Locator card) {
    return card.getByRole(
        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Select kart").setExact(true));
  }
}