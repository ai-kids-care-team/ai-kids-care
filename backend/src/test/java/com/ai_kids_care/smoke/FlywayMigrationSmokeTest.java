package com.ai_kids_care.smoke;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration smoke test for the real deploy/demo path (the one every integration test boots):
 * the database is initialised from db/initdb (schema + seed), Flyway then baselines at
 * {@code spring.flyway.baseline-version}, and JPA ddl-auto=validate passes.
 *
 * As of the squash-flyway-to-single-baseline change, the historical V2..V12 chain was folded into a
 * single consolidated V1__initial_baseline.sql. wire-notification-read-state then added the first
 * post-squash migration, V2__add_notification_read_at.sql — and because db/initdb/01_create_schema.sql
 * was updated to carry that same column (so the initdb/testcontainers path already reflects V2's
 * content), {@code baseline-version} moved from 1 to 2: the initdb-seeded schema is baselined AT V2,
 * not V1, so Flyway does not try to re-apply V2 on top of a schema that already has it. Any FUTURE
 * migration that also updates initdb the same way must bump baseline-version again in lockstep — the
 * two counters (initdb content vs. baseline-version) MUST always agree, or this test (and the real
 * app) fails with "column already exists" (see application.yml's flyway section for the full
 * rationale). On this path Flyway records exactly one history row: the baseline (type=BASELINE), and
 * nothing else runs.
 *
 * Note: the "fresh empty DB, Flyway runs every migration from V1" path is covered separately
 * by {@code com.ai_kids_care.FlywayMigrationTest}, which is unaffected by baseline-version (baseline
 * is only triggered against a non-empty schema with no flyway_schema_history table).
 */
class FlywayMigrationSmokeTest extends BaseIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void noFailedMigrations() {
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false",
                Integer.class);
        assertThat(failed).as("no Flyway migration should be recorded as failed").isZero();
    }

    @Test
    void baselineRecordedAndNoIncrementalMigrations() {
        // baseline-on-migrate records the configured baseline-version (currently 2, see class
        // javadoc) as the baseline (type=BASELINE, not a re-run SQL migration) against the
        // initdb-seeded schema — never a lower version, or Flyway would then try (and fail) to
        // re-apply migrations initdb already reflects.
        Integer baseline = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND type = 'BASELINE'",
                Integer.class);
        assertThat(baseline).as("V2 recorded as Flyway baseline").isEqualTo(1);

        // No SQL migration is applied on top of the baseline on the initdb+baseline path: initdb
        // already reflects the baseline-version's content, so nothing is left pending.
        Integer sqlMigrations = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'SQL'",
                Integer.class);
        assertThat(sqlMigrations).as("no incremental SQL migrations applied on the baseline path").isZero();

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history",
                Integer.class);
        assertThat(total).as("only the single baseline row exists").isEqualTo(1);
    }
}
