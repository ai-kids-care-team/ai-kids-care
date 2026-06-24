package com.ai_kids_care.v1.enums;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0013 enum metadata endpoint contract.
 *
 * {@code GET /api/v1/enums/{name}} exposes the codes of the backing {@code type.*}
 * enum so the front end can render reference data without the retired
 * {@code common_codes} dictionary table. Labels are the front end's job (i18n);
 * the endpoint returns codes only. The endpoint is anonymously readable.
 */
@AutoConfigureMockMvc
class EnumsControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void knownNameReturnsItsEnumCodesAnonymously() throws Exception {
        mockMvc.perform(get("/api/v1/enums/gender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("MALE", "FEMALE")));
    }

    @Test
    void guardianRelationshipReturnsRelationshipEnumCodes() throws Exception {
        mockMvc.perform(get("/api/v1/enums/guardian_relationship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("FATHER", "MOTHER")));
    }

    @Test
    void teacherLevelReturnsLevelEnumCodes() throws Exception {
        mockMvc.perform(get("/api/v1/enums/teacher_level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code",
                        containsInAnyOrder("DIRECTOR", "VICE_DIRECTOR", "TEACHER", "OTHER")));
    }

    @Test
    void eventTypeAndEventStatusAndStatusAreRegistered() throws Exception {
        mockMvc.perform(get("/api/v1/enums/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code",
                        containsInAnyOrder("ACTIVE", "PENDING", "DISABLED", "REJECTED")));
        mockMvc.perform(get("/api/v1/enums/event_status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code",
                        containsInAnyOrder("OPEN", "ACKNOWLEDGED", "IN_REVIEW", "RESOLVED", "DISMISSED", "ESCALATED")));
        mockMvc.perform(get("/api/v1/enums/event_type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(13)));
    }

    @Test
    void codesCarryAStableSortOrderIndex() throws Exception {
        mockMvc.perform(get("/api/v1/enums/gender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MALE"))
                .andExpect(jsonPath("$[0].sortOrder").value(0))
                .andExpect(jsonPath("$[1].code").value("FEMALE"))
                .andExpect(jsonPath("$[1].sortOrder").value(1));
    }

    @Test
    void contextParamIsAcceptedAndIgnoredForSharedStatus() throws Exception {
        mockMvc.perform(get("/api/v1/enums/status").param("context", "kindergartens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code",
                        containsInAnyOrder("ACTIVE", "PENDING", "DISABLED", "REJECTED")));
    }

    @Test
    void unknownNameReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/enums/this_enum_does_not_exist"))
                .andExpect(status().isNotFound());
    }
}
