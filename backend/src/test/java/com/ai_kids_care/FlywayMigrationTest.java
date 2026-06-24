package com.ai_kids_care;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the production first-deploy scenario: Flyway V1 runs on a completely empty
 * database (no initdb scripts), creates the full schema, and ddl-auto=validate passes.
 *
 * This is a separate Spring context from BaseIntegrationTest (different datasource URL),
 * so it is slower but covers the critical production path.
 *
 * ADR-0013 (implemented): the dictionary tables menu/common_codes are retired. They never
 * existed in a Flyway migration (only in db/initdb), and the CommonCode JPA entity has been
 * removed, so a fresh Flyway-only database with ddl-auto=validate now starts cleanly with
 * NEITHER table present. This test asserts that table-free outcome.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class FlywayMigrationTest {

    // Fresh container: no initdb scripts. Flyway V1 migration must create the schema.
    @Container
    static final PostgreSQLContainer<?> freshPostgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kids_postgres_db")
                    .withUsername("kids_user")
                    .withPassword("kids_pass");

    // This test owns a separate Spring context (its own datasource URL), so it cannot share
    // BaseIntegrationTest's Redis container; the full application context needs a reachable Redis
    // at startup, so provide one here too.
    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", freshPostgres::getJdbcUrl);
        registry.add("spring.datasource.username", freshPostgres::getUsername);
        registry.add("spring.datasource.password", freshPostgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
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
        // The retired dictionary tables (menu, common_codes) are intentionally excluded — they
        // are never created on the Flyway-only path (ADR-0013).
        for (String table : new String[]{"users", "kindergartens", "detection_events", "notifications", "appreciation_letters"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table '%s' should exist after V1 migration", table).isEqualTo(1);
        }
    }

    @Test
    void retiredDictionaryTablesAreAbsentOnFreshFlyway() {
        // ADR-0013: menu and common_codes must NOT exist on a fresh Flyway-only database.
        // (V10 drops them defensively; on this path they were never created in the first place.)
        for (String table : new String[]{"menu", "common_codes"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("retired dictionary table '%s' must not exist (ADR-0013)", table).isZero();
        }
    }
}
