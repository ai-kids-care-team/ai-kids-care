package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.CommonCodeService;
import com.ai_kids_care.v1.security.JwtUtil;
import com.ai_kids_care.v1.vo.CommonCodeVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommonCodeController.class)
class CommonCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommonCodeService service;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    @WithMockUser
    void listCodeGroupCommonCodes_returnsOkAndBody() throws Exception {
        CommonCodeVO vo = new CommonCodeVO(
                1L,
                "ANNOUNCEMENT",
                "ANNOUNCEMENT_STATUS",
                "ACTIVE",
                "활성",
                1,
                true,
                OffsetDateTime.parse("2026-03-24T00:00:00+09:00"),
                OffsetDateTime.parse("2026-03-24T00:00:00+09:00")
        );
        when(service.listCodeGroupCommonCodes("ANNOUNCEMENT_STATUS")).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/v1/common_codes/code_group/{codeGroup}", "ANNOUNCEMENT_STATUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codeId").value(1))
                .andExpect(jsonPath("$[0].codeGroup").value("ANNOUNCEMENT_STATUS"))
                .andExpect(jsonPath("$[0].code").value("ACTIVE"));
    }
}

