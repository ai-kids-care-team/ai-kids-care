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
 * Cypher writes that BIND a forbidden field to a parameter, in either form the loaders could use:
 *   - property assign:  {@code SET node.<field> = $param}
 *   - map-entry bind:   {@code SET node += { <field>: $param }}  (or a map literal)
 * Python {@code #} line comments are stripped first to avoid flagging commented-out examples.
 * It does not run the loaders or touch Neo4j — pure source-text scan, independent of the loader
 * runtime architecture (CSV snapshot vs live PG).
 *
 * Detection boundary (kept honest, INC-003 is a security guard): it flags a forbidden field bound
 * to a {@code $param}. It does NOT detect value-side f-string interpolation
 * ({@code f"SET n.email = '{row['email']}'"}) — the loaders use parameter binding exclusively, and
 * an embed of that kind would still surface the forbidden field name as a write target on review.
 * {@code REMOVE}/{@code MATCH}/{@code RETURN}/{@code WHERE} uses are intentionally not flagged
 * (e.g. no000_scrub_sensitive.py REMOVEs PII — that is the remediation, not a projection).
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

    private static final String FORBIDDEN_ALT = String.join("|", FORBIDDEN_FIELDS);

    // Cypher property assign: someNode.<forbidden> = $param  (excludes SQL positional $1).
    private static final Pattern PROP_ASSIGN = Pattern.compile(
            "\\b[A-Za-z_]\\w*\\.(" + FORBIDDEN_ALT + ")\\s*=\\s*\\$[A-Za-z_]");

    // Cypher map-entry bind: { ... <forbidden>: $param ... }  (covers SET n += {email: $email}).
    private static final Pattern MAP_ENTRY_BIND = Pattern.compile(
            "(?<![A-Za-z_])(" + FORBIDDEN_ALT + ")\\s*:\\s*\\$[A-Za-z_]");

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

    /** Drop Python {@code #} line comments so commented-out examples aren't flagged. */
    private static String stripHashComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            int hash = line.indexOf('#');
            out.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
        }
        return out.toString();
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
            String code = stripHashComments(source);
            for (Pattern pattern : List.of(PROP_ASSIGN, MAP_ENTRY_BIND)) {
                Matcher m = pattern.matcher(code);
                while (m.find()) {
                    violations.add(file.getFileName() + ": " + m.group().trim());
                }
            }
        }

        assertThat(violations)
                .as("Neo4j loader must not bind S0/PII fields into graph node properties (INC-003)")
                .isEmpty();
    }
}
