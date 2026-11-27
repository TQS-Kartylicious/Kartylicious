package pt.ua.kartylicious;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pt.ua.kartylicious")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, junit:target/xray-reports/TEST-cucumber.xml, json:target/cucumber-reports/cucumber.json")
public class CucumberSuite {}
