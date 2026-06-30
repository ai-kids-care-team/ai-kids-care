package com.ai_kids_care;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Base for graph integration tests that exercise the <strong>real</strong> Cypher of
 * {@code GraphRepository} against a Neo4j Testcontainer.
 *
 * <p>Why a dedicated base (and not {@link BaseIntegrationTest}): the shared base deliberately
 * excludes Neo4j ({@code application-test.yml} suppresses {@code Neo4jAutoConfiguration} and the
 * manual {@code Driver} bean points at an unused {@code bolt://localhost:7687} that is never
 * connected because no non-graph test runs a Cypher query). Activating {@code GraphService} relaxes a
 * {@code denyAll()} security gate, so the cross-tenant-isolation assertions must run against a live
 * graph rather than a mocked driver.
 *
 * <p>Scoping: this class starts a Neo4j container and overrides {@code spring.neo4j.*} via
 * {@link DynamicPropertySource}. Because that changes the context's property set, Spring builds a
 * <em>separate</em> cached application context for graph tests; the container is started only when a
 * subclass of this base is loaded, so the existing PG/Redis-only tests keep their cached context and
 * never start Neo4j. The PG + Redis containers are still inherited from {@link BaseIntegrationTest}
 * (needed for login/tenant-context seeding).
 */
public abstract class GraphIntegrationTest extends BaseIntegrationTest {

    private static final String NEO4J_PASSWORD = "graph-test-pass-not-secret";

    protected static final Neo4jContainer<?> neo4j =
            new Neo4jContainer<>(DockerImageName.parse("neo4j:5.19"))
                    .withAdminPassword(NEO4J_PASSWORD)
                    .withReuse(true);

    static {
        neo4j.start();
    }

    @DynamicPropertySource
    static void overrideNeo4j(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> NEO4J_PASSWORD);
    }

    @Autowired
    protected Driver driver;

    /** Wipe the whole graph (derived, non-authoritative) so each test starts from a clean slate. */
    protected void clearGraph() {
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run("MATCH (n) DETACH DELETE n").consume());
        }
    }

    /** Run a single write Cypher with parameters against the test graph. */
    protected void writeCypher(String cypher, Map<String, Object> params) {
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run(cypher, params).consume());
        }
    }
}
