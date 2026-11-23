@KARTY-34
Feature: End User authentication
  As a Registered User
  I want to log into and out of my Kartylicious account securely
  So that I can manage my bookings and protect my personal information

  Background:
    Given I am on the login screen

  Rule: A registered account can establish an authenticated session

    Scenario: A registered user logs in successfully
      Given I have a registered account
      When I enter my valid email and password on the login screen
      Then I am redirected to the user dashboard

  Rule: Invalid login credentials never disclose whether the email or password is wrong

    Scenario: An unregistered email is rejected
      When I enter an unregistered email
      Then access is denied and "Incorrect email or password." is displayed

    Scenario: An incorrect password is rejected
      Given I have a registered account
      When I enter the incorrect password
      Then access is denied and "Incorrect email or password." is displayed

  Rule: Authentication state must control access and logout

    Scenario: A logged-in user logs out
      Given I have a registered account
      And I am logged in
      When I click the "Logout" button
      Then I am redirected to the login page

    Scenario: A visitor cannot open the dashboard without logging in
      Given I am not logged in
      When I open the dashboard
      Then I am redirected to the login page
