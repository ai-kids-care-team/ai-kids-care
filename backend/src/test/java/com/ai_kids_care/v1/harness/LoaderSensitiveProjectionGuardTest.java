package com.ai_kids_care.v1.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardrail (computational Sensor) for SPEC-0001 §365: the Neo4j loaders must not project any
 * S0/PII field (password_hash, email, phone, address, rrn*, birth_date, emergency_contact_*) as a
 * graph node property. Turns INC-003 from an inferential convention into a build failure.
 *
 * <p>Sound by construction: it matches only a Cypher property bind {@code <var>.<field> = $<name>}
 * (the loaders' actual projection mechanism), so the many forbidden-field mentions in comments,
 * CSV-format docs, and the historical RETURN/WHERE example queries do NOT trip it. Detection lives
 * in {@link HarnessChecks#forbiddenLoaderProjections} and is self-tested (HarnessGuardsSelfTest).
 * See docs/engineering/incidents.md (INC-003).
 */
class LoaderSensitiveProjectionGuardTest {

    @Test
    @DisplayName("Neo4j loaders project no §365-forbidden S0/PII field as a node property")
    void loadersProjectNoForbiddenField() {
        Map<String, String> loaders = new TreeMap<>();
        for (Path file : HarnessTestSupport.loaderPythonFiles()) {
            loaders.put(file.getFileName().toString(), HarnessTestSupport.readString(file));
        }
        // A guard that scans nothing is worse than no guard.
        assertThat(loaders).as("found no Neo4j loader *.py to scan").isNotEmpty();

        Map<String, ? extends Set<String>> violations = HarnessChecks.forbiddenLoaderProjections(loaders);

        assertThat(violations)
                .as("SPEC-0001 §365: these Neo4j loaders project forbidden S0/PII fields — remove "
                        + "them from the Cypher SET / property bind (see no000_scrub_sensitive.py). "
                        + "Offenders: %s", violations)
                .isEmpty();
    }
}
