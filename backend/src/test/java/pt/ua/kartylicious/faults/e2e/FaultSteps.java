package pt.ua.kartylicious.faults.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pt.ua.kartylicious.e2e.support.PlaywrightContext;

public class FaultSteps {
  @When("I open Report Fault for the {string} mini-kart")
  public void openFaultForm(String kartId) {
    page().getByRole(AriaRole.ARTICLE, new Page.GetByRoleOptions().setName(kartId).setExact(true))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Report Fault"))
        .click();
  }

  @When("I select fault category {string}")
  public void selectCategory(String category) {
    form().getByLabel("Category").selectOption(category);
  }

  @When("I describe the issue as {string}")
  public void describe(String description) {
    form().getByLabel("Description").fill(description);
  }

  @When("I submit the fault form")
  public void submit() {
    form().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Submit")).click();
  }

  @Then("the fault form asks me to describe the issue")
  public void descriptionRequired() {
    assertThat(form().getByRole(AriaRole.ALERT)).hasText("Describe the issue");
    assertThat(page().getByText("Report submitted", new Page.GetByTextOptions().setExact(true)))
        .hasCount(0);
  }

  @Then("I cannot select the {string} mini-kart for booking")
  public void cannotSelectForBooking(String kartId) {
    assertThat(page()
        .getByRole(AriaRole.ARTICLE, new Page.GetByRoleOptions().setName(kartId).setExact(true))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Select kart")))
        .isDisabled();
  }

  private Locator form() {
    return page().getByRole(AriaRole.FORM, new Page.GetByRoleOptions().setName("Report Fault"));
  }

  private Page page() {
    return PlaywrightContext.page();
  }
}
