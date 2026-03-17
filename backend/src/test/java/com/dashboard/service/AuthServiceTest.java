package com.dashboard.service;

import com.ai_kids_care.dashboard.dto.SignupRequest;
import com.ai_kids_care.dashboard.repository.UserRepository;
import com.ai_kids_care.dashboard.security.JwtUtil;
import com.ai_kids_care.dashboard.service.AuthService;
import com.ai_kids_care.dashboard.service.CommonCodeService;
import com.ai_kids_care.dashboard.service.GuardianBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GuardianBindingService guardianBindingService;

    @Mock
    private CommonCodeService commonCodeService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtUtil,
                guardianBindingService,
                commonCodeService,
                jdbcTemplate
        );
        lenient().when(commonCodeService.existsActiveCode(eq("GUARDIAN_RELATIONSHIP"), anyString())).thenReturn(true);
    }

    @Test
    void signup_withoutChildId_throwsValidationError() {
        SignupRequest request = baseGuardianRequest();
        request.setChildId(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.signup(request));
        assertEquals("아이 찾기에서 아이를 선택해주세요.", ex.getMessage());
    }

    @Test
    void signup_withInvalidRrnBack7_throwsValidationError() {
        SignupRequest request = baseGuardianRequest();
        request.setRrnBack7("12AB");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.signup(request));
        assertEquals("주민등록번호 뒷자리는 숫자 7자리여야 합니다.", ex.getMessage());
    }

    @Test
    void signup_withOtherRelationshipButEmptyCustomValue_throwsValidationError() {
        SignupRequest request = baseGuardianRequest();
        request.setRelationship("OTHER");
        request.setCustomRelationship("   ");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.signup(request));
        assertEquals("기타 관계를 입력해주세요.", ex.getMessage());
    }

    @Test
    void signup_withUnknownRelationshipCode_throwsValidationError() {
        SignupRequest request = baseGuardianRequest();
        request.setRelationship("UNKNOWN_RELATION");
        when(commonCodeService.existsActiveCode("GUARDIAN_RELATIONSHIP", "UNKNOWN_RELATION")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.signup(request));
        assertEquals("유효하지 않은 관계 코드입니다.", ex.getMessage());
    }

    private SignupRequest baseGuardianRequest() {
        SignupRequest request = new SignupRequest();
        request.setMemberType("GUARDIAN");
        request.setLoginId("guardian_test");
        request.setPassword("password123");
        request.setName("테스트보호자");
        request.setEmail("guardian_test@example.com");
        request.setPhone("010-1234-5678");
        request.setChildId(3001L);
        request.setRrnFirst6("900101");
        request.setRrnBack7("2123456");
        request.setRelationship("MOTHER");
        request.setCustomRelationship("");
        request.setPrimaryGuardian(true);
        return request;
    }
}
