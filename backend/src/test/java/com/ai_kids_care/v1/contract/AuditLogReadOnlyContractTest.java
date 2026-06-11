package com.ai_kids_care.v1.contract;

import com.ai_kids_care.v1.controller.AuditLogController;
import com.ai_kids_care.v1.service.AuditLogService;
import com.ai_kids_care.v1.vo.AuditLogVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuditLogReadOnlyContractTest {

    @Test
    void readEndpointsRemainMapped() throws Exception {
        AuditLogService service = mock(AuditLogService.class);
        AuditLogVO auditLog = new AuditLogVO(
                7L,
                1L,
                2L,
                "READ",
                "Child",
                3L,
                "127.0.0.1",
                "contract-test",
                OffsetDateTime.parse("2026-06-10T00:00:00Z")
        );
        when(service.listAuditLogs(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(service.getAuditLog(7L)).thenReturn(auditLog);
        MockMvc mockMvc = standaloneSetup(new AuditLogController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        mockMvc.perform(get("/api/v1/audit_logs"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/audit_logs/7"))
                .andExpect(status().isOk());

        verify(service).listAuditLogs(null, PageRequest.of(0, 20));
        verify(service).getAuditLog(7L);
    }

    @Test
    void publicWriteEndpointsAreNotMappedAndDoNotCallService() throws Exception {
        AuditLogService service = mock(AuditLogService.class);
        MockMvc mockMvc = standaloneSetup(new AuditLogController(service)).build();

        mockMvc.perform(post("/api/v1/audit_logs")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/audit_logs/7")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/audit_logs/7"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(service);
    }
}
