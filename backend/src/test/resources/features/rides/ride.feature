@KARTY-37
Feature: Start an immediate mini-kart ride
  As an End User
  I want to unlock and start an immediate ride on an available or reserved mini-kart
  So that I can begin my journey without delay

  Background:
    Given I am logged in and viewing the hub directory for rides

  Rule: An eligible available kart starts a ride and begins the trip timer

    @BR1 @BR4 @BR7
    Scenario: Start a ride on an available mini-kart
      Given the "K-101" mini-kart is available at the "Central Square" municipal hub
      When I select the "K-101" mini-kart for an immediate ride
      And I click "Start Ride" for the "K-101" mini-kart
      Then the ride is started for the "K-101" mini-kart
      And the "K-101" mini-kart has status "In Use"
      And my trip timer is running

  Rule: An End User can start their active reservation

    @BR1 @BR4 @BR7
    Scenario: Start a ride on my reserved mini-kart
      Given the "K-102" mini-kart is available at the "Central Square" municipal hub
      When I reserve the "K-102" mini-kart for an immediate ride
      And I click "Start Ride" for my reserved mini-kart
      Then the ride is started for the "K-102" mini-kart
      And the "K-102" mini-kart has status "In Use"
      And my trip timer is running

  Rule: Low battery karts cannot start a ride

    @BR1
    Scenario: Reject a ride when the latest telemetry reports low battery
      Given the "K-103" mini-kart has latest telemetry battery at 19 percent
      Then I cannot start a ride on the "K-103" mini-kart
      And the "K-103" mini-kart is flagged as "Low Battery"

  Rule: An End User can have only one active ride

    @BR4
    Scenario: Reject a second ride while another ride is in use
      Given the "K-301" mini-kart is available at the "University Gardens" municipal hub
      When I reserve the "K-301" mini-kart for an immediate ride
      And I click "Start Ride" for my reserved mini-kart
      And I select the "Central Square" municipal hub
      When I attempt to start a ride on the "K-101" mini-kart
      Then the ride is not started
      And I see the ride error "Only one active trip is allowed per user."
