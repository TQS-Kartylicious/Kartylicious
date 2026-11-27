@KARTY-36
Feature: Reserve an available mini-kart
  As an End User
  I want to reserve an available mini-kart at a hub for a specific time window
  So that I can guarantee a comfortable vehicle is waiting for me when I need to travel

  Background:
    Given I am logged in and viewing the hub directory for booking

  Rule: A valid booking authorizes the security deposit and reserves the kart

    @BR1 @BR2 @BR3 @BR7
    Scenario: Confirm a booking for an available mini-kart
      Given the "K-301" mini-kart is available at the "University Gardens" municipal hub
      When I select the "K-301" mini-kart
      And I choose a future start time and a duration of 30 minutes
      And I confirm the booking
      Then the booking page shows "Booking confirmed."
      And the "K-301" mini-kart has status "Reserved"
      And my active trips include the "K-301" mini-kart

  Rule: A booking cannot exceed the municipality's maximum trip duration

    @BR3
    Scenario: Reject a booking longer than the permitted duration
      Given the "K-101" mini-kart is available at the "Central Square" municipal hub
      When I select the "K-101" mini-kart
      And I choose a future start time and a duration of 61 minutes
      And I confirm the booking
      Then the booking page shows "Maximum permitted rental duration: 60"
