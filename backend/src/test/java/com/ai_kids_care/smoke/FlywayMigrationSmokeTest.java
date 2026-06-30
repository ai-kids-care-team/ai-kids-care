package com.ai_kids_care.smoke;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration smoke test for the real deploy/demo path (the one every integration test boots):
 * the database is initialised from db/initdb (schema + seed), Flyway then baselines V1, and JPA
 * ddl-auto=validate passes.
 *
 * As of the squash-flyway-to-single-baseline change, the historical V2..V12 chain is folded into a
 * single consolidated V1__initial_baseline.sql; there are no incremental migrations to apply on top
 * of the baseline (future changes resume at V2). So on this path Flyway records exactly one history
 * row: V1 as the baseline (type=BASELINE), and nothing else runs.
 *
 * Note: the "fresh empty DB, Flyway V1 builds the whole schema" path is covered separately
 * by {@code com.ai_kids_care.FlywayMigrationTest}. As of ADR-0013 the dictionary tables
 * (menu/common_codes) are retired and the CommonCode JPA mapping removed, so that fresh-Flyway
 * path with ddl-auto=validate now passes with neither table present.
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
        // baseline-on-migrate records V1 as the baseline (type=BASELINE, not a re-run SQL
        // migration) against the initdb-seeded schema.
        Integer baseline = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND type = 'BASELINE'",
                Integer.class);
        assertThat(baseline).as("V1 recorded as Flyway baseline").isEqualTo(1);

        // After the squash there is no V2..Vn chain: the baseline is the only history row, and no
        // SQL migration is applied on top of it on the initdb+baseline path.
        Integer sqlMigrations = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'SQL'",
                Integer.class);
        assertThat(sqlMigrations).as("no incremental SQL migrations applied on the baseline path").isZero();

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history",
                Integer.class);
        assertThat(total).as("only the single V1 baseline row exists").isEqualTo(1);
    }
}
