package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AuthLoginDTO;
import com.ai_kids_care.v1.dto.AuthPasswordResetDTO;
import com.ai_kids_care.v1.dto.AuthRegisterDTO;
import com.ai_kids_care.v1.entity.*;
import com.ai_kids_care.v1.repository.*;
import com.ai_kids_care.v1.security.JwtUtil;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.TokenTypeEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.AuthRegisterResponse;
import com.ai_kids_care.v1.vo.AuthRegisterVO;
import com.ai_kids_care.v1.vo.TokenVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // UserRoleAssignment
        UserRoleEnum role = request.getUserRole();
        UserRoleAssignmentScopeType scopeType;
        Long scopeId = null;
        switch (role) {
            case GUARDIAN:
            case TEACHER:
            case KINDERGARTEN_ADMIN:
                scopeType = UserRoleAssignmentScopeType.KINDERGARTEN;
                scopeId = request.getKindergartenId();
                break;

            case PLATFORM_IT_ADMIN:
            case SUPERADMIN:
                scopeType = UserRoleAssignmentScopeType.PLATFORM;
                break;

            default:
                throw new IllegalArgumentException("Unsupported role: " + role);
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

        BiConsumer<User, AuthRegisterDTO> registerFunction = roleRegisterStrategies.get(role);
        if (registerFunction == null) {
            throw new IllegalArgumentException("지원하지 않는 회원유형입니다.");
        }
        registerFunction.accept(userSaved, request);

        return AuthRegisterResponse.builder()
                .userId(userSaved.getId())
                .status(userSaved.getStatus())
                .createdAt(userSaved.getCreatedAt())
                .build();
    }

    public TokenVO login(AuthLoginDTO request) {
        User user = userRepository.findByLoginIdOrEmailOrPhone(request.getIdentifier(), request.getIdentifier(), request.getIdentifier());

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
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
                .id(user.getId())
                .loginId(user.getLoginId())
                .build();
    }

    @Transactional(readOnly = true)
    public void passwordResets(AuthPasswordResetDTO request) {
        String to = request.getTo();
        boolean exists = userRepository.existsByLoginIdOrEmailOrPhone(to, to, to);
        // TODO: 메일/인증코드 발송 로직 연동
        throw new IllegalArgumentException("Not implemented");
    }


    private void registerGuardian(User user, AuthRegisterDTO request) {
        Child child = childrenService.getChildEntityByRRN(request.getChildRrnFirst6(), request.getChildRrnBack7())
                .orElseThrow(() -> new EntityNotFoundException("Child not found"));
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

        boolean primaryGuardian = !Boolean.FALSE.equals(request.getIsPrimaryGuardian());
        ChildGuardianRelationship childGuardianRelationship = ChildGuardianRelationship.builder()
                .id(relationshipId)
                .kindergarten(kg)
                .children(child)
                .guardians(guardian)
                .relationship(request.getRelationship())
                .isPrimary(primaryGuardian)
                .priority(primaryGuardian ? 1 : 2)
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
                .staffNo(request.getStaffNo())
                .startDate(LocalDate.now())
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
        //TODO 관리자는 없어서 같이 공유함.
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

    public AuthRegisterVO checkRegisterFieldAvailability(String field, String value) {
        return switch (field) {
            case "login_id", "login-id", "loginId" -> userRepository.existsByLoginId(value)
                    ? new AuthRegisterVO(false, "이미 사용 중인 로그인 ID입니다.")
                    : new AuthRegisterVO(true, "");
            case "email" -> userRepository.existsByEmailIgnoreCase(value)
                    ? new AuthRegisterVO(false, "이미 사용 중인 이메일입니다.")
                    : new AuthRegisterVO(true, "");
            case "phone" -> userRepository.existsByPhone(value)
                    ? new AuthRegisterVO(false, "이미 등록된 연락처(전화번호)입니다.")
                    : new AuthRegisterVO(true, "");
            default -> throw new IllegalArgumentException("지원하지 않는 field 입니다: " + field);
        };
    }
}
