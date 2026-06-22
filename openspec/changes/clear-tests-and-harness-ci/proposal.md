# Clear Backend Tests and Harness CI

## Why

承接 Change 1：bespoke harness 已移除，但后端测试层仍混着「harness 守卫测试」与产品功能测试，
且 `build.gradle` 强制、schema-digest 三件套、Backend Java Tests CI 仍是旧 harness 的一部分。
按 greenfield 最深档，一次性清空后端测试与 harness 专属 CI/构建强制；后端测试日后用 superpowers
TDD 按能力增量重建。

## What Changes

**BREAKING（后端测试与 CI 配置；不改产品运行时代码）：**

- 清空 `backend/src/test/**`（全部 33 个测试类，含 `harness/` 守卫测试与产品功能测试，及测试资源）。
- `build.gradle`：移除 `-Amapstruct.unmappedTargetPolicy=ERROR`（INC-005 强制）及其注释，回退 MapStruct 默认。
- 删除 schema-digest 三件套：`docs/engineering/schema-digest.md`、`scripts/schema-digest.sh`、
  `.github/workflows/schema-digest-drift.yml`（闭合 Change 1 推迟的 `schema-digest.md`）。
- 删除 `scripts/test-backend.sh`（无-JDK 容器测试回路）及 `settings.json` 中其 `permissions.allow` 项。
- 停用 Backend Java Tests CI：删除 `.github/workflows/backend-java-tests.yml`。
- **保留**：`compose-config.yml`、`frontend-lint-build.yml`、`ai-tests.yml`、`release.yml`（产品/发布 CI 不动）。

## Capabilities

- **New Capabilities**: `testing-and-ci` —— 后端测试与 CI 的目标状态：测试按能力 TDD 增量重建、
  无 harness 守卫测试层、CI 保留 compose/前端/ai/release 门、Backend Java Tests 门暂退。
- **Modified Capabilities**: 无

## Impact

- 影响面：后端测试套件 + 构建强制 + harness 专属 CI。**不改** `backend/src/main`、`frontend/`、`ai/`、`db/`。
- **维护者手动动作**：GitHub 分支保护 required checks 需移除 "Backend Java Tests"，否则 develop→main PR
  会卡在「等待状态」。此为 GitHub 服务端设置，不在仓库文件内（用仓库设置或 `gh` 处理）。
- 风险：清空期后端零回归护栏（已知接受）；INC-001/003/005 等守卫消失，转入 Change 3 的「待重建护栏」backlog。
