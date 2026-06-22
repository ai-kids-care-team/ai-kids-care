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
 * Asserts structural invariants of the fully-migrated schema (db/initdb baseline + Flyway
 * V2..Vn, the path BaseIntegrationTest boots). Both deploy paths (fresh-V1 and initdb+baseline)
 * converge here because the migrations are idempotent (IF NOT EXISTS / DROP NOT NULL). This
 * catches a migration regression that would leave the live schema in the wrong final shape.
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
}
