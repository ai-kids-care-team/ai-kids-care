package com.ai_kids_care.v1.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared support for the "harness guardrail" tests — deterministic checks that statically scan the
 * test sources / repo files to encode recurring-incident rules (see docs/engineering/incidents.md
 * and docs/engineering/harness.md). These are plain unit tests: no Spring context, no database.
 */
final class HarnessTestSupport {

    private HarnessTestSupport() {}

    /** Locate {@code src/test/java} whether tests run from the module dir or the repo root. */
    static Path testSourceRoot() {
        for (Path candidate : List.of(Paths.get("src", "test", "java"),
                                      Paths.get("backend", "src", "test", "java"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not locate src/test/java from working dir "
                        + Paths.get("").toAbsolutePath()
                        + " — harness guardrail tests cannot scan the test sources.");
    }

    /** All {@code *.java} files under the test source root. */
    static List<Path> javaFiles() {
        try (Stream<Path> walk = Files.walk(testSourceRoot())) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Locate the Neo4j loader dir whether tests run from the module dir (backend/) or the repo root. */
    static Path neo4jLoaderDir() {
        for (Path candidate : List.of(Paths.get("db", "ne4j_kindergartens"),
                                      Paths.get("..", "db", "ne4j_kindergartens"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not locate db/ne4j_kindergartens from working dir "
                        + Paths.get("").toAbsolutePath()
                        + " — the §365 loader guard cannot scan the loaders.");
    }

    /** Top-level Neo4j loader {@code *.py} files (non-recursive; excludes __pycache__). */
    static List<Path> loaderPythonFiles() {
        try (Stream<Path> list = Files.list(neo4jLoaderDir())) {
            return list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".py"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String simpleClassName(Path javaFile) {
        String name = javaFile.getFileName().toString();
        return name.endsWith(".java") ? name.substring(0, name.length() - ".java".length()) : name;
    }
}
