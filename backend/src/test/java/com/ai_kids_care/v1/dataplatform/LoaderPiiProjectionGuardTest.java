package com.ai_kids_care.v1.dataplatform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INC-003 guardrail (rebuilt as a data-platform capability test; was a removed harness check).
 *
 * The Neo4j data-loader scripts in {@code db/ne4j_kindergartens/} MUST NOT project any S0/PII
 * field into graph node properties. This test statically scans the loader Python source for
 * Cypher property binds of the form {@code <var>.<field> = $<param>} and fails if any forbidden
 * field is bound. It does not run the loaders or touch Neo4j — pure source-text scan, so it is
 * independent of the loader runtime architecture (CSV snapshot vs live PG).
 *
 * Forbidden fields per the data-platform spec (INC-003).
 */
class LoaderPiiProjectionGuardTest {

    private static final List<String> FORBIDDEN_FIELDS = List.of(
            "password_hash", "rrn_hash", "rrn_first6", "rrn_encrypted",
            "birth_date", "address", "email", "phone",
            "emergency_contact_name", "emergency_contact_phone",
            "contact_phone", "contact_email",
            "stream_password_encrypted", "stream_password_ciphertext");

    // Matches a Cypher property bind: someNode.<forbidden> = $param  (the loaders' write mechanism).
    // Skips comment-block RETURN/WHERE clauses, CSV-header docstrings, and SQL positional params ($1).
    private static final Pattern FORBIDDEN_BIND = Pattern.compile(
            "\\b[A-Za-z_]\\w*\\.(" + String.join("|", FORBIDDEN_FIELDS) + ")\\s*=\\s*\\$[A-Za-z_]");

    private static Path loaderDir() {
        return Paths.get(System.getProperty("user.dir"))
                .resolve("../db/ne4j_kindergartens")
                .normalize()
                .toAbsolutePath();
    }

    private static List<Path> loaderPythonFiles() {
        Path dir = loaderDir();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".py")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list loader dir: " + dir, e);
        }
    }

    @Test
    void loaderScriptsDoNotProjectS0OrPiiFields() {
        List<Path> loaders = loaderPythonFiles();

        // Guard against a vacuous pass (wrong path → zero files → always green).
        assertThat(loaders)
                .as("loader Python scripts must be found at %s", loaderDir())
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path file : loaders) {
            String source;
            try {
                source = Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
            Matcher m = FORBIDDEN_BIND.matcher(source);
            while (m.find()) {
                violations.add(file.getFileName() + ": " + m.group().trim());
            }
        }

        assertThat(violations)
                .as("Neo4j loader must not bind S0/PII fields into graph node properties (INC-003)")
                .isEmpty();
    }
}
