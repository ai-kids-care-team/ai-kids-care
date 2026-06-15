package com.ai_kids_care.v1.security;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Default-deny boundary contract for the published API surface.
 *
 * Every {@code /api/v1/**} path outside the explicit public whitelist must require an
 * authenticated session: anonymous callers receive {@code 401}, never an anonymous
 * {@code 404} existence oracle and never anonymous business data. Closed controllers
 * (no published operation) are also covered by default-deny rather than relying on a
 * {@code permitAll} matcher that lets anonymous requests reach the dispatcher.
 *
 * The public whitelist is limited to the credential-free registration/login surface
 * plus S3 reference/directory reads needed before a session exists.
 */
@AutoConfigureMockMvc
class SecurityBoundaryIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void anonymousAccessToPublishedBusinessEndpointsIsUnauthorized() throws Exception {
        for (String path : new String[]{
                "/api/v1/ai_models",
                "/api/v1/announcements",
                "/api/v1/classes",
                "/api/v1/rooms",
                "/api/v1/cctv_cameras",
                "/api/v1/camera_streams",
                "/api/v1/detection_sessions"
        }) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void anonymousAccessToClosedControllersIsUnauthorizedNotAnExistenceOracle() throws Exception {
        for (String path : new String[]{
                "/api/v1/detection_events",
                "/api/v1/children/rrn",
                "/api/v1/users",
                "/api/v1/teachers",
                "/api/v1/guardians",
                "/api/v1/event_reviews",
                "/api/v1/audit_logs",
                "/api/v1/notifications",
                "/api/v1/notification_rules",
                "/api/v1/device_tokens",
                "/api/v1/event_evidence_files",
                "/api/v1/appreciation_letters",
                "/api/v1/superadmins",
                "/api/v1/graph/children/1"
        }) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void anonymousAccessToPublicWhitelistIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/register/availability")
                        .param("field", "login_id")
                        .param("value", "boundary-" + UUID.randomUUID()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/kindergartens")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/common_codes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/menus")).andExpect(status().isOk());
    }
}
