package com.ai_kids_care.v1.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardrail (computational Sensor): no two test classes may use the same full phone literal.
 *
 * <p>Why: integration tests share ONE Testcontainer Postgres (Spring context caching) and
 * {@code users.phone} is UNIQUE. Two classes inserting the same full phone collide with
 * DuplicateKeyException. This exact bug — NotificationRead reusing TeacherChild's
 * 010-0800-0001 — once broke 6 tests (docs/engineering/incidents.md, INC-001).
 *
 * <p>This is a SOUND static check (no false positives): it only flags the same full literal
 * appearing as a quoted string in two different classes. Sharing a prefix with distinct suffixes
 * (010-0000-9995 vs 010-0000-9996) is allowed, matching how the suite actually works. See
 * docs/engineering/test-conventions.md section 1.
 */
class TestFixturePhoneUniquenessTest {

    // Full Korean-style phone literal 010-NNNN-XXXX, quoted. Comments use 010-NNNN-* (wildcard),
    // which has no quoted alphanumeric suffix and therefore never matches.
    private static final Pattern FULL_PHONE = Pattern.compile("\"(010-\\d{4}-[0-9A-Za-z]+)\"");

    // The harness package holds this regex/Javadoc; it inserts no fixtures, so skip it.
    private static final String SELF_PACKAGE = "/v1/harness/";

    @Test
    @DisplayName("no full phone literal is shared by two test classes (users.phone UNIQUE, shared container)")
    void fullPhoneLiteralsAreUniquePerTestClass() {
        Map<String, TreeSet<String>> literalToClasses = new TreeMap<>();
        for (Path file : HarnessTestSupport.javaFiles()) {
            if (file.toString().replace('\\', '/').contains(SELF_PACKAGE)) {
                continue;
            }
            String owner = HarnessTestSupport.simpleClassName(file);
            Matcher matcher = FULL_PHONE.matcher(HarnessTestSupport.readString(file));
            while (matcher.find()) {
                literalToClasses.computeIfAbsent(matcher.group(1), k -> new TreeSet<>()).add(owner);
            }
        }

        Map<String, TreeSet<String>> shared = new TreeMap<>();
        literalToClasses.forEach((literal, owners) -> {
            if (owners.size() > 1) {
                shared.put(literal, owners);
            }
        });

        assertThat(shared)
                .as("full phone literals reused across test classes will collide on users.phone "
                        + "(UNIQUE, shared Testcontainer). Give each class distinct full numbers — "
                        + "see docs/engineering/test-conventions.md section 1. Offenders: %s", shared)
                .isEmpty();
    }
}
