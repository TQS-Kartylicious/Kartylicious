# AGENTS.md

Instructions for AI assistants and agents working on Kartylicious. They must stay consistent with `CONTRIBUTING.md` and the QA Manual (sections 3.1, 3.2, 4.1, 6.1). If they conflict, the QA Manual wins; tell a human so it gets fixed.

## Project
Kartylicious is a managed fleet-as-a-service of light electric mini-karts (municipal concession model, fictional city).
- **Backend:** Spring Boot (stable), Java 21, Maven wrapper (`./mvnw`), in `backend/`.
- **Frontend:** React + Vite. End-to-end tests use Playwright.
- **Integrations (behind ports):** `TelemetryPort` (MQTT simulator), `PaymentPort` (Stripe test mode or simulated gateway), `GbfsPublisher` (optional GBFS feed).
- **Roles:** End User, Service Operator (includes technician), Municipal Worker, Admin. Authentication is JWT.

## Build and test commands
Run from `backend/` unless stated otherwise.
- Build and all checks: `./mvnw -B verify` (this is what CI runs)
- Unit tests only (Surefire): `./mvnw test`
- Integration tests (Failsafe, Testcontainers): `./mvnw verify`
- Run locally (repo root): `docker compose up`
- E2E (Playwright) and k6 load tests are **not** part of the regular build; run them only when asked.

## Code style and design rules
- Follow `CONTRIBUTING.md` (Google Java Style: 2-space indent, no wildcard imports; ESLint/Prettier for frontend).
- Constructor injection only; no field `@Autowired`.
- Thin controllers, RESTful resource-based API (`/bookings`, `/hubs`, `/vehicles`), no action-style URLs.
- Business logic and transaction boundaries live in services.
- Domain code depends on ports only, never directly on MQTT, Stripe or JPA adapters.
- Enforce RBAC in the backend on every endpoint, not only in the UI.
- Business rules BR1–BR15 (Product Specification, section 3.2) are the source of truth. Do not change a rule's behavior or boundaries (e.g. minimum battery 20%) without being asked.

## Testing rules
- Unit tests (JUnit 5, Mockito) for new business logic; include boundary cases (below, at, above the limit).
- Integration tests (Spring Boot Test, Testcontainers) for API, persistence and adapters; kept separate from unit tests.
- Core stories: Gherkin scenarios automated with Cucumber, tagged with the story key and the rule (e.g. `@KARTY-12 @BR1`).
- Test classes, feature files and test summaries carry the Jira key (needed for Xray linking).
- Never weaken, delete or skip a failing test to make the build pass. Fix the code or ask.
- Use synthetic data only.

## Definition of done
- Unit tests for new business logic
- Integration tests if API, persistence or adapters changed
- Gherkin scenarios for core user stories
- E2E test (Playwright) if the story is a core user flow
- CI green: build, tests, Sonar quality gate on new code
- Documentation updated when affected (API docs, reports)

## Rules
- Branch, commits and PR title start with the Jira key `KARTY-<n>` (e.g. `KARTY-12`).
- Never commit or read secrets, credentials or real personal data.
- Never merge or approve a PR: a human always does.
- Do not modify files in `docs/` unless asked.
- Keep changes small and scoped to one story; do not refactor unrelated code.
- In the PR description, state what you changed, how it was tested, and that AI was used.