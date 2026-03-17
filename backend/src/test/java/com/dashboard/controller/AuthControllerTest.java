package com.dashboard.controller;

import com.ai_kids_care.dashboard.controller.AuthController;
import com.ai_kids_care.dashboard.dto.CommonCodeResponse;
import com.ai_kids_care.dashboard.dto.SignupResponse;
import com.ai_kids_care.dashboard.entity.StatusEnum;
import com.ai_kids_care.dashboard.security.JwtAuthenticationFilter;
import com.ai_kids_care.dashboard.security.JwtUtil;
import com.ai_kids_care.dashboard.service.AuthService;
import com.ai_kids_care.dashboard.service.CommonCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private CommonCodeService commonCodeService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void signup_withGuardianPayload_returnsOkResponse() throws Exception {
        when(authService.signup(any())).thenReturn(new SignupResponse(10L, "guardian_test", StatusEnum.ACTIVE));

        Map<String, Object> payload = Map.ofEntries(
                new SimpleEntry<>("name", "테스트보호자"),
                new SimpleEntry<>("loginId", "guardian_test"),
                new SimpleEntry<>("password", "password123"),
                new SimpleEntry<>("email", "guardian_test@example.com"),
                new SimpleEntry<>("phone", "010-1111-2222"),
                new SimpleEntry<>("memberType", "GUARDIAN"),
                new SimpleEntry<>("childId", 3001),
                new SimpleEntry<>("rrnFirst6", "900101"),
                new SimpleEntry<>("rrnBack7", "2123456"),
                new SimpleEntry<>("relationship", "MOTHER"),
                new SimpleEntry<>("customRelationship", ""),
                new SimpleEntry<>("primaryGuardian", true)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.loginId").value("guardian_test"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void commonCodesLookup_returnsActiveCodes() throws Exception {
        List<CommonCodeResponse> response = List.of(
                new CommonCodeResponse("GUARDIAN_RELATIONSHIP", "FEMALE", "MOTHER", "엄마", 1),
                new CommonCodeResponse("GUARDIAN_RELATIONSHIP", "MALE", "FATHER", "아빠", 2)
        );
        when(commonCodeService.getActiveCodesByGroup("GUARDIAN_RELATIONSHIP")).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/common-codes").param("group", "GUARDIAN_RELATIONSHIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parentCode").value("FEMALE"))
                .andExpect(jsonPath("$[0].code").value("MOTHER"))
                .andExpect(jsonPath("$[0].codeName").value("엄마"));
    }
}
