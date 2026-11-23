package pt.ua.kartylicious.auth.e2e;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/auth/login.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pt.ua.kartylicious.auth.e2e")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, junit:target/xray-reports/login-TEST-cucumber.xml, json:target/cucumber-reports/login-cucumber.json")
public class LoginCucumberSuite {}
