---
name: adversarial-verification
description: 对抗式验证一条分析 finding——以反驳为默认假设复核，可选 DooD 动态实跑坐实。finding-verifier 使用。当需要复核/反驳分析结论、压制假阳性、动态验证测试缺口或契约错位、对 finding 投票定夺时使用。
---

# adversarial-verification — 对抗式验证方法

复核一条已有 finding，**默认它是假阳性，竭力反驳**；反驳不掉才确认。产出 `verdict`，不产新 finding。

## 为何对抗（原则）
确认偏误会让「看起来对」的结论蒙混过关。把默认假设设成 refuted，逼自己找反证，才能真正压制假阳性——尤其对纯静态推断（无运行时验证）。verifier 的价值不是给 finding 背书，而是当**敢于杀掉**站不住的结论。

## 静态复核手法（默认恒开）
逐条问：
1. **推断链成立吗？** 每一环（"因为 A 所以 B"）是否真的成立，还是跳步了？
2. **上下文反驳了吗？** 把相关代码上下文读全——是否有调用方约束、上游校验、框架默认行为，使该问题实际无法触发？
3. **是演示/seed 误报吗？** seed 账户、`test-pepper-not-secret-2026`、demo 注入脚本是预期产物；确认其**不进生产路径**即判 refuted。
4. **位置/证据对得上吗？** finding 引的 `file:line` 与描述是否一致；引错位置即降为 unverified。

> ⚠️ **gitignore 工具陷阱（复核「缺失/未接线」类时务必先核实）**：Claude 的 **Grep / Glob 默认静默跳过 `.gitignore` 内的文件**。若某目录确被忽略，用它们去「确认某文件不存在」会得到**假空命中**，把站得住的「未接线」finding 误判成「目标根本不存在 → refuted」（假阴性，放走真问题）。**注意：本工程 `frontend/` 并未被忽略**（2026-06-29 核实：`git check-ignore frontend/...` 返回空、`git ls-files frontend/src` 有 100+ 跟踪文件），Grep/Glob 对其完全可见——不要因旧笔记臆断它被忽略。正确做法：复核任何存在性结论前，先 `git check-ignore <path>` 确认目标**是否真被忽略**；确被忽略时才改用裸 `rg --no-ignore`（经 Bash）绕过。别把未经当前核验的具体断言当成既定事实。

## 何为"可动态验证"（深度档启用 DooD）
能用一次实跑直接证实/证伪的，才上 DooD（贵，限深度档）：
- **测试缺口**：实跑 testcontainers 套件，看声称缺失的测试是否真缺、相关路径是否真红。
- **契约错位**：构造一次真实调用/序列化，比对两侧 shape 是否真不匹配。
- **构建/lint**：前端用 `node:20` 容器实跑 lint/build，证实声称的错误。
配方与陷阱见 `references/dood-recipe.md`。纯设计判断、主观坏味**不**适合 DooD，维持静态。

## 投票规则
- **标准档**：每条 1 票（一次静态复核）。
- **深度档**：3 票或多 lens——`correctness`（事实对不对）/ `误报-as-design`（是不是预期设计）/ `可复现`（能否真触发/实跑坐实）。majority 反驳 → `refuted`。

## verdict 三态
- `confirmed`：反驳失败，证据支持 finding 成立。
- `refuted`：找到有效反证（推断断链 / 上下文挡住 / 演示误报 / 位置错）。
- `unverified`：证据不足以确认也不足以反驳（含 DooD 不可用时的"未动态坐实"）。

## 回退
DooD 跑不起来 → 回退静态，verdict 维持 `unverified`，note 注明"未动态坐实"，提醒报告在"覆盖与局限"列出。

## 输出
回写目标 finding 的 `verification:{verdict,method,votes,note}`（schema 见 `component-analysis-orchestrator/references/finding-schema.md`），存 `_workspace/{angle}_findings.verified.md`。冲突交 `analysis-lead` 裁决，不单方删除。
