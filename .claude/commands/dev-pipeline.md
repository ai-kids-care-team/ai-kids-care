---
name: "Dev Pipeline"
description: 实现一条已 propose 的 OpenSpec change——前后端并行 fan-out/fan-in + 分层门禁收口
category: Workflow
tags: [workflow, development, parallel]
---

把一条已 propose 的 OpenSpec change **实现落地**。本命令是薄入口,把编排委托给 `development-orchestrator` skill,由 `dev-lead`(opus)领队:fan-out 前后端 implementer(sonnet)在各自 worktree 并行实现 → fan-in cherry-pick 到 `develop` → 分层门禁收口 → 触发 archive。

---

**Input**:`/dev-pipeline` 后的参数 = 目标 change 名(kebab-case)。无参数则用 **AskUserQuestion** 问:
> "要实现哪条 change?(须已 propose——存在 `openspec/changes/<id>/` 与 `api-contract.md`)"

**Steps**

1. **校验前置**:确认目标 change 已 propose 且**契约冻结**——`openspec/changes/<id>/` 存在 design + tasks + `api-contract.md`。
   - 缺 change → 提示先 `/opsx:propose`。
   - 缺/含糊 `api-contract.md` → 提示回 design 补契约(不退化成后端先行)。
2. **调用 `development-orchestrator` skill** 走 Phase 0–5(上下文确认 → 契约确认 → fan-out 实现 → fan-in 收口 → 分层门禁+自修回路 → archive)。
3. **收口**:展示收口报告(实际拓扑 DAG / 门禁各层结论 / 未清零项);门禁清零后提示可 `/opsx:archive`。

**Guardrails**
- 破坏性变更(schema / 迁移 / 删除)须维护者**逐个批准**;门禁 **high-risk 一律 halt** 等批准。
- 本命令**不替代** `component-analysis-orchestrator`(审查工程现状)与 `/code-review`(只看未提交 diff);只负责"把一条 change 实现落地"。
