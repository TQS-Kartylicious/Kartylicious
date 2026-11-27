@KARTY-40 @BR9 @BR12
Feature: Cancel a mini-kart reservation

  Background:
    Given I am logged in and viewing the hub directory for booking
    And the "K-101" mini-kart is available at the "Central Square" municipal hub

  @BR7
  Scenario: A reservation is cancelled only after confirmation
    When I select the "K-101" mini-kart
    And I choose a future start time and a duration of 30 minutes
    And I confirm the booking
    Then my active trips include the "K-101" mini-kart
    When I dismiss the cancellation confirmation
    Then my active trips include the "K-101" mini-kart
    And the "K-101" mini-kart has status "Reserved"
    When I confirm cancellation for the "K-101" mini-kart
    Then the booking page shows "Reservation cancelled."
    And the booking page shows "No active trips."
    And the "K-101" mini-kart has status "Available"
    And I cannot cancel a reservation from my active trips

  Scenario: A started ride has no cancellation action
    When I reserve the "K-101" mini-kart for an immediate ride
    And I click "Start Ride" for my reserved mini-kart
    Then the ride is started for the "K-101" mini-kart
    And I cannot cancel a reservation from my active trips

  Scenario: A rejected cancellation displays the error
    When I select the "K-101" mini-kart
    And I choose a future start time and a duration of 30 minutes
    And I confirm the booking
    Then my active trips include the "K-101" mini-kart
    Given the cancellation request will be rejected
    When I confirm cancellation for the "K-101" mini-kart
    Then the booking page shows "Only an active, unexpired reservation can be cancelled."
    And my active trips include the "K-101" mini-kart
