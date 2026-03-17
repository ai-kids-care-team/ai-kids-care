package com.ai_kids_care.dashboard.service;

import com.ai_kids_care.dashboard.dto.LoginRequest;
import com.ai_kids_care.dashboard.dto.LoginResponse;
import com.ai_kids_care.dashboard.dto.KindergartenLookupResponse;
import com.ai_kids_care.dashboard.dto.ForgotPasswordRequest;
import com.ai_kids_care.dashboard.dto.SignupRequest;
import com.ai_kids_care.dashboard.dto.SignupResponse;
import com.ai_kids_care.dashboard.entity.User;
import com.ai_kids_care.dashboard.repository.UserRepository;
import com.ai_kids_care.dashboard.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final GuardianBindingService guardianBindingService;
    private final CommonCodeService commonCodeService;
    private final JdbcTemplate jdbcTemplate;

    public void forgotPassword(ForgotPasswordRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("이메일을 입력해주세요.");
        }

        String email = request.getEmail().trim();
        boolean exists = userRepository.existsByEmail(email);
        if (!exists) {
            throw new RuntimeException("등록되지 않은 이메일입니다.");
        }

        // TODO: 메일/인증코드 발송 로직 연동
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new RuntimeException("Invalid loginId or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid loginId or password");
        }

        String role = userRepository.findLatestRoleByUserId(user.getUserId())
                .orElseGet(() -> "admin".equalsIgnoreCase(user.getLoginId()) ? "ADMIN" : "USER");
        String token = jwtUtil.generateToken(user.getLoginId(), role);
        return new LoginResponse(token, user.getLoginId(), role);
    }

    public List<KindergartenLookupResponse> searchKindergartens(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isBlank()) {
            throw new RuntimeException("유치원명 또는 사업자번호를 입력해주세요.");
        }

        String likeKeyword = "%" + normalized + "%";
        return jdbcTemplate.query(
                """
                SELECT kindergarten_id, name, code, address, region_code, business_registration_no,
                       contact_name, contact_phone, contact_email, status
                FROM kindergartens
                WHERE name ILIKE ?
                   OR business_registration_no ILIKE ?
                ORDER BY kindergarten_id DESC
                LIMIT 100
                """,
                (rs, rowNum) -> new KindergartenLookupResponse(
                        rs.getLong("kindergarten_id"),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getString("address"),
                        rs.getString("region_code"),
                        rs.getString("business_registration_no"),
                        rs.getString("contact_name"),
                        rs.getString("contact_phone"),
                        rs.getString("contact_email"),
                        rs.getString("status")
                ),
                likeKeyword,
                likeKeyword
        );
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (request.getMemberType() == null || request.getMemberType().isBlank()) {
            throw new RuntimeException("회원유형을 선택해주세요.");
        }
        String memberType = request.getMemberType().trim().toUpperCase();

        if (request.getLoginId() == null || request.getLoginId().isBlank()) {
            throw new RuntimeException("로그인 ID를 입력해주세요.");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("비밀번호를 입력해주세요.");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("이름을 입력해주세요.");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("이메일을 입력해주세요.");
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new RuntimeException("전화번호를 입력해주세요.");
        }

        String loginId = request.getLoginId().trim();
        if (userRepository.existsByLoginId(loginId)) {
            throw new RuntimeException("이미 사용 중인 로그인 ID입니다.");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        String userEmail = request.getEmail().trim();
        String userTel = request.getPhone().trim();
        User user = new User(loginId, passwordHash, userEmail, userTel);
        user.recordLogin(Instant.now());

        User saved = userRepository.saveAndFlush(user);

        if ("GUARDIAN".equals(memberType)) {
            if (request.getChildId() == null) {
                throw new RuntimeException("아이 찾기에서 아이를 선택해주세요.");
            }
            if (request.getRrnFirst6() == null || !request.getRrnFirst6().matches("\\d{6}")) {
                throw new RuntimeException("주민등록번호 앞자리는 숫자 6자리여야 합니다.");
            }
            if (request.getRrnBack7() == null || !request.getRrnBack7().matches("\\d{7}")) {
                throw new RuntimeException("주민등록번호 뒷자리는 숫자 7자리여야 합니다.");
            }
            if (request.getRelationship() == null || request.getRelationship().isBlank()) {
                throw new RuntimeException("관계를 선택해주세요.");
            }
            if ("OTHER".equalsIgnoreCase(request.getRelationship())
                    && (request.getCustomRelationship() == null || request.getCustomRelationship().isBlank())) {
                throw new RuntimeException("기타 관계를 입력해주세요.");
            }

            String relationshipCode = request.getRelationship().trim().toUpperCase();
            boolean isValidRelationshipCode = commonCodeService.existsActiveCode("GUARDIAN_RELATIONSHIP", relationshipCode);
            if (!isValidRelationshipCode) {
                throw new RuntimeException("유효하지 않은 관계 코드입니다.");
            }

            guardianBindingService.bindGuardianToChild(saved, request);
            return new SignupResponse(saved.getUserId(), saved.getLoginId(), saved.getStatus());
        }

        if ("KINDERGARTEN".equals(memberType)) {
            if (request.getKindergartenId() == null) {
                throw new RuntimeException("유치원 찾기에서 유치원을 선택해주세요.");
            }
            if (request.getStaffNo() == null || request.getStaffNo().isBlank()) {
                throw new RuntimeException("직원(사번)을 입력해주세요.");
            }
            if (request.getRrnFirst6() == null || !request.getRrnFirst6().matches("\\d{6}")) {
                throw new RuntimeException("주민등록번호 앞자리는 숫자 6자리여야 합니다.");
            }
            if (request.getRrnBack7() == null || !request.getRrnBack7().matches("\\d{7}")) {
                throw new RuntimeException("주민등록번호 뒷자리는 숫자 7자리여야 합니다.");
            }
            if (request.getEmergencyContactName() == null || request.getEmergencyContactName().isBlank()) {
                throw new RuntimeException("비상 연락처 이름을 입력해주세요.");
            }
            if (request.getEmergencyContactPhone() == null || request.getEmergencyContactPhone().isBlank()) {
                throw new RuntimeException("비상 연락처 전화번호를 입력해주세요.");
            }
            if (request.getLevel() == null || request.getLevel().isBlank()) {
                throw new RuntimeException("직급을 선택해주세요.");
            }
            if (request.getStartDate() == null || request.getStartDate().isBlank()) {
                throw new RuntimeException("근무시작일을 입력해주세요.");
            }

            Long kindergartenId = request.getKindergartenId();
            Integer kindergartenCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM kindergartens WHERE kindergarten_id = ?",
                    Integer.class,
                    kindergartenId
            );
            if (kindergartenCount == null || kindergartenCount == 0) {
                throw new RuntimeException("선택한 유치원 정보가 유효하지 않습니다.");
            }

            String levelCode = request.getLevel().trim().toUpperCase();
            boolean isValidLevel = commonCodeService.existsActiveCode("teachers", levelCode);
            if (!isValidLevel) {
                throw new RuntimeException("유효하지 않은 직급 코드입니다.");
            }

            LocalDate startDate = parseDateOrThrow(request.getStartDate(), "근무시작일 형식이 올바르지 않습니다.");
            LocalDate endDate = null;
            if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
                endDate = parseDateOrThrow(request.getEndDate(), "근무종료일 형식이 올바르지 않습니다.");
                if (endDate.isBefore(startDate)) {
                    throw new RuntimeException("근무종료일은 근무시작일보다 빠를 수 없습니다.");
                }
            }

            String rrnEncrypted = passwordEncoder.encode(request.getRrnBack7().trim());
            String resolvedGender = resolveGenderFromRrnBack7(request.getRrnBack7().trim());

            jdbcTemplate.update(
                    """
                    INSERT INTO teachers
                        (kindergarten_id, user_id, staff_no, name, gender,
                         emergency_contact_name, emergency_contact_phone, rrn_encrypted, rrn_first6, level,
                         start_date, end_date, status, created_at, updated_at)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS status_enum), now(), now())
                    """,
                    kindergartenId,
                    saved.getUserId(),
                    request.getStaffNo().trim(),
                    request.getName().trim(),
                    resolvedGender,
                    request.getEmergencyContactName().trim(),
                    request.getEmergencyContactPhone().trim(),
                    rrnEncrypted,
                    request.getRrnFirst6().trim(),
                    levelCode,
                    startDate,
                    endDate,
                    "ACTIVE"
            );

            String roleCode = "PRINCIPAL".equals(levelCode) ? "KINDERGARTEN_ADMIN" : "TEACHER";
            jdbcTemplate.update(
                    """
                    INSERT INTO user_role_assignments
                        (user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at)
                    VALUES
                        (?, CAST(? AS user_role_enum), CAST(? AS user_role_assignment_scope_type), ?,
                         CAST(? AS status_enum), now(), ?, NULL)
                    """,
                    saved.getUserId(),
                    roleCode,
                    "KINDERGARTEN",
                    kindergartenId,
                    "ACTIVE",
                    saved.getUserId()
            );

            if ("PRINCIPAL".equals(levelCode)) {
                jdbcTemplate.update(
                        """
                        INSERT INTO user_kindergarten_memberships
                            (user_id, kindergarten_id, status, joined_at, left_at, created_at, updated_at)
                        VALUES
                            (?, ?, CAST(? AS status_enum), now(), NULL, now(), now())
                        ON CONFLICT (kindergarten_id, user_id) DO NOTHING
                        """,
                        saved.getUserId(),
                        kindergartenId,
                        "ACTIVE"
                );
            }

            return new SignupResponse(saved.getUserId(), saved.getLoginId(), saved.getStatus());
        }

        if ("SUPERADMIN".equals(memberType)) {
            if (request.getDepartment() == null || request.getDepartment().isBlank()) {
                throw new RuntimeException("행정청 부서명을 입력해주세요.");
            }

            jdbcTemplate.update(
                    """
                    INSERT INTO superadmins (user_id, name, department, created_at, updated_at)
                    VALUES (?, ?, ?, now(), now())
                    """,
                    saved.getUserId(),
                    request.getName().trim(),
                    request.getDepartment().trim()
            );

            jdbcTemplate.update(
                    """
                    INSERT INTO user_role_assignments
                        (user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at)
                    VALUES
                        (?, CAST(? AS user_role_enum), CAST(? AS user_role_assignment_scope_type), NULL,
                         CAST(? AS status_enum), now(), ?, NULL)
                    """,
                    saved.getUserId(),
                    "SUPERADMIN",
                    "PLATFORM",
                    "ACTIVE",
                    saved.getUserId()
            );
            return new SignupResponse(saved.getUserId(), saved.getLoginId(), saved.getStatus());
        }

        if ("PLATFORM_IT_ADMIN".equals(memberType)) {
            jdbcTemplate.update(
                    """
                    INSERT INTO user_role_assignments
                        (user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at)
                    VALUES
                        (?, CAST(? AS user_role_enum), CAST(? AS user_role_assignment_scope_type), NULL,
                         CAST(? AS status_enum), now(), ?, NULL)
                    """,
                    saved.getUserId(),
                    "PLATFORM_IT_ADMIN",
                    "PLATFORM",
                    "ACTIVE",
                    saved.getUserId()
            );
            return new SignupResponse(saved.getUserId(), saved.getLoginId(), saved.getStatus());
        }

        throw new RuntimeException("지원하지 않는 회원유형입니다: " + memberType);
    }

    private LocalDate parseDateOrThrow(String value, String errorMessage) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new RuntimeException(errorMessage);
        }
    }

    private String resolveGenderFromRrnBack7(String rrnBack7) {
        char first = rrnBack7.charAt(0);
        return switch (first) {
            case '1', '3', '5', '7' -> "MALE";
            case '2', '4', '6', '8' -> "FEMALE";
            default -> throw new RuntimeException("주민등록번호 뒷자리 첫 숫자가 유효하지 않습니다.");
        };
    }
}
