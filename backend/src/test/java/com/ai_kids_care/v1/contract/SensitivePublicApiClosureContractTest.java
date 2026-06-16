package com.ai_kids_care.v1.contract;

import com.ai_kids_care.v1.controller.AuditLogController;
import com.ai_kids_care.v1.controller.AppreciationLetterController;
import com.ai_kids_care.v1.controller.DetectionEventController;
import com.ai_kids_care.v1.controller.DeviceTokenController;
import com.ai_kids_care.v1.controller.EventReviewController;
import com.ai_kids_care.v1.controller.EventEvidenceFileController;
import com.ai_kids_care.v1.controller.GraphController;
import com.ai_kids_care.v1.controller.GuardianController;
import com.ai_kids_care.v1.controller.KindergartenController;
import com.ai_kids_care.v1.controller.NotificationRuleController;
import com.ai_kids_care.v1.controller.SuperadminController;
import com.ai_kids_care.v1.controller.TeacherController;
import com.ai_kids_care.v1.controller.UserController;
import com.ai_kids_care.v1.mapper.KindergartenMapper;
import com.ai_kids_care.v1.service.KindergartenService;
import com.ai_kids_care.v1.vo.KindergartenLookupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SensitivePublicApiClosureContractTest {

    @Test
    void graphAuditLogNotificationAndSensitiveControllersPublishNoOperations() {
        assertThat(GraphController.class.getDeclaredMethods()).isEmpty();
        assertThat(AuditLogController.class.getDeclaredMethods()).isEmpty();
        // NotificationController 已重开 tenant-scoped GET（SPEC-0001 / ADR-0018 A3d）——不再是空壳；
        // 其授权 / 作用域 / 隐藏 404 行为由 NotificationReadAuthorizationIntegrationTest 覆盖。
        assertThat(EventReviewController.class.getDeclaredMethods()).isEmpty();
        assertThat(NotificationRuleController.class.getDeclaredMethods()).isEmpty();
        assertThat(SuperadminController.class.getDeclaredMethods()).isEmpty();
        assertThat(AppreciationLetterController.class.getDeclaredMethods()).isEmpty();
        assertThat(UserController.class.getDeclaredMethods()).isEmpty();
        // ChildrenController 已重开 Guardian 关系-scoped GET（SPEC-0001 §349）——不再是空壳；
        // 其授权 / 关系 / 隐藏 404 行为由 GuardianChildAuthorizationIntegrationTest 覆盖。
        assertThat(GuardianController.class.getDeclaredMethods()).isEmpty();
        assertThat(TeacherController.class.getDeclaredMethods()).isEmpty();
        assertThat(DeviceTokenController.class.getDeclaredMethods()).isEmpty();
        assertThat(EventEvidenceFileController.class.getDeclaredMethods()).isEmpty();
        assertThat(DetectionEventController.class.getDeclaredMethods()).isEmpty();
    }

    @Test
    void controllersWithoutPublishedOperationsReturn404ForRepresentativePaths() throws Exception {
        MockMvc mockMvc = standaloneSetup(
                new GraphController(),
                new AuditLogController(),
                new EventReviewController(),
                new NotificationRuleController(),
                new SuperadminController(),
                new AppreciationLetterController(),
                new UserController(),
                new GuardianController(),
                new TeacherController(),
                new DeviceTokenController(),
                new EventEvidenceFileController(),
                new DetectionEventController()
        ).build();

        mockMvc.perform(get("/api/v1/graph/children/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/audit_logs"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/event_reviews"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/event_reviews/1/latest"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/notification_rules"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/superadmins"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/appreciation_letters"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/guardians"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/guardians/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teachers"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teachers/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/device_tokens"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/device_tokens/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/event_evidence_files"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/event_evidence_files/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/detection_events"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/detection_events/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/event_reviews").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/notification_rules").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/superadmins").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/appreciation_letters").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void kindergartenLookupReturnsOnlyApprovedDirectoryFields() throws Exception {
        KindergartenService service = mock(KindergartenService.class);
        when(service.searchForSignupByBusinessRegistrationNo("1234567890"))
                .thenReturn(List.of(new KindergartenLookupResponse(
                        1L,
                        "Sample Kindergarten",
                        "11",
                        "KG-001",
                        "ACTIVE"
                )));
        MockMvc mockMvc = standaloneSetup(new KindergartenController(service)).build();

        mockMvc.perform(get("/api/v1/kindergartens/business-registration-no/1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kindergartenId").value(1))
                .andExpect(jsonPath("$[0].name").value("Sample Kindergarten"))
                .andExpect(jsonPath("$[0].regionCode").value("11"))
                .andExpect(jsonPath("$[0].code").value("KG-001"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].address").doesNotExist())
                .andExpect(jsonPath("$[0].businessRegistrationNo").doesNotExist())
                .andExpect(jsonPath("$[0].contactName").doesNotExist())
                .andExpect(jsonPath("$[0].contactPhone").doesNotExist())
                .andExpect(jsonPath("$[0].contactEmail").doesNotExist());
    }

    @Test
    void kindergartenGenericWritesAreClosedWithoutCallingService() throws Exception {
        KindergartenService service = mock(KindergartenService.class);
        MockMvc mockMvc = standaloneSetup(new KindergartenController(service)).build();

        mockMvc.perform(post("/api/v1/kindergartens")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/kindergartens/1")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/kindergartens/1"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(service);
    }

    @Test
    void kindergartenGenericWriteDtosServicesAndMappersAreRemoved() {
        assertClassAbsent("com.ai_kids_care.v1.dto.KindergartenCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.KindergartenUpdateDTO");
        assertMethodNameAbsent(KindergartenService.class, "createKindergarten");
        assertMethodNameAbsent(KindergartenService.class, "updateKindergarten");
        assertMethodNameAbsent(KindergartenService.class, "deleteKindergarten");
        assertMethodNameAbsent(KindergartenMapper.class, "toEntity");
        assertMethodNameAbsent(KindergartenMapper.class, "updateEntity");
    }

    private void assertMethodNameAbsent(Class<?> ownerType, String methodName) {
        assertThat(ownerType.getDeclaredMethods())
                .as("%s must not expose %s", ownerType.getSimpleName(), methodName)
                .noneMatch(method -> method.getName().equals(methodName));
    }

    private void assertClassAbsent(String className) {
        assertThatCode(() -> Class.forName(className))
                .as("%s must not exist in the published generic write contract", className)
                .isInstanceOf(ClassNotFoundException.class);
    }
}
