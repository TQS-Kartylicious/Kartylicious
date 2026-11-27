@KARTY-39 @BR10 @BR12
Feature: Report a vehicle issue or fault

  Background:
    Given I am logged in and viewing the hub directory for booking
    And the "K-101" mini-kart is available at the "Central Square" municipal hub
    When I open Report Fault for the "K-101" mini-kart

  Scenario: A normal report leaves the kart available
    When I select fault category "NORMAL"
    And I describe the issue as "Seat needs cleaning"
    And I submit the fault form
    Then the booking page shows "Report submitted"
    And the "K-101" mini-kart has status "Available"

  Scenario: A critical report blocks the kart
    When I select fault category "CRITICAL"
    And I describe the issue as "Steering jammed"
    And I submit the fault form
    Then the booking page shows "Report submitted"
    And the "K-101" mini-kart has status "In Maintenance"
    And I cannot select the "K-101" mini-kart for booking
    And I cannot start a ride on the "K-101" mini-kart

  Scenario: A fault description is required
    When I select fault category "NORMAL"
    And I submit the fault form
    Then the fault form asks me to describe the issue
    And the "K-101" mini-kart has status "Available"
