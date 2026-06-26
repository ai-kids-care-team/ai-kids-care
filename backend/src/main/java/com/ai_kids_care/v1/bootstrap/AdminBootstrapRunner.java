package com.ai_kids_care.v1.bootstrap;

import com.ai_kids_care.v1.entity.Superadmin;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.repository.SuperadminRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.repository.UserRoleAssignmentRepository;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 생산 콜드 스타트 안전 부트스트랩(harden-auth-bootstrap-and-demo D1).
 *
 * <p>생산 DB는 Flyway schema-only({@code Dockerfile.prod}, no initdb seed)이므로 계정이 하나도 없다.
 * 본 러너는 배포 secret으로 첫 SUPERADMIN을 만든다:
 * <ul>
 *   <li>{@code BOOTSTRAP_ADMIN_LOGIN_ID} + {@code BOOTSTRAP_ADMIN_PASSWORD} 둘 다 있고
 *       {@code users} 테이블이 비어 있을 때만(=count()==0) 정확히 1개의 ACTIVE SUPERADMIN을 생성한다.</li>
 *   <li>이미 사용자가 있으면 아무것도 하지 않는다(멱등).</li>
 *   <li>env 미설정이면 조용히 건너뛴다(기본/추측 가능 계정을 절대 만들지 않는다).</li>
 *   <li>login_id가 {@code admin}(대소문자 무시)이면 거부한다(데모 seed의 admin anchor와 충돌·추측 방지).</li>
 * </ul>
 *
 * <p>Spring Boot는 Flyway 마이그레이션을 {@link ApplicationRunner} 보다 먼저 끝내므로, 러너가 돌 때
 * schema는 준비돼 있다. 비밀번호는 절대 로그에 남기지 않는다(operator가 설정한 강한 비밀번호를 그대로 bcrypt).
 * 자격증명 회전은 본 change 범위 밖(생산 리셋 runbook으로 수동 처리, zero schema이므로 강제 변경 없음).
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    /** 데모 seed anchor와 동일한 login_id는 거부(robot이 admin/admin123로 스캔 못 들어오게). */
    private static final String FORBIDDEN_LOGIN_ID = "admin";

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final SuperadminRepository superadminRepository;
    private final PasswordEncoder passwordEncoder;

    private final String bootstrapLoginId;
    private final String bootstrapPassword;
    private final String bootstrapName;
    private final String bootstrapDepartment;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            SuperadminRepository superadminRepository,
            PasswordEncoder passwordEncoder,
            // env 미설정 시 빈 문자열(기본값 없음 정책 — 빈 값이면 러너 무동작).
            @Value("${bootstrap.admin.login-id:}") String bootstrapLoginId,
            @Value("${bootstrap.admin.password:}") String bootstrapPassword,
            @Value("${bootstrap.admin.name:Bootstrap Admin}") String bootstrapName,
            @Value("${bootstrap.admin.department:Platform Operations}") String bootstrapDepartment
    ) {
        this.userRepository = userRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.superadminRepository = superadminRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapLoginId = bootstrapLoginId;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapName = bootstrapName;
        this.bootstrapDepartment = bootstrapDepartment;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    /**
     * 부트스트랩 본체(테스트에서 직접 호출 가능). env/멱등/admin-거부 가드를 모두 적용하고,
     * 조건이 맞을 때만 단일 ACTIVE SUPERADMIN을 생성한다. 비밀번호는 로그에 남기지 않는다.
     */
    @Transactional
    public void bootstrap() {
        if (isBlank(bootstrapLoginId) || isBlank(bootstrapPassword)) {
            // env 미설정 — 기본/추측 가능 계정을 만들지 않는다(조용히 건너뜀).
            return;
        }
        if (FORBIDDEN_LOGIN_ID.equalsIgnoreCase(bootstrapLoginId.trim())) {
            // 'admin'은 데모 seed anchor 전용 — 부트스트랩 계정 login_id로 거부.
            log.warn("Bootstrap admin login id '{}' is not allowed; refusing to create it.",
                    FORBIDDEN_LOGIN_ID);
            return;
        }
        if (userRepository.count() != 0) {
            // 이미 사용자가 존재 — 멱등하게 아무것도 하지 않는다.
            return;
        }

        String loginId = bootstrapLoginId.trim();
        OffsetDateTime now = OffsetDateTime.now();

        User user = User.builder()
                .loginId(loginId)
                .email(null)
                .phone(null)
                .passwordHash(passwordEncoder.encode(bootstrapPassword))
                .status(StatusEnum.ACTIVE)
                .lastLoginAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        User savedUser = userRepository.save(user);

        UserRoleAssignment roleAssignment = UserRoleAssignment.builder()
                .user(savedUser)
                .role(UserRoleEnum.SUPERADMIN)
                .scopeType(UserRoleAssignmentScopeType.PLATFORM)
                .scopeId(null)
                .status(StatusEnum.ACTIVE)
                .grantedAt(now)
                .grantedByUser(null)
                .revokedAt(null)
                .revokedByUser(null)
                .build();
        userRoleAssignmentRepository.save(roleAssignment);

        Superadmin superadmin = Superadmin.builder()
                .user(savedUser)
                .name(bootstrapName)
                .department(bootstrapDepartment)
                .status(StatusEnum.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        superadminRepository.save(superadmin);

        // 비밀번호는 절대 로그에 남기지 않는다(login_id만 기록).
        log.info("Cold-start bootstrap created the initial SUPERADMIN account (loginId={}).", loginId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
