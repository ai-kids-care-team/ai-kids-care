# 组件多角度分析 Harness v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 v1 的 4 角度分析 harness 升级为 v2：6 角度（加 performance、experience）+ 对抗式 finding-verifier（静态恒开、深度档加 DooD，覆盖 high+medium）+ 三档运行（轻量 fan-out / 标准 团队 / 深度 团队+DooD）。

**Architecture:** 纯 markdown harness（`.claude/agents/*.md` 定义"谁"，`.claude/skills/**/SKILL.md` 定义"怎么做"，orchestrator skill 编排"何时谁协作"）。无应用代码、无运行时编译；每个任务的"测试"是结构校验（frontmatter、模型、必备小节、无韩文、交叉引用一致）。

**Tech Stack:** Markdown + YAML frontmatter；Claude Code 的 Skill/Agent/Team 机制；DooD（docker-out-of-docker）配方用于深度档动态验证。

**权威来源：** 设计文档 `docs/superpowers/specs/2026-06-25-component-analysis-harness-v2-design.md`（下称 SPEC）。本计划中"内容遵循 SPEC §X"指必须实现该节枚举的全部要点，非占位。

## Global Constraints

- 工作目录：当前 worktree `C:\ai-kids-care\.claude\worktrees\harness-analysis-team`；所有路径相对此根。
- 语言：所有文件中文为主、英文为辅，**禁止韩文**（韩文扫描必须 0 命中）。例外：agent 间 SendMessage 约定可用英文（仅在文档中如此**描述**，描述本身用中文）。
- 模型分配（frontmatter `model:` 必须精确）：`architecture-analyst`、`quality-analyst` = `sonnet`；`security-analyst`、`integration-analyst`、`performance-analyst`、`experience-analyst`、`finding-verifier`、`analysis-lead` = `opus`。
- 每个 agent 定义文件必备小节：核心角色 / 分析维度（或作业职责）/ 作业原则 / 输入输出协议 / 错误处理 / 协作（团队通信协议）。
- 每个 skill 的 frontmatter 必含 `name`、`description`；description 要"pushy"且含后续触发词。
- 不在 `.claude/commands/` 下生成任何文件。
- finding id 前缀：ARC-（架构）/ QLT-（质量）/ SEC-（安全）/ INT-（集成）/ PRF-（性能）/ UX-（用户）。
- 每个任务结束：结构校验通过 + 一次 commit。

---

### Task 1: 拆出 performance 角度（新建 performance-analyst + analyze-performance，瘦身 analyze-architecture）

**Files:**
- Create: `.claude/agents/performance-analyst.md`
- Create: `.claude/skills/analyze-performance/SKILL.md`
- Modify: `.claude/skills/analyze-architecture/SKILL.md`（移除"可扩展性/性能隐患"段与相关手法，回纯结构）

**Interfaces:**
- Produces: agent `performance-analyst`（model opus，id 前缀 `PRF-`）；skill `analyze-performance`。后续 Task 5 在 orchestrator 与 lead 中引用该 agent 名。
- Consumes: 复用 v1 既有 `analyze-architecture` 的写法风格与 finding schema 引用方式作为模板。

- [ ] **Step 1: 写校验脚本（先失败）**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
test -f .claude/agents/performance-analyst.md && grep -q "^model: opus" .claude/agents/performance-analyst.md && grep -q "PRF-" .claude/agents/performance-analyst.md && test -f .claude/skills/analyze-performance/SKILL.md && ! grep -qi "N+1\|线程池\|背压" .claude/skills/analyze-architecture/SKILL.md && echo PASS || echo FAIL
```
Expected: `FAIL`（文件尚不存在 / 架构 skill 仍含性能词）

- [ ] **Step 2: 写 `.claude/agents/performance-analyst.md`**

frontmatter（精确）：
```yaml
---
name: performance-analyst
description: 从「性能/可扩展性」角度分析组件——N+1、事务内外部 IO、线程池、SSE/事件背压、多实例去重、缓存、延迟。组件多角度分析团队成员。
model: opus
---
```
正文必备小节与要点（内容遵循 SPEC §3 该行 + §1 动机点 2）：
- 核心角色：只看"快不快、扛不扛得住扩展"，与 architecture（纯结构）靠 cross_refs 互链不重复定级。
- 分析维度：① N+1/循环内查库/LAZY 滥用 ② 事务内外部 IO（HTTP/网络持库连接）③ `@Async` 线程池有界性 ④ SSE/事件背压、replay 上限、心跳 ⑤ 多实例去重（`@Scheduled` 无锁、进程内注册表）⑥ 缓存/连接池 ⑦ 延迟热点（深度档可 DooD 实测）。
- 作业原则：证据落 file:line；读 application.yml/compose 理解拓扑；本机无 JVM 时静态推断标 confidence=medium。
- 输入输出协议：写 `_workspace/performance_findings.md`，遵循 orchestrator 的 finding schema（id 前缀 `PRF-`），完成 SendMessage 通知 lead + top-3。
- 错误处理：工具缺失转静态、显式标未覆盖。
- 团队通信协议：跨边界性能问题抄送 `integration-analyst`；结构根因 → `architecture-analyst`；缺测试 → `quality-analyst`；完成 → `analysis-lead`。再次调用时增量修订。

- [ ] **Step 3: 写 `.claude/skills/analyze-performance/SKILL.md`**

frontmatter：
```yaml
---
name: analyze-performance
description: 从性能/可扩展性角度审查组件——N+1、事务内 IO、线程池、SSE/事件背压、多实例去重、缓存、延迟。performance-analyst 使用。当需要性能审查、扩展性评估、瓶颈/背压/多实例去重排查时使用。
---
```
正文要点（内容遵循 SPEC §3、§5 的 DooD 延迟测项）：检查清单按本工程栈（NotificationService 事务内 Pushover/SMS、DetectionEventSseService 进程内 emitter、DeferredNotificationScanner 无 ShedLock、`@EnableAsync` 默认 executor、AI `/predict/upload` 整块入内存、Caddy gzip 缓冲 SSE）；手法（Grep `@Transactional`/`@Scheduled`/`@Async`/循环内 repository 调用）；深度档可用 DooD 实测延迟（指向 `adversarial-verification/references/dood-recipe.md`）；协作同 agent。

- [ ] **Step 4: 瘦身 `.claude/skills/analyze-architecture/SKILL.md`**

删除"5. 可扩展性 / 性能隐患（重点）"维度段及"作业原则/手法"中专属性能的描述（N+1、@Async、@Scheduled、背压等）。在维度列表保留一行指引："性能/扩展性已独立为 `analyze-performance` 角度——发现性能问题标 `component` 并 SendMessage 抄送 `performance-analyst`，本角度只看纯结构。"

- [ ] **Step 5: 运行校验（应通过）**

Run: 同 Step 1 的命令
Expected: `PASS`

- [ ] **Step 6: 韩文扫描 + commit**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
for f in .claude/agents/performance-analyst.md .claude/skills/analyze-performance/SKILL.md .claude/skills/analyze-architecture/SKILL.md; do python -c "import re,sys; sys.exit(1 if re.search(r'[\uAC00-\uD7A3]', open(sys.argv[1],encoding='utf-8').read()) else 0)" "$f" || echo "KOREAN in $f"; done; echo done
git add .claude/agents/performance-analyst.md .claude/skills/analyze-performance .claude/skills/analyze-architecture/SKILL.md
git commit -m "feat(harness): split performance into its own analysis angle (v2 task 1)"
```
Expected: 无 "KOREAN in" 输出；commit 成功。
（注：若本机无 python，可改用 PowerShell `[regex]::Matches($c,'[\uAC00-\uD7A3]')` 等价扫描。）

---

### Task 2: 新增 experience 角度（用户/功能视角）

**Files:**
- Create: `.claude/agents/experience-analyst.md`
- Create: `.claude/skills/analyze-experience/SKILL.md`

**Interfaces:**
- Produces: agent `experience-analyst`（model opus，id 前缀 `UX-`）；skill `analyze-experience`。Task 5 在 orchestrator/lead 引用。
- Consumes: 可 SendMessage 询问 `integration-analyst`（Task 之前已存在于 v1）。

- [ ] **Step 1: 写校验（先失败）**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
test -f .claude/agents/experience-analyst.md && grep -q "^model: opus" .claude/agents/experience-analyst.md && grep -q "UX-" .claude/agents/experience-analyst.md && test -f .claude/skills/analyze-experience/SKILL.md && echo PASS || echo FAIL
```
Expected: `FAIL`

- [ ] **Step 2: 写 `.claude/agents/experience-analyst.md`**

frontmatter：
```yaml
---
name: experience-analyst
description: 从「用户/功能」视角分析工程——脱离架构与代码，按真实使用者立场判断功能是否兑现、流程是否走得通。组件多角度分析团队成员（唯一由外向内角度）。
model: opus
---
```
正文必备小节与要点（内容遵循 SPEC §7）：
- 核心角色：脱离架构/代码，只问"作为使用者，我的任务办成了吗"；唯一由外向内角度，与 5 个技术角度三角互证。
- 分析维度（镜头）：① 角色化旅程（家长/教师/园长 KG_ADMIN/平台 IT 管理员/超管，各核心任务端到端）② 功能完整性（后端有能力却无前端入口 / 入口在但功能坏 / 关键功能缺失）③ 流程连贯断点（注册→审批→登录→使用；告警→复核→家长通知）④ 反馈可理解性（错误是否人话、空/加载/失败有无出路）⑤ 价值兑现（"实时检测+及时通知家长"用户实际拿到没）。
- 作业原则：读前端路由/页面/状态 + README/spec 理解"承诺的功能"；**不读后端实现做评判**；severity 按用户影响重定标（代码 low 可能用户 high）；cross_refs 链技术根因。
- 输入输出协议：写 `_workspace/experience_findings.md`，遵循 finding schema（id 前缀 `UX-`），完成 SendMessage 通知 lead + top-3 用户旅程断点。
- 错误处理：读不到某入口本身即 finding（疑似未接线）。
- 团队通信协议：背后接通性疑问 → `integration-analyst`；功能坏的技术原因 → 相应技术角度；完成 → `analysis-lead`。再次调用增量修订。

- [ ] **Step 3: 写 `.claude/skills/analyze-experience/SKILL.md`**

frontmatter：
```yaml
---
name: analyze-experience
description: 从用户/功能视角审查工程——角色化用户旅程、功能完整性、流程连贯性、反馈可理解性、价值兑现。experience-analyst 使用。当需要用户体验审查、功能完整性评估、用户旅程走查、"功能是否真能用"判断时使用。
---
```
正文要点（内容遵循 SPEC §7）：为何这样审（用户不在乎为什么坏，只在乎任务办没办成）；5 个镜头展开为可走查清单（对每个角色列核心任务，逐一判 happy/失败路径）；本工程具体抓手（通知收件箱有无入口、CCTV 实时墙是否出画面、公告置顶是否保留、密码重置是否可用、注册→审批→登录是否顺）；手法（读 `frontend/src/app` 路由 + 组件状态 + README 承诺；不评判后端实现）；协作同 agent。

- [ ] **Step 4: 校验（应通过）+ 韩文扫描 + commit**

Run: 同 Step 1（期望 `PASS`），随后：
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
for f in .claude/agents/experience-analyst.md .claude/skills/analyze-experience/SKILL.md; do python -c "import re,sys; sys.exit(1 if re.search(r'[\uAC00-\uD7A3]', open(sys.argv[1],encoding='utf-8').read()) else 0)" "$f" || echo "KOREAN in $f"; done
git add .claude/agents/experience-analyst.md .claude/skills/analyze-experience
git commit -m "feat(harness): add experience (user/functional) analysis angle (v2 task 2)"
```
Expected: 无 KOREAN 输出；commit 成功。

---

### Task 3: 对抗式验证能力（finding-verifier + adversarial-verification skill + DooD 配方）

**Files:**
- Create: `.claude/agents/finding-verifier.md`
- Create: `.claude/skills/adversarial-verification/SKILL.md`
- Create: `.claude/skills/adversarial-verification/references/dood-recipe.md`

**Interfaces:**
- Produces: agent `finding-verifier`（model opus，general-purpose 可执行）；skill `adversarial-verification`；DooD 配方文件。Task 4 的 schema 用 `verification` 字段承接其产出；Task 5 的 orchestrator Phase 3 调用它。
- Consumes: 读各 `_workspace/{angle}_findings.md`；输出回写 `verification` 字段（schema 见 Task 4）。

- [ ] **Step 1: 写校验（先失败）**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
test -f .claude/agents/finding-verifier.md && grep -q "^model: opus" .claude/agents/finding-verifier.md && test -f .claude/skills/adversarial-verification/SKILL.md && test -f .claude/skills/adversarial-verification/references/dood-recipe.md && grep -q "TESTCONTAINERS_HOST_OVERRIDE" .claude/skills/adversarial-verification/references/dood-recipe.md && echo PASS || echo FAIL
```
Expected: `FAIL`

- [ ] **Step 2: 写 `.claude/agents/finding-verifier.md`**

frontmatter：
```yaml
---
name: finding-verifier
description: 对抗式复核单条 finding——默认假设其为假阳性并尝试反驳；深度档用 DooD 实跑坐实。组件多角度分析团队的验证者，general-purpose 可执行脚本。
model: opus
---
```
正文必备小节与要点（内容遵循 SPEC §5）：
- 核心角色：以反驳为默认假设复核 high/medium findings，压制假阳性；不产新 finding，只回写 verdict。
- 作业职责：① 静态复核（代码反读、上下文、是否演示/seed 误报、推断链是否成立）② 深度档动态复核（DooD 实跑，见 `references/dood-recipe.md`）。
- 作业原则：默认 refuted，证据足才 confirmed；证据不足标 unverified；区分演示/生产路径。
- 输入输出协议：读 `_workspace/{angle}_findings.md` 的目标条目，回写 `verification:{verdict,method,votes,note}`（schema 见 finding-schema），输出到 `_workspace/{angle}_findings.verified.md`。
- 错误处理：DooD 不可用 → 回退静态，verdict 维持 unverified-dynamic，报告需标注。
- 团队通信协议：与原 finding 提出者冲突时 SendMessage 讨论，最终裁决交 `analysis-lead`，不单方删除。
- 投票：标准档 1 票；深度档 3 票或多 lens（correctness / 误报-as-design / 可复现），majority 反驳即 refuted。

- [ ] **Step 3: 写 `.claude/skills/adversarial-verification/SKILL.md`**

frontmatter：
```yaml
---
name: adversarial-verification
description: 对抗式验证一条分析 finding——以反驳为默认假设复核，可选 DooD 动态实跑坐实。finding-verifier 使用。当需要复核/反驳分析结论、压制假阳性、动态验证测试缺口或契约错位时使用。
---
```
正文要点（内容遵循 SPEC §5）：为何对抗（默认 refuted 才能压假阳性）；静态复核手法清单；何为"可动态验证"（测试缺口、契约错位、构建/lint）；投票规则（标准 1 票 / 深度 3 票多 lens）；verdict 三态定义；DooD 何时启用 + 指向 `references/dood-recipe.md`；回退规则。

- [ ] **Step 4: 写 `.claude/skills/adversarial-verification/references/dood-recipe.md`**

内容（遵循 SPEC §5 C + 记忆"后端 DooD 测试调用法"/"前端验证用 docker node:20"）：
- 后端 testcontainers 全套件：挂 **repo 根**（非 backend 子目录）；env `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`；关 Ryuk；挂 docker socket；seed 类验证须 `gradle cleanTest`（不在 test 输入会被判 UP-TO-DATE）；真因常藏在 `build/test-results/*.xml`。
- 前端 lint/build：用 `node:20` 容器 DooD 跑；提交前还原 `next-env.d.ts`。
- 失败排查指引 + 何时判定"跑不起来→回退静态"。

- [ ] **Step 5: 校验（应通过）+ 韩文扫描 + commit**

Run: 同 Step 1（期望 `PASS`），随后扫描三文件韩文并：
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
git add .claude/agents/finding-verifier.md .claude/skills/adversarial-verification
git commit -m "feat(harness): add adversarial finding-verifier + DooD recipe (v2 task 3)"
```
Expected: 无 KOREAN；commit 成功。

---

### Task 4: 数据契约升级（finding-schema 加 verification + 6 前缀；report-template 加验证列）

**Files:**
- Modify: `.claude/skills/component-analysis-orchestrator/references/finding-schema.md`
- Modify: `.claude/skills/component-analysis-orchestrator/references/report-template.md`

**Interfaces:**
- Consumes: Task 3 的 `verification:{verdict,method,votes,note}` 形状。
- Produces: 全 6 角度统一引用的 schema；Task 5 的 orchestrator/lead 据此综合。

- [ ] **Step 1: 写校验（先失败）**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
grep -q "verification:" .claude/skills/component-analysis-orchestrator/references/finding-schema.md && grep -q "PRF-" .claude/skills/component-analysis-orchestrator/references/finding-schema.md && grep -q "UX-" .claude/skills/component-analysis-orchestrator/references/finding-schema.md && grep -qi "验证\|verdict" .claude/skills/component-analysis-orchestrator/references/report-template.md && echo PASS || echo FAIL
```
Expected: `FAIL`

- [ ] **Step 2: 改 finding-schema.md**

- 在字段表加可选 `verification` 字段（子字段 `verdict: confirmed|refuted|unverified`、`method: static|dood`、`votes`、`note`），说明由 finding-verifier 回写、lead 只综合 confirmed。
- id 前缀表扩到 6：ARC-/QLT-/SEC-/INT-/**PRF-**/**UX-**。
- 严重度基准补一行：UX 角度按"用户影响"定标（技术 low 可为用户 high）。
- 示例追加一条带 `verification` 的 confirmed 样例。

- [ ] **Step 3: 改 report-template.md**

- 关键发现项加「验证」标注（verdict + method）。
- 加「附录：被反驳（refuted）项」节——保留出处 + 反驳理由，不删。
- 健康度评分表角度列由 4 扩到 6（架构/质量/安全/集成/性能/用户）。
- 「覆盖与局限」节加"未动态坐实（unverified-dynamic）"清单说明。

- [ ] **Step 4: 校验（应通过）+ 韩文扫描 + commit**

Run: 同 Step 1（期望 `PASS`），随后：
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
git add .claude/skills/component-analysis-orchestrator/references
git commit -m "feat(harness): finding schema verification field + 6 angles in report template (v2 task 4)"
```
Expected: 无 KOREAN；commit 成功。

---

### Task 5: 编排升级（orchestrator 三档+团队+验证+6 成员；analysis-lead 同步）

**Files:**
- Modify: `.claude/skills/component-analysis-orchestrator/SKILL.md`
- Modify: `.claude/agents/analysis-lead.md`

**Interfaces:**
- Consumes: Task 1–4 产出的 6 个 agent 名（architecture/quality/security/integration/performance/experience）、`finding-verifier`、verification schema。
- Produces: 完整 v2 工作流（Phase 0.5 选档 → Phase 1 地图 → Phase 2 分析 → Phase 3 验证 → Phase 4 综合）。

- [ ] **Step 1: 写校验（先失败）**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
S=.claude/skills/component-analysis-orchestrator/SKILL.md
grep -q "performance-analyst" $S && grep -q "experience-analyst" $S && grep -q "finding-verifier" $S && grep -qi "轻量\|标准\|深度" $S && grep -q "performance-analyst\|experience-analyst" .claude/agents/analysis-lead.md && echo PASS || echo FAIL
```
Expected: `FAIL`

- [ ] **Step 2: 改 orchestrator SKILL.md**

- 描述加档位后续触发词（"快扫/快速体检"→轻量；"深度/发版前/关键审计"→深度）。
- 新增 **Phase 0.5 选档**：用户显式 > 默认标准；轻量/深度按点名或触发词。
- Phase 2 成员扩到 6：`architecture-analyst`(sonnet)、`quality-analyst`(sonnet)、`security-analyst`(opus)、`integration-analyst`(opus)、`performance-analyst`(opus)、`experience-analyst`(opus)。执行模式随档：轻量 fan-out；标准/深度 TeamCreate。
- 新增 **Phase 3 验证**：finding-verifier 对每条 **high+medium** 复核（静态恒开；深度档+DooD），回写 verification，产 `*_findings.verified.md`。
- Phase 4：lead 只综合 confirmed；refuted 入附录；unverified 标疑。
- 数据流图与"测试场景"更新为 SPEC §6、§10 四场景（正常/误报压制/降级/轻量）。
- 团队模式不可用 → 自动降级 fan-out 并注明（SPEC §8）。

- [ ] **Step 3: 改 analysis-lead.md**

- 工作流加选档与验证阶段；成员列表扩到 6 + 引入 finding-verifier。
- 综合原则加"只综合 confirmed、refuted 并列入附录、unverified 标疑"。
- 模型分配行更新为 6 成员的 sonnet/opus 划分。

- [ ] **Step 4: 校验（应通过）+ 韩文扫描 + commit**

Run: 同 Step 1（期望 `PASS`），随后：
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
git add .claude/skills/component-analysis-orchestrator/SKILL.md .claude/agents/analysis-lead.md
git commit -m "feat(harness): orchestrator tiers + team mode + verify phase + 6 members (v2 task 5)"
```
Expected: 无 KOREAN；commit 成功。

---

### Task 6: CLAUDE.md 注册 + 端到端结构校验

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: 全部 v2 文件已就位。
- Produces: 触发词含档位的 harness 指针 + v2 变更历史；最终一致性闸门。

- [ ] **Step 1: 写校验（先失败）**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
grep -qi "轻量\|深度" CLAUDE.md && grep -q "performance\|experience" CLAUDE.md && echo PASS || echo FAIL
```
Expected: `FAIL`

- [ ] **Step 2: 改 CLAUDE.md**

- 触发段补档位关键词（快扫/快速体检 → 轻量；深度/发版前/关键审计 → 深度）。
- 模型分配段更新为 6 成员划分（+ finding-verifier、experience/performance = opus）。
- 变更历史加一行：`| 2026-06-25 | v2：6 角度（+performance/+experience）+对抗式 finding-verifier+三档运行 | 全部 | 首跑短板：可信度/角度粒度/验证手段/视角单一 |`。

- [ ] **Step 3: 端到端结构校验（全 harness）**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
echo "=== agents 模型 ==="; for f in .claude/agents/*.md; do printf "%-42s " "$f"; grep -m1 "^model:" "$f"; done
echo "=== 期望: architecture/quality=sonnet; security/integration/performance/experience/finding-verifier/analysis-lead=opus ==="
echo "=== skills 存在 ==="; ls -d .claude/skills/analyze-architecture .claude/skills/analyze-quality .claude/skills/analyze-security .claude/skills/analyze-integration .claude/skills/analyze-performance .claude/skills/analyze-experience .claude/skills/adversarial-verification .claude/skills/component-analysis-orchestrator
echo "=== 无 commands 新增 ==="; git status --short .claude/commands 2>/dev/null
echo "=== 全局韩文扫描 ==="; FOUND=0; for f in CLAUDE.md $(git ls-files '.claude/agents/*.md' '.claude/skills/**/*.md' 'docs/superpowers/**/*.md'); do python -c "import re,sys; sys.exit(1 if re.search(r'[\uAC00-\uD7A3]', open(sys.argv[1],encoding='utf-8').read()) else 0)" "$f" || { echo "KOREAN in $f"; FOUND=1; }; done; [ $FOUND -eq 0 ] && echo "NO KOREAN"
```
Expected: 8 个 agent 模型与期望一致；8 个 skill 目录在；commands 无新增；输出 `NO KOREAN`。

- [ ] **Step 4: commit**

Run:
```bash
cd "C:/ai-kids-care/.claude/worktrees/harness-analysis-team"
git add CLAUDE.md
git commit -m "feat(harness): register v2 tiers/angles in CLAUDE.md + change log (v2 task 6)"
```
Expected: commit 成功。

---

## Self-Review（计划对照 SPEC）

**1. SPEC 覆盖**
- §3 阵容（6 角度 + verifier + lead，模型）→ Task 1（performance）、Task 2（experience）、Task 3（verifier）、Task 5（lead 模型行）、Task 6（校验）✓
- §4 三档（执行模式随档）→ Task 5 Phase 0.5 + Phase 2 ✓
- §5 验证（静态+DooD，high+medium，verdict 三态）→ Task 3 + Task 4（schema）✓
- §6 数据流 → Task 5 ✓
- §7 experience 细节 → Task 2 ✓
- §8 错误处理（降级 fan-out、DooD 回退、冲突不删）→ Task 3、Task 5 ✓
- §9 文件清单 → Task 1–6 全覆盖（13 文件）✓
- §10 测试场景 → Task 5 Step 2 写入 orchestrator ✓
- §11 团队规模/成本 → Task 5（档位控制）+ 各任务 log 缺口 ✓

**2. 占位符扫描**：无 TBD/TODO；"内容遵循 SPEC §X"均为带枚举要点的精确引用，非占位。

**3. 类型/命名一致**：agent 名（performance-analyst/experience-analyst/finding-verifier）、id 前缀（PRF-/UX-）、字段（verification.verdict/method/votes/note）、模型划分在 Task 1–6 与 Global Constraints 全一致 ✓。

**4. 范围**：单一 harness 子系统，13 文件，适合一个实现计划，无需拆分 ✓。
