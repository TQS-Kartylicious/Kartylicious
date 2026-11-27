@KARTY-38
Feature: End a mini-kart ride at a designated hub
  As an End User
  I want to end my active ride at an authorized municipal hub
  So that the kart is secured and I am charged the final fare

  Background:
    Given I am logged in and viewing the hub directory for rides
    And the "K-101" mini-kart is available at the "Central Square" municipal hub
    When I click "Start Ride" for the "K-101" mini-kart
    Then the ride is started for the "K-101" mini-kart
    And latest telemetry reports the "K-101" mini-kart at the "Central Square" municipal hub

  @BR1 @BR5 @BR8 @BR12 @BR14
  Scenario: Complete a ride at an authorized hub with capacity
    When I end the active ride at the "Central Square" municipal hub
    Then the ride is completed
    And the final charge is displayed
    And the "K-101" mini-kart has status "Available"

  @BR5 @BR12 @BR14
  Scenario: Reject completion outside the telemetry-reported hub
    When I end the active ride at the "Riverside Park" municipal hub
    Then I see the ride error "Park at an authorized hub"
    And my active ride remains in progress

  @BR5 @BR12
  Scenario: Reject completion when the destination hub is full
    Given the "Central Square" municipal hub has no free capacity
    When I end the active ride at the "Central Square" municipal hub
    Then I see the ride error "The hub is full"
    And my active ride remains in progress

  @BR4 @BR5 @BR12 @BR14
  Scenario: Restore the active ride after reload and another login
    When I reload the ride dashboard
    Then the ride is started for the "K-101" mini-kart
    And my trip timer is running
    When I log out and back in as the rider
    Then the ride is started for the "K-101" mini-kart
    And my trip timer is running
    When I end the active ride at the "Central Square" municipal hub
    Then the ride is completed
