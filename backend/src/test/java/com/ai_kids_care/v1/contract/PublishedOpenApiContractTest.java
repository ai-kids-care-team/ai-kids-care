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
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
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

        assertComponentPropertyAbsent(apiDocs, "UserVO", "passwordHash");
        assertComponentPropertyAbsent(apiDocs, "ChildVO", "rrnEncrypted");
        assertComponentPropertyAbsent(apiDocs, "GuardianVO", "rrnEncrypted");
        assertComponentPropertyAbsent(apiDocs, "TeacherVO", "rrnEncrypted");
        assertComponentPropertyAbsent(apiDocs, "DeviceTokenVO", "pushToken");
        assertComponentPropertyAbsent(apiDocs, "EventEvidenceFileVO", "storageUri");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "sourceUrl");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamUser");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPassword");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPasswordEncrypted");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPasswordCiphertext");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPasswordIv");
        assertComponentPropertyAbsent(apiDocs, "CameraStreamVO", "streamPasswordKeyVersion");
        assertComponentPropertyPresent(apiDocs, "CameraStreamVO", "hasPassword");
        assertComponentPropertyPresent(apiDocs, "CameraStreamVO", "playbackUrl");
        assertComponentPropertyPresent(apiDocs, "CameraStreamVO", "playbackProtocol");

        assertComponentAbsent(apiDocs, "DeviceTokenCreateDTO");
        assertComponentAbsent(apiDocs, "DeviceTokenUpdateDTO");
        assertComponentAbsent(apiDocs, "EventEvidenceFileCreateDTO");
        assertComponentAbsent(apiDocs, "EventEvidenceFileUpdateDTO");
        assertComponentAbsent(apiDocs, "CameraStreamCreateDTO");
        assertComponentAbsent(apiDocs, "CameraStreamUpdateDTO");

        assertOperationAbsent(apiDocs, "/api/v1/users", "post");
        assertOperationAbsent(apiDocs, "/api/v1/children", "post");
        assertOperationAbsent(apiDocs, "/api/v1/guardians", "post");
        assertOperationAbsent(apiDocs, "/api/v1/teachers", "post");

        assertOperationPresent(apiDocs, "/api/v1/device_tokens", "get");
        assertOperationPresent(apiDocs, "/api/v1/device_tokens/{id}", "get");
        assertOperationPresent(apiDocs, "/api/v1/device_tokens/{id}", "delete");
        assertOperationAbsent(apiDocs, "/api/v1/device_tokens", "post");
        assertOperationAbsent(apiDocs, "/api/v1/device_tokens/{id}", "put");

        assertOperationPresent(apiDocs, "/api/v1/event_evidence_files", "get");
        assertOperationPresent(apiDocs, "/api/v1/event_evidence_files/{id}", "get");
        assertOperationPresent(apiDocs, "/api/v1/event_evidence_files/{id}", "delete");
        assertOperationAbsent(apiDocs, "/api/v1/event_evidence_files", "post");
        assertOperationAbsent(apiDocs, "/api/v1/event_evidence_files/{id}", "put");

        assertOperationPresent(apiDocs, "/api/v1/camera_streams", "get");
        assertOperationPresent(apiDocs, "/api/v1/camera_streams/{id}", "get");
        assertOperationPresent(apiDocs, "/api/v1/camera_streams/{id}", "delete");
        assertOperationAbsent(apiDocs, "/api/v1/camera_streams", "post");
        assertOperationAbsent(apiDocs, "/api/v1/camera_streams/{id}", "put");

        assertOperationPresent(apiDocs, "/api/v1/audit_logs", "get");
        assertOperationPresent(apiDocs, "/api/v1/audit_logs/{id}", "get");
        assertOperationAbsent(apiDocs, "/api/v1/audit_logs", "post");
        assertOperationAbsent(apiDocs, "/api/v1/audit_logs/{id}", "post");
        assertOperationAbsent(apiDocs, "/api/v1/audit_logs/{id}", "put");
        assertOperationAbsent(apiDocs, "/api/v1/audit_logs/{id}", "delete");
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

    private Set<String> collectPropertyNames(JsonNode schemaNode) {
        Set<String> propertyNames = new LinkedHashSet<>();
        collectPropertyNames(schemaNode, propertyNames);
        return propertyNames;
    }

    private boolean isDeniedSensitiveProperty(String propertyName) {
        String normalized = propertyName.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
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
            FlywayAutoConfiguration.class
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
