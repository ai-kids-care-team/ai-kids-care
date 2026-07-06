# testing-and-ci Specification

## Purpose
定义后端测试与 CI 的目标状态：后端测试随各能力以 superpowers TDD 增量重建、不维护独立的 harness 守卫测试层；CI 门限于 compose-config / frontend / ai / release，Backend Java Tests 门在后端产品测试套件重建前暂退；构建配置不带 harness 专属强制（MapStruct unmappedTargetPolicy 回退框架默认）。
## Requirements
### Requirement: Backend tests are rebuilt per-capability via TDD
The backend test suite SHALL be rebuilt incrementally using test-driven development
(superpowers) as capabilities are (re-)specified in OpenSpec. The repository SHALL NOT
maintain a repo-wide harness/guard test layer separate from product behavior tests.

#### Scenario: Re-specifying a backend capability
- **WHEN** a backend capability is (re-)specified as an OpenSpec change
- **THEN** its tests are authored via TDD as part of that change, not as a separate guard layer

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

### Requirement: Build configuration carries no bespoke harness enforcement
The backend build SHALL use framework defaults and SHALL NOT carry harness-specific
enforcement such as `mapstruct.unmappedTargetPolicy=ERROR`.

#### Scenario: Compiling the backend
- **WHEN** the backend is compiled
- **THEN** an unmapped MapStruct target follows the MapStruct default policy, not a build-failing harness override

### Requirement: Frontend has a unit test harness for pure logic and components
The frontend SHALL provide a local unit test harness (Vitest + React Testing Library +
jsdom) enabling per-capability unit tests of pure logic (API helpers, enum/status
derivations, pagination mapping) and lightweight component rendering, authored via TDD as
capabilities evolve. The harness SHALL run via an `npm run test:run` script and SHALL NOT
alter the existing `lint`/`build` static-export pipeline.

#### Scenario: Running the frontend unit test suite
- **WHEN** a contributor runs `npm run test:run` in `frontend/`
- **THEN** Vitest executes all `src/**/*.{test,spec}.{ts,tsx}` files in a jsdom environment and reports pass/fail

#### Scenario: Notification unread derivation is regression-guarded
- **WHEN** the notification status enum allowlist in `notifications.api.ts` is changed
- **THEN** a unit test asserting the unread/read classification of every status value fails if the allowlist regresses

#### Scenario: Test harness does not affect the production build
- **WHEN** the frontend is built via `npm run build`
- **THEN** the static export succeeds unchanged and no test-only dependency is bundled into the output

