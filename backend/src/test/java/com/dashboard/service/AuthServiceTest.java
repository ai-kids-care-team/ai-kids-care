package com.dashboard.service;

import com.dashboard.dto.SignupRequest;
import com.dashboard.repository.UserRepository;
import com.dashboard.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private ChildLookupService childLookupService;

    @Mock
    private GuardianBindingService guardianBindingService;

    @Mock
    private CommonCodeService commonCodeService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil, childLookupService, guardianBindingService, commonCodeService);
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
