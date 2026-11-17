# AGENTS.md

## Project
Light-mobility fleet-as-a-service. Backend: Spring Boot, Java 21. - **Backend:** Java 21, Spring Boot, Maven (`./mvnw`)

## Build and test commands
- Build: `./mvnw clean verify`
- Unit tests: `./mvnw test`
- Integration tests: `./mvnw verify -Pintegration`
- Run locally: `docker compose up`

## Code Style & Design Rules
- Follow all conventions in `CONTRIBUTING.md` and Section 3.1 of the Quality Manual.
- Follow the Google Java Style Guide for backend code and ESLint/Prettier for frontend code.
- Use constructor injection exclusively; no field `@Autowired`.
- Keep business logic and transactional boundaries in services, not controllers.
- Isolate external dependencies behind ports.

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