package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AuthLoginDTO;
import com.ai_kids_care.v1.dto.AuthPasswordResetDTO;
import com.ai_kids_care.v1.dto.AuthRefreshRequest;
import com.ai_kids_care.v1.dto.AuthRegisterDTO;
import com.ai_kids_care.v1.dto.GuardianChildVerificationRequest;
import com.ai_kids_care.v1.entity.*;
import com.ai_kids_care.v1.repository.*;
import com.ai_kids_care.v1.security.JwtUtil;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.TokenTypeEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.type.LevelEnum;
import com.ai_kids_care.v1.vo.AuthRegisterResponse;
import com.ai_kids_care.v1.vo.AuthRegisterVO;
import com.ai_kids_care.v1.vo.GuardianChildVerificationResponse;
import com.ai_kids_care.v1.vo.TokenVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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

    @Transactional
    public AuthRegisterResponse register(AuthRegisterDTO request) {
        UserRoleEnum role = request.getUserRole();
        if (role == null || role == UserRoleEnum.PLATFORM_IT_ADMIN) {
            throw badRegistrationRequest("This role is not available for public registration");
        }
        RegistrationContext registrationContext = prepareRegistrationContext(role, request);

        User user = User.builder()
                .loginId(request.getLoginId())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(StatusEnum.PENDING)
                .lastLoginAt(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        User userSaved = userRepository.save(user);

        UserRoleAssignment userRoleAssignment = UserRoleAssignment.builder()
                .user(userSaved)
                .role(request.getUserRole())
                .scopeType(registrationContext.scopeType())
                .scopeId(registrationContext.scopeId())
                .status(StatusEnum.PENDING)
                .grantedAt(OffsetDateTime.now())
                .grantedByUser(null)
                .revokedAt(null)
                .revokedByUser(null)
                .build();
        userRoleAssignmentRepository.save(userRoleAssignment);

        switch (role) {
            case GUARDIAN -> registerGuardian(userSaved, request, registrationContext.child());
            case TEACHER, KINDERGARTEN_ADMIN ->
                    registerTeacher(userSaved, request, registrationContext.kindergarten());
            case SUPERADMIN -> registerSuperadmin(userSaved, request);
            default -> throw badRegistrationRequest("This role is not available for public registration");
        }

        return AuthRegisterResponse.builder()
                .userId(userSaved.getId())
                .status(userSaved.getStatus())
                .createdAt(userSaved.getCreatedAt())
                .build();
    }

    public TokenVO login(AuthLoginDTO request) {
        User user = userRepository.findByLoginIdOrEmailOrPhone(request.getIdentifier(), request.getIdentifier(), request.getIdentifier());

        if (user == null
                || user.getStatus() != StatusEnum.ACTIVE
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw authenticationFailure();
        }

        UserRoleAssignment activeRoleAssignment = resolveActiveRoleAssignment(user);
        String accessToken = jwtUtil.generateToken(request.getIdentifier());
        String refreshToken = jwtUtil.generateToken(request.getIdentifier());

        return TokenVO.builder()
                .accessToken(accessToken)
                .tokenType(TokenTypeEnum.BEARER)
                .expiresIn(expireSecond)
                .refreshToken(refreshToken)
                .refreshExpiresIn(expireSecond)
                .role(activeRoleAssignment.getRole().name())
                .kindergartenId(resolveKindergartenScope(activeRoleAssignment))
                .id(user.getId())
                .loginId(user.getLoginId())
                .build();
    }

    public TokenVO refresh(AuthRefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        String username;
        boolean validToken;
        try {
            username = jwtUtil.extractIdentifier(refreshToken);
            validToken = username != null && jwtUtil.validateToken(refreshToken, username);
        } catch (RuntimeException exception) {
            throw authenticationFailure();
        }
        if (!validToken) {
            throw authenticationFailure();
        }
        User user = userRepository.findByLoginIdOrEmailOrPhone(username, username, username);
        if (user == null || user.getStatus() != StatusEnum.ACTIVE) {
            throw authenticationFailure();
        }

        UserRoleAssignment activeRoleAssignment = resolveActiveRoleAssignment(user);
        String newAccessToken = jwtUtil.generateToken(username);
        String newRefreshToken = jwtUtil.generateToken(username);

        return TokenVO.builder()
                .accessToken(newAccessToken)
                .tokenType(TokenTypeEnum.BEARER)
                .expiresIn(expireSecond)
                .refreshToken(newRefreshToken)
                .refreshExpiresIn(expireSecond)
                .role(activeRoleAssignment.getRole().name())
                .kindergartenId(resolveKindergartenScope(activeRoleAssignment))
                .id(user.getId())
                .loginId(user.getLoginId())
                .build();
    }

    @Transactional(readOnly = true)
    public GuardianChildVerificationResponse verifyGuardianChild(GuardianChildVerificationRequest request) {
        requireDigits(request.childRrnFirst6(), 6, "Child RRN first part must be 6 digits");
        requireDigits(request.childRrnBack7(), 7, "Child RRN last part must be 7 digits");
        boolean verified = childrenService
                .getChildEntityByRRN(request.childRrnFirst6(), request.childRrnBack7())
                .isPresent();
        return new GuardianChildVerificationResponse(verified);
    }

    @Transactional(readOnly = true)
    public void passwordResets(AuthPasswordResetDTO request) {
        String to = request.getTo();
        boolean exists = userRepository.existsByLoginIdOrEmailOrPhone(to, to, to);
        // TODO: 메일/인증코드 발송 로직 연동
        throw new IllegalArgumentException("Not implemented");
    }


    private void registerGuardian(User user, AuthRegisterDTO request, Child child) {
        Guardian guardian = Guardian.builder()
                .user(user)
                .kindergarten(child.getKindergarten())
                .name(request.getName())
                .rrnEncrypted(passwordEncoder.encode(request.getRrnBack7()))
                .rrnFirst6(request.getRrnFirst6())
                .gender(request.getGender())
                .address(request.getAddress())
                .status(StatusEnum.PENDING)
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

        boolean primaryGuardian = !Boolean.FALSE.equals(request.getPrimaryGuardian());
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
                .status(StatusEnum.PENDING)
                .joinedAt(OffsetDateTime.now())
                .leftAt(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        userKindergartenMembershipRepository.save(userKindergartenMembership);
    }

    private void registerTeacher(User user, AuthRegisterDTO request, Kindergarten kindergarten) {
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
                .status(StatusEnum.PENDING)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        teacherRepository.save(teacher);

        UserKindergartenMembership userKindergartenMembership = UserKindergartenMembership.builder()
                .user(user)
                .kindergarten(kindergarten)
                .status(StatusEnum.PENDING)
                .joinedAt(OffsetDateTime.now())
                .leftAt(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        userKindergartenMembershipRepository.save(userKindergartenMembership);
    }

    private void registerSuperadmin(User user, AuthRegisterDTO request) {
        Superadmin superadmin = Superadmin.builder()
                .user(user)
                .name(request.getName())
                .department(request.getDepartment())
                .status(StatusEnum.PENDING)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        superadminRepository.save(superadmin);
    }

    private UserRoleAssignment resolveActiveRoleAssignment(User user) {
        List<UserRoleAssignment> activeAssignments = userRoleAssignmentRepository
                .findAllByUser_IdAndStatusOrderByGrantedAtDesc(user.getId(), StatusEnum.ACTIVE);
        if (activeAssignments.size() != 1) {
            throw authenticationFailure();
        }
        return activeAssignments.get(0);
    }

    private Long resolveKindergartenScope(UserRoleAssignment activeRoleAssignment) {
        return activeRoleAssignment.getScopeType() == UserRoleAssignmentScopeType.KINDERGARTEN
                ? activeRoleAssignment.getScopeId()
                : null;
    }

    private RegistrationContext prepareRegistrationContext(UserRoleEnum role, AuthRegisterDTO request) {
        return switch (role) {
            case GUARDIAN -> {
                requireDigits(request.getRrnFirst6(), 6, "Applicant RRN first part must be 6 digits");
                requireDigits(request.getRrnBack7(), 7, "Applicant RRN last part must be 7 digits");
                requireDigits(request.getChildRrnFirst6(), 6, "Child RRN first part must be 6 digits");
                requireDigits(request.getChildRrnBack7(), 7, "Child RRN last part must be 7 digits");
                if (request.getGender() == null || request.getRelationship() == null) {
                    throw badRegistrationRequest("Guardian profile fields are required");
                }

                Child child = childrenService
                        .getChildEntityByRRN(request.getChildRrnFirst6(), request.getChildRrnBack7())
                        .orElseThrow(() -> badRegistrationRequest("Child not found"));
                yield new RegistrationContext(
                        UserRoleAssignmentScopeType.KINDERGARTEN,
                        child.getKindergarten().getId(),
                        child,
                        child.getKindergarten()
                );
            }
            case TEACHER, KINDERGARTEN_ADMIN -> {
                if (request.getKindergartenId() == null) {
                    throw badRegistrationRequest("Kindergarten is required");
                }
                requireDigits(request.getRrnFirst6(), 6, "Applicant RRN first part must be 6 digits");
                requireDigits(request.getRrnBack7(), 7, "Applicant RRN last part must be 7 digits");
                requireText(request.getEmergencyContactName(), "Emergency contact is required");
                requireText(request.getEmergencyContactPhone(), "Emergency contact is required");
                requireText(request.getStaffNo(), "Staff number is required");
                if (request.getGender() == null || request.getLevel() == null) {
                    throw badRegistrationRequest("Teacher profile fields are required");
                }
                validateRoleLevel(role, request.getLevel());

                Kindergarten kindergarten = kindergartenRepository.findById(request.getKindergartenId())
                        .orElseThrow(() -> badRegistrationRequest("Kindergarten not found"));
                yield new RegistrationContext(
                        UserRoleAssignmentScopeType.KINDERGARTEN,
                        kindergarten.getId(),
                        null,
                        kindergarten
                );
            }
            case SUPERADMIN -> {
                requireText(request.getDepartment(), "Department is required");
                yield new RegistrationContext(
                        UserRoleAssignmentScopeType.PLATFORM,
                        null,
                        null,
                        null
                );
            }
            default -> throw badRegistrationRequest("This role is not available for public registration");
        };
    }

    private void validateRoleLevel(UserRoleEnum role, LevelEnum level) {
        boolean administratorLevel = level == LevelEnum.DIRECTOR || level == LevelEnum.VICE_DIRECTOR;
        if (role == UserRoleEnum.KINDERGARTEN_ADMIN && !administratorLevel) {
            throw badRegistrationRequest("Kindergarten administrator role requires director level");
        }
        if (role == UserRoleEnum.TEACHER && administratorLevel) {
            throw badRegistrationRequest("Director levels require kindergarten administrator role");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRegistrationRequest(message);
        }
    }

    private void requireDigits(String value, int length, String message) {
        if (value == null || !value.matches("\\d{" + length + "}")) {
            throw badRegistrationRequest(message);
        }
    }

    private ResponseStatusException badRegistrationRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException authenticationFailure() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed");
    }

    private record RegistrationContext(
            UserRoleAssignmentScopeType scopeType,
            Long scopeId,
            Child child,
            Kindergarten kindergarten
    ) {
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
