package com.ai_kids_care.v1.contract;

import com.ai_kids_care.v1.controller.ChildrenController;
import com.ai_kids_care.v1.controller.CameraStreamController;
import com.ai_kids_care.v1.controller.DeviceTokenController;
import com.ai_kids_care.v1.controller.EventEvidenceFileController;
import com.ai_kids_care.v1.controller.GuardianController;
import com.ai_kids_care.v1.controller.TeacherController;
import com.ai_kids_care.v1.controller.UserController;
import com.ai_kids_care.v1.service.CameraStreamService;
import com.ai_kids_care.v1.service.ChildrenService;
import com.ai_kids_care.v1.service.DeviceTokenService;
import com.ai_kids_care.v1.service.EventEvidenceFileService;
import com.ai_kids_care.v1.service.GuardianService;
import com.ai_kids_care.v1.service.TeacherService;
import com.ai_kids_care.v1.service.UserService;
import com.ai_kids_care.v1.vo.CameraStreamVO;
import com.ai_kids_care.v1.vo.ChildVO;
import com.ai_kids_care.v1.vo.DeviceTokenVO;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import com.ai_kids_care.v1.vo.GuardianVO;
import com.ai_kids_care.v1.vo.TeacherVO;
import com.ai_kids_care.v1.vo.UserVO;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SensitiveResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicResponseTypesDoNotExposeSensitiveStorageFields() {
        assertJacksonPropertyAbsent(UserVO.class, "passwordHash");
        assertJacksonPropertyAbsent(UserVO.class, "email");
        assertJacksonPropertyAbsent(UserVO.class, "phone");
        assertJacksonPropertyAbsent(ChildVO.class, "rrnEncrypted");
        assertJacksonPropertyAbsent(ChildVO.class, "rrnFirst6");
        assertJacksonPropertyAbsent(ChildVO.class, "birthDate");
        assertJacksonPropertyAbsent(ChildVO.class, "address");
        assertJacksonPropertyAbsent(GuardianVO.class, "rrnEncrypted");
        assertJacksonPropertyAbsent(GuardianVO.class, "rrnFirst6");
        assertJacksonPropertyAbsent(GuardianVO.class, "address");
        assertJacksonPropertyAbsent(TeacherVO.class, "rrnEncrypted");
        assertJacksonPropertyAbsent(TeacherVO.class, "staffNo");
        assertJacksonPropertyAbsent(TeacherVO.class, "rrnFirst6");
        assertJacksonPropertyAbsent(TeacherVO.class, "emergencyContactName");
        assertJacksonPropertyAbsent(TeacherVO.class, "emergencyContactPhone");
        assertJacksonPropertyAbsent(DeviceTokenVO.class, "pushToken");
        assertJacksonPropertyAbsent(EventEvidenceFileVO.class, "storageUri");
        assertJacksonPropertyAbsent(CameraStreamVO.class, "sourceUrl");
        assertJacksonPropertyAbsent(CameraStreamVO.class, "streamUser");
        assertJacksonPropertyAbsent(CameraStreamVO.class, "streamPassword");
        assertJacksonPropertyAbsent(CameraStreamVO.class, "streamPasswordEncrypted");
        assertJacksonPropertyAbsent(CameraStreamVO.class, "streamPasswordCiphertext");
        assertJacksonPropertyAbsent(CameraStreamVO.class, "streamPasswordIv");
        assertJacksonPropertyAbsent(CameraStreamVO.class, "streamPasswordKeyVersion");
        assertJacksonPropertyAbsent(CameraStreamVO.class, "playbackUrl");
        assertJacksonPropertyPresent(CameraStreamVO.class, "hasPassword");
        assertJacksonPropertyPresent(CameraStreamVO.class, "sourceProtocol");
        assertJacksonPropertyPresent(CameraStreamVO.class, "playbackProtocol");
    }

    @Test
    void representativeSpringMvcResponsesDoNotSerializeSensitiveStorageFields() throws Exception {
        UserService userService = mock(UserService.class);
        ChildrenService childrenService = mock(ChildrenService.class);
        GuardianService guardianService = mock(GuardianService.class);
        TeacherService teacherService = mock(TeacherService.class);
        DeviceTokenService deviceTokenService = mock(DeviceTokenService.class);
        EventEvidenceFileService eventEvidenceFileService = mock(EventEvidenceFileService.class);
        CameraStreamService cameraStreamService = mock(CameraStreamService.class);

        when(userService.getUser(1L)).thenReturn(new UserVO(
                1L,
                "guardian01",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-10T00:00:00Z"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-09T00:00:00Z")
        ));
        when(childrenService.getChild(1L)).thenReturn(new ChildVO(
                1L,
                10L,
                "Child One",
                "C-001",
                "F",
                LocalDate.parse("2024-03-01"),
                null,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-09T00:00:00Z")
        ));
        when(guardianService.getGuardian(1L)).thenReturn(new GuardianVO(
                1L,
                10L,
                20L,
                "Guardian One",
                "F",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-09T00:00:00Z")
        ));
        when(teacherService.getTeacher(1L)).thenReturn(new TeacherVO(
                1L,
                10L,
                30L,
                "Teacher One",
                "M",
                "DIRECTOR",
                LocalDate.parse("2024-03-01"),
                null,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-09T00:00:00Z")
        ));
        when(deviceTokenService.getDeviceToken(1L)).thenReturn(new DeviceTokenVO(
                1L,
                20L,
                "ANDROID",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-10T00:00:00Z"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z")
        ));
        when(eventEvidenceFileService.getEventEvidenceFile(1L)).thenReturn(new EventEvidenceFileVO(
                1L,
                40L,
                10L,
                "IMAGE",
                "image/jpeg",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                Boolean.FALSE,
                "sha256:abc123"
        ));
        when(cameraStreamService.getCameraStream(1L)).thenReturn(new CameraStreamVO(
                1L,
                10L,
                20L,
                "MAIN",
                Boolean.TRUE,
                "RTSP",
                "HTTPS",
                30,
                "1920x1080",
                Boolean.TRUE,
                Boolean.TRUE,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-09T00:00:00Z")
        ));

        MockMvc mockMvc = standaloneSetup(
                new UserController(),
                new ChildrenController(),
                new GuardianController(),
                new TeacherController(),
                new DeviceTokenController(),
                new EventEvidenceFileController(),
                new CameraStreamController(cameraStreamService)
        ).build();

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/children/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/guardians/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teachers/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/device_tokens/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/event_evidence_files/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/camera_streams/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.sourceProtocol").value("RTSP"))
                .andExpect(jsonPath("$.playbackUrl").doesNotExist())
                .andExpect(jsonPath("$.playbackProtocol").value("HTTPS"))
                .andExpect(jsonPath("$.sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.streamUser").doesNotExist())
                .andExpect(jsonPath("$.streamPassword").doesNotExist())
                .andExpect(jsonPath("$.streamPasswordEncrypted").doesNotExist())
                .andExpect(jsonPath("$.streamPasswordCiphertext").doesNotExist())
                .andExpect(jsonPath("$.streamPasswordIv").doesNotExist())
                .andExpect(jsonPath("$.streamPasswordKeyVersion").doesNotExist());
    }

    @Test
    void generatedOpenApiSchemasDoNotExposeSensitiveStorageFields() {
        assertOpenApiPropertyAbsent(UserVO.class, "passwordHash");
        assertOpenApiPropertyAbsent(UserVO.class, "email");
        assertOpenApiPropertyAbsent(UserVO.class, "phone");
        assertOpenApiPropertyAbsent(ChildVO.class, "rrnEncrypted");
        assertOpenApiPropertyAbsent(ChildVO.class, "rrnFirst6");
        assertOpenApiPropertyAbsent(ChildVO.class, "birthDate");
        assertOpenApiPropertyAbsent(ChildVO.class, "address");
        assertOpenApiPropertyAbsent(GuardianVO.class, "rrnEncrypted");
        assertOpenApiPropertyAbsent(GuardianVO.class, "rrnFirst6");
        assertOpenApiPropertyAbsent(GuardianVO.class, "address");
        assertOpenApiPropertyAbsent(TeacherVO.class, "rrnEncrypted");
        assertOpenApiPropertyAbsent(TeacherVO.class, "staffNo");
        assertOpenApiPropertyAbsent(TeacherVO.class, "rrnFirst6");
        assertOpenApiPropertyAbsent(TeacherVO.class, "emergencyContactName");
        assertOpenApiPropertyAbsent(TeacherVO.class, "emergencyContactPhone");
        assertOpenApiPropertyAbsent(DeviceTokenVO.class, "pushToken");
        assertOpenApiPropertyAbsent(EventEvidenceFileVO.class, "storageUri");
        assertOpenApiPropertyAbsent(CameraStreamVO.class, "sourceUrl");
        assertOpenApiPropertyAbsent(CameraStreamVO.class, "streamUser");
        assertOpenApiPropertyAbsent(CameraStreamVO.class, "streamPassword");
        assertOpenApiPropertyAbsent(CameraStreamVO.class, "streamPasswordEncrypted");
        assertOpenApiPropertyAbsent(CameraStreamVO.class, "streamPasswordCiphertext");
        assertOpenApiPropertyAbsent(CameraStreamVO.class, "streamPasswordIv");
        assertOpenApiPropertyAbsent(CameraStreamVO.class, "streamPasswordKeyVersion");
        assertOpenApiPropertyAbsent(CameraStreamVO.class, "playbackUrl");
        assertOpenApiPropertyPresent(CameraStreamVO.class, "hasPassword");
        assertOpenApiPropertyPresent(CameraStreamVO.class, "sourceProtocol");
        assertOpenApiPropertyPresent(CameraStreamVO.class, "playbackProtocol");
    }

    private void assertJacksonPropertyAbsent(Class<?> responseType, String forbiddenProperty) {
        assertThat(propertyNames(objectMapper.getSerializationConfig()
                .introspect(objectMapper.constructType(responseType))))
                .as("%s must not serialize %s", responseType.getSimpleName(), forbiddenProperty)
                .doesNotContain(forbiddenProperty);
    }

    private void assertJacksonPropertyPresent(Class<?> responseType, String expectedProperty) {
        assertThat(propertyNames(objectMapper.getSerializationConfig()
                .introspect(objectMapper.constructType(responseType))))
                .as("%s must continue serializing %s", responseType.getSimpleName(), expectedProperty)
                .contains(expectedProperty);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertOpenApiPropertyAbsent(Class<?> responseType, String forbiddenProperty) {
        ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(responseType));

        assertThat(resolvedSchema.schema)
                .as("OpenAPI schema must be generated for %s", responseType.getSimpleName())
                .isNotNull();

        Set<String> properties = resolvedSchema.schema.getProperties() == null
                ? Set.of()
                : resolvedSchema.schema.getProperties().keySet();

        assertThat(properties)
                .as("%s OpenAPI schema must not expose %s", responseType.getSimpleName(), forbiddenProperty)
                .doesNotContain(forbiddenProperty);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertOpenApiPropertyPresent(Class<?> responseType, String expectedProperty) {
        ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(responseType));

        assertThat(resolvedSchema.schema)
                .as("OpenAPI schema must be generated for %s", responseType.getSimpleName())
                .isNotNull();

        Set<String> properties = resolvedSchema.schema.getProperties() == null
                ? Set.of()
                : resolvedSchema.schema.getProperties().keySet();

        assertThat(properties)
                .as("%s OpenAPI schema must continue exposing %s", responseType.getSimpleName(), expectedProperty)
                .contains(expectedProperty);
    }

    private Set<String> propertyNames(BeanDescription description) {
        return description.findProperties().stream()
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toSet());
    }
}
