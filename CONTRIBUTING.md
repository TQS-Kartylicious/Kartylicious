# Contributing

Short guide for contributors. Rationale and evidence are in the QA Manual (sections 3 and 4). AI rules are in `AGENTS.md` and QA Manual section 6.

## Workflow
1. Pick a story from the iteration board in Jira and move it to **In Progress**.
2. Create a branch with the story key: `KARTY-<n>-<short-description>`.
3. Commit often, prefixing each message with the key: `KARTY-<n>: short description`.
4. Open a pull request with the key in the title and fill in the PR template (what changed, how it was tested, AI used and for what).
5. CI runs: build, unit and integration tests, Sonar quality gate, dependency scan.
6. An AI-assisted review comments; a teammate (never the author) reads the diff and the AI comments, then approves or requests changes.
7. Squash merge once approved and CI is green. The branch is deleted.
8. `main` is deployed automatically. Move the story to **Done** after acceptance.

Direct pushes to protected branches are blocked; everything goes through a PR.

## Code style
**Backend (Java 21)**
- Google Java Style Guide: 2-space indentation.
- `UpperCamelCase` for types, `lowerCamelCase` for methods and variables, `UPPER_SNAKE_CASE` for constants.
- Explicit imports, no wildcards.
- Constructor injection only.
- Thin controllers; business logic and transactions in services; RESTful, resource-based endpoints.
- External integrations (MQTT, Stripe, persistence adapters) are reached from the domain only through interfaces (ports).

**Frontend**
- ESLint and Prettier; formatter must pass.

**General**
- Never commit secrets, credentials or real personal data, and never share them with AI tools. Use synthetic test data.

## Tests
- Unit tests for business logic; integration tests (Testcontainers) for API, persistence and adapters, run separately.
- Core stories: Gherkin scenarios automated with Cucumber.
- Put the Jira key in test classes, feature-file tags and test summaries so Xray can link them.
- Playwright E2E tests for core user flows; they do not run on every build.
- Details: QA Manual section 5.

## AI use
- AI may help with code, tests, docs and reviews, but the author must understand and be able to explain every line.
- Declare AI use in the PR template.
- Each AI review finding is accepted (fixed) or rejected (reply with the reason); label the PR `ai-finding-accepted` and/or `ai-finding-rejected`.
- Agents never approve or merge.

## Review checklist
- [ ] The branch, PR title and commits contain the Jira key.
- [ ] The acceptance criteria are met.
- [ ] Tests were added or updated (unit, integration, Gherkin where applicable).
- [ ] CI and the quality gate are green.
- [ ] The business logic does not depend on adapters (MQTT, Stripe, JPA) except through ports.
- [ ] Access control is checked on the backend (RBAC), not only in the UI.
- [ ] No secrets, credentials or personal data are in the code, tests or AI prompts.
- [ ] Code style is respected (formatter passes).
- [ ] AI-generated code is understood by the author, who can explain it.
- [ ] AI findings are answered.

## Further references
- Product Specification report and QA Manual in `docs/`
- Google Java Style Guide: https://google.github.io/styleguide/javaguide.html