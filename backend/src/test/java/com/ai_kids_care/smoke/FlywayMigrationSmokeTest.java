package com.ai_kids_care.smoke;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration smoke test for the real deploy/demo path (the one every integration test boots):
 * the database is initialised from db/initdb (schema + seed), Flyway then baselines V1 and
 * applies V2–V6 on top, and JPA ddl-auto=validate passes.
 *
 * Note: the "fresh empty DB, Flyway V1 builds the whole schema" path is covered separately
 * by {@code com.ai_kids_care.FlywayMigrationTest}, which stays @Disabled until ADR-0013
 * removes the legacy CommonCode JPA mapping (common_codes lives in db/initdb, not in a Flyway
 * migration, so fresh-Flyway + ddl-auto=validate currently fails).
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
    void baselineAndIncrementalMigrationsApplied() {
        // baseline-on-migrate records V1 as the baseline (type=BASELINE, not a re-run SQL
        // migration) against the initdb-seeded schema.
        Integer baseline = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND type = 'BASELINE'",
                Integer.class);
        assertThat(baseline).as("V1 recorded as Flyway baseline").isEqualTo(1);

        // V2–V6 must each be applied successfully as SQL migrations on top of the baseline.
        for (String version : new String[]{"2", "3", "4", "5", "6"}) {
            Integer applied = jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history "
                            + "WHERE version = ? AND success = true AND type = 'SQL'",
                    Integer.class, version);
            assertThat(applied).as("migration V%s applied successfully", version).isEqualTo(1);
        }
    }
}
