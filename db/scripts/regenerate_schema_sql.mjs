#!/usr/bin/env node
// Regenerate the PostgreSQL schema SQL from db/dbml/schema.dbml (DB-first source of truth)
// and emit BOTH db/initdb/01_create_schema.sql and the Flyway V1 consolidated baseline so the
// fresh-V1 (production) and initdb+baseline (demo/CI) paths converge to the same terminal schema.
//
// Background (OpenSpec change squash-flyway-to-single-baseline): V1..V12 were squashed into a
// single V1 baseline. The DBML is the single source for everything DBML can express. Two terminal
// invariants CANNOT be expressed in DBML and are applied here as a deterministic supplement:
//   1. Partial unique indexes  uq_ura_one_active_per_user / uq_ukm_one_active_per_user
//      (one ACTIVE row per user): DBML emits them as plain UNIQUE indexes; we add WHERE status='ACTIVE'.
//   2. CHECK constraints chk_ura_scope_type_id / chk_audit_scope_kindergarten (V2/ADR-0021).
//
// Usage:  node db/scripts/regenerate_schema_sql.mjs
// Requires: @dbml/cli on PATH (dbml2sql).  Run from repo root.

import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const DBML = 'db/dbml/schema.dbml';
const INITDB = 'db/initdb/01_create_schema.sql';
const V1 = 'backend/src/main/resources/db/migration/V1__initial_baseline.sql';

// --- 1. dbml2sql -> raw SQL ---
const tmp = join(mkdtempSync(join(tmpdir(), 'dbmlgen-')), 'out.sql');
execFileSync('dbml2sql', [DBML, '--postgres', '-o', tmp], { stdio: 'inherit', shell: process.platform === 'win32' });
let sql = readFileSync(tmp, 'utf8').replace(/\r\n/g, '\n').trimEnd();

// --- 2. Supplement #1: convert the two "one ACTIVE per user" indexes to partial ---
function makePartial(text, idxName, table) {
  const re = new RegExp(
    `CREATE UNIQUE INDEX "${idxName}" ON "${table}" \\("user_id"\\);`);
  if (!re.test(text)) throw new Error(`expected plain unique index ${idxName} not found in dbml2sql output`);
  return text.replace(re,
    `CREATE UNIQUE INDEX "${idxName}" ON "${table}" ("user_id") WHERE ("status" = 'ACTIVE');`);
}
sql = makePartial(sql, 'uq_ura_one_active_per_user', 'user_role_assignments');
sql = makePartial(sql, 'uq_ukm_one_active_per_user', 'user_kindergarten_memberships');

// --- 3. Supplement #2: CHECK constraints DBML cannot express (V2 / ADR-0021) ---
const checks = `
-- ============================================================
-- Supplement (not expressible in DBML): CHECK constraints (V2 / ADR-0021)
-- ============================================================
ALTER TABLE "user_role_assignments"
  ADD CONSTRAINT "chk_ura_scope_type_id"
  CHECK (("scope_type" = 'PLATFORM' AND "scope_id" IS NULL)
      OR ("scope_type" = 'KINDERGARTEN' AND "scope_id" IS NOT NULL));

ALTER TABLE "audit_logs"
  ADD CONSTRAINT "chk_audit_scope_kindergarten"
  CHECK (("scope_type" = 'PLATFORM' AND "kindergarten_id" IS NULL)
      OR ("scope_type" = 'KINDERGARTEN' AND "kindergarten_id" IS NOT NULL));
`;
sql = sql + '\n' + checks;

// --- 4. Write both artifacts with their own headers ---
const genNote =
`-- GENERATED FROM db/dbml/schema.dbml via db/scripts/regenerate_schema_sql.mjs — DO NOT EDIT BY HAND.
-- Edit the DBML, then re-run the script. (Two terminal invariants are appended as a supplement:
-- partial "one ACTIVE per user" unique indexes + CHECK constraints; see the script header.)`;

writeFileSync(INITDB,
`${genNote}
-- Role: demo/CI database initialization (db/initdb is mounted into the integration-test container).
${sql}
`);

writeFileSync(V1,
`-- V1__initial_baseline.sql — consolidated baseline (squash of historical V1..V12).
${genNote}
-- Role: Flyway production baseline. Future schema changes resume at V2 (append-only after V1).
${sql}
`);

console.log('Wrote', INITDB, 'and', V1);
