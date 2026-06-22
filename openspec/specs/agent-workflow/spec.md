# agent-workflow Specification

## Purpose
定义本仓库的开发流程能力：用 OpenSpec 承载 spec 与变更生命周期（propose → apply → archive），用 superpowers 技能承载执行纪律，agent 指令文件保持最小并委派给二者；不维护自研的宪法、agent、workflow、hook 或域流程技能。
## Requirements
### Requirement: Spec and change lifecycle via OpenSpec
The repository SHALL use OpenSpec to capture capabilities and changes. New or changed
behavior SHALL be proposed as an OpenSpec change (propose → apply → archive), and durable
capabilities SHALL live in `openspec/specs/`.

#### Scenario: Proposing non-trivial work
- **WHEN** a contributor begins non-trivial new or changed behavior
- **THEN** they create an OpenSpec change (proposal + tasks, plus delta specs when a
  capability changes) before implementation

### Requirement: Execution discipline via superpowers skills
Implementation work SHALL follow superpowers skills for execution discipline (e.g.
brainstorming, writing-plans, executing-plans, test-driven-development,
verification-before-completion, requesting-code-review, using-git-worktrees). The
repository SHALL NOT maintain bespoke equivalents of these generic process controls.

#### Scenario: Implementing an approved change
- **WHEN** a contributor implements an approved OpenSpec change
- **THEN** they invoke the relevant superpowers skills rather than a repo-specific
  workflow script or custom planner/implementer/reviewer agent

### Requirement: Minimal agent instructions delegating to OpenSpec and superpowers
Root agent instruction files (`CLAUDE.md`, `AGENTS.md`) SHALL be minimal and SHALL delegate
process to OpenSpec (`openspec/`) and superpowers. The repository SHALL NOT carry a bespoke
constitution, custom agents, workflow scripts, local enforcement hooks, or domain process
skills as the source of development process.

#### Scenario: Reading agent instructions
- **WHEN** an agent reads `CLAUDE.md` / `AGENTS.md`
- **THEN** it finds a pointer to OpenSpec + superpowers, not a bespoke harness constitution

#### Scenario: Bespoke harness removed
- **WHEN** this change is applied
- **THEN** `.ai/`, `.claude/agents`, `.claude/workflows`, `.claude/hooks`,
  `.claude/skills/{authz-read-slice,checkpoint}`, and `docs/engineering/*`
  (except `schema-digest.md`, handled in Change 2) no longer exist
