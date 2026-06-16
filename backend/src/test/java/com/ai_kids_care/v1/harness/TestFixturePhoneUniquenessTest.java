package com.ai_kids_care.v1.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardrail (computational Sensor): no two test classes may use the same full phone literal.
 *
 * <p>Why: integration tests share ONE Testcontainer Postgres (Spring context caching) and
 * {@code users.phone} is UNIQUE. Two classes inserting the same full phone collide with
 * DuplicateKeyException. This exact bug — NotificationRead reusing TeacherChild's 010-0800-0001 —
 * once broke 6 tests (docs/engineering/incidents.md, INC-001).
 *
 * <p>SOUND static check (no false positives): only the same full literal in two classes is flagged;
 * sharing a prefix with distinct suffixes (010-0000-9995 vs 010-0000-9996) is allowed. Detection
 * lives in {@link HarnessChecks#sharedPhoneLiterals} so it can be self-tested (HarnessGuardsSelfTest).
 * See docs/engineering/test-conventions.md section 1.
 */
class TestFixturePhoneUniquenessTest {

    // The harness package holds the detector regex and planted self-test literals; it inserts no
    // fixtures, so exclude it from the scan of real test sources.
    private static final String SELF_PACKAGE = "/v1/harness/";

    @Test
    @DisplayName("no full phone literal is shared by two test classes (users.phone UNIQUE, shared container)")
    void fullPhoneLiteralsAreUniquePerTestClass() {
        Map<String, String> sourceByClass = new TreeMap<>();
        for (Path file : HarnessTestSupport.javaFiles()) {
            if (file.toString().replace('\\', '/').contains(SELF_PACKAGE)) {
                continue;
            }
            sourceByClass.put(HarnessTestSupport.simpleClassName(file), HarnessTestSupport.readString(file));
        }

        Map<String, ? extends Set<String>> shared = HarnessChecks.sharedPhoneLiterals(sourceByClass);

        assertThat(shared)
                .as("full phone literals reused across test classes will collide on users.phone "
                        + "(UNIQUE, shared Testcontainer). Give each class distinct full numbers — "
                        + "see docs/engineering/test-conventions.md section 1. Offenders: %s", shared)
                .isEmpty();
    }
}
