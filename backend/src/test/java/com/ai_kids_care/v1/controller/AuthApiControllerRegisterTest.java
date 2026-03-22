package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.AuthRegisterRequest;
import com.ai_kids_care.v1.dto.AuthRegisterResponse;
import com.ai_kids_care.v1.security.JwtUtil;
import com.ai_kids_care.v1.service.AuthService;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthApiController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
class AuthApiControllerRegisterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    /** SecurityConfig → JwtAuthenticationFilter 가 JwtUtil 을 요구 */
    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void register_guardian_jsonAccepted_returns201() throws Exception {
        when(authService.register(org.mockito.ArgumentMatchers.any(AuthRegisterRequest.class)))
                .thenReturn(AuthRegisterResponse.builder()
                        .userId(1L)
                        .status(StatusEnum.ACTIVE)
                        .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                        .build());

        String body = """
                {
                  "userRole": "GUARDIAN",
                  "status": "ACTIVE",
                  "loginId": "guardian1",
                  "email": "g@test.com",
                  "phone": "01011112222",
                  "password": "password12",
                  "name": "테스트",
                  "rrnFirst6": "900101",
                  "rrnBack7": "1234567",
                  "gender": "MALE",
                  "kindergartenId": 1,
                  "childId": 10,
                  "relationship": "MOTHER",
                  "primaryGuardian": true
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1));

        verify(authService).register(argThat(r ->
                r.getUserRole() == UserRoleEnum.GUARDIAN
                        && r.getChildId() == 10L
                        && r.getKindergartenId() == 1L
                        && "MOTHER".equals(r.getRelationship())));
    }

    @Test
    void register_principal_mapsToKinderAdminRoleInRequestBody() throws Exception {
        when(authService.register(org.mockito.ArgumentMatchers.any(AuthRegisterRequest.class)))
                .thenReturn(AuthRegisterResponse.builder()
                        .userId(2L)
                        .status(StatusEnum.ACTIVE)
                        .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                        .build());

        String body = """
                {
                  "userRole": "KINDERGARTEN_ADMIN",
                  "status": "ACTIVE",
                  "loginId": "director1",
                  "email": "d@test.com",
                  "phone": "01033334444",
                  "password": "password12",
                  "name": "원장",
                  "rrnFirst6": "800101",
                  "rrnBack7": "1111111",
                  "gender": "FEMALE",
                  "kindergartenId": 1,
                  "emergencyContactName": "비상",
                  "emergencyContactPhone": "01099998888",
                  "level": "PRINCIPAL",
                  "startDate": "2025-03-01"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(authService).register(argThat(r ->
                r.getUserRole() == UserRoleEnum.KINDERGARTEN_ADMIN
                        && "PRINCIPAL".equals(r.getLevel())
                        && r.getKindergartenId() == 1L));
    }

    @Test
    void register_teacherLevel_teacherRole() throws Exception {
        when(authService.register(org.mockito.ArgumentMatchers.any(AuthRegisterRequest.class)))
                .thenReturn(AuthRegisterResponse.builder()
                        .userId(3L)
                        .status(StatusEnum.ACTIVE)
                        .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                        .build());

        String body = """
                {
                  "userRole": "TEACHER",
                  "status": "ACTIVE",
                  "loginId": "teacher1",
                  "email": "t@test.com",
                  "phone": "01055556666",
                  "password": "password12",
                  "name": "교사",
                  "rrnFirst6": "910202",
                  "rrnBack7": "2222222",
                  "gender": "MALE",
                  "kindergartenId": 1,
                  "emergencyContactName": "비상",
                  "emergencyContactPhone": "01077778888",
                  "level": "TEACHER",
                  "startDate": "2025-03-01"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(authService).register(argThat(r ->
                r.getUserRole() == UserRoleEnum.TEACHER && "TEACHER".equals(r.getLevel())));
    }
}
