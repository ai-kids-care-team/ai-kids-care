package com.ai_kids_care.v1.harness;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, unit-testable detectors behind the harness guardrail tests. Extracted so each guard can be
 * proven to FIRE on a planted violation (see {@code HarnessGuardsSelfTest}) — not merely pass on
 * clean code, since a guard that cannot fail is worthless. See docs/engineering/incidents.md and
 * docs/engineering/harness.md.
 */
final class HarnessChecks {

    private HarnessChecks() {}

    // ---- INC-001: shared-container users.phone UNIQUE collision -------------------------------

    private static final Pattern FULL_PHONE = Pattern.compile("\"(010-\\d{4}-[0-9A-Za-z]+)\"");

    /** Full phone literals used by more than one test class (each entry is a real collision risk). */
    static Map<String, TreeSet<String>> sharedPhoneLiterals(Map<String, String> sourceByClass) {
        Map<String, TreeSet<String>> byLiteral = new TreeMap<>();
        sourceByClass.forEach((owner, text) -> {
            Matcher matcher = FULL_PHONE.matcher(text);
            while (matcher.find()) {
                byLiteral.computeIfAbsent(matcher.group(1), k -> new TreeSet<>()).add(owner);
            }
        });
        Map<String, TreeSet<String>> shared = new TreeMap<>();
        byLiteral.forEach((literal, owners) -> {
            if (owners.size() > 1) {
                shared.put(literal, owners);
            }
        });
        return shared;
    }

    // ---- INC-003: SPEC-0001 §365 — no S0/PII projected into Neo4j -----------------------------

    /** Forbidden Neo4j node properties (authoritative: db/ne4j_kindergartens/no000_scrub_sensitive.py). */
    static final Set<String> FORBIDDEN_LOADER_FIELDS = Set.of(
            "password_hash", "email", "phone",
            "address", "contact_phone", "contact_email",
            "rrn_encrypted", "rrn_first6",
            "emergency_contact_phone", "emergency_contact_name",
            "birth_date");

    // A forbidden field PROJECTED into Neo4j is a Cypher property bind `<var>.<field> = $<name>` —
    // the loaders' actual projection mechanism. This does NOT match comments, CSV-format docstrings,
    // or the historical RETURN/WHERE example queries (which never bind `= $<name>`), nor SQL
    // positional params (`= $1`, digit-led). Verified: 0 matches on the scrubbed loaders.
    private static final Pattern PROJECTION = Pattern.compile(
            "\\b[A-Za-z_]\\w*\\.(" + String.join("|", new TreeSet<>(FORBIDDEN_LOADER_FIELDS))
                    + ")\\s*=\\s*\\$[A-Za-z_]");

    /** Loader file name -> the forbidden fields it PROJECTS into Neo4j (empty map = clean). */
    static Map<String, TreeSet<String>> forbiddenLoaderProjections(Map<String, String> loaderByName) {
        Map<String, TreeSet<String>> hits = new TreeMap<>();
        loaderByName.forEach((name, text) -> {
            Matcher matcher = PROJECTION.matcher(text);
            TreeSet<String> fields = new TreeSet<>();
            while (matcher.find()) {
                fields.add(matcher.group(1));
            }
            if (!fields.isEmpty()) {
                hits.put(name, fields);
            }
        });
        return hits;
    }

    // ---- Gap 4: §372/§390 acceptance coverage map references only existing tests --------------

    /** Coverage-map class names that are not present among the given test classes. */
    static TreeSet<String> missingCoverageClasses(Map<String, List<String>> coverage, Set<String> present) {
        TreeSet<String> missing = new TreeSet<>();
        coverage.values().forEach(list -> list.forEach(name -> {
            if (!present.contains(name)) {
                missing.add(name);
            }
        }));
        return missing;
    }
}
