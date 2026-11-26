@KARTY-27 @BR12
Feature: End-user registration
  As an End User
  I want to create a personal account
  So that I can access the service and rent electric mini-karts

  Background:
    Given I am on the registration page for a new account

  Rule: New accounts use unique credentials and receive the End User role

    Scenario Outline: Register a unique account with a valid password
      When I submit unique credentials using password "<password>"
      Then my account is created
      And I am redirected to the dashboard

      Examples:
        | password      |
        | pass1234      |
        | ride-fast-12  |

    Scenario: Reject an email address that is already registered
      Given an account already exists with a unique email and username
      When I reuse its email with a different username
      Then registration is rejected with "The email is already in use" on the "Email" field

    Scenario: Reject a username that is already registered
      Given an account already exists with a unique email and username
      When I reuse its username with a different email
      Then registration is rejected with "The username is already in use" on the "Username" field

  Rule: Invalid form values are highlighted before a request is sent

    Scenario Outline: Reject an invalid email format
      When I submit the form with email "<email>"
      Then the "Email" field is highlighted with "The email format is incorrect" and no request is sent

      Examples:
        | email                  |
        | not-an-email           |
        | missing-at.example.com |
        | user@@example.com      |

    Scenario Outline: Reject a password shorter than the minimum length
      When I submit the form with password "<password>"
      Then the "Password" field is highlighted with "The password must be equal or longer than 8 characters" and no request is sent

      Examples:
        | password |
        | tiny     |
        | seven77  |
