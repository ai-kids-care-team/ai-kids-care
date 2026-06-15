package com.ai_kids_care.v1.schema;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.entity.AuditLog;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.entity.UserKindergartenMembership;
import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.repository.AuditLogRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.UserKindergartenMembershipRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.repository.UserRoleAssignmentRepository;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-0021 V2 schema 제약 검증 — initdb 경로(Testcontainers)를 통해 실행.
 *
 * 검증 항목:
 *  1. 같은 user에 두 번째 ACTIVE role assignment 삽입 → 부분 유일 인덱스(uq_ura_one_active_per_user) 위반 거부
 *  2. 같은 user에 두 번째 ACTIVE membership 삽입 → 부분 유일 인덱스(uq_ukm_one_active_per_user) 위반 거부
 *  3. audit_logs 에 scope_type=PLATFORM, kindergarten_id=NULL, user_id=NULL 삽입 → 성공
 *  4. audit_logs 에 scope_type=KINDERGARTEN, kindergarten_id=NULL 삽입 → CHECK 제약 위반 거부
 *
 * 모든 테스트는 시드 데이터 범위 밖의 user_id(9999xx)를 사용해 충돌을 방지한다.
 */
class V2SchemaConstraintIntegrationTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private KindergartenRepository kindergartenRepository;
    @Autowired private UserRoleAssignmentRepository roleAssignmentRepository;
    @Autowired private UserKindergartenMembershipRepository membershipRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    // ----------------------------------------------------------------
    // helper: seed 범위 밖 user 조회 (시드 데이터에 이미 존재하는 user 활용)
    // ----------------------------------------------------------------

    /** 이미 시드 데이터에 존재하는 user_id=1 (SUPERADMIN, ACTIVE role assignment 보유) */
    private User existingActiveUser() {
        return userRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("seed user_id=1 not found"));
    }

    /** 이미 시드 데이터에 존재하는 user_id=2 (SUPERADMIN, PENDING role assignment만 보유) */
    private User existingPendingUser() {
        return userRepository.findById(2L)
                .orElseThrow(() -> new IllegalStateException("seed user_id=2 not found"));
    }

    /** 이미 시드 데이터에 존재하는 kindergarten_id=1 */
    private Kindergarten existingKindergarten() {
        return kindergartenRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("seed kindergarten_id=1 not found"));
    }

    // ================================================================
    // Test 1: user_role_assignments 부분 유일 인덱스
    // ================================================================

    /**
     * user_id=1은 시드 데이터에서 이미 ACTIVE role assignment(row 1)를 갖는다.
     * 동일 user_id 에 두 번째 ACTIVE row를 삽입하면 uq_ura_one_active_per_user 위반.
     */
    @Test
    void secondActiveRoleAssignmentForSameUserIsRejected() {
        User user = existingActiveUser();

        UserRoleAssignment second = UserRoleAssignment.builder()
                .user(user)
                .role(UserRoleEnum.PLATFORM_IT_ADMIN)
                .scopeType(UserRoleAssignmentScopeType.PLATFORM)
                .scopeId(null)
                .status(StatusEnum.ACTIVE)
                .grantedAt(OffsetDateTime.now())
                .build();

        assertThatThrownBy(() -> roleAssignmentRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * PENDING 상태의 role assignment는 부분 인덱스 대상이 아니므로 삽입 가능.
     */
    @Test
    void pendingRoleAssignmentForUserWithActiveRoleIsAllowed() {
        User user = existingActiveUser();

        UserRoleAssignment pending = UserRoleAssignment.builder()
                .user(user)
                .role(UserRoleEnum.PLATFORM_IT_ADMIN)
                .scopeType(UserRoleAssignmentScopeType.PLATFORM)
                .scopeId(null)
                .status(StatusEnum.PENDING)
                .grantedAt(OffsetDateTime.now())
                .build();

        // 예외 없이 저장되어야 한다
        UserRoleAssignment saved = roleAssignmentRepository.saveAndFlush(pending);
        assertThat(saved.getId()).isNotNull();

        // 정리 (다른 테스트 영향 방지)
        roleAssignmentRepository.deleteById(saved.getId());
    }

    // ================================================================
    // Test 2: user_kindergarten_memberships 부분 유일 인덱스
    // ================================================================

    /**
     * user_id=101은 시드 데이터에서 kindergarten_id=1에 ACTIVE membership(row 1)를 갖는다.
     * 동일 user_id 에 두 번째 ACTIVE membership 삽입 시 uq_ukm_one_active_per_user 위반.
     */
    @Test
    void secondActiveMembershipForSameUserIsRejected() {
        User user = userRepository.findById(101L)
                .orElseThrow(() -> new IllegalStateException("seed user_id=101 not found"));
        Kindergarten kg = existingKindergarten();

        UserKindergartenMembership second = UserKindergartenMembership.builder()
                .user(user)
                .kindergarten(kg)
                .status(StatusEnum.ACTIVE)
                .joinedAt(OffsetDateTime.now())
                .build();

        assertThatThrownBy(() -> membershipRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ================================================================
    // Test 3: audit_logs PLATFORM scope 성공 케이스
    // ================================================================

    /**
     * scope_type=PLATFORM, kindergarten_id=NULL, user_id=NULL → CHECK 통과, 삽입 성공.
     */
    @Test
    void auditLogPlatformScopeWithNullKindergartenAndUserSucceeds() {
        AuditLog log = AuditLog.builder()
                .scopeType(UserRoleAssignmentScopeType.PLATFORM)
                .kindergarten(null)
                .user(null)
                .action("LOGIN_ATTEMPT")
                .ip("127.0.0.1")
                .effectiveRole("PLATFORM_IT_ADMIN")
                .result("DENIED")
                .correlationId("test-corr-001")
                .build();

        AuditLog saved = auditLogRepository.saveAndFlush(log);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getScopeType()).isEqualTo(UserRoleAssignmentScopeType.PLATFORM);
        assertThat(saved.getKindergarten()).isNull();
        assertThat(saved.getUser()).isNull();
        assertThat(saved.getEffectiveRole()).isEqualTo("PLATFORM_IT_ADMIN");
        assertThat(saved.getResult()).isEqualTo("DENIED");
        assertThat(saved.getCorrelationId()).isEqualTo("test-corr-001");

        // 정리
        auditLogRepository.deleteById(saved.getId());
    }

    // ================================================================
    // Test 4: audit_logs CHECK 위반 케이스
    // ================================================================

    /**
     * scope_type=KINDERGARTEN, kindergarten_id=NULL → chk_audit_scope_kindergarten 위반.
     */
    @Test
    void auditLogKindergartenScopeWithNullKindergartenIsRejected() {
        AuditLog log = AuditLog.builder()
                .scopeType(UserRoleAssignmentScopeType.KINDERGARTEN)
                .kindergarten(null)   // KINDERGARTEN scope인데 kindergarten 없음 → CHECK 위반
                .user(null)
                .action("TEST")
                .build();

        assertThatThrownBy(() -> auditLogRepository.saveAndFlush(log))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
