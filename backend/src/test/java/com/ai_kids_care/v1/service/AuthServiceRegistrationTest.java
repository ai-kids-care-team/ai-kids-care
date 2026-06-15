package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.AuthRegisterDTO;
import com.ai_kids_care.v1.dto.AuthLoginDTO;
import com.ai_kids_care.v1.dto.GuardianChildVerificationRequest;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.repository.ChildGuardianRelationshipRepository;
import com.ai_kids_care.v1.repository.GuardianRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.SuperadminRepository;
import com.ai_kids_care.v1.repository.TeacherRepository;
import com.ai_kids_care.v1.repository.UserKindergartenMembershipRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.repository.UserRoleAssignmentRepository;
import com.ai_kids_care.v1.security.AuthenticatedSession;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextService;
import com.ai_kids_care.v1.security.SessionPrincipal;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.AuthSessionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.time.OffsetDateTime;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegistrationTest {

    private static final String AUTHENTICATION_FAILURE_MESSAGE = "Authentication failed";

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private KindergartenRepository kindergartenRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private GuardianRepository guardianRepository;
    @Mock private SuperadminRepository superadminRepository;
    @Mock private ChildrenService childrenService;
    @Mock private ChildGuardianRelationshipRepository childGuardianRelationshipRepository;
    @Mock private UserKindergartenMembershipRepository userKindergartenMembershipRepository;
    @Mock private EffectiveAuthorizationContextService authorizationContextService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_platformItAdminRole_rejectsBeforeEncodingOrPersistence() {
        AuthRegisterDTO request = new AuthRegisterDTO();
        request.setUserRole(UserRoleEnum.PLATFORM_IT_ADMIN);

        ResponseStatusException exception = catchThrowableOfType(
                () -> authService.register(request),
                ResponseStatusException.class
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(
                passwordEncoder,
                userRepository,
                userRoleAssignmentRepository,
                kindergartenRepository,
                teacherRepository,
                guardianRepository,
                superadminRepository,
                childrenService,
                childGuardianRelationshipRepository,
                userKindergartenMembershipRepository
        );
    }

    @Test
    void verifyGuardianChild_returnsOnlyMatchState() {
        GuardianChildVerificationRequest matched =
                new GuardianChildVerificationRequest("200921", "4037926");
        GuardianChildVerificationRequest unmatched =
                new GuardianChildVerificationRequest("200921", "0000000");
        when(childrenService.getChildEntityByRRN("200921", "4037926"))
                .thenReturn(Optional.of(new Child()));
        when(childrenService.getChildEntityByRRN("200921", "0000000"))
                .thenReturn(Optional.empty());

        assertThat(authService.verifyGuardianChild(matched).verified()).isTrue();
        assertThat(authService.verifyGuardianChild(unmatched).verified()).isFalse();
    }

    @Test
    void verifyGuardianChild_rejectsMalformedRrnBeforeLookup() {
        GuardianChildVerificationRequest malformed =
                new GuardianChildVerificationRequest("abcdef", "zzzzzzz");

        ResponseStatusException exception = catchThrowableOfType(
                () -> authService.verifyGuardianChild(malformed),
                ResponseStatusException.class
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(childrenService);
    }

    @Test
    void login_rejectsInvalidCredentialsWithExplicitUnauthorizedStatus() {
        when(userRepository.findByLoginIdOrEmailOrPhone("user", "user", "user"))
                .thenReturn(null);

        ResponseStatusException exception = catchThrowableOfType(
                () -> authService.login(new AuthLoginDTO("user", "password")),
                ResponseStatusException.class
        );

        assertAuthenticationFailure(exception);
        verifyNoInteractions(userRoleAssignmentRepository, userKindergartenMembershipRepository);
    }

    @Test
    void login_rejectsMultipleActiveRoleAssignments() {
        User user = activeUser();
        when(userRepository.findByLoginIdOrEmailOrPhone("user", "user", "user"))
                .thenReturn(user);
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(authorizationContextService.establishSession(user))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        AUTHENTICATION_FAILURE_MESSAGE
                ));

        ResponseStatusException exception = catchThrowableOfType(
                () -> authService.login(new AuthLoginDTO("user", "password")),
                ResponseStatusException.class
        );

        assertAuthenticationFailure(exception);
        verifyNoInteractions(
                userRoleAssignmentRepository,
                userKindergartenMembershipRepository
        );
    }

    @Test
    void login_platformRoleReturnsMinimalSessionIdentity() {
        User user = activeUser();
        UserRoleAssignment role = activeRole(user, UserRoleEnum.SUPERADMIN);
        AuthenticatedSession expected = new AuthenticatedSession(
                new SessionPrincipal(1L, role.getId(), null, OffsetDateTime.now()),
                new AuthSessionVO(1L, "user", "SUPERADMIN", "PLATFORM", null)
        );
        when(userRepository.findByLoginIdOrEmailOrPhone("user", "user", "user"))
                .thenReturn(user);
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(authorizationContextService.establishSession(user)).thenReturn(expected);

        AuthenticatedSession session =
                authService.login(new AuthLoginDTO("user", "password"));

        assertThat(session.principal().userId()).isEqualTo(1L);
        assertThat(session.principal().roleAssignmentId()).isEqualTo(role.getId());
        assertThat(session.principal().membershipId()).isNull();
        assertThat(session.principal().getName()).isEqualTo("user:1");
        assertThat(session.profile().effectiveRole()).isEqualTo("SUPERADMIN");
        assertThat(session.profile().scopeType()).isEqualTo("PLATFORM");
        assertThat(session.profile().scopeId()).isNull();
    }

    @Test
    void login_kindergartenRoleWithoutMatchingMembershipIsRejected() {
        User user = activeUser();
        when(userRepository.findByLoginIdOrEmailOrPhone("user", "user", "user"))
                .thenReturn(user);
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(authorizationContextService.establishSession(user))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        AUTHENTICATION_FAILURE_MESSAGE
                ));

        ResponseStatusException exception = catchThrowableOfType(
                () -> authService.login(new AuthLoginDTO("user", "password")),
                ResponseStatusException.class
        );

        assertAuthenticationFailure(exception);
    }

    private User activeUser() {
        return User.builder()
                .id(1L)
                .loginId("user")
                .passwordHash("hash")
                .status(StatusEnum.ACTIVE)
                .build();
    }

    private UserRoleAssignment activeRole(User user, UserRoleEnum role) {
        return UserRoleAssignment.builder()
                .id(role == UserRoleEnum.SUPERADMIN ? 11L : 12L)
                .user(user)
                .role(role)
                .scopeType(UserRoleAssignmentScopeType.PLATFORM)
                .status(StatusEnum.ACTIVE)
                .build();
    }

    private UserRoleAssignment tenantRole(User user) {
        return UserRoleAssignment.builder()
                .id(13L)
                .user(user)
                .role(UserRoleEnum.TEACHER)
                .scopeType(UserRoleAssignmentScopeType.KINDERGARTEN)
                .scopeId(1L)
                .status(StatusEnum.ACTIVE)
                .build();
    }

    private void assertAuthenticationFailure(ResponseStatusException exception) {
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo(AUTHENTICATION_FAILURE_MESSAGE);
    }
}
