package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.vo.*;
import com.ai_kids_care.v1.entity.*;
import com.ai_kids_care.v1.repository.*;
import com.ai_kids_care.v1.security.JwtUtil;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.TokenTypeEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final KindergartenRepository kindergartenRepository;
    private final TeacherRepository teacherRepository;
    private final GuardianRepository guardianRepository;
    private final SuperadminRepository superadminRepository;
    private final ChildrenService childrenService;
    private final ChildGuardianRelationshipRepository childGuardianRelationshipRepository;
    private final UserKindergartenMembershipRepository userKindergartenMembershipRepository;


    @Value("${jwt.expiration}")
    private Integer expireSecond;

    private final Map<UserRoleEnum, BiConsumer<User, AuthRegisterDTO>> roleRegisterStrategies = Map.of(
            UserRoleEnum.GUARDIAN, this::registerGuardian,
            UserRoleEnum.TEACHER, this::registerTeacher,
            UserRoleEnum.KINDERGARTEN_ADMIN, this::registerTeacher,
            UserRoleEnum.PLATFORM_IT_ADMIN, this::registerPlatformItAdmin,
            UserRoleEnum.SUPERADMIN, this::registerSuperadmin
    );

    @Transactional
    public AuthRegisterResponse register(AuthRegisterDTO request) {

        try {
            validateRegisterRequest(request);
            assertRegisterCredentialsAvailable(request);

            Child resolvedChild = null;
            Long kindergartenScopeId = request.getKindergartenId();

            if (request.getUserRole() == UserRoleEnum.GUARDIAN) {
                resolvedChild = resolveChild(request); //주민번호 앞 + 뒤 자리로 찾기
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
                if (request.getLevel() == null) {
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
                case GUARDIAN -> registerGuardian(userSaved, request);
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

        }
    }

    public TokenVO login(AuthLoginDTO request) {
        User user = userRepository.findByLoginIdOrEmailOrPhone(request.getIdentifier(), request.getIdentifier(), request.getIdentifier());

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid loginId/email/phone or password");
        }

        String accessToken = jwtUtil.generateToken(request.getIdentifier());
        String refreshToken = jwtUtil.generateToken(request.getIdentifier());

        UserRoleEnum resolvedRole = userRoleAssignmentRepository
                .findFirstByUser_IdAndStatusOrderByGrantedAtDesc(user.getId(), StatusEnum.ACTIVE)
                .map(UserRoleAssignment::getRole)
                .orElse(UserRoleEnum.GUARDIAN);

        return TokenVO.builder()
                .accessToken(accessToken)
                .tokenType(TokenTypeEnum.BEARER)
                .expiresIn(expireSecond)
                .refreshToken(refreshToken)
                .refreshExpiresIn(expireSecond)
                .role(resolvedRole.name())
                .loginId(user.getLoginId())
                .build();
    }

    @Transactional(readOnly = true)
    public void passwordResets(AuthPasswordResetRequest request) {
        String to = request.getTo();
        boolean exists = userRepository.existsByLoginIdOrEmailOrPhone(to, to, to);
        // TODO: 메일/인증코드 발송 로직 연동
    }


    private void registerGuardian(User user, AuthRegisterDTO request) {
        Child child = childrenService.findChildByRrnParts(request.getChildRrnFirst6(), request.getChildRrnBack7())
                .orElseThrow(() -> new EntityNotFoundException("아이 정보를 찾을 수 없습니다."));

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

        Kindergarten kg = child.getKindergarten();
        ChildGuardianRelationshipId relationshipId = ChildGuardianRelationshipId.builder()
                .kindergartenId(kg.getId())
                .childId(child.getId())
                .guardianId(guardian.getId())
                .build();

        ChildGuardianRelationship childGuardianRelationship = ChildGuardianRelationship.builder()
                .id(relationshipId)
                .kindergarten(kg)
                .children(child)
                .guardians(guardian)
                .relationship(request.getRelationship())
                .isPrimary(request.getIsPrimaryGuardian())
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
    }

    private void registerTeacher(User user, AuthRegisterDTO request) {
        Kindergarten kindergarten = kindergartenRepository.findById(request.getKindergartenId())
                .orElseThrow(() -> new EntityNotFoundException("선택한 유치원 정보가 유효하지 않습니다."));

        Teacher teacher = Teacher.builder()
                .kindergarten(kindergarten)
                .user(user)
                .name(request.getName())
                .gender(request.getGender())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .rrnEncrypted(passwordEncoder.encode(request.getRrnBack7()))
                .rrnFirst6(request.getRrnFirst6())
                .level(request.getLevel())
                .startDate(null)
                .endDate(null)
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
    }

    private void registerPlatformItAdmin(User user, AuthRegisterDTO request) {
        //TODO
    }

    private void registerSuperadmin(User user, AuthRegisterDTO request) {
        Superadmin superadmin = Superadmin.builder()
                .user(user)
                .name(request.getName())
                .department(request.getDepartment())
                .status(StatusEnum.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        superadminRepository.save(superadmin);
    }

    private void validateRegisterRequest(AuthRegisterDTO request) {
        if (request.getUserRole() == UserRoleEnum.GUARDIAN) {

            boolean hasChildRrn = StringUtils.hasText(request.getChildRrnFirst6())
                    && StringUtils.hasText(request.getChildRrnBack7());
            if (!hasChildRrn) {
                throw new IllegalArgumentException("아이 찾기(주민번호) 정보가 필요합니다.");
            }
            if (request.getRelationship() == null) {
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

    /**
     * DB 유니크 제약(uq_user_account_*) 위반 전에 동일 조건으로 선검사 — 프론트 blur 검사를 건너뛴 경우에도 한글 메시지로 응답 가능.
     */
    private void assertRegisterCredentialsAvailable(AuthRegisterDTO request) {
        if (request.getLoginId() != null && StringUtils.hasText(request.getLoginId().trim())) {
            if (userRepository.existsByLoginId(request.getLoginId().trim())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
            }
        }
        if (request.getEmail() != null && StringUtils.hasText(request.getEmail().trim())) {
            if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
            }
        }
        if (request.getPhone() != null && StringUtils.hasText(request.getPhone().trim())) {
            if (userRepository.existsByPhone(request.getPhone().trim().replaceAll("\\D", ""))){
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 연락처(전화번호)입니다.");
            }

        }
    }

    private Child resolveChild(AuthRegisterDTO request) {
        return childrenService.findChildByRrnParts(request.getChildRrnFirst6(), request.getChildRrnBack7())
                .orElseThrow(() -> new EntityNotFoundException("아이 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public AuthRegisterVO checkRegisterFieldAvailability(String field, String value) {
        String normalizedField = field == null ? "" : field.trim().toLowerCase();
        String normalizedValue = value == null ? "" : value.trim();

        if (!StringUtils.hasText(normalizedField)) {
            throw new IllegalArgumentException("field 값이 필요합니다.");
        }
        if (!StringUtils.hasText(normalizedValue)) {
            return new AuthRegisterVO(false, "값을 입력해주세요.");
        }

        return switch (normalizedField) {
            case "loginid", "login_id", "login-id" -> userRepository.existsByLoginId(normalizedValue)
                    ? new AuthRegisterVO(false, "이미 사용 중인 로그인 ID입니다.")
                    : new AuthRegisterVO(true, null);
            case "email" -> userRepository.existsByEmailIgnoreCase(normalizedValue)
                    ? new AuthRegisterVO(false, "이미 사용 중인 이메일입니다.")
                    : new AuthRegisterVO(true, null);
            case "phone" -> {
                String digits = normalizedValue.replaceAll("\\D", "");
                boolean exists = userRepository.existsByPhone(digits);
                yield exists
                        ? new AuthRegisterVO(false, "이미 등록된 연락처(전화번호)입니다.")
                        : new AuthRegisterVO(true, null);
            }
            default -> throw new IllegalArgumentException("지원하지 않는 field 입니다: " + field);
        };
    }
}
