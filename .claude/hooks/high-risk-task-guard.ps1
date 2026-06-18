# high-risk-task-guard.ps1
# Hook: TaskCreated (Agent Teams). Bounded-autonomy gate.
# Blocks creation of a high-risk task until a human approves, per .ai/CONTEXT.md Risk Gates.
# Interpreter constraint (see .claude/hooks/README.md): only git / powershell / cmd are reliably
# resolvable on the maintainer's box, so this guard is pure PowerShell and reads the hook JSON
# from stdin. It is schema-agnostic: it matches the high-risk surface anywhere in the payload,
# so it keeps working even if the TaskCreated payload field names change.
#
# Exit 0 = allow. Exit 2 = block (stderr is fed back to the team lead, which must then escalate
# to the maintainer before proceeding). Self-test:
#   '{"task":{"subject":"add flyway migration"}}' | powershell -File .claude/hooks/high-risk-task-guard.ps1 ; $LASTEXITCODE  # -> 2
#   '{"task":{"subject":"tidy a unit test"}}'      | powershell -File .claude/hooks/high-risk-task-guard.ps1 ; $LASTEXITCODE  # -> 0
$ErrorActionPreference = 'Stop'
$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }

# Constitution's high-risk surface: DB schema/migration, auth/authz, billing, public API,
# CI/deploy, destructive ops. Matched case-insensitively as whole words / stems.
$pattern = '(?i)(\bschema\b|\bmigrat\w*|\bflyway\b|\bauthz?\b|\bauthoriz\w*|\bauthentic\w*|\bbilling\b|\bpayment\b|permitAll|public[ _-]?api|\bdeploy\w*|\bbranch[ _-]?protection|drop\s+table|\btruncate\b|destructive)'
if ($raw -match $pattern) {
  $hit = $Matches[0]
  [Console]::Error.WriteLine("HIGH-RISK task blocked (matched '$hit'). Per .ai/CONTEXT.md Risk Gates, schema/migration/auth/billing/public-API/deploy/destructive work requires explicit human approval. Stop and escalate to the maintainer before creating this task.")
  exit 2
}
exit 0
