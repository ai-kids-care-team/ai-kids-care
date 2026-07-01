# dev-pipeline 开发流水线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地一条 `plan → design → implement(前后端并行 fan-out/fan-in) → gate → archive` 的开发流水线编排,形态对称于既有的 `component-analysis-orchestrator`(审查侧)。

**Architecture:** 复用 OpenSpec 的 propose/archive 做 plan/design/archive;新造一个 `development-orchestrator` skill + 一个 `dev-lead`(opus) 领队,fan-out 两个专职 implementer(backend/frontend,sonnet) 在各自 git worktree 并行实现,fan-in cherry-pick 到 develop,经分层门禁(硬测试门 → /code-review → opus 安全+集成定向复核 → 自修回路)收口。执行模型与审查侧一致:本环境只有 `Agent`/`SendMessage`/`Task*`,无 `TeamCreate`,编排是显式 DAG 的 `Agent` fan-out + lead 合并。

**Tech Stack:** Claude Code agent/skill/command 定义文件(Markdown + YAML frontmatter);编排逻辑靠 prose 指令驱动 `Agent` 工具。无运行时代码——交付物全是 harness 配置文件。

## Global Constraints

这些约束对每个任务都隐式生效,值一律照抄本节,不得漂移:

- **交付物 canonical 路径**(跨文件引用必须逐字一致):
  - Skill:`.claude/skills/development-orchestrator/SKILL.md`
  - References:`.claude/skills/development-orchestrator/references/{api-contract-template,gate-checklist,fan-in-playbook}.md`
  - Agents:`.claude/agents/{dev-lead,backend-implementer,frontend-implementer}.md`
  - Command:`.claude/commands/dev-pipeline.md`
- **Agent name↔model 绑定**(frontmatter `name:`/`model:` 逐字):`dev-lead`=`opus`、`backend-implementer`=`sonnet`、`frontend-implementer`=`sonnet`。复用的 `security-analyst`/`integration-analyst`=`opus`(已存在,本计划不改)。
- **契约产物路径**:`openspec/changes/<change-id>/api-contract.md`(随 change 走,archive 时一并归档)。
- **执行模型诚实化**:本环境**无 `TeamCreate`**;一切并行都是 `Agent` fan-out + lead 合并,不假装并发 agent 能实时互通。每个描述执行模型的文件都要写明这点(照抄审查侧 SKILL.md 的 v3 诚实化措辞)。
- **语言约定**:面向用户中文为主、英文术语为辅;对子代理的 `Agent` prompt / `SendMessage` 可用英文保语义精确;产出文件与代码注释按各自既有风格。保留代码标识符、API 路径、enum 值、数据库名、韩语产品文案不变。
- **安全 invariants(implementer agent 定义里必须写死为硬约束,门禁据此复核)**:
  1. 多租户隔离靠 `kindergarten_id` 谓词写进 JPQL/SQL/Cypher,**禁止加载后过滤**;前端**绝不传 kindergartenId**;跨租户/不可见资源**一律 404**。
  2. CSRF 对所有写请求强制,**唯一豁免** = `/api/v1/internal/**`(用 Bearer `AI_SERVICE_TOKEN`/`ROLE_AI_SERVICE`)。
  3. `@PreAuthorize("@authorizationPolicy.isAllowed(...)")` 标在 **service** 方法(非 controller)。
  4. RRN = HMAC-SHA256+pepper(不可逆,列名 `rrn_hash`,不落明文/不打日志);摄像头流凭据 AES-256-GCM 可逆+版本化;两机制不可混用。
  5. secret 全 `${ENV}` 注入 + fail-fast;secret/PII 绝不入日志/审计/异常。
  6. MapStruct `unmappedTargetPolicy=ERROR`;Update 用 `NullValuePropertyMappingStrategy.IGNORE` 实现 PATCH;命名 `XxxCreateDTO`/`XxxUpdateDTO`/`Xxx`(entity)/`XxxVO`。
  7. JPA `ddl-auto: validate`、`open-in-view: false`;`@Async` 共用 `applicationTaskExecutor`(有界队列/CallerRunsPolicy),外部 HTTP 在事务边界外 + 超时。
- **测试门命令**(gate-checklist 与 dev-lead 必须逐字一致):
  - 后端:`cd backend && ./gradlew test`(需 Docker/testcontainers;本机无 Java → DooD 容器:挂 repo 根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` + Ryuk 关)。
  - 前端:`cd frontend && npm run lint && npm run build`(本机 node v24 原生;回退 `node:20` 容器,提交前还原 `next-env.d.ts`)。
  - 改了 `db/initdb/` seed → **必须** `cd backend && ./gradlew cleanTest test`(seed 即 fixture,不在 test 输入会被判 UP-TO-DATE)。
- **不做**:多实例扩展相关任何事(无限期搁置);不重造 plan/design/archive(复用 OpenSpec);不替代 `component-analysis-orchestrator`;单 change 不细分多个前端并行 lane(默认 1 BE + 1 FE)。
- **文件编码**:Windows 环境,写文件用 UTF-8(无 BOM);frontmatter 用 `---` 包裹,`name`/`description`(agent 另加 `model`)必填。

---

## File Structure

八个新建文件,各自单一职责,files-that-change-together-live-together:

| 文件 | 职责 | 类比既有 |
|---|---|---|
| `.claude/skills/development-orchestrator/SKILL.md` | 编排器主体:阶段 DAG、Phase 0–5 流程、agent 分派表、数据传递协议、错误处理、测试场景 | `component-analysis-orchestrator/SKILL.md` |
| `references/api-contract-template.md` | 契约产物模板(design 阶段冻结的 API 契约结构) | `references/finding-schema.md` |
| `references/gate-checklist.md` | 分层门禁清单(硬测试门命令 + 定向复核触发条件 + 自修回路规则) | `references/report-template.md` |
| `references/fan-in-playbook.md` | cherry-pick 收口手法 + 冲突裁决 + 踩踏防护 | (无直接对应,新造) |
| `.claude/agents/dev-lead.md` | 领队 agent 定义(opus):编排而非亲写 | `.claude/agents/analysis-lead.md` |
| `.claude/agents/backend-implementer.md` | 后端实现者(sonnet):只写 backend/,TDD,守安全约定 | (无,新造) |
| `.claude/agents/frontend-implementer.md` | 前端实现者(sonnet):只写 frontend/,对契约接线 | (无,新造) |
| `.claude/commands/dev-pipeline.md` | 薄命令入口,委托到 skill | `.claude/commands/opsx/propose.md` |

**构建顺序理由**:先建被引用的叶子(3 references + 3 agents),再建汇聚它们的 SKILL.md,最后建最薄的 command。但因是 prose 交叉引用,各任务的 Interfaces 块已声明彼此的 canonical 名字/路径,乱序阅读也能对齐。

---

## Task 1: api-contract-template.md(契约产物模板)

**Files:**
- Create: `.claude/skills/development-orchestrator/references/api-contract-template.md`

**Interfaces:**
- Consumes: 无(叶子)。
- Produces: 契约产物结构,被 `SKILL.md`(design 阶段)、`dev-lead.md`、`backend-implementer.md`、`frontend-implementer.md`、`integration-analyst`(fan-in 校验)引用;canonical 落点 `openspec/changes/<change-id>/api-contract.md`。

- [ ] **Step 1: 写模板文件**

标题 `# API 契约产物模板`,开头一句说明:design 阶段冻结此产物为前后端唯一真源,archive 时随 change 一并归档。包含一个 fenced ```markdown 模板块,逐字段覆盖设计稿 §4:

必须包含的模板小节(每个受影响端点重复一组):
- **端点标识**:路径(注意命名不统一 `detection-events` 连字符 vs `detection_sessions`/`cctv_cameras` 下划线,以实际 controller 为准)、HTTP 方法。
- **鉴权**:会话+CSRF(业务 API)或 internal Bearer(`/api/v1/internal/**`,`ROLE_AI_SERVICE`)。
- **请求**:DTO 类名(`XxxCreateDTO`/`XxxUpdateDTO`)、字段名 / 类型 / 可空性 / 校验注解。
- **响应**:VO 类名(`XxxVO`)、字段名 / 类型 / 可空性 / 嵌套 shape。
- **enum**:涉及的 enum 名 + 值(提醒三处同步:DB / 后端 `type.*` / 前端 i18n,单一真源 `GET /api/v1/enums/{name}`)。
- **分页**:是否分页;Spring `Page` ↔ 前端 `PageResponse` 的 shape 对齐。
- **错误契约**:跨租户/不可见 → 404(隐藏存在性);校验失败 shape。
- **前端对齐点**:对应 `frontend/src/services/apis/*.api.ts` 文件名 + RTK Query/Axios 双客户端 + CSRF 头回填。

结尾一句「填写守则」:契约含糊 → 回到 design 补清,不退化成后端先行;字段级必须双侧可读,integration-analyst 在 fan-in 逐字段比对。

- [ ] **Step 2: 结构校验**

Run: `node -e "const s=require('fs').readFileSync('.claude/skills/development-orchestrator/references/api-contract-template.md','utf8'); if(!/请求|DTO/.test(s)||!/响应|VO/.test(s)||!/enum/i.test(s)||!/分页|Page/.test(s)||!/404/.test(s)||!/services\/apis/.test(s)) throw new Error('缺必需小节'); console.log('OK')"`
Expected: 输出 `OK`(本机 node v24 在 PATH)。

- [ ] **Step 3: 占位符扫描**

Run: `grep -nE "TBD|TODO|FIXME|待补|xxx占位" .claude/skills/development-orchestrator/references/api-contract-template.md || echo "CLEAN"`
Expected: `CLEAN`(模板里的 `XxxVO`/`XxxCreateDTO` 是命名约定示例,非占位符,不算命中)。

- [ ] **Step 4: 提交**

```bash
git add .claude/skills/development-orchestrator/references/api-contract-template.md
git commit -m "feat(dev-pipeline): 契约产物模板(api-contract-template)"
```

---

## Task 2: gate-checklist.md(分层门禁清单)

**Files:**
- Create: `.claude/skills/development-orchestrator/references/gate-checklist.md`

**Interfaces:**
- Consumes: 无(叶子)。
- Produces: 门禁四层清单,被 `SKILL.md`(Phase 4 门禁)、`dev-lead.md`(驱动门禁)引用。四层命名固定:`①硬测试门`/`②/code-review`/`③安全+集成定向复核`/`④自修回路`。

- [ ] **Step 1: 写清单文件**

标题 `# 分层门禁清单`,说明:fan-in 后按序执行,任一层不过则回路;因两端 implementer 都是 sonnet,门禁是质量承重墙,③ 强制非可选。四层内容(照抄 Global Constraints 的测试门命令,逐字):

1. **①硬测试门(不绿不放行)**:
   - 后端 `cd backend && ./gradlew test`(DooD:挂 repo 根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` + Ryuk 关;真因藏在 `build/test-results` xml)。
   - 前端 `cd frontend && npm run lint && npm run build`(node v24 原生 / 回退 node:20 容器,提交前还原 `next-env.d.ts`)。
   - 若改 `db/initdb/` seed → 必须 `./gradlew cleanTest test`。
2. **②`/code-review`**:对合并 diff 做通用正确性/复用审查。
3. **③定向复核(强制)**:
   - `security-analyst`(opus):认证授权、多租户隔离(JPQL 谓词非加载后过滤)、PII/RRN/密钥、注入/CSRF、internal Bearer 范围。
   - `integration-analyst`(opus):契约双侧吻合(后端 DTO/VO ↔ 前端 api.ts 逐字段)、字段错位、SSE/事件协议、enum 三处同步。
4. **④自修回路**:findings 回给**对应 implementer** 自修 → 重跑 ①–③ 直到清零。
   - **high-risk 一律 halt 等维护者批准**(sub-skill: implement-review-loop 的 halt 约定;批准后编辑 run 脚本 halt 块 + 带同样 args resume 放行)。
   - 回路 **exhausted 仍未清零** → dev-lead 自验 + 提交剩余修正,收口报告**如实标注**未清零项(记忆:loop exhausted 末轮 fix 常留工作树未提交,Lead 须自验+提交)。

结尾放一张「通过判据」小表:每层的 pass 条件 + 不过时去向(回哪个 implementer / halt / 回 design)。

- [ ] **Step 2: 结构校验(四层齐备 + 命令逐字)**

Run: `node -e "const s=require('fs').readFileSync('.claude/skills/development-orchestrator/references/gate-checklist.md','utf8'); for(const k of ['./gradlew test','npm run lint && npm run build','cleanTest','/code-review','security-analyst','integration-analyst','halt','TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal']){ if(!s.includes(k)) throw new Error('缺: '+k);} console.log('OK')"`
Expected: 输出 `OK`。

- [ ] **Step 3: 提交**

```bash
git add .claude/skills/development-orchestrator/references/gate-checklist.md
git commit -m "feat(dev-pipeline): 分层门禁清单(gate-checklist)"
```

---

## Task 3: fan-in-playbook.md(收口手法)

**Files:**
- Create: `.claude/skills/development-orchestrator/references/fan-in-playbook.md`

**Interfaces:**
- Consumes: 无(叶子)。
- Produces: fan-in 收口步骤,被 `SKILL.md`(Phase 3 fan-in)、`dev-lead.md`(执行收口)引用。

- [ ] **Step 1: 写 playbook 文件**

标题 `# Fan-in 收口 Playbook`。内容(照抄设计稿 §5 + 记忆里 parallel-apply 教训):

- **Fan-out 前提回顾**:每 implementer 在独立 git worktree(`.claude/worktrees/`)工作;单 change 默认 1 BE lane + 1 FE lane;纯后端/纯前端则只开一侧;实现者彼此不通信,跨侧疑问记进各自 notes 由 dev-lead 核对。
- **收口步骤**:
  1. 每批开工前 dev-lead 核对 `git status`(踩踏防护:未提交改动的 worktree 上再跑会被 fix 轮 clobber 回 HEAD)。
  2. 批次间先 commit,再进下一轮门禁/修复。
  3. 把两个 worktree 的提交 **cherry-pick 到 `develop`**;冲突由 dev-lead 裁决(前后端文件域基本不重叠,冲突罕见;若重叠优先保契约一致侧)。
  4. cherry-pick 后立即进 gate-checklist ①硬测试门(合并后的 develop 状态才是门禁对象)。
- **worktree 卫生**:收口后按 finishing-a-development-branch / GC 三层机制清理残留 worktree/分支(SessionStart hook 自动 prune;/gc skill 带确认回收)。判定用 `git rev-parse --show-toplevel` == 自身,避免对无 `.git` 目录误判到父仓库。
- **exhausted 兜底**:门禁回路耗尽仍未清零时,末轮 implementer fix 常留工作树未提交 → dev-lead 必须自验 + 提交,不能假设已提交。

- [ ] **Step 2: 结构校验**

Run: `node -e "const s=require('fs').readFileSync('.claude/skills/development-orchestrator/references/fan-in-playbook.md','utf8'); for(const k of ['worktree','cherry-pick','git status','develop','clobber']){ if(!s.includes(k)) throw new Error('缺: '+k);} console.log('OK')"`
Expected: 输出 `OK`。

- [ ] **Step 3: 提交**

```bash
git add .claude/skills/development-orchestrator/references/fan-in-playbook.md
git commit -m "feat(dev-pipeline): fan-in 收口 playbook"
```

---

## Task 4: backend-implementer.md(后端实现者 agent)

**Files:**
- Create: `.claude/agents/backend-implementer.md`

**Interfaces:**
- Consumes: `references/api-contract-template.md` 定义的契约结构(对着 `openspec/changes/<change-id>/api-contract.md` 实现)。
- Produces: agent name `backend-implementer`(model `sonnet`),被 `dev-lead.md`/`SKILL.md` 的 `Agent` fan-out 调用。产出:`backend/` 下的实现 + 测试,提交在自己的 worktree。

- [ ] **Step 1: 写 agent 定义**

Frontmatter(逐字):
```yaml
---
name: backend-implementer
description: 开发流水线的后端实现者——只写 backend/,TDD,对着冻结的 API 契约实现,严守多租户隔离/CSRF/@PreAuthorize/MapStruct 约定。dev-lead fan-out 的实现侧成员。
model: sonnet
---
```

正文小节(照 analysis 侧 agent 的结构:核心角色 / 作业维度 / 作业原则 / 输入输出协议 / 错误处理 / 协作通信):
- **核心角色**:只写 `backend/`(Spring Boot);对着 dev-lead 传入的冻结契约路径实现分配到的 tasks 子集;TDD 优先(先写失败测试)。不碰 frontend/ai/db schema(schema 变更须维护者批准,超出实现者权限 → 记 notes 交 dev-lead)。
- **作业维度/硬约束**(照抄 Global Constraints 安全 invariants 1–7,逐条):多租户 `kindergarten_id` 谓词进 JPQL(禁加载后过滤,跨租户 404);CSRF 强制(仅 internal Bearer 豁免);`@PreAuthorize` 标 service 方法;RRN=HMAC 不可逆不打日志;secret `${ENV}` fail-fast 不入日志;MapStruct `unmappedTargetPolicy=ERROR` + Update 用 `IGNORE`;命名 `XxxCreateDTO/Xxx/XxxVO`;`ddl-auto: validate`、`open-in-view: false`;`@Async` 共用有界池、外部 HTTP 出事务 + 超时。
- **包结构约定**:按层平铺(controller 仅路由→service 业务+授权+事务→repository/mapper),根包 `com.ai_kids_care.v1`;controller 只注入 service。
- **TDD/测试**:testcontainers 自起 PG+Redis;改 seed 记入 notes(dev-lead 会触发 cleanTest);断言要有意义(非仅 status 200),覆盖租户隔离/授权/错误路径。
- **输入**:dev-lead 的 `Agent` prompt(负责组件、change 路径、契约路径、tasks 子集、worktree 路径)。**输出**:worktree 内提交 + 返回 top 摘要 + 遗留跨侧疑问 notes 给 dev-lead。
- **错误处理**:契约含糊/需 schema 变更/需跨侧决策 → 不自作主张,记 notes 交 dev-lead(回 design 或等批准)。测试跑不起来(缺 Docker)→ 标注,交 dev-lead 在门禁 DooD 跑。
- **协作**:不与 frontend-implementer 直接通信(不存在实时互通);一切经 dev-lead 在 fan-in 核对。已有产出则增量修订指定反馈点(自修回路)。

- [ ] **Step 2: frontmatter + 内容校验**

Run: `node -e "const s=require('fs').readFileSync('.claude/agents/backend-implementer.md','utf8'); const m=s.match(/^---\n([\s\S]*?)\n---/); if(!m) throw new Error('无 frontmatter'); if(!/name:\s*backend-implementer/.test(m[1])) throw new Error('name 错'); if(!/model:\s*sonnet/.test(m[1])) throw new Error('model 错'); for(const k of ['kindergarten_id','@PreAuthorize','CSRF','TDD','契约']){ if(!s.includes(k)) throw new Error('缺: '+k);} console.log('OK')"`
Expected: 输出 `OK`。

- [ ] **Step 3: 提交**

```bash
git add .claude/agents/backend-implementer.md
git commit -m "feat(dev-pipeline): backend-implementer agent(sonnet)"
```

---

## Task 5: frontend-implementer.md(前端实现者 agent)

**Files:**
- Create: `.claude/agents/frontend-implementer.md`

**Interfaces:**
- Consumes: `references/api-contract-template.md` 定义的契约结构。
- Produces: agent name `frontend-implementer`(model `sonnet`),被 `dev-lead.md`/`SKILL.md` fan-out 调用。产出:`frontend/` 下的接线实现,提交在自己的 worktree。

- [ ] **Step 1: 写 agent 定义**

Frontmatter(逐字):
```yaml
---
name: frontend-implementer
description: 开发流水线的前端实现者——只写 frontend/,对着冻结的 API 契约接线 services/apis/*,RTK Query/Axios 双客户端 + CSRF 回填,绝不传 kindergartenId。dev-lead fan-out 的实现侧成员。
model: sonnet
---
```

正文小节(同结构):
- **核心角色**:只写 `frontend/`(Next.js App Router,`src/app/`);对着冻结契约接线 API 层(全在 `src/services/apis/`);不碰 backend/ai。
- **硬约束**:**绝不传 kindergartenId**(租户靠后端会话上下文);双 HTTP 客户端并存(RTK Query `baseApi` + Axios `apiClient`),两者拦截器都注入 CSRF 头(回填 `X-XSRF-TOKEN`);Redux store 两 reducer(`api` + `user`);会话恢复靠 `SessionBootstrap` 调 `GET /api/v1/auth/session`;`output: 'export'` 纯静态导出无 SSR;enum label 归前端 i18n(值取 `GET /api/v1/enums/{name}`)。
- **契约对齐**:请求/响应逐字段贴合契约(字段名/可空性/enum/分页 `PageResponse` ↔ Spring `Page`);后端返回 X 前端读 Y 的错位是重点自查项。
- **测试/验证**:`npm run lint && npm run build`(node v24 原生 / node:20 容器回退,提交前还原 `next-env.d.ts`);React 19 lint 坑注意。
- **输入/输出**:同 backend-implementer(dev-lead prompt → worktree 提交 + notes)。
- **错误处理**:契约含糊/后端字段缺失 → 记 notes 交 dev-lead(「单侧缺失疑未接线」本身是要上报的 finding),不自造字段。
- **协作**:不与 backend-implementer 直接通信;经 dev-lead fan-in 核对;自修回路增量修订。

- [ ] **Step 2: frontmatter + 内容校验**

Run: `node -e "const s=require('fs').readFileSync('.claude/agents/frontend-implementer.md','utf8'); const m=s.match(/^---\n([\s\S]*?)\n---/); if(!m) throw new Error('无 frontmatter'); if(!/name:\s*frontend-implementer/.test(m[1])) throw new Error('name 错'); if(!/model:\s*sonnet/.test(m[1])) throw new Error('model 错'); for(const k of ['kindergartenId','CSRF','services/apis','PageResponse','契约']){ if(!s.includes(k)) throw new Error('缺: '+k);} console.log('OK')"`
Expected: 输出 `OK`。

- [ ] **Step 3: 提交**

```bash
git add .claude/agents/frontend-implementer.md
git commit -m "feat(dev-pipeline): frontend-implementer agent(sonnet)"
```

---

## Task 6: dev-lead.md(领队 agent)

**Files:**
- Create: `.claude/agents/dev-lead.md`

**Interfaces:**
- Consumes: `backend-implementer`/`frontend-implementer`(fan-out 对象)、`security-analyst`/`integration-analyst`(门禁复核,已存在)、三个 references、契约产物。
- Produces: agent name `dev-lead`(model `opus`),被 `SKILL.md`/`command` 作为编排入口调用。

- [ ] **Step 1: 写 agent 定义**

Frontmatter(逐字):
```yaml
---
name: dev-lead
description: 开发流水线的领队/编排者——读 change 与冻结契约,建执行 DAG,fan-out 前后端实现者并行实现,驱动分层门禁与自修回路,fan-in cherry-pick 收口,触发 archive。用 opus 保证编排与裁决质量。
model: opus
---
```

正文(照 analysis-lead 结构):
- **核心角色**:领队与收口者,不亲自写某侧代码,而是:读 change/契约 → 建执行 DAG → fan-out 实现者 → 驱动分层门禁 → fan-in cherry-pick → 触发 archive。
- **作业原则**:编排而非替代;契约含糊回 design 补清(不退化后端先行);两端 sonnet → 门禁是承重墙,③ 定向复核强制非可选;冲突不删由 lead 裁决;诚实标注未清零项。
- **工作流(详见 `development-orchestrator` skill)**:
  1. 读 change(`openspec/changes/<change-id>/`)+ 冻结契约 `api-contract.md`;缺架构上下文先派 `Explore`。
  2. 建执行 DAG(同层无依赖并行):`[backend-implementer worktree A ∥ frontend-implementer worktree B]` → gate → archive。
  3. fan-out:用 `Agent` 并行 spawn 两实现者,各传 `model`(sonnet),prompt 自包含(组件/change/契约路径/tasks 子集/worktree/本侧硬约束);纯后端或纯前端 change 只开一侧。
  4. fan-in:按 `references/fan-in-playbook.md` cherry-pick 到 develop(核对 git status 防 clobber,批次间 commit)。
  5. 门禁:按 `references/gate-checklist.md` ①→④;findings 回对应 implementer 自修重跑;high-risk halt 等批准;exhausted 自验+提交+如实标注。
  6. archive:门禁清零后触发 `openspec-archive-change`(契约随 change 归档)。
- **输入/输出**:输入=用户开发请求 + change id + 冻结契约;输出=落 develop 的实现 + 收口报告(含实际拓扑 DAG、门禁结论、未清零项)。
- **错误处理**:实现者 1 次重试仍失败 → 不阻塞,报告标注,用其余成文;无 `TeamCreate`,编排一律 `Agent` fan-out + lead 合并,无「团队模式」可降级。
- **协作**:`Agent` 并行分派自包含任务;`SendMessage` 续聊**已 spawn** 的子代理补澄清;跨侧互证由 lead 在 fan-in 对照双侧产物完成(不靠并发 agent 互通)。

- [ ] **Step 2: frontmatter + 内容 + 引用校验**

Run: `node -e "const s=require('fs').readFileSync('.claude/agents/dev-lead.md','utf8'); const m=s.match(/^---\n([\s\S]*?)\n---/); if(!m) throw new Error('无 frontmatter'); if(!/name:\s*dev-lead/.test(m[1])) throw new Error('name 错'); if(!/model:\s*opus/.test(m[1])) throw new Error('model 错'); for(const k of ['backend-implementer','frontend-implementer','fan-in-playbook','gate-checklist','TeamCreate','cherry-pick']){ if(!s.includes(k)) throw new Error('缺: '+k);} console.log('OK')"`
Expected: 输出 `OK`。

- [ ] **Step 3: 提交**

```bash
git add .claude/agents/dev-lead.md
git commit -m "feat(dev-pipeline): dev-lead 领队 agent(opus)"
```

---

## Task 7: SKILL.md(编排器主体)

**Files:**
- Create: `.claude/skills/development-orchestrator/SKILL.md`

**Interfaces:**
- Consumes: 三个 references、三个新 agent、复用的 `security-analyst`/`integration-analyst`/`Explore`、OpenSpec 的 `openspec-propose`/`openspec-archive-change`。
- Produces: skill name `development-orchestrator`;被 `.claude/commands/dev-pipeline.md` 委托触发。

- [ ] **Step 1: 写 SKILL.md**

Frontmatter(`name` + `description`;description 必须含**排除性区分**,照抄审查侧写法):
```yaml
---
name: development-orchestrator
description: 编排一条 plan→design→implement(前后端并行 fan-out/fan-in)→gate→archive 的开发流水线,实现一条 OpenSpec change。当用户要求"实现/开发某 change"、"按流水线做前后端实现"、"并行实现前后端"时使用。排除性区分:①"分析/审查工程现状/多角度健康度"→用 component-analysis-orchestrator,不是本 skill;②"只看当前未提交 diff"→用 /code-review;③纯 harness/agent/skill 自身变更→用 harness:harness;④只想生成 change 的 proposal/design/tasks 不实现→用 openspec propose。本 skill 是"把一条已 propose 的 change 实现落地"。
---
```

正文小节(镜像 component-analysis-orchestrator):
- **开头执行模型诚实化**:照抄审查侧 v3 措辞——本环境只有 `Agent`/`SendMessage`/`Task*`,无 `TeamCreate`;编排是显式 DAG 的 `Agent` fan-out + lead 合并,不假装并发 agent 实时互通。
- **阶段拓扑(总 DAG)**:照抄设计稿 §2 的 ASCII 图(`openspec-propose` → `[backend ∥ frontend]` → gate ①②③④ → `openspec-archive-change`);标注 plan/design/archive=复用 OpenSpec,只中间 implement+gate 新造。
- **角色与模型分配表**:dev-lead(opus)/backend-implementer(sonnet)/frontend-implementer(sonnet)/security-analyst(opus,复用)/integration-analyst(opus,复用)/Explore(复用)。
- **语言约定**:同 Global Constraints。
- **Phase 0 上下文确认**:确认 change 已 propose(存在 `openspec/changes/<id>/` + design + tasks + `api-contract.md`);契约缺失或含糊 → 回 design(`/opsx:propose` 或补契约)。判断纯后端/纯前端/全栈 change → 决定开几个 lane。
- **Phase 1 契约冻结确认**:核对 `api-contract.md` 存在且字段级完整(对照 `references/api-contract-template.md`);这是并行前提。
- **Phase 2 fan-out 实现**:dev-lead 用 `Agent` 并行 spawn 两实现者(worktree 隔离,各传 model,prompt 自包含);引 `references/fan-in-playbook.md` 的 fan-out 前提。
- **Phase 3 fan-in 收口**:cherry-pick 到 develop,按 `references/fan-in-playbook.md`(git status 防 clobber、批次间 commit)。
- **Phase 4 分层门禁 + 自修回路**:按 `references/gate-checklist.md` ①硬测试门→②/code-review→③安全+集成定向复核→④自修回路;high-risk halt;exhausted 自验+提交+标注。
- **Phase 5 archive**:门禁清零 → `openspec-archive-change`(契约随 change 归档)。
- **数据传递协议**:返回值级(`Agent` 返回 top 摘要 + worktree/文件路径)、续聊级(`SendMessage` 对已 spawn 子代理)、文件级(change 目录 + worktree + develop)。
- **错误处理**:实现者失败 1 次重试不阻塞;契约含糊回 design;无 `TeamCreate` 无降级;环境事实(node v24 在 PATH,Java 无 → 后端走 DooD)。
- **测试场景**(≥3 条,照审查侧风格):①全栈正常流(propose 好的 change → 双实现者并行 → cherry-pick → 门禁绿 → archive);②契约含糊流(Phase 1 发现契约缺字段 → 回 design 补 → 再 fan-out);③门禁 halt 流(security-analyst 报多租户越权 high-risk → halt 等维护者批准);④纯前端 change(只开 FE lane,后端门只跑 lint/build 侧)。

- [ ] **Step 2: frontmatter + 排除性区分 + 引用校验**

Run: `node -e "const s=require('fs').readFileSync('.claude/skills/development-orchestrator/SKILL.md','utf8'); const m=s.match(/^---\n([\s\S]*?)\n---/); if(!m) throw new Error('无 frontmatter'); if(!/name:\s*development-orchestrator/.test(m[1])) throw new Error('name 错'); if(!/component-analysis-orchestrator/.test(m[1])||!/code-review/.test(m[1])) throw new Error('description 缺排除性区分'); for(const k of ['TeamCreate','openspec-propose','openspec-archive-change','gate-checklist','fan-in-playbook','api-contract-template','backend-implementer','frontend-implementer']){ if(!s.includes(k)) throw new Error('缺: '+k);} console.log('OK')"`
Expected: 输出 `OK`。

- [ ] **Step 3: 引用路径存在性校验**

Run: `node -e "const fs=require('fs'); for(const p of ['.claude/skills/development-orchestrator/references/api-contract-template.md','.claude/skills/development-orchestrator/references/gate-checklist.md','.claude/skills/development-orchestrator/references/fan-in-playbook.md','.claude/agents/dev-lead.md','.claude/agents/backend-implementer.md','.claude/agents/frontend-implementer.md']){ if(!fs.existsSync(p)) throw new Error('引用不存在: '+p);} console.log('OK')"`
Expected: 输出 `OK`(前置任务已建齐这些文件)。

- [ ] **Step 4: 提交**

```bash
git add .claude/skills/development-orchestrator/SKILL.md
git commit -m "feat(dev-pipeline): development-orchestrator 编排器 skill"
```

---

## Task 8: dev-pipeline.md(薄命令入口)+ 全链一致性收口

**Files:**
- Create: `.claude/commands/dev-pipeline.md`

**Interfaces:**
- Consumes: `development-orchestrator` skill(委托目标)。
- Produces: `/dev-pipeline` 命令。

- [ ] **Step 1: 写命令文件**

Frontmatter(照 `opsx/propose.md` 风格):
```yaml
---
name: "Dev Pipeline"
description: 实现一条已 propose 的 OpenSpec change——前后端并行 fan-out/fan-in + 分层门禁收口
category: Workflow
tags: [workflow, development, parallel]
---
```

正文(薄委托,不重复 skill 逻辑):
- 一句说明:本命令把开发流水线委托给 `development-orchestrator` skill,由 `dev-lead`(opus)编排。
- **Input**:`/dev-pipeline` 后的参数 = 目标 change 名(kebab-case);无参数则用 AskUserQuestion 问「要实现哪条 change?(须已 propose,存在 openspec/changes/<id>/ 与 api-contract.md)」。
- **Steps**:1) 校验 change 已 propose + 契约冻结(缺则提示先 `/opsx:propose` 或补契约);2) 调用 `development-orchestrator` skill 走 Phase 0–5;3) 收口后展示报告 + 提示门禁清零可 `/opsx:archive`。
- **Guardrails**:破坏性变更(schema/迁移/删除)须维护者逐个批准;high-risk 门禁 halt;不替代 `component-analysis-orchestrator` 与 `/code-review`。

- [ ] **Step 2: 命令文件校验**

Run: `node -e "const s=require('fs').readFileSync('.claude/commands/dev-pipeline.md','utf8'); const m=s.match(/^---\n([\s\S]*?)\n---/); if(!m) throw new Error('无 frontmatter'); if(!/development-orchestrator/.test(s)) throw new Error('未委托到 skill'); console.log('OK')"`
Expected: 输出 `OK`。

- [ ] **Step 3: 全链一致性收口校验(所有 8 文件 + 交叉引用 + 占位符)**

Run: `node -e "const fs=require('fs'); const files=['.claude/skills/development-orchestrator/SKILL.md','.claude/skills/development-orchestrator/references/api-contract-template.md','.claude/skills/development-orchestrator/references/gate-checklist.md','.claude/skills/development-orchestrator/references/fan-in-playbook.md','.claude/agents/dev-lead.md','.claude/agents/backend-implementer.md','.claude/agents/frontend-implementer.md','.claude/commands/dev-pipeline.md']; for(const f of files){ if(!fs.existsSync(f)) throw new Error('缺文件: '+f); const s=fs.readFileSync(f,'utf8'); if(/\bTBD\b|\bTODO\b|\bFIXME\b|待补|填在此/.test(s)) throw new Error('占位符命中: '+f); if(!/^---\n[\s\S]*?\n---/.test(s)) throw new Error('frontmatter 缺: '+f);} console.log('全 8 文件 OK,无占位符,frontmatter 齐备')"`
Expected: 输出 `全 8 文件 OK,无占位符,frontmatter 齐备`。

- [ ] **Step 4: agent name/model 绑定终检**

Run: `node -e "const fs=require('fs'); const want={'.claude/agents/dev-lead.md':['dev-lead','opus'],'.claude/agents/backend-implementer.md':['backend-implementer','sonnet'],'.claude/agents/frontend-implementer.md':['frontend-implementer','sonnet']}; for(const [f,[n,mdl]] of Object.entries(want)){ const s=fs.readFileSync(f,'utf8'); const m=s.match(/^---\n([\s\S]*?)\n---/)[1]; if(!new RegExp('name:\\\\s*'+n).test(m)||!new RegExp('model:\\\\s*'+mdl).test(m)) throw new Error('绑定错: '+f);} console.log('name↔model 绑定 OK')"`
Expected: 输出 `name↔model 绑定 OK`。

- [ ] **Step 5: 提交**

```bash
git add .claude/commands/dev-pipeline.md
git commit -m "feat(dev-pipeline): /dev-pipeline 薄命令入口 + 全链收口"
```

---

## Self-Review(计划对 spec 的覆盖核对)

**1. Spec coverage** — 逐节对照设计稿:
- §1 目标/非目标 → Global Constraints「不做」清单 + 各 agent 定义的职责边界 ✓
- §2 阶段拓扑 DAG → Task 7 SKILL.md 阶段拓扑小节 ✓
- §3 Agent 矩阵 → Task 4/5/6(三新 agent)+ Task 7 分配表(含复用 security/integration/Explore)✓
- §4 契约先行 → Task 1 模板 + Task 7 Phase 1 + 各 implementer「对契约实现」✓
- §5 fan-out/fan-in → Task 3 playbook + Task 6 dev-lead 工作流 + Task 7 Phase 2/3 ✓
- §6 分层门禁+自修回路 → Task 2 gate-checklist + Task 7 Phase 4 ✓
- §7 交付物清单 → 8 个 Task 一一对应 ✓
- §8 决策记录 → 已固化进各文件(混合底座/契约先行/两端 sonnet/分层门禁/skill+薄命令/TDD/单 lane)✓
- §9 未决/风险 → 触发边界(Task 7 description 排除性区分)、两端 sonnet 漏网(门禁③强制 + 可改 frontmatter 升 opus,写进 dev-lead 作业原则)、DooD 耗时(gate-checklist 注记)✓

**2. Placeholder scan** — Task 8 Step 3 全链扫描兜底;各 Task 已给完整 frontmatter + 小节内容要求,无「实现 later」。

**3. Type/name consistency** — canonical 名字/路径集中在 Global Constraints;各 Task Interfaces 块声明 Produces/Consumes;Task 8 Step 4 终检 name↔model 绑定。三处引用的四层门禁命名统一为 `①硬测试门/②/code-review/③安全+集成定向复核/④自修回路`。

无遗漏,无需返工。
