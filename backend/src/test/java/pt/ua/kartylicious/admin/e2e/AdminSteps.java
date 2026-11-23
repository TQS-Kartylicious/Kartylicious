package pt.ua.kartylicious.admin.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.options.RequestOptions;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import pt.ua.kartylicious.e2e.support.E2eSupport;

public class AdminSteps {

  private Playwright playwright;
  private Browser browser;
  private Page page;
  private String adminEmail;
  private String adminPassword;
  private String adminToken;
  private String knownEmail;
  private String knownPassword;
  private String knownUserId;

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

  @Given("I am logged in as the seeded admin")
  public void loginAsSeededAdmin() {
    adminEmail =
        E2eSupport.environmentOr("e2e.admin.email", "E2E_ADMIN_EMAIL",
            "admin@kartylicious.test");
    adminPassword =
        E2eSupport.environmentOr("e2e.admin.password", "E2E_ADMIN_PASSWORD",
            "change-me-admin-pass");
    adminToken = loginAndGetToken(adminEmail, adminPassword);
    page.navigate(E2eSupport.baseUrl() + "/login");
    submitLogin(adminEmail, adminPassword);
    page.waitForURL("**/dashboard");
  }

  @When("I create an operator account")
  public void createOperatorAccount() {
    page.navigate(E2eSupport.baseUrl() + "/admin/users");
    var token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    page.getByLabel("Username").fill("staff-" + token);
    page.getByLabel("Email").fill("staff-" + token + "@example.com");
    page.getByLabel("Password").fill("strong-pass-123");
    page.getByLabel("Role").selectOption("OPERATOR");
    page.getByRole(AriaRole.BUTTON,
        new com.microsoft.playwright.Page.GetByRoleOptions().setName("Create account")).click();
  }

  @Given("an account already exists with a known email")
  public void createKnownAccount() {
    createPreconditionAccount("END_USER");
  }

  @When("I create another account with that email")
  public void createDuplicateStaffAccount() {
    page.navigate(E2eSupport.baseUrl() + "/admin/users");
    page.getByLabel("Username").fill("duplicate-" + UUID.randomUUID());
    page.getByLabel("Email").fill(knownEmail);
    page.getByLabel("Password").fill("strong-pass-123");
    page.getByLabel("Role").selectOption("OPERATOR");
    page.getByRole(AriaRole.BUTTON,
        new com.microsoft.playwright.Page.GetByRoleOptions().setName("Create account")).click();
  }

  @Given("a user account exists")
  @Given("an active user account exists")
  public void createActiveUserAccount() {
    createPreconditionAccount("END_USER");
  }

  @When("I change that user's role to Municipal Worker")
  public void changeRole() {
    page.navigate(E2eSupport.baseUrl() + "/admin/users");
    var row = page.locator(".form-field").filter(new com.microsoft.playwright.Locator.FilterOptions()
        .setHasText(knownEmail));
    row.getByRole(com.microsoft.playwright.options.AriaRole.COMBOBOX,
        new com.microsoft.playwright.Locator.GetByRoleOptions()).selectOption("MUNICIPAL_WORKER");
    row.getByRole(AriaRole.BUTTON,
        new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Save role")).click();
  }

  @When("I deactivate that account")
  public void deactivateAccount() {
    page.navigate(E2eSupport.baseUrl() + "/admin/users");
    var row = page.locator(".form-field").filter(new com.microsoft.playwright.Locator.FilterOptions()
        .setHasText(knownEmail));
    row.getByRole(AriaRole.BUTTON,
        new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Deactivate")).click();
  }

  @When("that user tries to log in")
  public void deactivatedUserLogsIn() {
    page.navigate(E2eSupport.baseUrl() + "/login");
    submitLogin(knownEmail, knownPassword);
  }

  @Given("no other active admin exists")
  public void ensureOnlySeededAdmin() {
    // E2E setup should run against a fresh database; the scenario requires it.
  }

  @When("I deactivate my own account")
  public void deactivateSeededAdmin() {
    page.navigate(E2eSupport.baseUrl() + "/admin/users");
    var row = page.locator(".form-field").filter(new com.microsoft.playwright.Locator.FilterOptions()
        .setHasText(adminEmail));
    row.getByRole(AriaRole.BUTTON,
        new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Deactivate")).click();
  }

  @Given("I am logged in as a non-admin")
  public void loginAsNonAdmin() {
    if (knownEmail == null) createPreconditionAccount("OPERATOR");
    page.navigate(E2eSupport.baseUrl() + "/login");
    submitLogin(knownEmail, knownPassword);
    page.waitForURL("**/dashboard");
  }

  @When("I open user management")
  public void openUserManagement() {
    page.navigate(E2eSupport.baseUrl() + "/admin/users");
  }

  @Then("I am on the dashboard")
  public void onDashboard() {
    PlaywrightAssertions.assertThat(page).hasURL(E2eSupport.baseUrl() + "/dashboard");
  }

  @And("the {string} link is not visible")
  public void linkNotVisible(String name) {
    PlaywrightAssertions.assertThat(page.getByText(name)).hasCount(0);
  }

  @Then("{string} is displayed")
  public void messageDisplayed(String message) {
    PlaywrightAssertions.assertThat(page.getByText(message)).isVisible();
  }

  private void createPreconditionAccount(String role) {
    if (adminToken == null) {
      adminEmail =
          E2eSupport.environmentOr("e2e.admin.email", "E2E_ADMIN_EMAIL",
              "admin@kartylicious.test");
      adminPassword =
          E2eSupport.environmentOr("e2e.admin.password", "E2E_ADMIN_PASSWORD",
              "change-me-admin-pass");
      adminToken = loginAndGetToken(adminEmail, adminPassword);
    }
    knownPassword = "strong-pass-123";
    var token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    knownEmail = "precondition-" + token + "@example.com";
    if ("END_USER".equals(role)) {
      knownUserId =
          callApi("POST", "/api/registration",
              "{\"username\":\"precondition-" + token
                  + "\",\"email\":\"" + knownEmail
                  + "\",\"password\":\"" + knownPassword + "\"}",
              null, 201);
    } else {
      knownUserId =
          callApi("POST", "/api/admin/users",
              "{\"username\":\"precondition-" + token
                  + "\",\"email\":\"" + knownEmail
                  + "\",\"password\":\"" + knownPassword
                  + "\",\"role\":\"" + role + "\"}",
              adminToken, 201);
    }
  }

  private String loginAndGetToken(String email, String password) {
    var response = callApi("POST", "/api/auth/login",
        "{\"user\":\"" + email + "\",\"password\":\"" + password + "\"}",
        null, 200);
    var tokenMarker = "\"token\":\"";
    var markerIndex = response.indexOf(tokenMarker);
    if (markerIndex < 0) {
      throw new IllegalStateException("Test precondition failed: login response was invalid");
    }
    var tokenStart = markerIndex + tokenMarker.length();
    var tokenEnd = response.indexOf('"', tokenStart);
    if (tokenEnd < 0) {
      throw new IllegalStateException("Test precondition failed: login response was invalid");
    }
    return response.substring(tokenStart, tokenEnd);
  }

  private String callApi(
      String method, String path, String body, String token, int expectedStatus) {
    APIRequestContext api = playwright.request().newContext();
    try {
      var response =
          api.fetch(
              E2eSupport.baseUrl().replace(":3000", ":8080") + path,
              RequestOptions.create()
                  .setMethod(method)
                  .setData(body)
                  .setHeader("Content-Type", "application/json")
                  .setHeader("Authorization", token == null ? "" : "Bearer " + token));
      if (response.status() != expectedStatus) {
        throw new IllegalStateException(
            "Test precondition failed: " + method + " " + path
                + " returned " + response.status());
      }
      return response.text();
    } finally {
      api.dispose();
    }
  }

  private void submitLogin(String email, String password) {
    page.getByLabel("Email").fill(email);
    page.getByLabel("Password").fill(password);
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Log in")).click();
  }
}
