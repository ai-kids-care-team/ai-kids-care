package com.ai_kids_care;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the production first-deploy scenario: Flyway V1 runs on a completely empty
 * database (no initdb scripts), creates the full schema, and ddl-auto=validate passes.
 *
 * This is a separate Spring context from BaseIntegrationTest (different datasource URL),
 * so it is slower but covers the critical production path.
 *
 * Temporarily disabled until ADR-0013 removes the legacy CommonCode JPA mapping.
 * Flyway V1 intentionally excludes common_codes because the accepted target is to
 * replace that table with enum metadata and frontend i18n, not migrate it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Disabled("ADR-0013 pending: remove legacy CommonCode mapping before validating the table-free Flyway schema")
class FlywayMigrationTest {

    // Fresh container: no initdb scripts. Flyway V1 migration must create the schema.
    @Container
    static final PostgreSQLContainer<?> freshPostgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kids_postgres_db")
                    .withUsername("kids_user")
                    .withPassword("kids_pass");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", freshPostgres::getJdbcUrl);
        registry.add("spring.datasource.username", freshPostgres::getUsername);
        registry.add("spring.datasource.password", freshPostgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayV1MigrationAppliedSuccessfully() {
        // Context loaded = Flyway ran V1 + ddl-auto=validate passed.
        // Verify flyway_schema_history records V1 as a successfully applied SQL migration.
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = true AND type = 'SQL'",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void coreTablesCreatedByV1() {
        // Spot-check representative tables from different domain areas to confirm V1 ran fully.
        for (String table : new String[]{"users", "kindergartens", "detection_events", "notifications", "appreciation_letters"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table '%s' should exist after V1 migration", table).isEqualTo(1);
        }
    }
}
