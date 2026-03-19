package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.*;
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
    private final ChildRepository childRepository;
    private final ChildGuardianRelationshipRepository childGuardianRelationshipRepository;
    private final UserKindergartenMembershipRepository userKindergartenMembershipRepository;

    @Value("${jwt.expiration}")
    private Integer expireSecond;

    private final Map<UserRoleEnum, BiConsumer<User, AuthRegisterRequest>> roleRegisterStrategies = Map.of(
            UserRoleEnum.GUARDIAN, this::registerGuardian,
            UserRoleEnum.TEACHER, this::registerTeacher,
            UserRoleEnum.KINDERGARTEN_ADMIN, this::registerTeacher,
            UserRoleEnum.PLATFORM_IT_ADMIN, this::registerPlatformItAdmin,
            UserRoleEnum.SUPERADMIN, this::registerSuperadmin
    );

    @Transactional
    public AuthRegisterResponse register(AuthRegisterRequest request) {
        // User
        User user = User.builder()
                .loginId(request.getLoginId())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .phone(request.getPhone())
                .lastLoginAt(OffsetDateTime.now())
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

        BiConsumer<User, AuthRegisterRequest> registerFunction = roleRegisterStrategies.get(request.getUserRole());
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


    public TokenResponse login(AuthLoginRequest request) {
        User user = userRepository.findByLoginIdOrEmailOrPhone(request.getIdentifier(), request.getIdentifier(), request.getIdentifier());

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid loginId/email/phone or password");
        }

        String accessToken = jwtUtil.generateToken(request.getIdentifier());
        String refreshToken = jwtUtil.generateToken(request.getIdentifier());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType(TokenTypeEnum.BEARER)
                .expiresIn(expireSecond)
                .refreshToken(refreshToken)
                .refreshExpiresIn(expireSecond)
                .build();
    }

    @Transactional(readOnly = true)
    public void passwordResets(AuthPasswordResetRequest request) {
        String to = request.getTo();
        boolean exists = userRepository.existsByLoginIdOrEmailOrPhone(to, to, to);
        // TODO: 메일/인증코드 발송 로직 연동
    }


    private void registerGuardian(User user, AuthRegisterRequest request) {
        Child child = childRepository.findByRrnFirst6AndRrnEncrypted(
                request.getChildRrnFirst6(),
                passwordEncoder.encode(request.getChildRrnBack7())
        );

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
        guardianRepository.save(guardian);

        ChildGuardianRelationship childGuardianRelationship = ChildGuardianRelationship.builder()
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

    private void registerTeacher(User user, AuthRegisterRequest request) {
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
                .startDate(LocalDate.now())
                .endDate(null)
                .status(StatusEnum.PENDING)
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

    private void registerPlatformItAdmin(User user, AuthRegisterRequest request) {
        //TODO
    }

    private void registerSuperadmin(User user, AuthRegisterRequest request) {
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
}
