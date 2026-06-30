package com.ai_kids_care.v1.dataplatform;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * schema-digest guardrail, rebuilt as a data-platform capability test (was a removed
 * digest-script + CI-diff harness).
 *
 * Asserts structural invariants of the terminal schema (the path BaseIntegrationTest boots:
 * db/initdb creates the consolidated schema, Flyway baselines V1). After the
 * squash-flyway-to-single-baseline change the historical V2..V12 chain is folded into the single
 * V1__initial_baseline.sql, so both deploy paths (fresh-V1 and initdb+baseline) are generated from
 * db/dbml/schema.dbml and converge to the same terminal shape. The method names below reference the
 * historical migration (V3/V4../V12) that originally introduced each invariant, for traceability.
 * This catches a regression that would leave the live schema in the wrong final shape.
 *
 * (Entity↔schema alignment is separately enforced by ddl-auto=validate at context load.)
 */
class SchemaConsistencyGuardTest extends BaseIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private boolean tableExists(String table) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                Integer.class, table) == 1;
    }

    private boolean columnExists(String table, String column) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name=? AND column_name=?",
                Integer.class, table, column) == 1;
    }

    private boolean columnNullable(String table, String column) {
        return "YES".equals(jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name=? AND column_name=?",
                String.class, table, column));
    }

    private boolean typeExists(String type) {
        return jdbc.queryForObject("SELECT count(*) FROM pg_type WHERE typname=?",
                Integer.class, type) >= 1;
    }

    /**
     * True if an index of this name exists. Note: a UNIQUE CONSTRAINT (e.g. uq_*_rrn_hash added
     * via ALTER TABLE ... ADD CONSTRAINT in V5) is backed by an index of the same name and is
     * visible here too — so this matches both CREATE UNIQUE INDEX and constraint-backed uniques.
     */
    private boolean indexExists(String index) {
        return jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname=?",
                Integer.class, index) >= 1;
    }

    @Test
    void v7_pushSubscriptionsReplacedDeviceTokens() {
        assertThat(tableExists("push_subscriptions")).as("push_subscriptions exists (V7)").isTrue();
        assertThat(typeExists("push_provider_enum")).as("push_provider_enum exists (V7)").isTrue();
        assertThat(tableExists("device_tokens")).as("device_tokens dropped (V7)").isFalse();
        assertThat(typeExists("device_platform_enum")).as("device_platform_enum dropped (V7)").isFalse();
        assertThat(indexExists("uq_push_subscriptions_user_provider_address")).isTrue();
    }

    @Test
    void v3_notificationsPendingColumnsRelaxed() {
        assertThat(columnNullable("notifications", "sent_at")).as("sent_at nullable (V3)").isTrue();
        assertThat(columnNullable("notifications", "fail_reason")).as("fail_reason nullable (V3)").isTrue();
    }

    @Test
    void v4_v5_v6_rrnHashReplacedRrnEncrypted() {
        for (String t : new String[]{"children", "guardians", "teachers"}) {
            assertThat(columnExists(t, "rrn_hash")).as("%s.rrn_hash exists (V4)", t).isTrue();
            assertThat(columnNullable(t, "rrn_hash")).as("%s.rrn_hash NOT NULL (V5)", t).isFalse();
            assertThat(columnExists(t, "rrn_encrypted")).as("%s.rrn_encrypted dropped (V6)", t).isFalse();
            assertThat(indexExists("uq_" + t + "_rrn_hash")).as("uq_%s_rrn_hash unique (V5)", t).isTrue();
        }
    }

    @Test
    void v2_auditLogsScopeColumnsRelaxed() {
        assertThat(columnNullable("audit_logs", "kindergarten_id")).as("audit_logs.kindergarten_id nullable (V2)").isTrue();
        assertThat(columnNullable("audit_logs", "user_id")).as("audit_logs.user_id nullable (V2)").isTrue();
        assertThat(columnExists("audit_logs", "correlation_id")).as("audit_logs.correlation_id added (V2)").isTrue();
    }

    @Test
    void coreTenantInvariantsPresent() {
        assertThat(indexExists("uq_notifications_dedupe")).isTrue();
        assertThat(indexExists("uq_user_account_phone")).isTrue();
    }

    @Test
    void v8_detectionEventDedupKey() {
        assertThat(columnExists("detection_events", "dedup_key")).as("detection_events.dedup_key exists (V8)").isTrue();
        assertThat(columnNullable("detection_events", "dedup_key")).as("dedup_key NOT NULL (V8)").isFalse();
        assertThat(indexExists("uq_detection_events_dedup")).as("uq_detection_events_dedup unique (V8)").isTrue();
    }

    private boolean enumHasValue(String type, String value) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM pg_enum e JOIN pg_type t ON e.enumtypid = t.oid "
                        + "WHERE t.typname = ? AND e.enumlabel = ?",
                Integer.class, type, value) >= 1;
    }

    @Test
    void v10_dictionaryTablesRetired() {
        // ADR-0013: menu and common_codes are dropped (V10) / no longer seeded by db/initdb.
        assertThat(tableExists("menu")).as("menu dropped (V10, ADR-0013)").isFalse();
        assertThat(tableExists("common_codes")).as("common_codes dropped (V10, ADR-0013)").isFalse();
    }

    @Test
    void v9_quietHoursDeferralColumnsAndEnum() {
        assertThat(columnExists("notifications", "deferred_until")).as("notifications.deferred_until exists (V9)").isTrue();
        assertThat(columnNullable("notifications", "deferred_until")).as("deferred_until nullable (V9)").isTrue();
        assertThat(columnExists("kindergartens", "notification_quiet_hours_json"))
                .as("kindergartens.notification_quiet_hours_json exists (V9)").isTrue();
        assertThat(columnNullable("kindergartens", "notification_quiet_hours_json"))
                .as("notification_quiet_hours_json nullable (V9)").isTrue();
        assertThat(enumHasValue("notification_status_enum", "DEFERRED"))
                .as("notification_status_enum has DEFERRED (V9)").isTrue();
    }
}
