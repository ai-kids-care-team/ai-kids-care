package com.ai_kids_care.v1.contract;

import com.ai_kids_care.v1.controller.ChildrenController;
import com.ai_kids_care.v1.controller.CameraStreamController;
import com.ai_kids_care.v1.controller.DeviceTokenController;
import com.ai_kids_care.v1.controller.EventEvidenceFileController;
import com.ai_kids_care.v1.controller.GuardianController;
import com.ai_kids_care.v1.controller.TeacherController;
import com.ai_kids_care.v1.controller.UserController;
import com.ai_kids_care.v1.dto.ChildCreateDTO;
import com.ai_kids_care.v1.dto.ChildUpdateDTO;
import com.ai_kids_care.v1.dto.GuardianCreateDTO;
import com.ai_kids_care.v1.dto.GuardianUpdateDTO;
import com.ai_kids_care.v1.dto.TeacherCreateDTO;
import com.ai_kids_care.v1.dto.TeacherUpdateDTO;
import com.ai_kids_care.v1.dto.UserCreateDTO;
import com.ai_kids_care.v1.dto.UserUpdateDTO;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.entity.Guardian;
import com.ai_kids_care.v1.entity.Teacher;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.mapper.CameraStreamMapper;
import com.ai_kids_care.v1.mapper.ChildMapper;
import com.ai_kids_care.v1.mapper.DeviceTokenMapper;
import com.ai_kids_care.v1.mapper.EventEvidenceFileMapper;
import com.ai_kids_care.v1.mapper.GuardianMapper;
import com.ai_kids_care.v1.mapper.TeacherMapper;
import com.ai_kids_care.v1.mapper.UserMapper;
import com.ai_kids_care.v1.service.CameraStreamService;
import com.ai_kids_care.v1.service.ChildrenService;
import com.ai_kids_care.v1.service.DeviceTokenService;
import com.ai_kids_care.v1.service.EventEvidenceFileService;
import com.ai_kids_care.v1.service.GuardianService;
import com.ai_kids_care.v1.service.TeacherService;
import com.ai_kids_care.v1.service.UserService;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SensitiveWriteContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void genericDtosDoNotAcceptOrExposeSensitiveStorageProperties() {
        assertJacksonPropertyAbsent(UserCreateDTO.class, "passwordHash");
        assertJacksonPropertyAbsent(UserUpdateDTO.class, "passwordHash");
        assertJacksonPropertyAbsent(ChildCreateDTO.class, "rrnEncrypted");
        assertJacksonPropertyAbsent(ChildUpdateDTO.class, "rrnEncrypted");
        assertJacksonPropertyAbsent(GuardianCreateDTO.class, "rrnEncrypted");
        assertJacksonPropertyAbsent(GuardianUpdateDTO.class, "rrnEncrypted");
        assertJacksonPropertyAbsent(TeacherCreateDTO.class, "rrnEncrypted");
        assertJacksonPropertyAbsent(TeacherUpdateDTO.class, "rrnEncrypted");
    }

    @Test
    void eventEvidenceFilePublicWriteDtosAreRemoved() {
        assertClassAbsent("com.ai_kids_care.v1.dto.EventEvidenceFileCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.EventEvidenceFileUpdateDTO");
    }

    @Test
    void deviceTokenPublicWriteDtosAreRemoved() {
        assertClassAbsent("com.ai_kids_care.v1.dto.DeviceTokenCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.DeviceTokenUpdateDTO");
    }

    @Test
    void cameraStreamPublicWriteDtosAreRemoved() {
        assertClassAbsent("com.ai_kids_care.v1.dto.CameraStreamCreateDTO");
        assertClassAbsent("com.ai_kids_care.v1.dto.CameraStreamUpdateDTO");
    }

    @Test
    void genericCreateEndpointsAndSensitiveWriteEndpointsAreClosedWithoutCallingServices() throws Exception {
        UserService userService = mock(UserService.class);
        ChildrenService childrenService = mock(ChildrenService.class);
        GuardianService guardianService = mock(GuardianService.class);
        TeacherService teacherService = mock(TeacherService.class);
        DeviceTokenService deviceTokenService = mock(DeviceTokenService.class);
        EventEvidenceFileService eventEvidenceFileService = mock(EventEvidenceFileService.class);
        CameraStreamService cameraStreamService = mock(CameraStreamService.class);
        MockMvc mockMvc = standaloneSetup(
                new UserController(userService),
                new ChildrenController(childrenService),
                new GuardianController(guardianService),
                new TeacherController(teacherService),
                new DeviceTokenController(deviceTokenService),
                new EventEvidenceFileController(eventEvidenceFileService),
                new CameraStreamController(cameraStreamService)
        ).build();

        mockMvc.perform(post("/api/v1/users").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/children").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/guardians").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/teachers").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/event_evidence_files").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/event_evidence_files/1").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/device_tokens").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/device_tokens/1").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/camera_streams").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/camera_streams/1").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(
                userService,
                childrenService,
                guardianService,
                teacherService,
                deviceTokenService,
                eventEvidenceFileService,
                cameraStreamService
        );
    }

    @Test
    void genericCreateServicesAndSensitiveWriteMappersAreNoLongerExposed() {
        assertMethodAbsent(UserService.class, "createUser");
        assertMethodAbsent(ChildrenService.class, "createChildren");
        assertMethodAbsent(GuardianService.class, "createGuardian");
        assertMethodAbsent(TeacherService.class, "createTeacher");
        assertMethodNameAbsent(DeviceTokenService.class, "createDeviceToken");
        assertMethodNameAbsent(DeviceTokenService.class, "updateDeviceToken");
        assertMethodNameAbsent(EventEvidenceFileService.class, "createEventEvidenceFile");
        assertMethodNameAbsent(EventEvidenceFileService.class, "updateEventEvidenceFile");
        assertMethodNameAbsent(CameraStreamService.class, "createCameraStream");
        assertMethodNameAbsent(CameraStreamService.class, "updateCameraStream");

        assertMethodAbsent(UserMapper.class, "toEntity", UserCreateDTO.class);
        assertMethodAbsent(ChildMapper.class, "toEntity", ChildCreateDTO.class);
        assertMethodAbsent(GuardianMapper.class, "toEntity", GuardianCreateDTO.class);
        assertMethodAbsent(TeacherMapper.class, "toEntity", TeacherCreateDTO.class);
        assertMethodNameAbsent(DeviceTokenMapper.class, "toEntity");
        assertMethodNameAbsent(DeviceTokenMapper.class, "updateEntity");
        assertMethodNameAbsent(EventEvidenceFileMapper.class, "toEntity");
        assertMethodNameAbsent(EventEvidenceFileMapper.class, "updateEntity");
        assertMethodNameAbsent(CameraStreamMapper.class, "toEntity");
        assertMethodNameAbsent(CameraStreamMapper.class, "updateEntity");
    }

    @Test
    void genericUpdateMappersPreserveSensitiveStorageValues() {
        User user = new User();
        user.setEmail("before@example.com");
        user.setPasswordHash("existing-password-hash");
        UserUpdateDTO userUpdate = new UserUpdateDTO();
        userUpdate.setEmail("after@example.com");
        Mappers.getMapper(UserMapper.class).updateEntity(userUpdate, user);

        Child child = new Child();
        child.setName("before");
        child.setRrnEncrypted("existing-child-rrn");
        ChildUpdateDTO childUpdate = new ChildUpdateDTO();
        childUpdate.setName("after");
        Mappers.getMapper(ChildMapper.class).updateEntity(childUpdate, child);

        Guardian guardian = new Guardian();
        guardian.setName("before");
        guardian.setRrnEncrypted("existing-guardian-rrn");
        GuardianUpdateDTO guardianUpdate = new GuardianUpdateDTO();
        guardianUpdate.setName("after");
        Mappers.getMapper(GuardianMapper.class).updateEntity(guardianUpdate, guardian);

        Teacher teacher = new Teacher();
        teacher.setName("before");
        teacher.setRrnEncrypted("existing-teacher-rrn");
        TeacherUpdateDTO teacherUpdate = new TeacherUpdateDTO();
        teacherUpdate.setName("after");
        Mappers.getMapper(TeacherMapper.class).updateEntity(teacherUpdate, teacher);

        assertThat(user.getEmail()).isEqualTo("after@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("existing-password-hash");
        assertThat(child.getName()).isEqualTo("after");
        assertThat(child.getRrnEncrypted()).isEqualTo("existing-child-rrn");
        assertThat(guardian.getName()).isEqualTo("after");
        assertThat(guardian.getRrnEncrypted()).isEqualTo("existing-guardian-rrn");
        assertThat(teacher.getName()).isEqualTo("after");
        assertThat(teacher.getRrnEncrypted()).isEqualTo("existing-teacher-rrn");
    }

    private void assertJacksonPropertyAbsent(Class<?> dtoType, String propertyName) {
        assertThat(propertyNames(objectMapper.getSerializationConfig().introspect(
                objectMapper.constructType(dtoType)))).doesNotContain(propertyName);
        assertThat(propertyNames(objectMapper.getDeserializationConfig().introspect(
                objectMapper.constructType(dtoType)))).doesNotContain(propertyName);
    }

    private Set<String> propertyNames(BeanDescription description) {
        return description.findProperties().stream()
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toSet());
    }

    private void assertMethodAbsent(Class<?> ownerType, String methodName, Class<?>... parameterTypes) {
        assertThat(findMethod(ownerType, methodName, parameterTypes))
                .as("%s must not expose %s", ownerType.getSimpleName(), methodName)
                .isEmpty();
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

    private java.util.Optional<Method> findMethod(Class<?> ownerType, String methodName, Class<?>... parameterTypes) {
        try {
            return java.util.Optional.of(ownerType.getDeclaredMethod(methodName, parameterTypes));
        } catch (NoSuchMethodException ignored) {
            return java.util.Optional.empty();
        }
    }
}
