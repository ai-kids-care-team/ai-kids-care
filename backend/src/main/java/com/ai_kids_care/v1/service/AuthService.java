package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.entity.*;
import com.ai_kids_care.v1.repository.*;
import com.ai_kids_care.v1.security.JwtUtil;
import com.ai_kids_care.v1.type.LevelEnum;
import com.ai_kids_care.v1.type.RelationshipEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.TokenTypeEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final KindergartenRepository kindergartenRepository;
    private final TeacherRepository teacherRepository;
    private final GuardianRepository guardianRepository;
    private final SuperadminRepository superadminRepository;
    private final ChildRepository childRepository;
    private final ChildGuardianRelationshipRepository childGuardianRelationshipRepository;
    private final UserKindergartenMembershipRepository userKindergartenMembershipRepository;

    @Value("${jwt.expiration}")
    private Integer expireSecond;

    @Transactional
    public AuthRegisterResponse register(AuthRegisterRequest request) {
        log.info("[START] register loginId={}, userRole={}", request.getLoginId(), request.getUserRole());
        try {
            validateRegisterRequest(request);

            Child resolvedChild = null;
            Long kindergartenScopeId = request.getKindergartenId();

            if (request.getUserRole() == UserRoleEnum.GUARDIAN) {
                resolvedChild = resolveChild(request);
                kindergartenScopeId = resolvedChild.getKindergarten().getId();
            } else if (request.getUserRole() == UserRoleEnum.TEACHER
                    || request.getUserRole() == UserRoleEnum.KINDERGARTEN_ADMIN) {
                if (request.getKindergartenId() == null) {
                    throw new IllegalArgumentException("유치원을 선택해주세요.");
                }
                kindergartenScopeId = request.getKindergartenId();
                if (!StringUtils.hasText(request.getEmergencyContactName())
                        || !StringUtils.hasText(request.getEmergencyContactPhone())) {
                    throw new IllegalArgumentException("비상 연락처를 입력해주세요.");
                }
                if (!StringUtils.hasText(request.getLevel())) {
                    throw new IllegalArgumentException("직급을 선택해주세요.");
                }
            }

            User user = User.builder()
                    .loginId(request.getLoginId())
                    .email(request.getEmail())
                    .phone(request.getPhone())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .status(StatusEnum.ACTIVE)
                    .lastLoginAt(null)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            User userSaved = userRepository.save(user);

            UserRoleAssignmentScopeType scopeType;
            Long scopeId = null;
            switch (request.getUserRole()) {
                case GUARDIAN:
                case TEACHER:
                case KINDERGARTEN_ADMIN:
                    scopeType = UserRoleAssignmentScopeType.KINDERGARTEN;
                    scopeId = kindergartenScopeId;
                    break;
                case PLATFORM_IT_ADMIN:
                case SUPERADMIN:
                    scopeType = UserRoleAssignmentScopeType.PLATFORM;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported role: " + request.getUserRole());
            }

            UserRoleAssignment userRoleAssignment = UserRoleAssignment.builder()
                    .user(userSaved)
                    .role(request.getUserRole())
                    .scopeType(scopeType)
                    .scopeId(scopeId)
                    .status(StatusEnum.ACTIVE)
                    .grantedAt(OffsetDateTime.now())
                    .grantedByUser(null)
                    .revokedAt(null)
                    .revokedByUser(null)
                    .build();
            userRoleAssignmentRepository.save(userRoleAssignment);

            switch (request.getUserRole()) {
                case GUARDIAN -> registerGuardian(userSaved, request, resolvedChild);
                case TEACHER, KINDERGARTEN_ADMIN -> registerTeacher(userSaved, request);
                case PLATFORM_IT_ADMIN -> registerPlatformItAdmin(userSaved, request);
                case SUPERADMIN -> registerSuperadmin(userSaved, request);
                default -> throw new IllegalArgumentException("지원하지 않는 회원유형입니다.");
            }

            return AuthRegisterResponse.builder()
                    .userId(userSaved.getId())
                    .status(userSaved.getStatus())
                    .createdAt(userSaved.getCreatedAt())
                    .build();
        } finally {
            log.debug("[END] register loginId={}", request.getLoginId());
        }
    }

    private void validateRegisterRequest(AuthRegisterRequest request) {
        if (request.getUserRole() == UserRoleEnum.GUARDIAN) {
            boolean hasChildId = request.getChildId() != null;
            boolean hasChildRrn = StringUtils.hasText(request.getChildRrnFirst6())
                    && StringUtils.hasText(request.getChildRrnBack7());
            if (!hasChildId && !hasChildRrn) {
                throw new IllegalArgumentException("아이 찾기(아이 ID 또는 아이 주민번호) 정보가 필요합니다.");
            }
            if (!StringUtils.hasText(request.getRelationship())) {
                throw new IllegalArgumentException("보호자 관계를 선택해주세요.");
            }
        }
        if (request.getUserRole() == UserRoleEnum.TEACHER
                || request.getUserRole() == UserRoleEnum.KINDERGARTEN_ADMIN
                || request.getUserRole() == UserRoleEnum.GUARDIAN) {
            if (request.getGender() == null) {
                throw new IllegalArgumentException("성별을 입력해주세요.");
            }
            if (!StringUtils.hasText(request.getRrnFirst6()) || request.getRrnFirst6().length() != 6) {
                throw new IllegalArgumentException("주민등록번호 앞 6자리를 입력해주세요.");
            }
            if (!StringUtils.hasText(request.getRrnBack7()) || request.getRrnBack7().length() != 7) {
                throw new IllegalArgumentException("주민등록번호 뒤 7자리를 입력해주세요.");
            }
        }
        if (request.getUserRole() == UserRoleEnum.SUPERADMIN
                && !StringUtils.hasText(request.getDepartment())) {
            throw new IllegalArgumentException("행정청 부서명을 입력해주세요.");
        }
    }

    private Child resolveChild(AuthRegisterRequest request) {
        if (request.getChildId() != null) {
            return childRepository.findById(request.getChildId())
                    .orElseThrow(() -> new EntityNotFoundException("아이 정보를 찾을 수 없습니다."));
        }
        Child byRrn = childRepository.findByRrnFirst6AndRrnEncrypted(
                request.getChildRrnFirst6(),
                passwordEncoder.encode(request.getChildRrnBack7())
        );
        if (byRrn == null) {
            throw new EntityNotFoundException("아이 정보를 찾을 수 없습니다.");
        }
        return byRrn;
    }

    /**
     * DB relationship_enum 은 FATHER, MOTHER 만 허용.
     */
    private RelationshipEnum mapRelationshipToDb(String code) {
        if (!StringUtils.hasText(code)) {
            return RelationshipEnum.MOTHER;
        }
        String u = code.trim().toUpperCase();
        return switch (u) {
            case "FATHER", "PATERNAL_GRANDFATHER", "MATERNAL_GRANDFATHER" -> RelationshipEnum.FATHER;
            default -> RelationshipEnum.MOTHER;
        };
    }

    private LevelEnum mapTeacherLevel(String code) {
        if (!StringUtils.hasText(code)) {
            return LevelEnum.TEACHER;
        }
        return switch (code.trim().toUpperCase()) {
            case "PRINCIPAL" -> LevelEnum.DIRECTOR;
            case "VICE_PRINCIPAL" -> LevelEnum.VICE_DIRECTOR;
            case "TEACHER" -> LevelEnum.TEACHER;
            default -> LevelEnum.OTHER;
        };
    }

    private LocalDate parseLocalDateOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다: " + value);
        }
    }

    public TokenResponse login(AuthLoginRequest request) {
        log.info("[START] login identifier={}", request.getIdentifier());
        try {
            User user = userRepository.findByLoginIdOrEmailOrPhone(
                    request.getIdentifier(), request.getIdentifier(), request.getIdentifier());

            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new RuntimeException("Invalid loginId/email/phone or password");
            }

            UserRoleEnum primaryRole = resolvePrimaryRoleForUser(user.getId());
            String displayName = resolveLoginDisplayName(user);

            String accessToken = jwtUtil.generateToken(request.getIdentifier());
            String refreshToken = jwtUtil.generateToken(request.getIdentifier());
            TokenResponse response = TokenResponse.builder()
                    .accessToken(accessToken)
                    .tokenType(TokenTypeEnum.BEARER)
                    .expiresIn(expireSecond)
                    .refreshToken(refreshToken)
                    .refreshExpiresIn(expireSecond)
                    .loginId(user.getLoginId())
                    .name(displayName)
                    .role(primaryRole.name())
                    .build();

            // 디버그용: 운영 환경에서는 전체 토큰 로깅 지양 → log.debug + 마스킹 권장
            log.info("[login] identifier={} accessToken={} refreshToken={}",
                    request.getIdentifier(),
                    response.getAccessToken(),
                    response.getRefreshToken());

            return response;

        } finally {
            log.info("[END] login identifier={}", request.getIdentifier());
        }
    }

    /**
     * 동일 사용자에게 복수의 활성 역할이 있을 수 있어, UI·권한에 맞게 우선순위가 높은 역할을 고른다.
     */
    private UserRoleEnum resolvePrimaryRoleForUser(Long userId) {
        List<UserRoleAssignment> active =
                userRoleAssignmentRepository.findByUser_IdAndStatus(userId, StatusEnum.ACTIVE);
        if (active.isEmpty()) {
            return UserRoleEnum.GUARDIAN;
        }
        return active.stream()
                .map(UserRoleAssignment::getRole)
                .max(Comparator.comparingInt(AuthService::priorityOfRole))
                .orElse(UserRoleEnum.GUARDIAN);
    }

    private static int priorityOfRole(UserRoleEnum r) {
        if (r == null) {
            return 0;
        }
        return switch (r) {
            case SUPERADMIN -> 50;
            case PLATFORM_IT_ADMIN -> 40;
            case KINDERGARTEN_ADMIN -> 30;
            case TEACHER -> 20;
            case GUARDIAN -> 10;
        };
    }

    private String resolveLoginDisplayName(User user) {
        Long uid = user.getId();
        Optional<String> guardianName =
                guardianRepository.findByUser_Id(uid).map(Guardian::getName).filter(StringUtils::hasText);
        if (guardianName.isPresent()) {
            return guardianName.get();
        }
        Optional<String> teacherName =
                teacherRepository.findByUser_Id(uid).map(Teacher::getName).filter(StringUtils::hasText);
        if (teacherName.isPresent()) {
            return teacherName.get();
        }
        Optional<String> superadminName =
                superadminRepository.findByUser_Id(uid).map(Superadmin::getName).filter(StringUtils::hasText);
        return superadminName.orElseGet(() -> StringUtils.hasText(user.getLoginId()) ? user.getLoginId() : "");
    }

    @Transactional(readOnly = true)
    public void passwordResets(AuthPasswordResetRequest request) {
        log.info("[START] passwordResets");
        boolean exists = false;
        try {
            String to = request.getTo();
            exists = userRepository.existsByLoginIdOrEmailOrPhone(to, to, to);
        } finally {
            log.info("[END] passwordResets exists={}", exists);
        }
    }

    private void registerGuardian(User user, AuthRegisterRequest request, Child child) {
        log.info("[START] registerGuardian userId={}, loginId={}, childId={}",
                user.getId(), user.getLoginId(), child.getId());
        try {
            Guardian guardian = Guardian.builder()
                    .user(user)
                    .kindergarten(child.getKindergarten())
                    .name(request.getName())
                    .rrnEncrypted(passwordEncoder.encode(request.getRrnBack7()))
                    .rrnFirst6(request.getRrnFirst6())
                    .gender(request.getGender())
                    .address(request.getAddress())
                    .status(StatusEnum.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            guardian = guardianRepository.save(guardian);

            // @EmbeddedId + @MapsId: id 가 null 이면 flush 시 childId 세팅에서 NPE — 복합키를 명시
            Long kgId = child.getKindergarten().getId();
            ChildGuardianRelationshipId relationshipId = ChildGuardianRelationshipId.builder()
                    .kindergartenId(kgId)
                    .childId(child.getId())
                    .guardianId(guardian.getId())
                    .build();

            ChildGuardianRelationship childGuardianRelationship = ChildGuardianRelationship.builder()
                    .id(relationshipId)
                    .children(child)
                    .guardians(guardian)
                    .relationship(mapRelationshipToDb(request.getRelationship()))
                    .isPrimary(Boolean.TRUE.equals(request.getIsPrimaryGuardian()))
                    .startDate(LocalDate.now())
                    .endDate(null)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            childGuardianRelationshipRepository.save(childGuardianRelationship);

            UserKindergartenMembership userKindergartenMembership = UserKindergartenMembership.builder()
                    .user(user)
                    .kindergarten(child.getKindergarten())
                    .status(StatusEnum.ACTIVE)
                    .joinedAt(OffsetDateTime.now())
                    .leftAt(null)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            userKindergartenMembershipRepository.save(userKindergartenMembership);
        } finally {
            log.info("[END] registerGuardian userId={}", user.getId());
        }
    }

    private void registerTeacher(User user, AuthRegisterRequest request) {
        log.info("[START] registerTeacher userId={}, loginId={}, kindergartenId={}",
                user.getId(), user.getLoginId(), request.getKindergartenId());
        try {
            Kindergarten kindergarten = kindergartenRepository.findById(request.getKindergartenId())
                    .orElseThrow(() -> new EntityNotFoundException("선택한 유치원 정보가 유효하지 않습니다."));

            LocalDate start = parseLocalDateOrNull(request.getStartDate());
            LocalDate end = parseLocalDateOrNull(request.getEndDate());
            if (start == null) {
                start = LocalDate.now();
            }
            if (end != null && end.isBefore(start)) {
                throw new IllegalArgumentException("근무종료일은 근무시작일보다 빠를 수 없습니다.");
            }

            Teacher teacher = Teacher.builder()
                    .kindergarten(kindergarten)
                    .user(user)
                    .name(request.getName())
                    .gender(request.getGender())
                    .emergencyContactName(request.getEmergencyContactName())
                    .emergencyContactPhone(request.getEmergencyContactPhone())
                    .rrnEncrypted(passwordEncoder.encode(request.getRrnBack7()))
                    .rrnFirst6(request.getRrnFirst6())
                    .level(mapTeacherLevel(request.getLevel()))
                    .startDate(start)
                    .endDate(end)
                    .status(StatusEnum.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            teacherRepository.save(teacher);

            UserKindergartenMembership userKindergartenMembership = UserKindergartenMembership.builder()
                    .user(user)
                    .kindergarten(kindergarten)
                    .status(StatusEnum.ACTIVE)
                    .joinedAt(OffsetDateTime.now())
                    .leftAt(null)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            userKindergartenMembershipRepository.save(userKindergartenMembership);
        } finally {
            log.info("[END] registerTeacher userId={}", user.getId());
        }
    }

    private void registerPlatformItAdmin(User user, AuthRegisterRequest request) {
        log.info("[START] registerPlatformItAdmin userId={}, loginId={}", user.getId(), user.getLoginId());
        try {
            // TODO
        } finally {
            log.info("[END] registerPlatformItAdmin userId={}", user.getId());
        }
    }

    private void registerSuperadmin(User user, AuthRegisterRequest request) {
        log.info("[START] registerSuperadmin userId={}, loginId={}", user.getId(), user.getLoginId());
        try {
            Superadmin superadmin = Superadmin.builder()
                    .user(user)
                    .name(request.getName())
                    .department(request.getDepartment())
                    .status(StatusEnum.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            superadminRepository.save(superadmin);
        } finally {
            log.info("[END] registerSuperadmin userId={}", user.getId());
        }
    }
}
