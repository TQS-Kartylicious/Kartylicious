# Contributing

## Workflow
1. Pick a story in Jira, create a branch `TQS-<n>-<short-name>`
2. Commit often, prefixing messages with the Jira key
3. Open a PR; at least one teammate must approve
4. Squash merge once CI and the quality gate pass

## Code style & Architecture
- **Java (Java 21 / Spring Boot):** Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
- **Frontend (Web UI):** Follow project ESLint and Prettier rules.
- **Dependency Injection:** Always use constructor injection; never use field `@Autowired`.
- **Architecture:** Respect Ports and Adapters boundaries. Keep controllers thin and place all business rules (`BR1`–`BR15`) and transactions in service classes.
- **REST APIs:** Design endpoints around domain resources (`/hubs`, `/karts`, `/bookings`, `/maintenance-tickets`, `/rules`).


## Review checklist
- [ ] Tests added or updated
- [ ] CI and quality gate green
- [ ] No secrets or personal data
- [ ] Acceptance criteria met