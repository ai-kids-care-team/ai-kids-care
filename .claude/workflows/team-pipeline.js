export const meta = {
  name: 'team-pipeline',
  description: 'Discovery(人工门外置)→ 并行细化规划 → 按 work-item 并发 implement<->review(worktree+codex/<id> 分支)→ 集成评审。各 item 独立成 lane 并发推进;高风险整批在规划后 halt 待人工批准(approved=true 续跑)。合并入 develop 仍由 Lead 人工执行。',
  phases: [
    { title: 'Plan' },
    { title: 'Implement' },
    { title: 'Review' },
    { title: 'Integrate' },
  ],
}

// ---- args: { brief, approved?, items?, maxFixRounds?, model? } -----------
// 容错:args 可能以对象或 JSON 字符串送达(见本仓库 harness 经验)。
let a = args
if (typeof a === 'string') { try { a = JSON.parse(a) } catch (e) { a = { brief: args } } }
const brief = (a && a.brief) ? a.brief : (typeof a === 'string' ? a : '')
const approved = !!(a && a.approved)
const MAX_FIX = (a && a.maxFixRounds) ? a.maxFixRounds : 3
const MODEL = (a && a.model) ? a.model : 'sonnet' // 子 agent 默认 sonnet(用户偏好)
const preItems = (a && Array.isArray(a.items)) ? a.items : null
if (!brief && !preItems) {
  return { error: 'pass { brief } (人工已定优先级的任务简报) 或 { items:[{id,title,scope}] }' }
}

const ITEMPLAN_SCHEMA = {
  type: 'object',
  required: ['id', 'title', 'riskLevel', 'allowedFiles', 'plan', 'acceptance', 'verify'],
  properties: {
    id: { type: 'string' },
    title: { type: 'string' },
    riskLevel: { type: 'string', enum: ['low', 'medium', 'high'] },
    allowedFiles: { type: 'array', items: { type: 'string' } },
    plan: { type: 'string' },
    acceptance: { type: 'array', items: { type: 'string' } },
    verify: { type: 'array', items: { type: 'string' } },
    riskReason: { type: 'string' },
  },
}
const VERDICT_SCHEMA = {
  type: 'object',
  required: ['pass', 'findings', 'verification'],
  properties: {
    pass: { type: 'boolean' },
    findings: { type: 'array', items: { type: 'object',
      required: ['severity', 'detail'],
      properties: {
        severity: { type: 'string', enum: ['P0', 'P1', 'P2', 'P3'] },
        detail: { type: 'string' },
        file: { type: 'string' }, // optional: 命中文件路径(不要求行号,避免幻觉)
      } } },
    // ② 证据字段:记录 reviewer 实际跑了哪些验证命令及结果(executable evidence,非意见)
    verification: { type: 'array', items: { type: 'object',
      required: ['command', 'passed'],
      properties: {
        command: { type: 'string' },
        passed: { type: 'boolean' },
        note: { type: 'string' },
      } } },
  },
}

// ---- Plan: decompose(barrier,需全局视图)→ 并行 detail-plan(只读) ----
phase('Plan')
let skeleton
if (preItems) {
  skeleton = preItems
} else {
  const decomp = await agent(
    `You are the decomposer。Discovery 与优先级判定已由人工完成。把这个已批准的简报拆成相互【文件不重叠】的独立 work-item,每个有单一明确产出。` +
    `【粒度护栏】每条 work-item 必须有实质工作量才值得单独成 lane(每条 lane = 一整套 plan+implement+worktree+review,开销很大)。` +
    `若多处改动是同一种【统一/机械】模式(如对 N 个文件做同一种注解/重命名),把它们【合并成 1 条 lane】整体处理,不要按文件拆成 N 条。只有当 item 之间逻辑独立、各自需要不同设计判断时才拆多条。宁可少拆。` +
    `优先复用仓库既有模式(authz-read-slice skill、SPEC-0001 切片、docs/assessments/2026-06-17-followup-audit.md)。\n简报: ${brief}`,
    { agentType: 'planner', phase: 'Plan', model: MODEL, label: 'decompose', schema: {
        type: 'object', required: ['items'],
        properties: { items: { type: 'array', items: {
          type: 'object', required: ['id', 'title', 'scope'],
          properties: { id: { type: 'string' }, title: { type: 'string' }, scope: { type: 'string' } } } } },
    } })
  skeleton = (decomp && decomp.items) ? decomp.items : []
}
if (!skeleton.length) { return { error: 'decomposer produced no items', brief } }
log(`拆出 ${skeleton.length} 个 work-item,并行细化规划中。`)

const plans = (await parallel(skeleton.map((it) => () =>
  agent(
    `You are a detail-planner for ONE work-item。产出 step 级计划、风险分级(按 .ai/CONTEXT.md Risk Gates)、严格的 allowedFiles、可执行验收点、验证命令(用仓库命令:bash scripts/test-backend.sh '<pattern>' / docker compose -f docker-compose.yml -f docker-compose.prod.yml config / cd frontend && npm.cmd run build / python -m py_compile)。保持 allowedFiles 与其它 item 不重叠。\n` +
    `work-item id=${it.id}: ${it.title}\nScope: ${it.scope}\n更广简报: ${brief}`,
    { agentType: 'planner', phase: 'Plan', model: MODEL, label: `plan:${it.id}`, schema: ITEMPLAN_SCHEMA })
))).filter(Boolean)
if (!plans.length) { return { error: 'detail-planning produced no plans' } }

// 文件重叠检测:worktree 隔离了并发写,但合并入 develop 可能冲突 → 警告(建议串行集成)。
const seen = {}
for (const p of plans) for (const f of (p.allowedFiles || [])) {
  if (seen[f]) log(`⚠ 文件重叠: ${f} 被 ${seen[f]} 与 ${p.id} 同时触及 — 合并 develop 时需串行/留意。`)
  else seen[f] = p.id
}

// ---- 高风险门(bounded autonomy):规划后、实现前整批 halt ----
const highRisk = plans.filter((p) => p.riskLevel === 'high')
if (highRisk.length && !approved) {
  log(`HIGH RISK item(s): ${highRisk.map((p) => p.id).join(', ')} — 规划后 halt,待人工批准(approved=true 续跑)。`)
  return {
    halted: true, reason: 'high-risk requires human approval',
    plans, highRisk: highRisk.map((p) => ({ id: p.id, title: p.title, riskReason: p.riskReason })),
  }
}

// ---- Implement <-> Review:每个 item 一条并发 lane(worktree + codex/<id>) ----
const results = await pipeline(plans, async (p) => {
  let verdict = null, impl = null
  for (let round = 1; round <= MAX_FIX; round++) {
    impl = await agent(
      `You are the implementer for work-item ${p.id}(${p.title})。你在一个独立 git worktree 中。\n` +
      `分支策略:先 git worktree prune(清理已回收 worktree 的占用)。若分支 codex/${p.id} 已存在(修复轮)→ git checkout codex/${p.id} 继续(失败则 git checkout -B codex/${p.id} codex/${p.id});否则 git checkout -b codex/${p.id} develop。\n` +
      `只改这些文件:${p.allowedFiles.join(', ')}。计划:${p.plan}\n验收点:${(p.acceptance || []).join(' | ')}\n` +
      `本地验证(用仓库命令):${(p.verify || []).join(' ; ')}。验证通过后必须 git add + git commit 到 codex/${p.id}(这是唯一交付载体——worktree 会被回收,未 commit 的改动会丢失)。不要 push,不要切到/改 develop。保留 Korean 文案与代码标识符。\n` +
      `${verdict ? '需修复的评审发现:\n' + JSON.stringify(verdict.findings, null, 2) : ''}`,
      { agentType: 'implementer', phase: 'Implement', model: MODEL, isolation: 'worktree', label: `impl:${p.id}:r${round}` })

    verdict = await agent(
      `You are the independent reviewer(fresh context)for work-item ${p.id}。\n` +
      `审读分支 diff:git --no-pager diff develop...codex/${p.id}。对照计划与验收点评审,跑受影响验证(${(p.verify || []).join(' ; ')})与 git diff --check。\n` +
      `① 严重度:P0-P2 任一即 fail(pass=false);P3 记为【非阻断 observation】,不要因 P3 而判 fail。\n` +
      `② 证据:verification 字段必须如实记录你【实际执行】的命令及结果(passed true/false),不要凭空断言;无法执行的命令不要列。\n` +
      `计划:${p.plan}\n验收点:${(p.acceptance || []).join(' | ')}\n实现摘要:${impl}`,
      { agentType: 'reviewer', phase: 'Review', model: MODEL, label: `review:${p.id}:r${round}`, schema: VERDICT_SCHEMA })

    // ① 门控:只有 P0-P2 阻断;P3-only 视为通过(代码里裁定,不全信模型的 pass 标志)
    const blocking = (verdict.findings || []).filter((f) => f.severity !== 'P3')
    const p3 = (verdict.findings || []).filter((f) => f.severity === 'P3')
    if (verdict.pass || blocking.length === 0) {
      log(`✓ ${p.id} PASS(round ${round})${p3.length ? `,${p3.length} 条 P3 非阻断` : ''}。`)
      return { id: p.id, title: p.title, branch: `codex/${p.id}`, pass: true, rounds: round, verdict, nonBlocking: p3 }
    }
    log(`${p.id} round ${round}: ${blocking.length} 条阻断(P0-P2)— 循环修复。`)
  }
  log(`✗ ${p.id} 用尽 ${MAX_FIX} 轮仍未过 — 交人工。`)
  return { id: p.id, title: p.title, branch: `codex/${p.id}`, pass: false, rounds: MAX_FIX, exhausted: true, verdict }
})

const passed = results.filter((r) => r && r.pass)
const failed = results.filter((r) => !r || !r.pass)

// ---- Integration review(barrier):通过分支的跨 item 一致性 ----
phase('Integrate')
let integration = null
if (passed.length) {
  integration = await agent(
    `You are the integration reviewer(read-only)。这些已通过单项评审的分支即将合并入 develop:${passed.map((r) => r.branch).join(', ')}。\n` +
    `逐个 git --no-pager diff develop...<branch> 审读,检查跨 item 的一致性:共享配置是否被不同 item 以不兼容方式修改、合并冲突风险、整体是否仍自洽。报告任何阻断合并的问题。`,
    { agentType: 'reviewer', phase: 'Integrate', model: MODEL, label: 'integrate', schema: {
        type: 'object', required: ['mergeable', 'notes'],
        properties: {
          mergeable: { type: 'boolean' }, notes: { type: 'string' },
          blockers: { type: 'array', items: { type: 'string' } },
        },
    } })
}

return {
  halted: false,
  summary: `${passed.length}/${results.length} item 通过单项评审`,
  passed: passed.map((r) => ({
    id: r.id, branch: r.branch, rounds: r.rounds,
    nonBlockingP3: (r.nonBlocking || []).length,
    verification: r.verdict && r.verdict.verification, // ② executable-evidence 透出给 Lead
  })),
  failed: failed.map((r) => r && { id: r.id, branch: r.branch, findings: r.verdict && r.verdict.findings }),
  integration,
  nextAction: 'Lead 复核 integration 结论后,把通过的 codex/<id> 分支(文件重叠者串行)合并入 develop;失败/exhausted 的 item 交人工。',
}
