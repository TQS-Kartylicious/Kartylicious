package pt.ua.kartylicious.e2e.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import io.cucumber.java.After;
import io.cucumber.java.Before;

public class PlaywrightContext {
  private static final String BROWSER_SCENARIOS =
      "@KARTY-35 or @KARTY-36 or @KARTY-37 or @KARTY-38 or @KARTY-39 or @KARTY-40"
          + " or @operator-fleet";

  private static Playwright playwright;
  private static Browser browser;
  private static Page page;

  @Before(BROWSER_SCENARIOS)
  public void setUp() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    page = browser.newPage();
  }

  @After(BROWSER_SCENARIOS)
  public void tearDown() {
    if (page != null) {
      page.close();
      page = null;
    }
    if (browser != null) {
      browser.close();
      browser = null;
    }
    if (playwright != null) {
      playwright.close();
      playwright = null;
    }
  }

  public static Page page() {
    return page;
  }
}
