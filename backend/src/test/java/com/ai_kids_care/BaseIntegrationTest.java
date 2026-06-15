package com.ai_kids_care;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Paths;

/**
 * Base class for integration tests.
 *
 * Starts a single PostgreSQL container (shared across all subclass test runs via Spring
 * context caching). The container is initialised with the real db/initdb scripts so that
 * every test run also validates ddl-auto=validate against the live schema.
 *
 * Container reuse across Gradle invocations is opt-in: add
 *   testcontainers.reuse.enable=true
 * to ~/.testcontainers.properties to enable it (speeds up repeated local runs).
 *
 * Neo4j is excluded via application-test.yml; none of the test paths touch graph storage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Resolved relative to the Gradle project dir (backend/).  ../db/initdb = repo-root/db/initdb.
    private static final String INITDB_HOST_PATH =
            Paths.get(System.getProperty("user.dir"))
                 .resolve("../db/initdb")
                 .normalize()
                 .toAbsolutePath()
                 .toString();

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kids_postgres_db")
                    .withUsername("kids_user")
                    .withPassword("kids_pass")
                    // Mount real initdb scripts; PG runs them in filename order at container start.
                    // This simultaneously initialises schema + seed data AND proves that
                    // ddl-auto=validate passes against the live schema (no drift).
                    .withFileSystemBind(INITDB_HOST_PATH, "/docker-entrypoint-initdb.d", BindMode.READ_ONLY)
                    .withReuse(true);  // requires testcontainers.reuse.enable=true in ~/.testcontainers.properties

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        // Keep one container alive for the full Gradle test JVM. JUnit's per-class
        // @Container lifecycle would stop this inherited container between subclasses
        // while Spring still reuses the cached application context.
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
