## 1. 前置门（维护者审批）

- [ ] 1.1 维护者审阅并批准（破坏性 + 安全敏感，apply 前必须）

## 2. 迁移：安全与鉴权（最慎重，逐条核对）

- [x] 2.1 `openspec/specs/auth-authorization/spec.md` ← SPEC-0001 + security-architecture + ADR-0003/0007/0009/0016/0019（服务端会话、租户隔离、角色 scope、关闭路径/认证失败契约；逐条 SHALL+Scenario）
- [x] 2.2 `openspec/specs/sensitive-data-handling/spec.md` ← SPEC-0001 敏感数据段 + ADR-0010/0024/0025/0026（RRN 哈希、pepper 轮换、流口令加密、S0/S1 不外泄）
- [x] 2.3 逐条核对：租户隔离 / 角色 scope / S0-S1 边界 / 关闭路径契约 未丢失（人工评审）

## 3. 迁移：其余产品能力

- [x] 3.1 `admin-management/spec.md` ← SPEC-0002 + ADR-0021
- [x] 3.2 `appreciation-letters/spec.md` ← SPEC-0003 + ADR-0028
- [x] 3.3 `ai-detection/spec.md` ← ADR-0006/0015 + ai-architecture + ai-service-api
- [x] 3.4 `notifications/spec.md` ← ADR-0018
- [x] 3.5 `data-platform/spec.md` ← ADR-0002/0013 + data-architecture + docs/db/ERD

## 4. 护栏 backlog

- [x] 4.1 `rebuild-guardrails/spec.md`（本 change 已含；archive 时同步入 openspec/specs）

## 5. 清除迁后历史文档

- [x] 5.1 删除 `docs/specs/*`
- [x] 5.2 删除 `docs/decisions/adr/*`
- [x] 5.3 删除 `docs/architecture/*`、`docs/product/*`、`docs/api/*`、`docs/operations/*`、`docs/db/*`
- [x] 5.4 删除 `docs/assessments/*`、`docs/modernization/*`、`docs/governance/*`、`docs/README.md`
- [x] 5.5 若 `docs/` 全空则移除目录

## 6. 同步根 README 链接

- [x] 6.1 `README.md`/`README.zh-CN.md`/`README.en.md` 中指向已删 docs 的链接改指 `openspec/`

## 7. 验证与提交

- [x] 7.1 `openspec validate --specs` 全部能力通过；每条 requirement 有 ≥1 Scenario
- [x] 7.2 grep 确认无指向已删 docs 的悬挂链接（README、代码注释）
- [x] 7.3 产品代码 / 保留 CI 未受影响；`git diff --check` 干净
- [ ] 7.4 建议按能力分提交，提交 develop
