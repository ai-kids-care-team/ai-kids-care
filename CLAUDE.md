# CLAUDE.md

Constitution for every agent working in this repository. When guidance here conflicts with a request, surface the conflict before proceeding — never resolve it silently.

## Mission

This is a long-lived production system, not a one-time rewrite. Optimize every change for long-term evolution: preserve business correctness, improve maintainability, reduce technical debt, and keep the architecture understandable enough to sustain feature delivery.

A contribution is acceptable only if it preserves system stability while doing at least one of: improving understanding, improving maintainability, or delivering business value.

## Source of Truth

When information conflicts, defer in this order:

1. ADRs under `docs/decisions/adr`
2. Architecture documents under `docs/architecture`
3. Product and business documentation under `docs/product`
4. Existing code
5. Assumptions

Never silently overwrite a documented decision. To change one, propose an ADR.

## Architecture Principles

Hold these unless an ADR explicitly changes them:

- Prefer explicit dependencies, and composition over inheritance.
- Keep module boundaries clear; avoid hidden coupling.
- Keep business logic out of transport layers, and framework specifics out of domain logic.
- Optimize for maintainability over cleverness.

## Working Modes

Declare the active mode at the start of a session; do not switch modes silently.

- **Discovery** — understand the system. Analyze, document, ask questions. Do not refactor, change behavior, or make architectural decisions.
- **Design** — evaluate options. Compare alternatives, identify tradeoffs, produce ADR proposals. Do not implement.
- **Implementation** — execute approved work. Implement one specific task, keep changes small, update tests and docs. Do not perform unrelated refactors or introduce architecture changes without an approved ADR.

## ADR Rules

Significant decisions require an ADR: architecture-style, database-strategy, module-boundary, messaging-strategy, and API-compatibility changes. Implementation details do not.

ADR title format: English ID plus a descriptive title — e.g. `ADR-0005: Introduce Repository Layer`.

## Documentation Rules

- Architecture changes must update the architecture docs, the relevant ADR status, and affected engineering guides.
- Behavior changes must update the product docs, API contracts, and user-facing behavior descriptions.

## Testing Rules

When modifying code:

1. Identify existing tests.
2. Preserve existing behavior unless a change is specified.
3. Add characterization tests for legacy behavior when needed.
4. Add or update automated tests.
5. Verify affected workflows.

Never remove a test without stated justification.

## Session Rules

Each session must have a single objective, reference the relevant ADRs, define its success criteria, be reviewable on its own, and leave the repository in a working state.

## Communication Style

Separate facts, inferences, assumptions, and recommendations. Raise an **Open Questions** section whenever uncertainty remains. Never invent missing information.

## Language Policy

- Use the user's language as the primary communication language.
- Maintain language consistency throughout the session.
- Use English technical terminology when it improves precision and clarity.
