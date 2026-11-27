@operator-fleet @BR12
Feature: Service operators manage parking hubs and mini-karts

  Background:
    Given I am signed in as a fleet operator

  @KARTY-48 @BR6
  Scenario: Register and edit a hub
    When I register an operator hub with capacity 1
    Then the fleet management message is "Hub registered."
    And the hub shows 0 of 1 spaces occupied
    When I update the hub name and location with capacity 2
    Then the fleet management message is "Hub updated."
    And the renamed hub keeps its identifier and shows 0 of 2 spaces occupied

  @KARTY-48 @BR6
  Scenario: Capacity can equal parked occupancy but cannot be lower
    Given I register an operator hub with capacity 3
    And I register two mini-karts at that operator hub
    When I update the hub name and location with capacity 1
    Then the fleet management error is "Hub capacity cannot be below current occupancy."
    And the hub shows 2 of 3 spaces occupied
    When I update the hub name and location with capacity 2
    Then the fleet management message is "Hub updated."
    And the renamed hub keeps its identifier and shows 2 of 2 spaces occupied

  @KARTY-49 @BR6
  Scenario: Register an inactive kart and reject a duplicate or a full hub
    Given I register an operator hub with capacity 1
    When I register the test mini-kart at that operator hub
    Then the fleet management message is "Mini-kart registered."
    And the test mini-kart shows 0 percent with status "INACTIVE" and active state "Inactive"
    And the hub shows 1 of 1 spaces occupied
    When I register the test mini-kart at that operator hub
    Then the fleet management error is "A mini-kart with this identifier already exists."
    When I register another test mini-kart at that operator hub
    Then the fleet management error is "The hub is full."

  @KARTY-50 @BR1 @BR10 @BR11
  Scenario: Activate and deactivate a mini-kart with sufficient battery
    Given I register an operator hub with capacity 1
    And I register the test mini-kart at that operator hub
    And the test mini-kart receives a fixture observation of 19 percent
    When I choose "Activate" for the test mini-kart
    Then the fleet management error is "The battery must be at least 20% to activate this mini-kart."
    When the test mini-kart receives a fixture observation of 20 percent
    Then the test mini-kart shows 20 percent with status "INACTIVE" and active state "Inactive"
    When I choose "Activate" for the test mini-kart
    Then the fleet management message is "Mini-kart activated."
    And the test mini-kart shows 20 percent with status "AVAILABLE" and active state "Active"
    When I choose "Deactivate" for the test mini-kart
    Then the fleet management message is "Mini-kart deactivated."
    And the test mini-kart shows 20 percent with status "INACTIVE" and active state "Inactive"
