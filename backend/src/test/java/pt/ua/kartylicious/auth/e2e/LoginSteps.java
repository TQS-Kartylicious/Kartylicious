package pt.ua.kartylicious.auth.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;

public class LoginSteps {

  private Playwright playwright;
  private Browser browser;
  private Page page;
  private String email;
  private String password;

  @Before
  public void startBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    page = browser.newPage();
  }

  @After
  public void closeBrowser() {
    if (page != null) page.close();
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
  }

  @Given("I have a registered account")
  public void registerAccount() {
    password = "strong-pass-123";
    var token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    email = "login-" + token + "@example.com";
    page.navigate(baseUrl() + "/register");
    page.getByLabel("Username").fill("login-" + token);
    page.getByLabel("Email").fill(email);
    page.getByLabel("Password").fill(password);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account")).click();
    page.waitForURL("**/dashboard");
  }

  @Given("I am on the login screen")
  public void openLoginScreen() {
    page.navigate(baseUrl() + "/login");
  }

  @Given("I am logged in")
  public void ensureLoggedIn() {
    if (!page.url().contains("/dashboard")) {
      page.navigate(baseUrl() + "/login");
      submitLogin(email, password);
      page.waitForURL("**/dashboard");
    }
  }

  @Given("I am not logged in")
  public void clearSession() {
    page.navigate(baseUrl());
    page.evaluate("() => sessionStorage.clear()");
  }

  @When("I enter my valid email and password on the login screen")
  public void enterValidCredentials() {
    page.navigate(baseUrl() + "/login");
    submitLogin(email, password);
  }

  @When("I enter an unregistered email")
  public void enterUnregisteredEmail() {
    submitLogin("missing-" + UUID.randomUUID() + "@example.com", "strong-pass-123");
  }

  @When("I enter the incorrect password")
  public void enterIncorrectPassword() {
    submitLogin(email, "wrong-password");
  }

  @When("I click the {string} button")
  public void clickButton(String name) {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name)).click();
  }

  @When("I open the dashboard")
  public void openDashboard() {
    page.navigate(baseUrl() + "/dashboard");
  }

  @Then("I am redirected to the user dashboard")
  public void redirectedToDashboard() {
    PlaywrightAssertions.assertThat(page).hasURL(baseUrl() + "/dashboard");
  }

  @Then("access is denied and {string} is displayed")
  public void deniedWithMessage(String message) {
    PlaywrightAssertions.assertThat(page.getByText(message)).isVisible();
  }

  @Then("I am redirected to the login page")
  public void redirectedToLogin() {
    page.waitForURL("**/login");
    PlaywrightAssertions.assertThat(page).hasURL(baseUrl() + "/login");
  }

  private void submitLogin(String loginEmail, String loginPassword) {
    page.getByLabel("Email").fill(loginEmail);
    page.getByLabel("Password").fill(loginPassword);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
  }

  private static String baseUrl() {
    var configured = System.getProperty("e2e.baseUrl", System.getenv("E2E_BASE_URL"));
    var value = configured == null || configured.isBlank() ? "http://localhost:3000" : configured;
    return value.replaceAll("/+$", "");
  }
}
