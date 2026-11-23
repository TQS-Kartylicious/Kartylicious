@KARTY-76
Feature: Platform admin user management
  As a Platform Admin
  I want to create and manage staff accounts
  So that only authorized people can operate the platform

  Rule: Staff accounts are created with an allowed role

    Scenario: An admin creates a staff account
      Given I am logged in as the seeded admin
      When I create an operator account
      Then "Account created successfully." is displayed

    Scenario: An existing email is rejected
      Given I am logged in as the seeded admin
      And an account already exists with a known email
      When I create another account with that email
      Then "The email is already in use." is displayed

  Rule: Roles and status changes affect existing sessions

    Scenario: An admin changes a user's role
      Given I am logged in as the seeded admin
      And a user account exists
      When I change that user's role to Municipal Worker
      Then "Role updated." is displayed

    Scenario: An admin deactivates a user
      Given I am logged in as the seeded admin
      And an active user account exists
      When I deactivate that account
      And that user tries to log in
      Then "This account is deactivated." is displayed

  Rule: Access and last-admin protection

    Scenario: The only active admin cannot deactivate themselves
      Given I am logged in as the seeded admin
      And no other active admin exists
      When I deactivate my own account
      Then "At least one active Admin is required." is displayed

    Scenario: A non-admin cannot open user management
      Given I am logged in as a non-admin
      When I open user management
      Then I am on the dashboard
      And the "User management" link is not visible
