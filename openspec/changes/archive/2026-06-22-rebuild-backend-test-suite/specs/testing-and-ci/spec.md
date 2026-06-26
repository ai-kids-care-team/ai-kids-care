## MODIFIED Requirements

### Requirement: CI gates limited to product and release checks
Continuous integration SHALL run compose-config validation, frontend lint/build, the AI
test suite, the backend test suite (`./gradlew test`), and the release pipeline. Now that the
backend product test suite has begun rebuilding per-capability (auth-authorization first
slice), the backend test gate SHALL run on pushes to develop and on pull requests targeting
develop or main. The backend gate runs the product behavior tests authored via TDD; the
repository still SHALL NOT maintain a repo-wide harness/guard test layer separate from
product behavior tests.

#### Scenario: Pushing to develop

- **WHEN** a contributor pushes to develop
- **THEN** CI runs compose / frontend / ai / backend test / release checks, including the Backend Java Tests gate

#### Scenario: Backend test gate fails the build on regression

- **WHEN** a change breaks an existing backend behavior test and a contributor pushes it to develop or opens a PR
- **THEN** the Backend Java Tests CI job fails and the build is marked failing, blocking the regression from merging once the gate is added to branch protection
