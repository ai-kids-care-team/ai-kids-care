# Migrate Knowledge into OpenSpec Specs

## Why

Change 1/2 已移除 bespoke harness 与后端测试/CI。最后一步：把仍生效的产品知识从 `docs/` 迁入
`openspec/specs/` 作为 OpenSpec 能力，并清除迁移后的历史文档，使仓库达到「产品代码 + OpenSpec +
superpowers」最小化终态。原始内容保留在 git 历史与已归档 change 中。

## What Changes

**BREAKING（文档/知识库结构；不触产品运行时代码）：**

- **迁入 `openspec/specs/`**（按能力，逐一忠实迁移当前事实；安全要求逐条保留）：
  - `auth-authorization` ← SPEC-0001 + `security-architecture.md` + ADR-0003/0007/0009/0016/0019
    （服务端会话、租户按 kindergarten_id 隔离、角色 scope、关闭路径/认证失败契约）
  - `sensitive-data-handling` ← SPEC-0001 敏感数据段 + ADR-0010/0024/0025/0026
    （RRN 单向哈希、pepper 轮换、摄像头流口令加密、S0/S1 不外泄）
  - `admin-management` ← SPEC-0002 + ADR-0021（admin 管理/审批端点、审计）
  - `appreciation-letters` ← SPEC-0003 + ADR-0028（读写端点）
  - `ai-detection` ← ADR-0006/0015 + `ai-architecture.md` + `ai-service-api.md`（VideoMAE 闭环）
  - `notifications` ← ADR-0018（通知子系统当前事实）
  - `data-platform` ← ADR-0002/0013 + `data-architecture.md` + `docs/db/ERD`（双数据存储、字典表、ERD）
  - `rebuild-guardrails` ←（本 change 直接提供）incidents INC-001/003/005 + schema-digest 漂移 +
    spec 验收覆盖：记录被 Change 2 移除、待以 TDD 重建的护栏。
- **迁后清除**：`docs/specs/*`、`docs/decisions/adr/*`、`docs/architecture/*`、`docs/product/*`、
  `docs/api/*`、`docs/operations/*`、`docs/db/*`、`docs/assessments/*`、`docs/modernization/*`、
  `docs/governance/*`、`docs/README.md`。
- 同步：根 `README.md`/`README.zh-CN.md`/`README.en.md` 中指向已删 docs 的链接改为指向 `openspec/`。

## Capabilities

- **New Capabilities**:
  - `auth-authorization`、`sensitive-data-handling`、`admin-management`、`appreciation-letters`、
    `ai-detection`、`notifications`、`data-platform` —— apply 阶段逐一从对应 SPEC/ADR/architecture
    忠实迁移当前事实（每条 SHALL 要求至少一个 Scenario）。
  - `rebuild-guardrails` —— 本 change 直接提供（见 specs/）。
- **Modified Capabilities**: 无（`agent-workflow`、`testing-and-ci` 已在 Change 1/2 建立，不变）。

## Impact

- 影响面：`docs/` 知识库 + 根 README 链接。**不触** `backend/src/main`、`frontend/`、`ai/`、`db/`
  产品代码与运行时，不触已保留的 CI。
- 风险：**安全敏感**。SPEC-0001（~600 行）批量迁移易丢要求；apply 须逐条核对（租户隔离、角色 scope、
  S0/S1 边界、关闭路径契约）。本机无 JDK/测试，迁移正确性靠人工评审，不靠自动校验。
- ADR 取舍：28 份 ADR 的**当前事实**迁入对应能力 spec；ADR 的**决策理由（Why）**不进 OpenSpec 能力 spec，
  仅存于 git 历史。这是单个大 change + 最小化的已知代价（你已确认）。
- 体量大、blast radius 大（已提示）。建议 apply 分阶段提交（每能力一提交）以便评审。

## Known Gaps Carried Forward（迁移评审发现，记录以防随 docs 删除丢失）

迁移评审中各能力 spec 已忠实记录如下「规范要求存在、实现滞后」的缺口（spec 是目标，缺口待 TDD 重建时补齐）：
- **auth-authorization**：ADR-0019 stage 4-6 部分延后——DetectionEvent 租户迁移（控制器已删）、`GuardianChildPolicy` 全量、状态变更全量会话吊销触发器。
- **notifications**：Pushover 凭证硬编码空串（PUSH 运行时即抛错）、SMS 未接线、规则引擎→派发管道未实现、`DeviceTokenController`/`NotificationRuleController` 为空壳。
- **ai-detection**：ADR-0015 闭环为目标态，当前仅 Pushover/SMS/CSV 告警、无 DB 写入；检测表已就绪但未接。
- **data-platform**：loader 多数节点用 CSV 快照而非实时 PG 查询（与 ADR-0002「PG 派生视图」目标有偏差）。
- **ai 环境**：ai/README.md 已修正为 Python 3.12，与 Dockerfile python:3.12-slim 一致（矛盾已解决）；CUDA 13.2 为目标 GPU 环境，cu130 wheel index 向前兼容，未在 spec 断言版本。
