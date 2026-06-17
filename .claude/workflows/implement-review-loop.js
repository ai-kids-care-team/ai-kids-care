export const meta = {
  name: 'implement-review-loop',
  description: 'Plan -> implement -> review -> fix loop for one approved task. Bounded autonomy: high-risk halts for human approval. Deterministic, reproducible; use for the Implementation phase.',
  phases: [{ title: 'Plan' }, { title: 'Implement' }, { title: 'Review' }],
}

// args: a string task description, or { task: "..." }
const task = (args && args.task) ? args.task : args
if (!task) { return { error: 'no task provided (pass a task description as args)' } }

// --- Plan + risk classification (planner sub-agent) -----------------------
phase('Plan')
const plan = await agent(
  `You are the planner. Design the approach for this task and classify its risk per .ai/CONTEXT.md Risk Gates.\nTask: ${task}`,
  { agentType: 'planner', phase: 'Plan', schema: {
      type: 'object',
      required: ['riskLevel', 'plan', 'allowedFiles'],
      properties: {
        riskLevel: { type: 'string', enum: ['low', 'medium', 'high'] },
        plan: { type: 'string' },
        allowedFiles: { type: 'array', items: { type: 'string' } },
        highRiskReason: { type: 'string' },
      },
  } })

// Bounded autonomy: high-risk halts and returns for human approval (constitution-compliant).
if (plan.riskLevel === 'high') {
  log(`HIGH RISK: ${plan.highRiskReason || '(no reason given)'} - halting for human approval per .ai/CONTEXT.md.`)
  return { halted: true, reason: 'high-risk requires human approval', plan }
}

// --- Implement <-> review fix loop (sequential, same checkout) ------------
// No per-call worktree isolation: rounds are sequential (no file conflict) and each fix round
// must build on the previous round's edits. Run this workflow from a codex/<task> branch.
phase('Implement')
let verdict = null
let impl = null
const MAX_ROUNDS = 3
for (let round = 1; round <= MAX_ROUNDS; round++) {
  impl = await agent(
    `You are the implementer. ${round === 1 ? 'Implement this plan.' : 'Fix the review findings below.'}\n` +
    `Plan: ${plan.plan}\nAllowed files: ${plan.allowedFiles.join(', ')}\n` +
    `${verdict ? 'Review findings to fix:\n' + JSON.stringify(verdict.findings, null, 2) : ''}`,
    { agentType: 'implementer', phase: 'Implement', label: `implement:r${round}` })

  verdict = await agent(
    `You are the independent reviewer (fresh context). Review the implementation against the plan and acceptance criteria. ` +
    `Run git diff --check and the touched-area tests. Report P0-P3 findings; any finding fails the gate.\n` +
    `Plan: ${plan.plan}\nImplementation summary: ${impl}`,
    { agentType: 'reviewer', phase: 'Review', label: `review:r${round}`, schema: {
        type: 'object',
        required: ['pass', 'findings'],
        properties: {
          pass: { type: 'boolean' },
          findings: { type: 'array', items: { type: 'object',
            required: ['severity', 'detail'],
            properties: {
              severity: { type: 'string', enum: ['P0', 'P1', 'P2', 'P3'] },
              detail: { type: 'string' },
            } } },
        },
    } })

  if (verdict.pass) {
    log(`Review PASS on round ${round}.`)
    return { halted: false, rounds: round, plan, verdict, implementation: impl }
  }
  log(`Round ${round}: reviewer found ${verdict.findings.length} issue(s) - looping.`)
}

log(`Exhausted ${MAX_ROUNDS} rounds without a clean review - returning for human attention.`)
return { halted: false, exhausted: true, rounds: MAX_ROUNDS, plan, verdict, implementation: impl }
