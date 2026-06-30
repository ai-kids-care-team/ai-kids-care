package com.ai_kids_care.v1.graph;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INC-003 read-path guard (design OQ7): {@code GraphRepository} maps exclusively from Neo4j driver
 * values and MUST NOT join back to PostgreSQL to enrich a response, so no PII column can re-enter via
 * the graph read path. This is a pure source-text scan (no container) asserting the repository
 * depends on no JPA/JDBC/PostgreSQL access surface — neither another JPA repository, nor an
 * {@code EntityManager}, {@code JdbcTemplate}, {@code DataSource}, nor any {@code spring.data.jpa}
 * type. {@code jakarta.persistence.EntityNotFoundException} is allowed: it is the 404-signalling
 * exception type, not a PG access path.
 */
class GraphRepositoryNoPgJoinGuardTest {

    private static final List<String> FORBIDDEN_IMPORT_SUBSTRINGS = List.of(
            "jakarta.persistence.EntityManager",
            "javax.persistence.EntityManager",
            "org.springframework.data.jpa",
            "org.springframework.data.repository",
            "org.springframework.jdbc",
            "javax.sql.DataSource",
            "com.ai_kids_care.v1.repository.",   // any OTHER repository in this package
            "ChildRepository", "TeacherRepository", "GuardianRepository",
            "ClassRepository", "KindergartenRepository");

    private static Path graphRepositorySource() {
        return Paths.get(System.getProperty("user.dir"))
                .resolve("src/main/java/com/ai_kids_care/v1/repository/GraphRepository.java")
                .normalize()
                .toAbsolutePath();
    }

    @Test
    void graphRepositoryDoesNotDependOnPostgresAccess() {
        Path source = graphRepositorySource();
        assertThat(source).as("GraphRepository.java must exist at %s", source).exists();

        String code;
        try {
            code = Files.readString(source);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + source, e);
        }

        // Only inspect import statements so an unrelated word in a comment/string is not flagged.
        List<String> imports = code.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("import "))
                .toList();

        for (String forbidden : FORBIDDEN_IMPORT_SUBSTRINGS) {
            assertThat(imports)
                    .as("GraphRepository must not import a PostgreSQL access type (%s) — INC-003 read-path guard", forbidden)
                    .noneMatch(imp -> imp.contains(forbidden));
        }

        // Positive assertion: it does drive the Neo4j graph (guards against a vacuous pass).
        assertThat(imports).anyMatch(imp -> imp.contains("org.neo4j.driver.Driver"));
    }
}
