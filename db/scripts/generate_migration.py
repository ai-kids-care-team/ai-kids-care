#!/usr/bin/env python3
"""
db/scripts/generate_migration.py
─────────────────────────────────
Flyway migration draft generator (ADR-0012).

Diffs the *current* production schema (= all Flyway migration files applied in
order) against the *target* schema (= schema.dbml exported to SQL) and writes a
V{N}__<description>.sql draft ready for human review.

Usage
─────
  python3 db/scripts/generate_migration.py <description>
  python3 db/scripts/generate_migration.py add_rrn_hash_to_children

Or via Gradle (from backend/):
  ./gradlew generateMigration -Pdesc=add_rrn_hash_to_children

Prerequisites (one-time setup)
──────────────────────────────
  npm install -g @dbml/cli                             # DBML → SQL exporter
  pip install -r db/scripts/requirements-migra.txt    # migra diff tool
  Docker daemon running

Algorithm
─────────
  1. Export db/dbml/schema.dbml → temp SQL via dbml2sql
  2. Start an ephemeral postgres:16-alpine Docker container
  3. Create DB "schema_from": apply all V*.sql migrations in version order
  4. Create DB "schema_to":   apply the DBML-exported SQL
  5. Run `migra --unsafe schema_from schema_to` → SQL diff
  6. Write draft to backend/src/main/resources/db/migration/V{N}__<desc>.sql
  7. Remove the ephemeral container
"""

from __future__ import annotations

import argparse
import random
import re
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

# ── path constants ─────────────────────────────────────────────────────────────
SCRIPT_DIR    = Path(__file__).parent.resolve()
REPO_ROOT     = (SCRIPT_DIR / "../..").resolve()
DBML_FILE     = REPO_ROOT / "db/dbml/schema.dbml"
MIGRATION_DIR = REPO_ROOT / "backend/src/main/resources/db/migration"

# ── docker / pg settings ───────────────────────────────────────────────────────
PG_IMAGE = "postgres:16-alpine"
PG_USER  = "migra"
PG_PASS  = "migra_tmp"   # ephemeral throwaway container
DB_FROM  = "schema_from"
DB_TO    = "schema_to"

# ── terminal colours ───────────────────────────────────────────────────────────
_GREEN  = "\033[32m"
_YELLOW = "\033[33m"
_RED    = "\033[31m"
_RESET  = "\033[0m"

def log(msg: str)   -> None: print(f"{_GREEN}[migrate-gen]{_RESET} {msg}")
def warn(msg: str)  -> None: print(f"{_YELLOW}[migrate-gen] WARN:{_RESET} {msg}")
def err(msg: str)   -> None: print(f"{_RED}[migrate-gen] ERROR:{_RESET} {msg}", file=sys.stderr)


# ── helpers ────────────────────────────────────────────────────────────────────

def find_dbml2sql() -> list[str] | None:
    """Return the command list for dbml2sql, or None if unavailable."""
    if shutil.which("dbml2sql"):
        return ["dbml2sql", "--postgres"]
    if shutil.which("npx"):
        return ["npx", "--yes", "@dbml/cli@3", "dbml2sql", "--postgres"]
    return None


def check_prerequisites() -> list[str]:
    """Verify all required tools. Return the dbml2sql command list."""
    errors: list[str] = []

    if not shutil.which("docker"):
        errors.append("docker not found — install Docker: https://docs.docker.com/get-docker/")

    try:
        import migra  # noqa: F401
    except ImportError:
        errors.append(
            "migra not installed — run:\n"
            "  pip install -r db/scripts/requirements-migra.txt"
        )

    dbml2sql_cmd = find_dbml2sql()
    if dbml2sql_cmd is None:
        errors.append(
            "dbml2sql / npx not found — install @dbml/cli:\n"
            "  npm install -g @dbml/cli"
        )

    if errors:
        for e in errors:
            err(e)
        sys.exit(1)

    return dbml2sql_cmd  # type: ignore[return-value]


def next_version() -> int:
    """Return max(existing Flyway version numbers) + 1."""
    max_v = 1
    for f in MIGRATION_DIR.glob("V*.sql"):
        m = re.match(r"V(\d+)__", f.name)
        if m:
            v = int(m.group(1))
            max_v = max(max_v, v)
    return max_v + 1


def sorted_migrations() -> list[Path]:
    """Return Flyway V*.sql files sorted by version number (ascending)."""
    pairs: list[tuple[int, Path]] = []
    for f in MIGRATION_DIR.glob("V*.sql"):
        m = re.match(r"V(\d+)__", f.name)
        if m:
            pairs.append((int(m.group(1)), f))
    return [p for _, p in sorted(pairs)]


def wait_for_pg(container: str, timeout: int = 40) -> None:
    for _ in range(timeout):
        r = subprocess.run(
            ["docker", "exec", container, "pg_isready", "-U", PG_USER, "-q"],
            capture_output=True,
        )
        if r.returncode == 0:
            return
        time.sleep(1)
    raise RuntimeError("PostgreSQL container did not become ready in time.")


def get_host_port(container: str) -> str:
    """Return the host-side port mapped to container port 5432."""
    out = subprocess.check_output(
        ["docker", "port", container, "5432"]
    ).decode().strip()
    # "0.0.0.0:PORT" or "127.0.0.1:PORT" — take the last token after ':'
    return out.split(":")[-1]


def psql_cmd(container: str, db: str, sql: str) -> None:
    """Execute a SQL string inside the container."""
    subprocess.run(
        ["docker", "exec", container,
         "psql", "-U", PG_USER, "-d", db, "-q", "-c", sql],
        check=True, capture_output=True,
    )


def psql_file(container: str, db: str, sql_path: Path) -> None:
    """Apply a SQL file inside the container via stdin."""
    with open(sql_path, "rb") as fh:
        subprocess.run(
            ["docker", "exec", "-i", container,
             "psql", "-U", PG_USER, "-d", db, "-q"],
            stdin=fh, check=True, capture_output=True,
        )


# ── main ───────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "description",
        help="Short snake_case label for the migration (e.g. add_rrn_hash_to_children)",
    )
    args = parser.parse_args()

    # sanitise: only a-z 0-9 _
    desc = re.sub(r"[^a-z0-9_]", "_", args.description.lower()).strip("_")
    if not desc:
        err("Description must contain at least one alphanumeric character.")
        sys.exit(1)

    dbml2sql_cmd = check_prerequisites()

    next_v      = next_version()
    output_file = MIGRATION_DIR / f"V{next_v}__{desc}.sql"
    log(f"Output:  {output_file.relative_to(REPO_ROOT)}")
    log(f"Version: V{next_v}")

    schema_tmp = Path(tempfile.mktemp(suffix=".sql"))
    container  = f"migra-diff-{random.randint(10000, 99999)}"
    container_started = False

    try:
        # ── step 1: DBML → SQL ──────────────────────────────────────────────
        log("Step 1/5  Export DBML → SQL")
        subprocess.run(
            dbml2sql_cmd + [str(DBML_FILE), "-o", str(schema_tmp)],
            check=True,
        )
        log(f"          Wrote: {schema_tmp}")

        # ── step 2: start container ─────────────────────────────────────────
        log(f"Step 2/5  Start PostgreSQL container ({PG_IMAGE})")
        subprocess.run([
            "docker", "run", "-d",
            "--name", container,
            "-e", f"POSTGRES_USER={PG_USER}",
            "-e", f"POSTGRES_PASSWORD={PG_PASS}",
            "-e", "POSTGRES_DB=postgres",
            "-p", "127.0.0.1::5432",
            PG_IMAGE,
        ], check=True, capture_output=True)
        container_started = True

        log("          Waiting for ready...")
        wait_for_pg(container)

        host_port = get_host_port(container)
        base_url  = f"postgresql://{PG_USER}:{PG_PASS}@127.0.0.1:{host_port}"

        # ── step 3: 'from' = current Flyway migrations ──────────────────────
        log(f"Step 3/5  Build 'from' (all Flyway migrations → {DB_FROM})")
        psql_cmd(container, "postgres", f"CREATE DATABASE {DB_FROM};")
        migrations = sorted_migrations()
        if not migrations:
            warn("No Flyway migration files found in migration dir — 'from' will be empty.")
        for f in migrations:
            log(f"          Applying: {f.name}")
            psql_file(container, DB_FROM, f)

        # ── step 4: 'to' = DBML target schema ──────────────────────────────
        log(f"Step 4/5  Build 'to' (DBML schema → {DB_TO})")
        psql_cmd(container, "postgres", f"CREATE DATABASE {DB_TO};")
        psql_file(container, DB_TO, schema_tmp)

        # ── step 5: migra diff ──────────────────────────────────────────────
        log("Step 5/5  Running migra diff")
        from_url = f"{base_url}/{DB_FROM}"
        to_url   = f"{base_url}/{DB_TO}"

        result = subprocess.run(
            [sys.executable, "-m", "migra", "--unsafe", from_url, to_url],
            capture_output=True, text=True,
        )
        # migra exit codes: 0 = no diff, 2 = diff found, 1 = error
        if result.returncode == 1:
            err(f"migra reported an error:\n{result.stderr}")
            sys.exit(1)

        diff_sql = result.stdout.strip()

        if not diff_sql:
            log("")
            log("✅ No schema differences detected — no new migration needed.")
        else:
            ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            header = "\n".join([
                f"-- V{next_v}__{desc}.sql",
                f"-- Generated: {ts}",
                "-- Generator: db/scripts/generate_migration.py",
                "--",
                "-- ⚠️  HUMAN REVIEW REQUIRED before committing:",
                "--    • Remove DROP statements for objects you intend to keep.",
                "--    • Verify NOT NULL additions need no data backfill / DEFAULT.",
                "--    • Add DEFERRABLE INITIALLY IMMEDIATE to FK constraints if needed.",
                "--    • Confirm ENUM value additions are backward-compatible.",
                "--    • Run: ./gradlew test  (validates migration + ddl-auto=validate)",
                "",
            ])
            output_file.write_text(header + diff_sql + "\n", encoding="utf-8")

            log("")
            log(f"✅ Draft written:")
            log(f"   {output_file.relative_to(REPO_ROOT)}")
            log("")
            warn("── NEXT STEPS ──────────────────────────────────────────────────")
            warn("1. Review & edit the draft (keep only intentional changes).")
            warn("2. Sync JPA entity classes to match the schema change.")
            warn("3. ./gradlew test   (Testcontainers validates migration + validate)")
            warn("4. Commit the migration file with the feature PR.")
            warn("────────────────────────────────────────────────────────────────")

    finally:
        if container_started:
            log(f"Cleanup: removing container '{container}'")
            subprocess.run(["docker", "rm", "-f", container], capture_output=True)
        if schema_tmp.exists():
            schema_tmp.unlink()


if __name__ == "__main__":
    main()
