# AGENTS.md

## Project
Light-mobility fleet-as-a-service. Backend: Spring Boot, Java 21. Frontend: <your stack>.

## Build and test commands
- Build: `./mvnw clean verify`
- Unit tests: `./mvnw test`
- Integration tests: `./mvnw verify -Pintegration`
- Run locally: `docker compose up`

## Code style
- Follow the conventions in CONTRIBUTING.md
- Constructor injection, no field `@Autowired`
- Business logic in services, not controllers

## Definition of done
- Unit tests for new business logic
- Integration test if API or persistence changed
- Gherkin scenario for core user stories
- Sonar quality gate passes

## Rules
- Branch and commit names start with the Jira key (e.g. `TQS-12`)
- Never commit secrets; use synthetic test data only
- Never merge: a human approves every PR
- Do not modify files in `docs/` without being asked