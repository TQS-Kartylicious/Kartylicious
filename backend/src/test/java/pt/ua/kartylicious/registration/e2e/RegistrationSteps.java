package pt.ua.kartylicious.registration.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class RegistrationSteps {

  private static final String VALID_PASSWORD = "strong-pass-123";

  private Playwright playwright;
  private Browser browser;
  private Page page;
  private Response registrationResponse;
  private String email;
  private String username;
  private String existingEmail;
  private String existingUsername;
  private final AtomicInteger registrationRequests = new AtomicInteger();

  @Before
  public void startBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    page = browser.newPage();
    page.onRequest(
        request -> {
          if (isRegistrationRequest(request.method(), request.url())) {
            registrationRequests.incrementAndGet();
          }
        });
  }

  @After
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

  @Given("I am on the registration page for a new account")
  public void openRegistrationPage() {
    page.navigate(baseUrl());
  }

  @Given("an account already exists with a unique email and username")
  public void createExistingAccount() {
    String token = token();
    existingUsername = "existing-" + token;
    existingEmail = "existing-" + token + "@example.com";
    Response existingAccount = submitRegistration(existingUsername, existingEmail, VALID_PASSWORD);
    assertThat(existingAccount.status()).isEqualTo(201);
    page.waitForURL("**/dashboard");
    page.navigate(baseUrl() + "/register");
  }

  @When(
      "I submit unique credentials using password {string}")
  public void submitUniqueValidRegistration(String password) {
    String token = token();
    username = "e2e-" + token;
    email = "e2e-" + token + "@example.com";
    registrationResponse = submitRegistration(username, email, password);
  }

  @When("I reuse its email with a different username")
  public void submitDuplicateEmailRegistration() {
    registrationResponse = submitRegistration("another-" + token(), existingEmail, VALID_PASSWORD);
  }

  @When("I reuse its username with a different email")
  public void submitDuplicateUsernameRegistration() {
    String token = token();
    registrationResponse =
        submitRegistration(existingUsername, "another-" + token + "@example.com", VALID_PASSWORD);
  }

  @When("I submit the form with email {string}")
  public void submitInvalidEmail(String email) {
    fillForm("invalid-" + token(), email, VALID_PASSWORD);
    submit();
  }

  @When("I submit the form with password {string}")
  public void submitShortPassword(String password) {
    String token = token();
    fillForm("short-" + token, "short-" + token + "@example.com", password);
    submit();
  }

  @Then("my account is created")
  public void accountHasBeenCreated() {
    assertThat(registrationResponse.status()).isEqualTo(201);
  }

  @Then("I am redirected to the dashboard")
  public void redirectedToDashboard() {
    page.waitForURL("**/dashboard");
    PlaywrightAssertions.assertThat(page).hasURL(baseUrl() + "/dashboard");
  }

  @Then("registration is rejected with {string} on the {string} field")
  public void registrationIsRejectedWithMessage(String message, String field) {
    assertThat(registrationResponse.status()).isEqualTo(409);
    PlaywrightAssertions.assertThat(page.getByText(message)).isVisible();
    PlaywrightAssertions.assertThat(page.getByLabel(field)).hasAttribute("aria-invalid", "true");
  }

  @Then("the {string} field is highlighted with {string} and no request is sent")
  public void fieldIsHighlightedAndRequestIsPrevented(String field, String message) {
    PlaywrightAssertions.assertThat(page.getByText(message)).isVisible();
    PlaywrightAssertions.assertThat(page.getByLabel(field)).hasAttribute("aria-invalid", "true");
    assertThat(registrationRequests.get()).isZero();
  }

  private Response submitRegistration(String username, String email, String password) {
    fillForm(username, email, password);
    return page.waitForResponse(
        response -> isRegistrationRequest(response.request().method(), response.url()), this::submit);
  }

  private void fillForm(String username, String email, String password) {
    page.getByLabel("Username").fill(username);
    page.getByLabel("Email").fill(email);
    page.getByLabel("Password").fill(password);
  }

  private void submit() {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account")).click();
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

  private static String token() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}
