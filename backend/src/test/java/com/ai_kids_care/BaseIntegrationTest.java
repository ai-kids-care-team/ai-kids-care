package com.ai_kids_care;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;
import java.util.concurrent.ThreadPoolExecutor;

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
                    // Copy real initdb scripts in; PG runs them in filename order at container start.
                    // This simultaneously initialises schema + seed data AND proves that
                    // ddl-auto=validate passes against the live schema (no drift).
                    // withCopyFileToContainer (not withFileSystemBind) so this works both on CI and
                    // under Docker-out-of-Docker (running gradle in a container against the host
                    // daemon), where a host bind path resolved inside the gradle container would not
                    // exist on the host daemon.
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(INITDB_HOST_PATH),
                            "/docker-entrypoint-initdb.d")
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

    /** Shared Boot {@code @Async} executor — the one every {@code @Async} method runs on. */
    @Autowired
    @Qualifier("applicationTaskExecutor")
    private ThreadPoolTaskExecutor applicationTaskExecutor;

    /**
     * Block until the shared {@code @Async} executor has fully drained (no active and no queued
     * tasks). Use this in tests that ingest detection events and then assert on a side-effect path
     * (SSE replay frames, Hibernate {@code Statistics} counts, …): the ingest publishes
     * {@code @Async} listeners (SSE live push, staff-alert) that run on this shared executor on
     * other threads, so a task still in flight when the test proceeds can interleave an extra frame
     * or extra SQL into the assertion window. Waiting on the executor itself is exact (polling a
     * derived counter is not — a momentarily-idle queue can read quiescent prematurely). Bounded to
     * ~10s; if it ever exhausts the budget the test proceeds (and will fail loudly) rather than hang.
     */
    protected void awaitAsyncQuiescence() {
        ThreadPoolExecutor exec = applicationTaskExecutor.getThreadPoolExecutor();
        for (int i = 0; i < 200 && (exec.getActiveCount() > 0 || !exec.getQueue().isEmpty()); i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
