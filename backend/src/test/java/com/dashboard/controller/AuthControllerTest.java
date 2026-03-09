package com.dashboard.controller;

import com.dashboard.dto.ChildLookupResponse;
import com.dashboard.dto.CommonCodeResponse;
import com.dashboard.dto.SignupResponse;
import com.dashboard.security.JwtAuthenticationFilter;
import com.dashboard.security.JwtUtil;
import com.dashboard.service.AuthService;
import com.dashboard.service.CommonCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(authService.signup(any())).thenReturn(new SignupResponse(10L, "guardian_test", "ACTIVE"));

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

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.loginId").value("guardian_test"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void childrenLookup_returnsList() throws Exception {
        List<ChildLookupResponse> response = List.of(
                new ChildLookupResponse(3001L, 1001L, 2001L, "햇살반", "김하린", "C-2026-001", LocalDate.of(2019, 1, 1), "F")
        );
        when(authService.searchChildrenByName(eq("김"))).thenReturn(response);

        mockMvc.perform(get("/api/auth/children").param("name", "김"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].childId").value(3001))
                .andExpect(jsonPath("$[0].name").value("김하린"))
                .andExpect(jsonPath("$[0].className").value("햇살반"));
    }

    @Test
    void commonCodesLookup_returnsActiveCodes() throws Exception {
        List<CommonCodeResponse> response = List.of(
                new CommonCodeResponse("GUARDIAN_RELATIONSHIP", "FEMALE", "MOTHER", "엄마", 1),
                new CommonCodeResponse("GUARDIAN_RELATIONSHIP", "MALE", "FATHER", "아빠", 2)
        );
        when(commonCodeService.getActiveCodesByGroup(eq("GUARDIAN_RELATIONSHIP"))).thenReturn(response);

        mockMvc.perform(get("/api/auth/common-codes").param("group", "GUARDIAN_RELATIONSHIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parentCode").value("FEMALE"))
                .andExpect(jsonPath("$[0].code").value("MOTHER"))
                .andExpect(jsonPath("$[0].codeName").value("엄마"));
    }
}
