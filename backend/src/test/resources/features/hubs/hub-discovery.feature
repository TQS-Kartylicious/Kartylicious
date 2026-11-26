@KARTY-35 @BR1
Feature: Discover mini-karts by municipal hub
  As an End User
  I want to browse municipal hubs and view available mini-karts with their battery levels
  So that I can find a ready-to-ride kart near me

  Background:
    Given I am logged in and viewing the hub directory

  Rule: Selecting a hub shows its parked fleet and current state

    Scenario: View mini-karts, battery levels, and availability at a hub
      When I select the "Central Square" municipal hub
      Then I see the parked mini-karts with their battery levels and availability statuses
        | id    | name           | battery | status         |
        | K-101 | Mini-kart 101  | 84      | Available      |
        | K-102 | Mini-kart 102  | 20      | Available      |
        | K-103 | Mini-kart 103  | 19      | Low Battery    |
        | K-104 | Mini-kart 104  | 71      | In Use         |
        | K-105 | Mini-kart 105  | 63      | Reserved       |
        | K-106 | Mini-kart 106  | 92      | In Maintenance |

  Rule: Only available karts with at least 20 percent battery can be selected

    Scenario: Prevent selection of karts that are busy or below the battery threshold
      When I select the "Central Square" municipal hub
      Then only available karts with at least 20 percent battery can be selected
        | id    | battery | status         | selectable |
        | K-101 | 84      | Available      | true       |
        | K-102 | 20      | Available      | true       |
        | K-103 | 19      | Low Battery    | false      |
        | K-104 | 71      | In Use         | false      |
        | K-105 | 63      | Reserved       | false      |
        | K-106 | 92      | In Maintenance | false      |
      And I can select an eligible kart for booking

  Rule: A hub with no eligible karts explains that none are available

    Scenario: Show the empty availability message for a hub with no eligible karts
      When I select the "Riverside Park" municipal hub
      Then I see "No karts are currently available at this location."
