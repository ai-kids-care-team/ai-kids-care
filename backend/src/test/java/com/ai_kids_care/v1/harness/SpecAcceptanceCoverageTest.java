package com.ai_kids_care.v1.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Machine-anchored acceptance coverage map for SPEC-0001 §372 (negative authorization matrix) and
 * §390 (no S0 in response / error / audit). Grounded in SPEC-0001 slices 7-9, which name these
 * covering tests explicitly.
 *
 * <p>Gap this closes (docs/engineering/harness.md): the §372/§390 coverage matrix used to live only
 * as prose in the spec, judged by hand. Here it is a version-controlled, machine-readable map whose
 * referenced tests are asserted to EXIST — so deleting or renaming a covering test without updating
 * the map breaks the build instead of silently rotting the coverage claim.
 *
 * <p>Scope: this anchors EXISTENCE, not semantics. Whether the listed tests semantically cover each
 * dimension remains maintainer-reviewed (SPEC-0001: "§372/§390 验收勾选保留维护者评审").
 */
class SpecAcceptanceCoverageTest {

    /** dimension / leg -> covering test classes (simple names), per SPEC-0001 slices 7-9. */
    private static final Map<String, List<String>> COVERAGE = new LinkedHashMap<>();

    static {
        // §372 negative authorization matrix — five dimensions.
        COVERAGE.put("§372 anonymous / authenticated", List.of(
                "SecurityBoundaryIntegrationTest",
                "GuardianChildAuthorizationIntegrationTest",
                "NotificationReadAuthorizationIntegrationTest"));
        COVERAGE.put("§372 right / wrong role", List.of(
                "GuardianChildAuthorizationIntegrationTest",
                "TeacherChildAuthorizationIntegrationTest",
                "NotificationReadAuthorizationIntegrationTest",
                "AdminApprovalAuthorizationIntegrationTest",
                "PlatformAdminApprovalAuthorizationIntegrationTest"));
        COVERAGE.put("§372 same / cross tenant", List.of(
                "TenantIsolationIntegrationTest",
                "GuardianChildAuthorizationIntegrationTest",
                "NotificationReadAuthorizationIntegrationTest"));
        COVERAGE.put("§372 relationship exists / absent", List.of(
                "GuardianChildAuthorizationIntegrationTest",
                "TeacherChildAuthorizationIntegrationTest",
                "TeacherAssignmentAuthorizationIntegrationTest",
                "TeacherRoomAssignmentAuthorizationIntegrationTest"));
        COVERAGE.put("§372 sensitive-field absence", List.of(
                "SensitiveResponseContractTest",
                "PublishedOpenApiContractTest",
                "SensitivePublicApiClosureContractTest",
                "SensitiveWriteContractTest"));
        // §390 no S0 in response / error / audit — three legs.
        COVERAGE.put("§390 response leg", List.of(
                "SensitiveResponseContractTest",
                "PublishedOpenApiContractTest"));
        COVERAGE.put("§390 error leg", List.of(
                "ErrorResponseSensitiveDataIntegrationTest"));
        COVERAGE.put("§390 audit leg", List.of(
                "SecurityAuditIntegrationTest"));
    }

    @Test
    @DisplayName("every test named in the §372/§390 coverage map still exists (no rotted coverage claim)")
    void coverageMapReferencesOnlyExistingTests() {
        Set<String> present = new TreeSet<>();
        for (Path file : HarnessTestSupport.javaFiles()) {
            present.add(HarnessTestSupport.simpleClassName(file));
        }
        Set<String> missing = HarnessChecks.missingCoverageClasses(COVERAGE, present);
        assertThat(missing)
                .as("the SPEC-0001 §372/§390 coverage map references test classes that no longer "
                        + "exist — restore them or update the map (and the spec). Missing: %s", missing)
                .isEmpty();
    }

    @Test
    @DisplayName("every §372/§390 dimension lists at least one covering test")
    void everyDimensionHasCoverage() {
        Set<String> empty = new TreeSet<>();
        COVERAGE.forEach((dimension, tests) -> {
            if (tests.isEmpty()) {
                empty.add(dimension);
            }
        });
        assertThat(empty)
                .as("these §372/§390 dimensions have no covering test in the map: %s", empty)
                .isEmpty();
    }
}
