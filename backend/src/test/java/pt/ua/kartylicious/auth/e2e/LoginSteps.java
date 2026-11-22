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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
    var username = "login-" + token;
    email = ("LOGIN-" + token + "@EXAMPLE.COM").toLowerCase(Locale.ROOT);
    insertAccountDirectly(username, email, password);
  }

  private void insertAccountDirectly(String username, String email, String password) {
    var jdbcUrl =
        configured("e2e.db.url", "E2E_DB_URL", "jdbc:postgresql://localhost:5432/kartylicious");
    var databaseUser =
        configured("e2e.db.username", "E2E_DB_USERNAME", "kartylicious");
    var databasePassword =
        configured("e2e.db.password", "E2E_DB_PASSWORD", "change-me");
    var passwordHash = new BCryptPasswordEncoder().encode(password);

    try (Connection connection =
            DriverManager.getConnection(jdbcUrl, databaseUser, databasePassword);
        PreparedStatement statement =
            connection.prepareStatement(
                """
                insert into user_accounts
                    (id, username, email, password_hash, role)
                values (?, ?, ?, ?, ?)
                """)) {
      statement.setObject(1, UUID.randomUUID());
      statement.setString(2, username);
      statement.setString(3, email);
      statement.setString(4, passwordHash);
      statement.setString(5, "END_USER");
      statement.executeUpdate();
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Could not create the synthetic account directly in the E2E database",
          exception);
    }
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
    page.navigate(baseUrl() + "/login");
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
    var value = configured("e2e.baseUrl", "E2E_BASE_URL", "http://localhost:3000");
    return value.replaceAll("/+$", "");
  }

  private static String configured(
      String propertyName, String environmentName, String fallback) {
    var propertyValue = System.getProperty(propertyName);
    var environmentValue = System.getenv(environmentName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }
    if (environmentValue != null && !environmentValue.isBlank()) {
      return environmentValue;
    }
    return fallback;
  }
}
