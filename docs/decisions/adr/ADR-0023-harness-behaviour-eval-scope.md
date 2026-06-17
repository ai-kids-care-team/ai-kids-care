---
ADR: ADR-0023
title: "ADR-0023: harness 行为评估以确定性守卫 + 事件台账为准，不建 LLM 评判式 agent eval runner（暂）"
status: Proposed
implementation: Complete
date: 2026-06-17
deciders: 接手人起草，维护者待 Accept
supersedes: []
superseded_by: null
related_specs: [SPEC-0001]
---

# ADR-0023: harness 行为评估的范围——确定性守卫 + 事件台账，而非 LLM 评判式 eval runner

> 本 ADR 界定 [`docs/engineering/harness.md`](../../engineering/harness.md) 中「behaviour-eval 套件」缺口的**收口范围**：用确定性守卫测试 + 事件→控制台账覆盖已发生的失败类，并明确**不**构建「场景 prompt 回放 + LLM-as-judge」式 agent 行为 eval runner（暂）。

## 状态（Status）

Decision: `Proposed`（接手人起草，维护者待 Accept——架构/范围权衡归维护者）

Implementation: `Complete`（确定性核心已落地并入 develop、CI 绿；非目标按定义即不构建）

> **Note (2026-06-17):** implementation field reflects that the deterministic guard core is merged and CI-green; status: Proposed reflects that the maintainer has not yet formally Accepted the ADR scope boundary. Awaiting maintainer decision.

## 背景（Context）

- harness「Known gaps」原列「无 behaviour-eval 套件（binary pass/fail；convert incidents to evals）」。
- 事实：本仓库的 agent 失败模式多为「是否遵守已文档化的控制/约定」，其中可确定性检查的已建为守卫测试（如 `TestFixturePhoneUniquenessTest`、`LoaderSensitiveProjectionGuardTest`、`SpecAcceptanceCoverageTest`、schema-digest 漂移、MapStruct `unmappedTargetPolicy=ERROR`）。
- 「LLM-as-judge 回放 agent」的 runner 需要独立基础设施（runner、场景夹具、判定器）、非确定、消耗 token，且在产品仓库内常驻运行的价值与可维护性存疑（属「vibecoding」风险）。
- 关键风险：建一个空壳 runner 会制造「有 eval 覆盖」的假象，比不建更糟（与本仓库「假绿」教训一致）。

## 决策（Decision）

一句话：harness 的行为评估以**确定性守卫 + 事件台账**为准，并以「守卫自检」证明守卫非死；**不**构建 LLM 评判式 agent eval runner，直到出现确定性手段无法覆盖的、复发的 agent 行为回归。

必须遵守的边界：
- 每个失败**类**（incident）在 [`incidents.md`](../../engineering/incidents.md) 登记，并尽量配一个确定性控制（computational 优先）。
- 守卫的探测逻辑抽为纯函数（`HarnessChecks`），并由 `HarnessGuardsSelfTest` 用「植入违例」证明其会 FIRE——死守卫（永不失败）视同缺陷。
- 语义级判断（如 §372/§390 是否**语义**覆盖某维度）保留维护者评审，不假装机器化。
- 复发且确定性手段无法捕获的 agent 行为回归出现时，重启本决策、评估引入 eval runner。

## 方案比较（Options）

| 方案 | 优点 | 代价/风险 | 结论 |
| --- | --- | --- | --- |
| A. 确定性守卫 + 台账 + 守卫自检（本决策） | 二元、快、可常驻 CI；无 token 成本；证明守卫非死 | 只覆盖可静态/运行时确定的失败类 | **采纳** |
| B. LLM-as-judge agent eval runner | 能评模糊/语义行为 | 非确定、需基础设施、token 成本、易成空壳假象 | 暂不建；待真实需求 |
| C. 不做（保持纯文档约定） | 零成本 | 约定会漂移、守卫可能变死而无人知 | 拒绝 |

## 后果（Consequences）

- **正面**：失败类有二元可复跑的护栏；守卫自检防「死守卫」；范围诚实、无假象；零额外运行成本。
- **负面 / 代价**：模糊/语义行为无自动 eval；新失败类需人工登记并补控制（即「recurring issue → add a control」纪律）。
- **影响范围**：`backend` 测试（`com.ai_kids_care.v1.harness`）、`docs/engineering/{harness,incidents,test-conventions}.md`、`backend/build.gradle`（MapStruct 策略）。

## 合规与验证（Compliance）

- `bash scripts/test-backend.sh 'com.ai_kids_care.v1.harness.*'` 全绿即守卫与自检生效；CI「Backend Java Tests」常驻执行。
- `incidents.md` 的「Runnable eval」列标注每个失败类是否有确定性控制。
- 本 ADR 不维护逐项任务；落地证据见上述测试与文档。

## 关联（References）

- [`docs/engineering/harness.md`](../../engineering/harness.md)、[`docs/engineering/incidents.md`](../../engineering/incidents.md)
- [ADR-0020](ADR-0020-branch-protection-release-model.md)（发布门 / 评审模型）
- SPEC-0001 §365（loader 去敏）、§372/§390（负向矩阵 / 无 S0 验收）
