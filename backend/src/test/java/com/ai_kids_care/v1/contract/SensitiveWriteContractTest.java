package com.ai_kids_care.v1.contract;

import com.ai_kids_care.v1.controller.CameraStreamController;
import com.ai_kids_care.v1.controller.ChildrenController;
import com.ai_kids_care.v1.controller.CctvCameraController;
import com.ai_kids_care.v1.controller.DetectionSessionController;
import com.ai_kids_care.v1.controller.DeviceTokenController;
import com.ai_kids_care.v1.controller.EventEvidenceFileController;
import com.ai_kids_care.v1.controller.GuardianController;
import com.ai_kids_care.v1.controller.NotificationController;
import com.ai_kids_care.v1.controller.TeacherController;
import com.ai_kids_care.v1.mapper.AppreciationLetterMapper;
import com.ai_kids_care.v1.mapper.CameraStreamMapper;
import com.ai_kids_care.v1.mapper.CctvCameraMapper;
import com.ai_kids_care.v1.mapper.ChildMapper;
import com.ai_kids_care.v1.mapper.DetectionEventMapper;
import com.ai_kids_care.v1.mapper.DetectionSessionMapper;
import com.ai_kids_care.v1.mapper.DeviceTokenMapper;
import com.ai_kids_care.v1.mapper.EventEvidenceFileMapper;
import com.ai_kids_care.v1.mapper.EventReviewMapper;
import com.ai_kids_care.v1.mapper.GuardianMapper;
import com.ai_kids_care.v1.mapper.NotificationRuleMapper;
import com.ai_kids_care.v1.mapper.SuperadminMapper;
import com.ai_kids_care.v1.mapper.TeacherMapper;
import com.ai_kids_care.v1.mapper.UserMapper;
import com.ai_kids_care.v1.service.AppreciationLetterService;
import com.ai_kids_care.v1.service.CameraStreamService;
import com.ai_kids_care.v1.service.ChildrenService;
import com.ai_kids_care.v1.service.CctvCameraService;
import com.ai_kids_care.v1.service.DetectionEventService;
import com.ai_kids_care.v1.service.DetectionSessionService;
import com.ai_kids_care.v1.service.DeviceTokenService;
import com.ai_kids_care.v1.service.EventEvidenceFileService;
import com.ai_kids_care.v1.service.EventReviewService;
import com.ai_kids_care.v1.service.GuardianService;
import com.ai_kids_care.v1.service.NotificationRuleService;
import com.ai_kids_care.v1.service.NotificationService;
import com.ai_kids_care.v1.service.SuperadminService;
import com.ai_kids_care.v1.service.TeacherService;
import com.ai_kids_care.v1.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SensitiveWriteContractTest {

    @Test
    void closedPublicWriteDtosAreRemovedFromPublishedContract() {
        assertClassAbsent("com.ai_kids_care.v1.dto.UserCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.UserUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.ChildCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.ChildUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.GuardianCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.GuardianUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.TeacherCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.TeacherUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.DeviceTokenCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.DeviceTokenUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.EventEvidenceFileCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.EventEvidenceFileUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.CameraStreamCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.CameraStreamUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.DetectionEventCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.DetectionEventUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.DetectionSessionCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.DetectionSessionUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.EventReviewCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.EventReviewUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.CctvCameraCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.CctvCameraUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.NotificationRuleCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.NotificationRuleUpdateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.SuperadminCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.SuperadminUpdateDTO");
        // SPEC-0003：感谢信 DTO 已合法发布；改为存在性正向断言。
        assertClassPresent("com.ai_kids_care.v1.dto.AppreciationLetterCreateDTO");
        assertClassPresent("com.ai_kids_care.v1.dto.AppreciationLetterUpdateDTO");
    }

    @Test
    void closedPublicWriteEndpointsDoNotCallServices() throws Exception {
        ChildrenService childrenService = mock(ChildrenService.class);
        GuardianService guardianService = mock(GuardianService.class);
        TeacherService teacherService = mock(TeacherService.class);
        DeviceTokenService deviceTokenService = mock(DeviceTokenService.class);
        EventEvidenceFileService eventEvidenceFileService = mock(EventEvidenceFileService.class);
        CameraStreamService cameraStreamService = mock(CameraStreamService.class);
        DetectionSessionService detectionSessionService = mock(DetectionSessionService.class);
        CctvCameraService cctvCameraService = mock(CctvCameraService.class);
        NotificationService notificationService = mock(NotificationService.class);
        MockMvc mockMvc = standaloneSetup(
                new ChildrenController(childrenService),
                new GuardianController(),
                new TeacherController(),
                new DeviceTokenController(),
                new EventEvidenceFileController(),
                new CameraStreamController(cameraStreamService),
                new DetectionSessionController(detectionSessionService),
                new CctvCameraController(cctvCameraService),
                new NotificationController(notificationService)
        ).build();

        // children 已重开 GET（Guardian 关系-scoped，SPEC-0001 §349）；写操作仍未发布 → 405（路径存在但无写 handler）。
        mockMvc.perform(post("/api/v1/children").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/children/1").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/children/1"))
                .andExpect(status().isMethodNotAllowed());

        // notifications 已重开 GET（tenant-scoped，SPEC-0001 / ADR-0018 A3d）；写操作仍未发布 → 405（路径存在但无写 handler）。
        mockMvc.perform(post("/api/v1/notifications").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/notifications/1").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/notifications/1"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/v1/guardians").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/guardians/1").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/guardians/1"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/teachers").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/teachers/1").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/teachers/1"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/device_tokens").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/device_tokens/1").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/device_tokens/1"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/event_evidence_files").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/event_evidence_files/1").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/event_evidence_files/1"))
                .andExpect(status().isNotFound());

        // ADR-0026 Phase 1：camera_streams POST/PUT 已合法发布；授权由集成测试（CameraStreamEncryptionIntegrationTest）覆盖。
        // DELETE 仍未发布 → 405。
        mockMvc.perform(delete("/api/v1/camera_streams/1"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/v1/detection_sessions").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/detection_sessions/1").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/detection_sessions/1"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/v1/cctv_cameras").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/cctv_cameras/1").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/cctv_cameras/1"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(
                childrenService,
                guardianService,
                teacherService,
                deviceTokenService,
                eventEvidenceFileService,
                cameraStreamService,
                detectionSessionService,
                cctvCameraService,
                notificationService
        );
    }

    @Test
    void genericWriteControllersServicesAndMappersAreNoLongerExposed() {
        assertMethodNameAbsent(ChildrenController.class, "getChildByRRN");
        assertMethodNameAbsent(ChildrenController.class, "updateChildren");
        assertMethodNameAbsent(ChildrenController.class, "deleteChildren");
        assertMethodNameAbsent(GuardianController.class, "updateGuardian");
        assertMethodNameAbsent(GuardianController.class, "deleteGuardian");
        assertMethodNameAbsent(TeacherController.class, "updateTeacher");
        assertMethodNameAbsent(TeacherController.class, "deleteTeacher");
        assertMethodNameAbsent(DeviceTokenController.class, "deleteDeviceToken");
        assertMethodNameAbsent(EventEvidenceFileController.class, "deleteEventEvidenceFile");
        assertMethodNameAbsent(CameraStreamController.class, "deleteCameraStream");
        assertMethodNameAbsent(DetectionSessionController.class, "createDetectionSession");
        assertMethodNameAbsent(DetectionSessionController.class, "updateDetectionSession");
        assertMethodNameAbsent(DetectionSessionController.class, "deleteDetectionSession");
        assertMethodNameAbsent(CctvCameraController.class, "createCctvCamera");
        assertMethodNameAbsent(CctvCameraController.class, "updateCctvCamera");
        assertMethodNameAbsent(CctvCameraController.class, "deleteCctvCamera");

        assertMethodNameAbsent(UserService.class, "createUser");
        assertMethodNameAbsent(UserService.class, "updateUser");
        assertMethodNameAbsent(UserService.class, "deleteUser");
        assertMethodNameAbsent(ChildrenService.class, "createChildren");
        assertMethodNameAbsent(ChildrenService.class, "getChildByRRN");
        assertMethodNameAbsent(ChildrenService.class, "updateChildren");
        assertMethodNameAbsent(ChildrenService.class, "deleteChildren");
        assertMethodNameAbsent(GuardianService.class, "createGuardian");
        assertMethodNameAbsent(GuardianService.class, "updateGuardian");
        assertMethodNameAbsent(GuardianService.class, "deleteGuardian");
        assertMethodNameAbsent(TeacherService.class, "createTeacher");
        assertMethodNameAbsent(TeacherService.class, "updateTeacher");
        assertMethodNameAbsent(TeacherService.class, "deleteTeacher");
        assertMethodNameAbsent(DeviceTokenService.class, "createDeviceToken");
        assertMethodNameAbsent(DeviceTokenService.class, "updateDeviceToken");
        assertMethodNameAbsent(DeviceTokenService.class, "deleteDeviceToken");
        assertMethodNameAbsent(EventEvidenceFileService.class, "createEventEvidenceFile");
        assertMethodNameAbsent(EventEvidenceFileService.class, "updateEventEvidenceFile");
        assertMethodNameAbsent(EventEvidenceFileService.class, "deleteEventEvidenceFile");
        // ADR-0026 Phase 1：camera_streams 写（createCameraStream/updateCameraStream）已合法发布；
        // 授权集成测试由 CameraStreamEncryptionIntegrationTest 覆盖。
        assertMethodNameAbsent(CameraStreamService.class, "deleteCameraStream");
        assertMethodNameAbsent(DetectionEventService.class, "createDetectionEvent");
        assertMethodNameAbsent(DetectionEventService.class, "updateDetectionEvent");
        assertMethodNameAbsent(DetectionEventService.class, "deleteDetectionEvent");
        assertMethodNameAbsent(DetectionSessionService.class, "createDetectionSession");
        assertMethodNameAbsent(DetectionSessionService.class, "updateDetectionSession");
        assertMethodNameAbsent(DetectionSessionService.class, "deleteDetectionSession");
        assertMethodNameAbsent(EventReviewService.class, "createEventReview");
        assertMethodNameAbsent(EventReviewService.class, "updateEventReview");
        assertMethodNameAbsent(EventReviewService.class, "deleteEventReview");
        assertMethodNameAbsent(CctvCameraService.class, "createCctvCamera");
        assertMethodNameAbsent(CctvCameraService.class, "updateCctvCamera");
        assertMethodNameAbsent(CctvCameraService.class, "deleteCctvCamera");
        assertMethodNameAbsent(NotificationRuleService.class, "createNotificationRule");
        assertMethodNameAbsent(NotificationRuleService.class, "updateNotificationRule");
        assertMethodNameAbsent(NotificationRuleService.class, "deleteNotificationRule");
        assertMethodNameAbsent(SuperadminService.class, "createSuperadmin");
        assertMethodNameAbsent(SuperadminService.class, "updateSuperadmin");
        assertMethodNameAbsent(SuperadminService.class, "deleteSuperadmin");
        // SPEC-0003：感谢信写方法已合法发布；改为存在性正向断言。
        assertMethodNamePresent(AppreciationLetterService.class, "createAppreciationLetter");
        assertMethodNamePresent(AppreciationLetterService.class, "updateAppreciationLetter");
        assertMethodNamePresent(AppreciationLetterService.class, "deleteAppreciationLetter");

        assertMethodNameAbsent(UserMapper.class, "toEntity");
        assertMethodNameAbsent(UserMapper.class, "updateEntity");
        assertMethodNameAbsent(ChildMapper.class, "toEntity");
        assertMethodNameAbsent(ChildMapper.class, "updateEntity");
        assertMethodNameAbsent(GuardianMapper.class, "toEntity");
        assertMethodNameAbsent(GuardianMapper.class, "updateEntity");
        assertMethodNameAbsent(TeacherMapper.class, "toEntity");
        assertMethodNameAbsent(TeacherMapper.class, "updateEntity");
        assertMethodNameAbsent(DeviceTokenMapper.class, "toEntity");
        assertMethodNameAbsent(DeviceTokenMapper.class, "updateEntity");
        assertMethodNameAbsent(EventEvidenceFileMapper.class, "toEntity");
        assertMethodNameAbsent(EventEvidenceFileMapper.class, "updateEntity");
        assertMethodNameAbsent(CameraStreamMapper.class, "toEntity");
        assertMethodNameAbsent(CameraStreamMapper.class, "updateEntity");
        assertMethodNameAbsent(DetectionEventMapper.class, "toEntity");
        assertMethodNameAbsent(DetectionEventMapper.class, "updateEntity");
        assertMethodNameAbsent(DetectionSessionMapper.class, "toEntity");
        assertMethodNameAbsent(DetectionSessionMapper.class, "updateEntity");
        assertMethodNameAbsent(EventReviewMapper.class, "toEntity");
        assertMethodNameAbsent(EventReviewMapper.class, "updateEntity");
        assertMethodNameAbsent(CctvCameraMapper.class, "toEntity");
        assertMethodNameAbsent(CctvCameraMapper.class, "updateEntity");
        assertMethodNameAbsent(NotificationRuleMapper.class, "toEntity");
        assertMethodNameAbsent(NotificationRuleMapper.class, "updateEntity");
        assertMethodNameAbsent(SuperadminMapper.class, "toEntity");
        assertMethodNameAbsent(SuperadminMapper.class, "updateEntity");
        // SPEC-0003：感谢信 Mapper 写方法已合法发布；改为存在性正向断言。
        assertMethodNamePresent(AppreciationLetterMapper.class, "toEntity");
        assertMethodNamePresent(AppreciationLetterMapper.class, "updateEntity");
    }

    private void assertMethodNameAbsent(Class<?> ownerType, String methodName) {
        assertThat(ownerType.getDeclaredMethods())
                .as("%s must not expose %s", ownerType.getSimpleName(), methodName)
                .noneMatch(method -> method.getName().equals(methodName));
    }

    private void assertMethodNamePresent(Class<?> ownerType, String methodName) {
        assertThat(ownerType.getDeclaredMethods())
                .as("%s must expose %s", ownerType.getSimpleName(), methodName)
                .anyMatch(method -> method.getName().equals(methodName));
    }

    private void assertClassAbsent(String className) {
        assertThatCode(() -> Class.forName(className))
                .as("%s must not exist in the published generic write contract", className)
                .isInstanceOf(ClassNotFoundException.class);
    }

    private void assertClassPresent(String className) {
        assertThatCode(() -> Class.forName(className))
                .as("%s must exist in the published contract", className)
                .doesNotThrowAnyException();
    }
}
