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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminBootstrapRunner 단위 테스트(harden-auth-bootstrap-and-demo, group 1).
 *
 * 통합 컨테이너는 항상 데모 seed(사용자 존재)로 뜨므로 "빈 DB" 상태를 그곳에서 만들기 어렵다.
 * 부트스트랩 가드 로직(env / count()==0 멱등 / 'admin' 거부 / 정확히 1개 ACTIVE SUPERADMIN)은
 * 레포지토리를 mock 한 단위 테스트로 결정적으로 검증한다. 실제 BCrypt 인코더로 비밀번호가
 * 평문이 아닌 해시로 저장되는지도 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private SuperadminRepository superadminRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminBootstrapRunner runner(String loginId, String password) {
        return new AdminBootstrapRunner(
                userRepository,
                userRoleAssignmentRepository,
                superadminRepository,
                passwordEncoder,
                loginId,
                password,
                "Bootstrap Admin",
                "Platform Operations");
    }

    @Test
    void emptyDatabaseWithEnv_createsExactlyOneActiveSuperadmin() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawPassword = "Operator-Strong-Pass-2026!";
        runner("ops-root", rawPassword).bootstrap();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getLoginId()).isEqualTo("ops-root");
        assertThat(saved.getStatus()).isEqualTo(StatusEnum.ACTIVE);
        // 평문이 아니라 bcrypt 해시로 저장되어야 한다.
        assertThat(saved.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, saved.getPasswordHash())).isTrue();

        ArgumentCaptor<UserRoleAssignment> roleCaptor =
                ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(userRoleAssignmentRepository).save(roleCaptor.capture());
        UserRoleAssignment role = roleCaptor.getValue();
        assertThat(role.getRole()).isEqualTo(UserRoleEnum.SUPERADMIN);
        assertThat(role.getScopeType()).isEqualTo(UserRoleAssignmentScopeType.PLATFORM);
        assertThat(role.getScopeId()).isNull();
        assertThat(role.getStatus()).isEqualTo(StatusEnum.ACTIVE);

        ArgumentCaptor<Superadmin> superadminCaptor = ArgumentCaptor.forClass(Superadmin.class);
        verify(superadminRepository).save(superadminCaptor.capture());
        assertThat(superadminCaptor.getValue().getStatus()).isEqualTo(StatusEnum.ACTIVE);
    }

    @Test
    void existingUsers_isIdempotentNoOp() {
        when(userRepository.count()).thenReturn(7L);

        runner("ops-root", "Operator-Strong-Pass-2026!").bootstrap();

        verify(userRepository, never()).save(any());
        verify(userRoleAssignmentRepository, never()).save(any());
        verify(superadminRepository, never()).save(any());
    }

    @Test
    void envUnset_createsNothing() {
        runner("", "").bootstrap();

        // count() 조차 호출하지 않고 일찍 반환한다.
        verify(userRepository, never()).count();
        verify(userRepository, never()).save(any());
        verify(userRoleAssignmentRepository, never()).save(any());
        verify(superadminRepository, never()).save(any());
    }

    @Test
    void loginIdAdmin_isRejected_evenOnEmptyDatabase() {
        // count() 를 0 으로 stub 해도(빈 DB) 'admin' 거부가 먼저라 아무것도 만들지 않아야 한다.
        lenient().when(userRepository.count()).thenReturn(0L);

        runner("ADMIN", "Operator-Strong-Pass-2026!").bootstrap();

        verify(userRepository, never()).save(any());
        verify(userRoleAssignmentRepository, never()).save(any());
        verify(superadminRepository, never()).save(any());
    }
}
