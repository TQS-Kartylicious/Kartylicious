# Contributing

## Workflow
1. Pick a story in Jira, create a branch `KARTY-<n>-<short-name>`
2. Commit often, prefixing messages with the Jira key
3. Open a PR; at least one teammate must approve
4. Squash merge once CI and the quality gate pass

## Code style

## Review checklist
- [ ] The branch, PR title and commits contain the Jira key.
- [ ] The acceptance criteria are met.
- [ ] Tests were added or updated.
- [ ] CI and the quality gate are green.
- [ ] The business logic does not depend on adapters (MQTT, Stripe, JPA) except through ports.
- [ ] Access control is checked on the backend (RBAC), not only in the UI.
- [ ] No secrets, credentials or personal data are in the code, tests or AI prompts.
- [ ] Code style is respected (formatter passes).
- [ ] AI-generated code is understood by the author, who can explain it.
- [ ] AI findings are answered.
