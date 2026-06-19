package com.ai_kids_care.v1.contract;

import com.ai_kids_care.v1.controller.CameraStreamController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.neo4j.driver.Driver;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PublishedOpenApiContractTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("openapi-contract")
class PublishedOpenApiContractTest {

    private static final String CONTROLLER_BASE_PACKAGE = "com.ai_kids_care.v1.controller";

    private static final Set<String> NORMALIZED_SENSITIVE_SCHEMA_PROPERTY_DENYLIST = Set.of(
            "passwordhash",
            "rrnencrypted",
            "rrnhash",
            "pushtoken",
            "storageuri",
            "sourceurl",
            "streamuser",
            "streampassword",
            "streampasswordencrypted",
            "streampasswordciphertext",
            "streampasswordiv",
            "streampasswordkeyversion",
            "camerapasswordciphertext",
            "camerapasswordiv",
            "camerapasswordkeyversion",
            "credentialciphertext",
            "credentialiv",
            "credentialkeyversion",
            "streamcredentialciphertext",
            "streamcredentialiv",
            "streamcredentialkeyversion",
            "cameracredentialciphertext",
            "cameracredentialiv",
            "cameracredentialkeyversion",
            "ciphertext",
            "iv",
            "keyversion"
    );

    private static final Set<String> NORMALIZED_RESTRICTED_S1_SCHEMA_PROPERTIES = Set.of(
            "email",
            "phone",
            "address",
            "birthdate",
            "rrnfirst6",
            "rrnback7",
            "childrrnfirst6",
            "childrrnback7",
            "emergencycontactname",
            "emergencycontactphone",
            "staffno",
            "relationship",
            "businessregistrationno",
            "contactname",
            "contactphone",
            "contactemail",
            "ip",
            "useragent",
            "dedupekey",
            "failreason"
    );

    private static final Map<String, Set<String>> RESTRICTED_S1_COMMAND_ALLOWLIST = Map.of(
            "AuthRegisterDTO", Set.of(
                    "email",
                    "phone",
                    "address",
                    "rrnfirst6",
                    "rrnback7",
                    "childrrnfirst6",
                    "childrrnback7",
                    "emergencycontactname",
                    "emergencycontactphone",
                    "staffno",
                    "relationship"
            ),
            "GuardianChildVerificationRequest", Set.of(
                    "childrrnfirst6",
                    "childrrnback7"
            )
    );

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private ApplicationContext applicationContext;

    @org.springframework.beans.factory.annotation.Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void publishedOpenApiDoesNotExposeRemovedSensitiveFieldsOrClosedEndpoints() throws Exception {
        JsonNode apiDocs = readApiDocs();

        assertComponentAbsent(apiDocs, "UserVO");
        assertComponentAbsent(apiDocs, "ChildVO");
        // SPEC-0001 §349：Guardian 关系-scoped 读取只发布最小 GuardianChildVO（无 RRN/address/birth_date/childNo）。
        assertComponentHasOnlyProperties(apiDocs, "GuardianChildVO", Set.of(
                "childId", "name", "status"
        ));
        assertComponentAbsent(apiDocs, "GuardianVO");
        assertComponentAbsent(apiDocs, "TeacherVO");
        assertComponentHasOnlyProperties(apiDocs, "KindergartenVO", Set.of(
                "kindergartenId", "name", "regionCode", "code", "status", "createdAt", "updatedAt"
        ));
        assertComponentHasOnlyProperties(apiDocs, "KindergartenLookupResponse", Set.of(
                "kindergartenId", "name", "regionCode", "code", "status"
        ));
        assertComponentAbsent(apiDocs, "DeviceTokenVO");
        assertComponentAbsent(apiDocs, "EventEvidenceFileVO");
        assertComponentAbsent(apiDocs, "DetectionEventVO");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "sourceUrl");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamUser");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPassword");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPasswordEncrypted");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPasswordCiphertext");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPasswordIv");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPasswordKeyVersion");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "playbackUrl");
        assertComponentPropertyPresent(apiDocs, "CameraStreamVO", "hasPassword");
        assertComponentPropertyPresent(apiDocs, "CameraStreamVO", "playbackProtocol");
        assertComponentPropertyAbsent(apiDocs, "AuthRegisterDTO", "status");
        assertComponentPropertyAbsent(apiDocs, "AuthRegisterDTO", "scopeType");
        assertComponentPropertyAbsent(apiDocs, "AuthRegisterDTO", "scopeId");
        assertComponentPropertyAbsent(apiDocs, "AuthRegisterDTO", "childId");
        assertComponentHasOnlyProperties(apiDocs, "AuthRegisterDTO", Set.of(
                "userRole",
                "loginId",
                "email",
                "phone",
                "password",
                "kindergartenId",
                "name",
                "rrnFirst6",
                "rrnBack7",
                "gender",
                "address",
                "childRrnFirst6",
                "childRrnBack7",
                "relationship",
                "primaryGuardian",
                "emergencyContactName",
                "emergencyContactPhone",
                "level",
                "staffNo",
                "department"
        ));
        assertComponentPropertyPresent(apiDocs, "AuthRegisterDTO", "userRole");
        assertComponentRequiredProperty(apiDocs, "AuthRegisterDTO", "userRole");
        assertComponentRequiredProperty(apiDocs, "AuthRegisterDTO", "loginId");
        assertComponentRequiredProperty(apiDocs, "AuthRegisterDTO", "email");
        assertComponentRequiredProperty(apiDocs, "AuthRegisterDTO", "phone");
        assertComponentRequiredProperty(apiDocs, "AuthRegisterDTO", "password");
        assertComponentRequiredProperty(apiDocs, "AuthRegisterDTO", "name");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "kindergartenId");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "rrnFirst6");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "rrnBack7");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "gender");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "address");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "childRrnFirst6");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "childRrnBack7");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "relationship");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "primaryGuardian");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "emergencyContactName");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "emergencyContactPhone");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "level");
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "staffNo");
        assertComponentHasOnlyProperties(apiDocs, "AuthSessionVO", Set.of(
                "userId", "loginId", "effectiveRole", "scopeType", "scopeId", "name"
        ));
        assertComponentOptionalProperty(apiDocs, "AuthSessionVO", "name");
        assertComponentHasOnlyProperties(apiDocs, "TenantContextRequest", Set.of(
                "kindergartenId"
        ));
        assertComponentHasOnlyProperties(apiDocs, "TenantContextVO", Set.of(
                "selectedKindergartenId"
        ));
        assertComponentOptionalProperty(apiDocs, "AuthRegisterDTO", "department");
        assertComponentRequiredProperty(apiDocs, "GuardianChildVerificationRequest", "childRrnFirst6");
        assertComponentRequiredProperty(apiDocs, "GuardianChildVerificationRequest", "childRrnBack7");
        assertComponentHasOnlyProperties(apiDocs, "GuardianChildVerificationRequest", Set.of(
                "childRrnFirst6",
                "childRrnBack7"
        ));
        assertComponentPropertyPresent(apiDocs, "GuardianChildVerificationResponse", "verified");
        assertComponentHasOnlyProperties(apiDocs, "GuardianChildVerificationResponse", Set.of("verified"));

        assertComponentAbsent(apiDocs, "DeviceTokenCreateDTO");
        assertComponentAbsent(apiDocs, "DeviceTokenUpdateDTO");
        assertComponentAbsent(apiDocs, "EventEvidenceFileCreateDTO");
        assertComponentAbsent(apiDocs, "EventEvidenceFileUpdateDTO");
        assertComponentAbsent(apiDocs, "CameraStreamCreateDTO");
        assertComponentAbsent(apiDocs, "CameraStreamUpdateDTO");
        assertComponentAbsent(apiDocs, "UserCreateDTO");
        assertComponentAbsent(apiDocs, "UserUpdateDTO");
        assertComponentAbsent(apiDocs, "ChildCreateDTO");
        assertComponentAbsent(apiDocs, "ChildUpdateDTO");
        assertComponentAbsent(apiDocs, "GuardianCreateDTO");
        assertComponentAbsent(apiDocs, "GuardianUpdateDTO");
        assertComponentAbsent(apiDocs, "TeacherCreateDTO");
        assertComponentAbsent(apiDocs, "TeacherUpdateDTO");
        assertComponentAbsent(apiDocs, "DetectionEventCreateDTO");
        assertComponentAbsent(apiDocs, "DetectionEventUpdateDTO");
        assertComponentAbsent(apiDocs, "DetectionSessionCreateDTO");
        assertComponentAbsent(apiDocs, "DetectionSessionUpdateDTO");
        assertComponentAbsent(apiDocs, "EventReviewCreateDTO");
        assertComponentAbsent(apiDocs, "EventReviewUpdateDTO");
        assertComponentAbsent(apiDocs, "CctvCameraCreateDTO");
        assertComponentAbsent(apiDocs, "CctvCameraUpdateDTO");
        assertComponentAbsent(apiDocs, "NotificationRuleCreateDTO");
        assertComponentAbsent(apiDocs, "NotificationRuleUpdateDTO");
        assertComponentAbsent(apiDocs, "SuperadminCreateDTO");
        assertComponentAbsent(apiDocs, "SuperadminUpdateDTO");
        assertComponentAbsent(apiDocs, "AppreciationLetterCreateDTO");
        assertComponentAbsent(apiDocs, "AppreciationLetterUpdateDTO");
        assertComponentAbsent(apiDocs, "KindergartenCreateDTO");
        assertComponentAbsent(apiDocs, "KindergartenUpdateDTO");
        assertComponentAbsent(apiDocs, "ChildGraphVO");
        assertComponentAbsent(apiDocs, "AuditLogVO");
        assertComponentAbsent(apiDocs, "NotificationVO");
        // SPEC-0001 / ADR-0018 A3d：通知读取只发布最小 NotificationReadVO（无 channel/dedupeKey/sentAt/failReason/retryCount/recipientUserId/kindergartenId）。
        assertComponentHasOnlyProperties(apiDocs, "NotificationReadVO", Set.of(
                "notificationId", "title", "body", "status", "createdAt"
        ));
        assertComponentAbsent(apiDocs, "NotificationCreateDTO");
        assertComponentAbsent(apiDocs, "NotificationUpdateDTO");
        // AN-READ：AnnouncementVO 平台级发布，无 S0/S1；authorId 已删除（BE-3b）；锁定允许字段集。
        assertComponentHasOnlyProperties(apiDocs, "AnnouncementVO", Set.of(
                "id", "title", "body", "isPinned", "pinnedUntil",
                "status", "publishedAt", "startsAt", "endsAt",
                "viewCount", "createdAt", "updatedAt", "deletedAt"
        ));
        assertOperationPresent(apiDocs, "/api/v1/announcements", "get");
        assertOperationPresent(apiDocs, "/api/v1/announcements/{id}", "get");
        assertOperationPresent(apiDocs, "/api/v1/announcements", "post");
        assertOperationPresent(apiDocs, "/api/v1/announcements/{id}", "put");
        assertOperationPresent(apiDocs, "/api/v1/announcements/{id}", "delete");
        assertComponentAbsent(apiDocs, "AuthLogoutDTO");
        assertComponentAbsent(apiDocs, "ChangePasswordRequest");
        assertComponentAbsent(apiDocs, "AuthPasswordResetDTO");
        assertComponentAbsent(apiDocs, "AuthPasswordResetsVO");
        assertComponentAbsent(apiDocs, "ResetPasswordRequest");
        assertComponentAbsent(apiDocs, "VerificationCodeCreateRequest");
        assertComponentAbsent(apiDocs, "VerificationCodeCreateVO");
        assertComponentAbsent(apiDocs, "VerifyVerificationCodeRequest");
        assertComponentAbsent(apiDocs, "VerifyVerificationCodeVO");

        assertPathAbsent(apiDocs, "/api/v1/users");
        assertPathAbsent(apiDocs, "/api/v1/users/{id}");
        // SPEC-0001 §349：children GET 已重开（Guardian 关系-scoped，返回最小 GuardianChildVO）。
        // 完整 ChildVO 仍不发布（见上 assertComponentAbsent("ChildVO")）；/children/rrn 仍关闭（见下）。
        assertOperationPresent(apiDocs, "/api/v1/children", "get");
        assertOperationPresent(apiDocs, "/api/v1/children/{id}", "get");
        assertPathAbsent(apiDocs, "/api/v1/guardians");
        assertPathAbsent(apiDocs, "/api/v1/guardians/{id}");
        assertPathAbsent(apiDocs, "/api/v1/teachers");
        assertPathAbsent(apiDocs, "/api/v1/teachers/{id}");
        assertPathAbsent(apiDocs, "/api/v1/children/rrn");
        assertOperationPresent(apiDocs, "/api/v1/auth/guardian-child-verifications", "post");
        assertOperationPresent(apiDocs, "/api/v1/auth/login", "post");
        assertOperationPresent(apiDocs, "/api/v1/auth/session", "get");
        assertOperationPresent(apiDocs, "/api/v1/auth/session/tenant-context", "post");
        assertOperationPresent(apiDocs, "/api/v1/auth/logout", "post");
        assertOperationPresent(apiDocs, "/api/v1/auth/csrf", "get");
        assertPathAbsent(apiDocs, "/api/v1/auth/refresh");
        assertPathAbsent(apiDocs, "/api/v1/auth/password");
        assertPathAbsent(apiDocs, "/api/v1/auth/password-resets");
        assertPathAbsent(apiDocs, "/api/v1/auth/password-resets/{resetToken}");
        assertPathAbsent(apiDocs, "/api/v1/auth/verification-codes");
        assertPathAbsent(apiDocs, "/api/v1/auth/verification-codes/{challengeId}/verifications");

        assertPathAbsent(apiDocs, "/api/v1/device_tokens");
        assertPathAbsent(apiDocs, "/api/v1/device_tokens/{id}");
        assertPathAbsent(apiDocs, "/api/v1/event_evidence_files");
        assertPathAbsent(apiDocs, "/api/v1/event_evidence_files/{id}");

        assertOperationPresent(apiDocs, "/api/v1/camera_streams", "get");
        assertOperationPresent(apiDocs, "/api/v1/camera_streams/{id}", "get");
        // ADR-0026 Phase 1：camera_streams POST/PUT 已合法发布。
        assertOperationPresent(apiDocs, "/api/v1/camera_streams", "post");
        assertOperationPresent(apiDocs, "/api/v1/camera_streams/{id}", "put");
        assertOperationAbsent(apiDocs, "/api/v1/camera_streams/{id}", "delete");

        assertPathAbsent(apiDocs, "/api/v1/detection_events");
        assertPathAbsent(apiDocs, "/api/v1/detection_events/{id}");

        assertOperationPresent(apiDocs, "/api/v1/detection_sessions", "get");
        assertOperationPresent(apiDocs, "/api/v1/detection_sessions/{id}", "get");
        assertOperationAbsent(apiDocs, "/api/v1/detection_sessions", "post");
        assertOperationAbsent(apiDocs, "/api/v1/detection_sessions/{id}", "put");
        assertOperationAbsent(apiDocs, "/api/v1/detection_sessions/{id}", "delete");

        assertOperationPresent(apiDocs, "/api/v1/cctv_cameras", "get");
        assertOperationPresent(apiDocs, "/api/v1/cctv_cameras/{id}", "get");
        assertOperationAbsent(apiDocs, "/api/v1/cctv_cameras", "post");
        assertOperationAbsent(apiDocs, "/api/v1/cctv_cameras/{id}", "put");
        assertOperationAbsent(apiDocs, "/api/v1/cctv_cameras/{id}", "delete");

        assertOperationPresent(apiDocs, "/api/v1/kindergartens", "get");
        assertOperationPresent(apiDocs, "/api/v1/kindergartens/{id}", "get");
        assertOperationPresent(apiDocs,
                "/api/v1/kindergartens/business-registration-no/{businessRegistrationNo}",
                "get");
        assertOperationAbsent(apiDocs, "/api/v1/kindergartens", "post");
        assertOperationAbsent(apiDocs, "/api/v1/kindergartens/{id}", "put");
        assertOperationAbsent(apiDocs, "/api/v1/kindergartens/{id}", "delete");

        assertPathAbsent(apiDocs, "/api/v1/graph/children/{childId}");
        assertPathAbsent(apiDocs, "/api/v1/audit_logs");
        assertPathAbsent(apiDocs, "/api/v1/audit_logs/{id}");
        // SPEC-0001 / ADR-0018 A3d：notifications GET 已重开（tenant-scoped，返回最小 NotificationReadVO）。
        // 完整 NotificationVO 仍不发布（见上 assertComponentAbsent("NotificationVO")）；写操作仍关闭 → 405。
        assertOperationPresent(apiDocs, "/api/v1/notifications", "get");
        assertOperationPresent(apiDocs, "/api/v1/notifications/{id}", "get");
        assertPathAbsent(apiDocs, "/api/v1/event_reviews");
        assertPathAbsent(apiDocs, "/api/v1/event_reviews/{id}");
        assertPathAbsent(apiDocs, "/api/v1/event_reviews/{eventId}/latest");
        assertPathAbsent(apiDocs, "/api/v1/notification_rules");
        assertPathAbsent(apiDocs, "/api/v1/notification_rules/{id}");
        assertPathAbsent(apiDocs, "/api/v1/superadmins");
        assertPathAbsent(apiDocs, "/api/v1/superadmins/{id}");
        assertPathAbsent(apiDocs, "/api/v1/appreciation_letters");
        assertPathAbsent(apiDocs, "/api/v1/appreciation_letters/{id}");
    }

    @Test
    void openApiTestContextExplicitlyIncludesCameraStreamController() {
        assertThat(applicationContext.getBeansOfType(CameraStreamController.class))
                .as("OpenAPI contract test context must include CameraStreamController")
                .hasSize(1);
    }

    @Test
    void publishedOpenApiScansEveryPublishedSchemaForSensitiveProperties() throws Exception {
        JsonNode apiDocs = readApiDocs();
        JsonNode schemas = apiDocs.path("components").path("schemas");

        assertThat(schemas.isObject())
                .as("Expected OpenAPI component schemas to be published")
                .isTrue();
        assertThat(schemas.size())
                .as("Expected the published /v3/api-docs document to contain component schemas")
                .isGreaterThan(0);

        assertThat(collectPropertyNames(apiDocs))
                .as("Published component and inline schemas must not expose denied sensitive properties")
                .noneMatch(this::isDeniedSensitiveProperty);
    }

    @Test
    void restrictedS1PropertiesOnlyAppearInApprovedDedicatedCommandSchemas() throws Exception {
        JsonNode schemas = readApiDocs().path("components").path("schemas");

        schemas.fields().forEachRemaining(schemaEntry -> {
            String schemaName = schemaEntry.getKey();
            Set<String> allowedProperties = RESTRICTED_S1_COMMAND_ALLOWLIST
                    .getOrDefault(schemaName, Set.of());
            JsonNode properties = schemaEntry.getValue().path("properties");
            if (!properties.isObject()) {
                return;
            }

            properties.fieldNames().forEachRemaining(propertyName -> {
                String normalized = normalizePropertyName(propertyName);
                if (!NORMALIZED_RESTRICTED_S1_SCHEMA_PROPERTIES.contains(normalized)) {
                    return;
                }

                assertThat(allowedProperties)
                        .as("%s must not publish restricted S1 property %s", schemaName, propertyName)
                        .contains(normalized);
            });
        });
    }

    @Test
    void sensitivePropertyDenylistRejectsGenericStorageRepresentationNames() {
        assertThat(List.of("ciphertext", "iv", "keyVersion", "key_version"))
                .allMatch(this::isDeniedSensitiveProperty);
    }

    @Test
    void sensitivePropertyScanIncludesInlinePathSchemas() throws Exception {
        JsonNode inlineSchemaDocument = objectMapper.readTree("""
                {
                  "paths": {
                    "/api/v1/example": {
                      "post": {
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "ciphertext": {
                                    "type": "string"
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """);

        assertThat(collectPropertyNames(inlineSchemaDocument))
                .anyMatch(this::isDeniedSensitiveProperty);
    }

    @Test
    void openApiTestContextRegistersAllRestControllersUnderV1Package() {
        Set<Class<?>> expectedControllers = discoverRestControllerClasses();
        Set<Class<?>> actualControllers = applicationContext.getBeansWithAnnotation(RestController.class)
                .values()
                .stream()
                .map(ClassUtils::getUserClass)
                .filter(controllerClass -> controllerClass.getPackageName().startsWith(CONTROLLER_BASE_PACKAGE))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(actualControllers)
                .as("OpenAPI contract test context must include every v1 RestController so newly added resources cannot bypass schema checks")
                .containsExactlyInAnyOrderElementsOf(expectedControllers);
    }

    @Test
    void publishedOpenApiIncludesEveryV1ControllerOperation() throws Exception {
        JsonNode apiDocs = readApiDocs();
        Set<PublishedOperation> expectedOperations = discoverV1ControllerOperations();

        assertThat(expectedOperations)
                .as("Expected Spring MVC to publish v1 controller operations")
                .isNotEmpty();
        expectedOperations.forEach(operation ->
                assertOperationPresent(apiDocs, operation.path(), operation.httpMethod()));
    }

    @Test
    void openApiTestContextDoesNotStartDatasourceJpaFlywayOrNeo4jInfrastructure() {
        assertThat(applicationContext.getBeansOfType(DataSource.class))
                .as("OpenAPI contract test must not start a JDBC datasource or Testcontainers-backed database")
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(LocalContainerEntityManagerFactoryBean.class))
                .as("OpenAPI contract test must not bootstrap JPA")
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(org.flywaydb.core.Flyway.class))
                .as("OpenAPI contract test must not start Flyway migrations")
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(Driver.class))
                .as("OpenAPI contract test must not start Neo4j infrastructure")
                .isEmpty();
    }

    private JsonNode readApiDocs() throws Exception {
        JsonNode apiDocs = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(apiDocs.path("openapi").asText())
                .as("Expected a real /v3/api-docs document")
                .isNotBlank();
        assertThat(apiDocs.path("paths").isObject())
                .as("Expected published OpenAPI paths to be present")
                .isTrue();
        assertThat(apiDocs.path("paths").size())
                .as("Expected published OpenAPI to contain at least one path")
                .isGreaterThan(0);

        return apiDocs;
    }

    private void assertComponentPropertyAbsent(JsonNode apiDocs, String schemaName, String propertyName) {
        JsonNode schemaProperties = apiDocs.path("components").path("schemas").path(schemaName).path("properties");
        assertThat(schemaProperties.isObject())
                .as("Expected component schema %s to be published", schemaName)
                .isTrue();
        assertThat(schemaProperties.has(propertyName))
                .as("%s schema must not expose %s", schemaName, propertyName)
                .isFalse();
    }

    private void assertComponentPropertyPresent(JsonNode apiDocs, String schemaName, String propertyName) {
        JsonNode schemaProperties = apiDocs.path("components").path("schemas").path(schemaName).path("properties");
        assertThat(schemaProperties.isObject())
                .as("Expected component schema %s to be published", schemaName)
                .isTrue();
        assertThat(schemaProperties.has(propertyName))
                .as("%s schema must continue publishing %s", schemaName, propertyName)
                .isTrue();
    }

    private void assertComponentRequiredProperty(JsonNode apiDocs, String schemaName, String propertyName) {
        JsonNode requiredProperties = apiDocs.path("components").path("schemas").path(schemaName).path("required");
        assertThat(requiredProperties.isArray())
                .as("Expected component schema %s to publish required properties", schemaName)
                .isTrue();
        assertThat(requiredProperties)
                .extracting(JsonNode::asText)
                .as("%s schema must require %s", schemaName, propertyName)
                .contains(propertyName);
    }

    private void assertComponentOptionalProperty(JsonNode apiDocs, String schemaName, String propertyName) {
        assertComponentPropertyPresent(apiDocs, schemaName, propertyName);
        JsonNode requiredProperties = apiDocs.path("components").path("schemas").path(schemaName).path("required");
        assertThat(requiredProperties)
                .extracting(JsonNode::asText)
                .as("%s schema must not globally require role-specific property %s", schemaName, propertyName)
                .doesNotContain(propertyName);
    }

    private void assertComponentHasOnlyProperties(JsonNode apiDocs, String schemaName, Set<String> expectedProperties) {
        JsonNode properties = apiDocs.path("components").path("schemas").path(schemaName).path("properties");
        assertThat(properties.isObject())
                .as("Expected component schema %s to publish properties", schemaName)
                .isTrue();
        Set<String> actualProperties = new LinkedHashSet<>();
        properties.fieldNames().forEachRemaining(actualProperties::add);
        assertThat(actualProperties)
                .as("%s schema must expose only the approved properties", schemaName)
                .isEqualTo(expectedProperties);
    }

    private void assertComponentAbsent(JsonNode apiDocs, String schemaName) {
        JsonNode schemas = apiDocs.path("components").path("schemas");
        assertThat(schemas.isObject())
                .as("Expected OpenAPI component schemas to be published before checking %s", schemaName)
                .isTrue();
        assertThat(schemas.has(schemaName))
                .as("Component schema %s must not be published", schemaName)
                .isFalse();
    }

    private void assertOperationPresent(JsonNode apiDocs, String path, String httpMethod) {
        JsonNode pathItem = apiDocs.path("paths").path(path);
        assertThat(pathItem.isObject())
                .as("Expected OpenAPI path %s to be published", path)
                .isTrue();
        assertThat(pathItem.has(httpMethod))
                .as("Expected %s %s to be published", httpMethod.toUpperCase(), path)
                .isTrue();
    }

    private void assertOperationAbsent(JsonNode apiDocs, String path, String httpMethod) {
        JsonNode pathItem = apiDocs.path("paths").path(path);
        assertThat(pathItem.isObject())
                .as("Expected OpenAPI path %s to be published before checking removed methods", path)
                .isTrue();
        assertThat(pathItem.has(httpMethod))
                .as("%s %s must not be published", httpMethod.toUpperCase(), path)
                .isFalse();
    }

    private void assertRequiredQueryParameter(
            JsonNode apiDocs,
            String path,
            String httpMethod,
            String parameterName
    ) {
        JsonNode parameters = apiDocs.path("paths").path(path).path(httpMethod).path("parameters");
        assertThat(parameters.isArray())
                .as("Expected %s %s to publish parameters", httpMethod.toUpperCase(), path)
                .isTrue();
        assertThat(parameters)
                .as("%s %s must require query parameter %s", httpMethod.toUpperCase(), path, parameterName)
                .anyMatch(parameter ->
                        parameterName.equals(parameter.path("name").asText())
                                && "query".equals(parameter.path("in").asText())
                                && parameter.path("required").asBoolean());
    }

    private void assertPathAbsent(JsonNode apiDocs, String path) {
        assertThat(apiDocs.path("paths").has(path))
                .as("OpenAPI path %s must not be published", path)
                .isFalse();
    }

    private Set<String> collectPropertyNames(JsonNode schemaNode) {
        Set<String> propertyNames = new LinkedHashSet<>();
        collectPropertyNames(schemaNode, propertyNames);
        return propertyNames;
    }

    private boolean isDeniedSensitiveProperty(String propertyName) {
        String normalized = normalizePropertyName(propertyName);
        if (NORMALIZED_SENSITIVE_SCHEMA_PROPERTY_DENYLIST.contains(normalized)) {
            return true;
        }

        boolean cameraCredentialField = normalized.contains("camera")
                || normalized.contains("stream")
                || normalized.contains("credential");
        return cameraCredentialField
                && (normalized.contains("ciphertext")
                || normalized.endsWith("iv")
                || normalized.contains("keyversion"));
    }

    private String normalizePropertyName(String propertyName) {
        return propertyName.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private void collectPropertyNames(JsonNode schemaNode, Set<String> propertyNames) {
        if (schemaNode == null || schemaNode.isMissingNode() || schemaNode.isNull()) {
            return;
        }

        if (schemaNode.isArray()) {
            schemaNode.forEach(child -> collectPropertyNames(child, propertyNames));
            return;
        }

        if (!schemaNode.isObject()) {
            return;
        }

        schemaNode.fields().forEachRemaining(field -> {
            if ("properties".equals(field.getKey()) && field.getValue().isObject()) {
                field.getValue().fieldNames().forEachRemaining(propertyNames::add);
            }
            collectPropertyNames(field.getValue(), propertyNames);
        });
    }

    private Set<Class<?>> discoverRestControllerClasses() {
        org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider scanner =
                new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<Class<?>> controllers = new LinkedHashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(CONTROLLER_BASE_PACKAGE)) {
            controllers.add(ClassUtils.resolveClassName(candidate.getBeanClassName(), ClassUtils.getDefaultClassLoader()));
        }
        return controllers;
    }

    private Set<PublishedOperation> discoverV1ControllerOperations() {
        Set<PublishedOperation> operations = new LinkedHashSet<>();

        requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            Class<?> controllerClass = ClassUtils.getUserClass(handlerMethod.getBeanType());
            if (!controllerClass.getPackageName().startsWith(CONTROLLER_BASE_PACKAGE)
                    || !controllerClass.isAnnotationPresent(RestController.class)) {
                return;
            }

            Set<RequestMethod> requestMethods = mapping.getMethodsCondition().getMethods();
            assertThat(requestMethods)
                    .as("Every v1 controller mapping must declare an HTTP method: %s", mapping)
                    .isNotEmpty();

            mapping.getPatternValues().stream()
                    .filter(path -> path.startsWith("/api/v1/"))
                    .forEach(path -> requestMethods.forEach(requestMethod ->
                            operations.add(new PublishedOperation(
                                    path,
                                    requestMethod.name().toLowerCase(Locale.ROOT)
                            ))));
        });

        return operations;
    }

    private record PublishedOperation(String path, String httpMethod) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
            SessionAutoConfiguration.class
    })
    @ComponentScan(
            basePackages = "com.ai_kids_care.v1.controller",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class)
    )
    @Import(OpenApiControllerDependencyMockConfig.class)
    static class TestApplication {
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class OpenApiControllerDependencyMockConfig {

        @org.springframework.context.annotation.Bean
        static BeanDefinitionRegistryPostProcessor controllerDependencyMockRegistrar() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    Set<Class<?>> dependencyTypes = new LinkedHashSet<>();

                    for (String beanName : registry.getBeanDefinitionNames()) {
                        BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
                        String beanClassName = beanDefinition.getBeanClassName();
                        if (beanClassName == null) {
                            continue;
                        }

                        Class<?> beanClass = ClassUtils.resolveClassName(beanClassName, ClassUtils.getDefaultClassLoader());
                        if (!beanClass.getPackageName().startsWith(CONTROLLER_BASE_PACKAGE) || !beanClass.isAnnotationPresent(RestController.class)) {
                            continue;
                        }

                        Constructor<?> constructor = BeanUtils.getResolvableConstructor(beanClass);
                        for (Class<?> parameterType : constructor.getParameterTypes()) {
                            dependencyTypes.add(parameterType);
                        }
                    }

                    for (Class<?> dependencyType : dependencyTypes) {
                        String mockBeanName = dependencyType.getName() + "#openApiContractMock";
                        if (registry.containsBeanDefinition(mockBeanName)) {
                            continue;
                        }

                        RootBeanDefinition mockDefinition = new RootBeanDefinition(dependencyType);
                        mockDefinition.setInstanceSupplier(() -> Mockito.mock(dependencyType));
                        registry.registerBeanDefinition(mockBeanName, mockDefinition);
                    }
                }

                @Override
                public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                    // no-op
                }
            };
        }
    }
}
