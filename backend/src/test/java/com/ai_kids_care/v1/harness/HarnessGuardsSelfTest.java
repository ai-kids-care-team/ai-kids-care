package com.ai_kids_care.v1.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behaviour-eval suite (deterministic): proves each harness guard actually FIRES on a planted
 * violation, and stays silent on a near-miss. A guard that can only ever pass is dead weight — these
 * tests fail if a detector stops detecting (e.g. a broken regex). This is the runnable core of the
 * "convert incidents to evals" discipline (docs/engineering/incidents.md). A full LLM-judge
 * agent-behaviour eval runner is a separate, deliberately-deferred effort (see ADR-0023).
 */
class HarnessGuardsSelfTest {

    @Test
    @DisplayName("phone guard FIRES when two classes share a full phone literal")
    void phoneGuardCatchesPlantedCollision() {
        Map<String, String> planted = Map.of(
                "AlphaTest", "phone = \"010-0800-0001\";",
                "BetaTest", "var p = \"010-0800-0001\";",
                "GammaTest", "var q = \"010-0905-0002\";"); // distinct -> must not be flagged
        Map<String, ? extends Set<String>> shared = HarnessChecks.sharedPhoneLiterals(planted);
        assertThat(shared).containsOnlyKeys("010-0800-0001");
        assertThat(shared.get("010-0800-0001")).containsExactlyInAnyOrder("AlphaTest", "BetaTest");
    }

    @Test
    @DisplayName("phone guard STAYS SILENT for a shared prefix with distinct suffixes")
    void phoneGuardAllowsSharedPrefixDistinctSuffix() {
        Map<String, String> planted = Map.of(
                "OneTest", "var a = \"010-0000-9995\";",
                "TwoTest", "var b = \"010-0000-9996\";");
        assertThat(HarnessChecks.sharedPhoneLiterals(planted)).isEmpty();
    }

    @Test
    @DisplayName("§365 loader guard FIRES on a planted projection but ignores comments / queries")
    void loaderGuardCatchesPlantedProjection() {
        Map<String, String> planted = Map.of(
                "bad_loader.py", "        SET u.email = $email,\n            u.name = $name\n",
                "good_loader.py", "        SET u.name = $name, u.status = $status\n",
                "doc_only.py", "# CSV: email, phone, rrn_first6\n\"\"\"\nRETURN u.email AS email\n\"\"\"\n");
        Map<String, ? extends Set<String>> hits = HarnessChecks.forbiddenLoaderProjections(planted);
        assertThat(hits).containsOnlyKeys("bad_loader.py");
        assertThat(hits.get("bad_loader.py")).containsExactly("email");
    }

    @Test
    @DisplayName("acceptance-coverage guard FIRES when a referenced test is absent")
    void coverageGuardCatchesMissingClass() {
        Map<String, List<String>> coverage = Map.of("dim", List.of("ExistingTest", "DeletedTest"));
        Set<String> present = Set.of("ExistingTest");
        assertThat(HarnessChecks.missingCoverageClasses(coverage, present)).containsExactly("DeletedTest");
    }
}
