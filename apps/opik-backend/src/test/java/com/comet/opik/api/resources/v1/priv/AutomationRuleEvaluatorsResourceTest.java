package com.comet.opik.api.resources.v1.priv;

import com.comet.opik.api.AutomationRuleEvaluator;
import com.comet.opik.api.resources.utils.AuthTestUtils;
import com.comet.opik.api.resources.utils.ClientSupportUtils;
import com.comet.opik.api.resources.utils.MigrationUtils;
import com.comet.opik.api.resources.utils.MySQLContainerUtils;
import com.comet.opik.api.resources.utils.RedisContainerUtils;
import com.comet.opik.api.resources.utils.TestDropwizardAppExtensionUtils;
import com.comet.opik.api.resources.utils.TestUtils;
import com.comet.opik.api.resources.utils.WireMockUtils;
import com.comet.opik.podam.PodamFactoryUtils;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.redis.testcontainers.RedisContainer;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;
import ru.vyarus.dropwizard.guice.test.ClientSupport;
import ru.vyarus.dropwizard.guice.test.jupiter.ext.TestDropwizardAppExtension;
import uk.co.jemos.podam.api.PodamFactory;

import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.comet.opik.infrastructure.auth.RequestContext.SESSION_COOKIE;
import static com.comet.opik.infrastructure.auth.RequestContext.WORKSPACE_HEADER;
import static com.comet.opik.infrastructure.auth.TestHttpClientUtils.UNAUTHORIZED_RESPONSE;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Automation Rule Evaluators Resource Test")
class AutomationRuleEvaluatorsResourceTest {

    private static final String URL_TEMPLATE = "%s/v1/private/automation/evaluators/";
    private static final String URL_TEMPLATE_BY_PROJ_ID = "%s/v1/private/automation/evaluators/projectId/%s";
    private static final String URL_TEMPLATE_BY_PROJ_ID_AND_EVAL_ID = "%s/v1/private/automation/evaluators/projectId/%s/evaluatorId/%s";

    private static final String USER = UUID.randomUUID().toString();
    private static final String API_KEY = UUID.randomUUID().toString();
    private static final String WORKSPACE_ID = UUID.randomUUID().toString();
    private static final String TEST_WORKSPACE = UUID.randomUUID().toString();

    private static final RedisContainer REDIS = RedisContainerUtils.newRedisContainer();

    private static final MySQLContainer<?> MYSQL = MySQLContainerUtils.newMySQLContainer();

    @RegisterExtension
    private static final TestDropwizardAppExtension app;

    private static final WireMockUtils.WireMockRuntime wireMock;

    static {
        Startables.deepStart(REDIS, MYSQL).join();

        wireMock = WireMockUtils.startWireMock();

        app = TestDropwizardAppExtensionUtils.newTestDropwizardAppExtension(MYSQL.getJdbcUrl(), null,
                wireMock.runtimeInfo(), REDIS.getRedisURI());
    }

    private final PodamFactory factory = PodamFactoryUtils.newPodamFactory();

    private String baseURI;
    private ClientSupport client;

    @BeforeAll
    void setUpAll(ClientSupport client, Jdbi jdbi) {

        MigrationUtils.runDbMigration(jdbi, MySQLContainerUtils.migrationParameters());

        this.baseURI = "http://localhost:%d".formatted(client.getPort());
        this.client = client;

        ClientSupportUtils.config(client);

        mockTargetWorkspace(API_KEY, TEST_WORKSPACE, WORKSPACE_ID);
    }

    private static void mockTargetWorkspace(String apiKey, String workspaceName, String workspaceId) {
        AuthTestUtils.mockTargetWorkspace(wireMock.server(), apiKey, workspaceName, workspaceId, USER);
    }

    @AfterAll
    void tearDownAll() {
        wireMock.server().stop();
    }

    private UUID create(AutomationRuleEvaluator evaluator, String apiKey, String workspaceName) {
        try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .header(WORKSPACE_HEADER, workspaceName)
                .post(Entity.json(evaluator))) {

            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(201);

            return TestUtils.getIdFromLocation(actualResponse.getLocation());
        }
    }

    @Nested
    @DisplayName("Api Key Authentication:")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ApiKey {

        private final String fakeApikey = UUID.randomUUID().toString();
        private final String okApikey = UUID.randomUUID().toString();

        Stream<Arguments> credentials() {
            return Stream.of(
                    arguments(okApikey, true),
                    arguments(fakeApikey, false),
                    arguments("", false));
        }

        @BeforeEach
        void setUp() {

            wireMock.server().stubFor(
                    post(urlPathEqualTo("/opik/auth"))
                            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(fakeApikey))
                            .withRequestBody(matchingJsonPath("$.workspaceName", matching(".+")))
                            .willReturn(WireMock.unauthorized()));

            wireMock.server().stubFor(
                    post(urlPathEqualTo("/opik/auth"))
                            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(""))
                            .withRequestBody(matchingJsonPath("$.workspaceName", matching(".+")))
                            .willReturn(WireMock.unauthorized()));
        }

        @ParameterizedTest
        @MethodSource("credentials")
        @DisplayName("create evaluator definition: when api key is present, then return proper response")
        void createAutomationRuleEvaluator__whenApiKeyIsPresent__thenReturnProperResponse(String apiKey,
                                                                                          boolean isAuthorized) {

            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class);

            mockTargetWorkspace(okApikey, TEST_WORKSPACE, WORKSPACE_ID);

            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
                    .request()
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
                    .post(Entity.json(ruleEvaluator))) {

                if (isAuthorized) {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(201);
                    assertThat(actualResponse.hasEntity()).isFalse();
                } else {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
                            .isEqualTo(UNAUTHORIZED_RESPONSE);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("credentials")
        @DisplayName("get evaluators by project id: when api key is present, then return proper response")
        void getProjectAutomationRuleEvaluators__whenApiKeyIsPresent__thenReturnProperResponse(String apiKey,
                                                                                               boolean isAuthorized) {

            final String workspaceName = UUID.randomUUID().toString();
            final String workspaceId = UUID.randomUUID().toString();
            final UUID projectId = UUID.randomUUID();

            mockTargetWorkspace(okApikey, workspaceName, workspaceId);

            int samplesToCreate = 15;

            IntStream.range(0, samplesToCreate).forEach(i -> {
                var evaluator = factory.manufacturePojo(AutomationRuleEvaluator.class)
                        .toBuilder().projectId(projectId).build();
                create(evaluator, okApikey, workspaceName);
            });

            try (var actualResponse = client.target(URL_TEMPLATE_BY_PROJ_ID.formatted(baseURI, projectId))
                    .queryParam("size", samplesToCreate)
                    .request()
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(WORKSPACE_HEADER, workspaceName)
                    .get()) {

                if (isAuthorized) {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
                    assertThat(actualResponse.hasEntity()).isTrue();

                    var actualEntity = actualResponse
                            .readEntity(AutomationRuleEvaluator.AutomationRuleEvaluatorPage.class);
                    assertThat(actualEntity.content()).hasSize(samplesToCreate);
                    assertThat(actualEntity.total()).isEqualTo(samplesToCreate);

                } else {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
                            .isEqualTo(UNAUTHORIZED_RESPONSE);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("credentials")
        @DisplayName("get evaluator by id: when api key is present, then return proper response")
        void getAutomationRuleEvaluatorById__whenApiKeyIsPresent__thenReturnProperResponse(String apiKey,
                                                                                           boolean isAuthorized) {

            var evaluator = factory.manufacturePojo(AutomationRuleEvaluator.class);

            String workspaceName = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            mockTargetWorkspace(okApikey, workspaceName, workspaceId);

            UUID id = create(evaluator, okApikey, workspaceName);

            try (var actualResponse = client.target(URL_TEMPLATE_BY_PROJ_ID_AND_EVAL_ID.formatted(baseURI, evaluator.projectId(), id))
                    //.path(id.toString())
                    .request()
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(WORKSPACE_HEADER, workspaceName)
                    .get()) {

                if (isAuthorized) {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
                    assertThat(actualResponse.hasEntity()).isTrue();

                    var ruleEvaluator = actualResponse.readEntity(AutomationRuleEvaluator.class);
                    assertThat(ruleEvaluator.id()).isEqualTo(id);
                } else {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
                            .isEqualTo(UNAUTHORIZED_RESPONSE);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("credentials")
        @DisplayName("update evaluator: when api key is present, then return proper response")
        void updateAutomationRuleEvaluator__whenApiKeyIsPresent__thenReturnProperResponse(String apiKey,
                                                                                          boolean isAuthorized) {

            var evaluator = factory.manufacturePojo(AutomationRuleEvaluator.class);

            String workspaceName = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            mockTargetWorkspace(okApikey, workspaceName, workspaceId);

            UUID id = create(evaluator, okApikey, workspaceName);

            var updatedEvaluator = evaluator.toBuilder()
                    .code(UUID.randomUUID().toString())
                    .samplingRate(RandomGenerator.getDefault().nextFloat())
                    .build();

            try (var actualResponse = client.target(URL_TEMPLATE_BY_PROJ_ID_AND_EVAL_ID.formatted(baseURI, evaluator.projectId(), id))
                    .request()
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(WORKSPACE_HEADER, workspaceName)
                    .put(Entity.json(updatedEvaluator))) {

                if (isAuthorized) {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(204);
                    assertThat(actualResponse.hasEntity()).isFalse();
                } else {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
                            .isEqualTo(UNAUTHORIZED_RESPONSE);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("credentials")
        @DisplayName("delete evaluator by id: when api key is present, then return proper response")
        void deleteAutomationRuleEvaluator__whenApiKeyIsPresent__thenReturnProperResponse(String apiKey,
                                                                                          boolean isAuthorized) {

            var evaluator = factory.manufacturePojo(AutomationRuleEvaluator.class);

            String workspaceName = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            mockTargetWorkspace(okApikey, workspaceName, workspaceId);

            UUID id = create(evaluator, okApikey, workspaceName);

            try (var actualResponse = client.target(URL_TEMPLATE_BY_PROJ_ID_AND_EVAL_ID.formatted(baseURI, evaluator.projectId(), id))
                    .request()
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(WORKSPACE_HEADER, workspaceName)
                    .delete()) {

                if (isAuthorized) {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(204);
                    assertThat(actualResponse.hasEntity()).isFalse();
                } else {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
                            .isEqualTo(UNAUTHORIZED_RESPONSE);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("credentials")
        @DisplayName("delete evaluators by project id: when api key is present, then return proper response")
        void deleteProjectAutomationRuleEvaluators__whenApiKeyIsPresent__thenReturnProperResponse(String apiKey,
                                                                                                  boolean isAuthorized) {

            var projectId = UUID.randomUUID();
            var evaluator1 = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder().projectId(projectId).build();
            var evaluator2 = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder().projectId(projectId).build();

            String workspaceName = UUID.randomUUID().toString();
            String workspaceId = UUID.randomUUID().toString();

            mockTargetWorkspace(okApikey, workspaceName, workspaceId);

            create(evaluator1, okApikey, workspaceName);
            create(evaluator2, okApikey, workspaceName);

            try (var actualResponse = client.target(URL_TEMPLATE_BY_PROJ_ID.formatted(baseURI, evaluator1.projectId()))
                    .request()
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(WORKSPACE_HEADER, workspaceName)
                    .delete()) {

                if (isAuthorized) {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(204);
                    assertThat(actualResponse.hasEntity()).isFalse();
                } else {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
                            .isEqualTo(UNAUTHORIZED_RESPONSE);
                }
            }

            // we shall see a single evaluators for the project now
            try (var actualResponse = client.target(URL_TEMPLATE_BY_PROJ_ID.formatted(baseURI, projectId))
                    .request()
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(WORKSPACE_HEADER, workspaceName)
                    .get()) {

                if (isAuthorized) {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
                    assertThat(actualResponse.hasEntity()).isTrue();

                    var actualEntity = actualResponse
                            .readEntity(AutomationRuleEvaluator.AutomationRuleEvaluatorPage.class);
                    assertThat(actualEntity.content()).hasSize(0);
                    assertThat(actualEntity.total()).isEqualTo(0);

                } else {
                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
                            .isEqualTo(UNAUTHORIZED_RESPONSE);
                }
            }
        }
    }

//    @Nested
//    @DisplayName("Session Token Authentication:")
//    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
//    class SessionTokenCookie {
//
//        private final String sessionToken = UUID.randomUUID().toString();
//        private final String fakeSessionToken = UUID.randomUUID().toString();
//
//        Stream<Arguments> credentials() {
//            return Stream.of(
//                    arguments(sessionToken, true, "OK_" + UUID.randomUUID()),
//                    arguments(fakeSessionToken, false, UUID.randomUUID().toString()));
//        }
//
//        @BeforeEach
//        void setUp() {
//            wireMock.server().stubFor(
//                    post(urlPathEqualTo("/opik/auth-session"))
//                            .withCookie(SESSION_COOKIE, equalTo(sessionToken))
//                            .withRequestBody(matchingJsonPath("$.workspaceName", matching("OK_.+")))
//                            .willReturn(okJson(AuthTestUtils.newWorkspaceAuthResponse(USER, WORKSPACE_ID))));
//
//            wireMock.server().stubFor(
//                    post(urlPathEqualTo("/opik/auth-session"))
//                            .withCookie(SESSION_COOKIE, equalTo(fakeSessionToken))
//                            .withRequestBody(matchingJsonPath("$.workspaceName", matching(".+")))
//                            .willReturn(WireMock.unauthorized()));
//        }
//
//        //                    .cookie(SESSION_COOKIE, sessionToken)
//
//        @ParameterizedTest
//        @MethodSource("credentials")
//        @DisplayName("create evaluator definition: when session token is present, then return proper response")
//        void createAutomationRuleEvaluator__whenSessionTokenIsPresent__thenReturnProperResponse(String sessionToken,
//                boolean success, String workspaceName) {
//
//            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class);
//
//            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
//                    .request()
//                    .cookie(SESSION_COOKIE, sessionToken)
//                    .accept(MediaType.APPLICATION_JSON_TYPE)
//                    .header(WORKSPACE_HEADER, workspaceName)
//                    .post(Entity.json(ruleEvaluator))) {
//
//                if (success) {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(201);
//                    assertThat(actualResponse.hasEntity()).isFalse();
//                } else {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
//                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
//                            .isEqualTo(UNAUTHORIZED_RESPONSE);
//                }
//            }
//        }
//
//        @ParameterizedTest
//        @MethodSource("credentials")
//        @DisplayName("get evaluators: when session token is present, then return proper response")
//        void getAutomationRuleEvaluators__whenSessionTokenIsPresent__thenReturnProperResponse(String sessionToken,
//                boolean success, String workspaceName) {
//
//            int size = 15;
//            var newWorkspaceName = UUID.randomUUID().toString();
//            var newWorkspaceId = UUID.randomUUID().toString();
//
//            wireMock.server().stubFor(
//                    post(urlPathEqualTo("/opik/auth-session"))
//                            .withCookie(SESSION_COOKIE, equalTo(sessionToken))
//                            .withRequestBody(matchingJsonPath("$.workspaceName", equalTo(newWorkspaceName)))
//                            .willReturn(okJson(AuthTestUtils.newWorkspaceAuthResponse(USER, newWorkspaceId))));
//
//            IntStream.range(0, size).forEach(i -> {
//                create(factory.manufacturePojo(AutomationRuleEvaluator.class), API_KEY, TEST_WORKSPACE);
//            });
//
//            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
//                    .queryParam("workspace_name", workspaceName)
//                    .queryParam("size", size)
//                    .request()
//                    .cookie(SESSION_COOKIE, sessionToken)
//                    .accept(MediaType.APPLICATION_JSON_TYPE)
//                    .header(WORKSPACE_HEADER, workspaceName)
//                    .get()) {
//
//                if (success) {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
//                    assertThat(actualResponse.hasEntity()).isTrue();
//
//                    var actualEntity = actualResponse.readEntity(AutomationRuleEvaluatorUpdate.class);
//                    assertThat(actualEntity.content()).hasSize(size);
//                } else {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
//                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
//                            .isEqualTo(UNAUTHORIZED_RESPONSE);
//                }
//            }
//        }
//
//        @ParameterizedTest
//        @MethodSource("credentials")
//        @DisplayName("get evaluator by id: when session token is present, then return proper response")
//        void getAutomationRuleEvaluatorById__whenSessionTokenIsPresent__thenReturnProperResponse(String sessionToken,
//                boolean success, String workspaceName) {
//
//            var feedback = factory.manufacturePojo(AutomationRuleEvaluator.class);
//
//            UUID id = create(feedback, API_KEY, TEST_WORKSPACE);
//
//            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
//                    .path(id.toString())
//                    .request()
//                    .cookie(SESSION_COOKIE, sessionToken)
//                    .accept(MediaType.APPLICATION_JSON_TYPE)
//                    .header(WORKSPACE_HEADER, workspaceName)
//                    .get()) {
//
//                if (success) {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
//                    assertThat(actualResponse.hasEntity()).isTrue();
//
//                    var ruleEvaluator = actualResponse
//                            .readEntity(AutomationRuleEvaluator.class);
//                    assertThat(ruleEvaluator.getId()).isEqualTo(id);
//                } else {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
//                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
//                            .isEqualTo(UNAUTHORIZED_RESPONSE);
//                }
//            }
//
//        }
//
//        @ParameterizedTest
//        @MethodSource("credentials")
//        @DisplayName("update evaluator: when session token is present, then return proper response")
//        void updateAutomationRuleEvaluator__whenSessionTokenIsPresent__thenReturnProperResponse(String sessionToken,
//                boolean success, String workspaceName) {
//
//            var feedback = factory.manufacturePojo(AutomationRuleEvaluator.class);
//
//            UUID id = create(feedback, API_KEY, TEST_WORKSPACE);
//
//            var updatedFeedback = feedback.toBuilder()
//                    .name(UUID.randomUUID().toString())
//                    .build();
//
//            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
//                    .path(id.toString())
//                    .request()
//                    .cookie(SESSION_COOKIE, sessionToken)
//                    .accept(MediaType.APPLICATION_JSON_TYPE)
//                    .header(WORKSPACE_HEADER, workspaceName)
//                    .put(Entity.json(updatedFeedback))) {
//
//                if (success) {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(204);
//                    assertThat(actualResponse.hasEntity()).isFalse();
//                } else {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
//                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
//                            .isEqualTo(UNAUTHORIZED_RESPONSE);
//                }
//            }
//        }
//
//        @ParameterizedTest
//        @MethodSource("credentials")
//        @DisplayName("delete evaluator: when session token is present, then return proper response")
//        void deleteAutomationRuleEvaluator__whenSessionTokenIsPresent__thenReturnProperResponse(String sessionToken,
//                boolean success, String workspaceName) {
//
//            var feedback = factory.manufacturePojo(AutomationRuleEvaluator.class);
//
//            UUID id = create(feedback, API_KEY, TEST_WORKSPACE);
//
//            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
//                    .path(id.toString())
//                    .request()
//                    .cookie(SESSION_COOKIE, sessionToken)
//                    .accept(MediaType.APPLICATION_JSON_TYPE)
//                    .header(WORKSPACE_HEADER, workspaceName)
//                    .delete()) {
//
//                if (success) {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(204);
//                    assertThat(actualResponse.hasEntity()).isFalse();
//                } else {
//                    assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(401);
//                    assertThat(actualResponse.readEntity(io.dropwizard.jersey.errors.ErrorMessage.class))
//                            .isEqualTo(UNAUTHORIZED_RESPONSE);
//                }
//            }
//        }
//    }
//
    //    @Nested
    //    @DisplayName("Get:")
    //    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    //    class GetAllAutomationRuleEvaluator {
    //
    //        @Test
    //        @DisplayName("Success")
    //        void find() {
    //
    //            String workspaceName = UUID.randomUUID().toString();
    //            String workspaceId = UUID.randomUUID().toString();
    //            String apiKey = UUID.randomUUID().toString();
    //
    //            mockTargetWorkspace(apiKey, workspaceName, workspaceId);
    //
    //            IntStream.range(0, 15).forEach(i -> {
    //                create(i % 2 == 0
    //                        ? factory.manufacturePojo(AutomationRuleEvaluator.class)
    //                        : factory.manufacturePojo(AutomationRuleEvaluator.class),
    //                        apiKey,
    //                        workspaceName);
    //            });
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .queryParam("workspace_name", workspaceName)
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, apiKey)
    //                    .header(WORKSPACE_HEADER, workspaceName)
    //                    .get();
    //
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluatorPage.class);
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
    //            assertThat(actualEntity.page()).isEqualTo(1);
    //            assertThat(actualEntity.size()).isEqualTo(10);
    //            assertThat(actualEntity.content()).hasSize(10);
    //            assertThat(actualEntity.total()).isGreaterThanOrEqualTo(15);
    //        }
    //
    //        @Test
    //        @DisplayName("when searching by name, then return feedbacks")
    //        void find__whenSearchingByName__thenReturnFeedbacks() {
    //
    //            String workspaceName = UUID.randomUUID().toString();
    //            String workspaceId = UUID.randomUUID().toString();
    //            String apiKey = UUID.randomUUID().toString();
    //
    //            mockTargetWorkspace(apiKey, workspaceName, workspaceId);
    //            String name = "My Feedback:" + UUID.randomUUID();
    //
    //            var feedback = factory.manufacturePojo(AutomationRuleEvaluator.class)
    //                    .toBuilder()
    //                    .name(name)
    //                    .build();
    //
    //            create(feedback, apiKey, workspaceName);
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .queryParam("name", "eedback")
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, apiKey)
    //                    .header(WORKSPACE_HEADER, workspaceName)
    //                    .get();
    //
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluatorPage.class);
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
    //            assertThat(actualEntity.page()).isEqualTo(1);
    //            assertThat(actualEntity.size()).isEqualTo(1);
    //            assertThat(actualEntity.total()).isEqualTo(1);
    //
    //            List<AutomationRuleEvaluator<?>> content = actualEntity.content();
    //            assertThat(content.stream().map(AutomationRuleEvaluator::getName).toList()).contains(name);
    //        }
    //
    //        @Test
    //        @DisplayName("when searching by type, then return feedbacks")
    //        void find__whenSearchingByType__thenReturnFeedbacks() {
    //
    //            String workspaceName = UUID.randomUUID().toString();
    //            String workspaceId = UUID.randomUUID().toString();
    //            String apiKey = UUID.randomUUID().toString();
    //
    //            mockTargetWorkspace(apiKey, workspaceName, workspaceId);
    //
    //            var feedback1 = factory.manufacturePojo(AutomationRuleEvaluator.class);
    //            var feedback2 = factory.manufacturePojo(AutomationRuleEvaluator.class);
    //
    //            create(feedback1, apiKey, workspaceName);
    //            create(feedback2, apiKey, workspaceName);
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .queryParam("type", FeedbackType.NUMERICAL.getType())
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, apiKey)
    //                    .header(WORKSPACE_HEADER, workspaceName)
    //                    .get();
    //
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluatorPage.class);
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
    //            assertThat(actualEntity.page()).isEqualTo(1);
    //            assertThat(actualEntity.size()).isEqualTo(1);
    //            assertThat(actualEntity.total()).isEqualTo(1);
    //
    //            List<AutomationRuleEvaluator<?>> content = actualEntity.content();
    //            assertThat(
    //                    content.stream().map(AutomationRuleEvaluator::getType).allMatch(type -> FeedbackType.NUMERICAL == type))
    //                    .isTrue();
    //        }
    //
    //        @Test
    //        @DisplayName("when searching by workspace name, then return feedbacks")
    //        void find__whenSearchingByWorkspaceName__thenReturnFeedbacks() {
    //
    //            String workspaceName = UUID.randomUUID().toString();
    //            String workspaceId = UUID.randomUUID().toString();
    //            String apiKey = UUID.randomUUID().toString();
    //
    //            String workspaceName2 = UUID.randomUUID().toString();
    //            String workspaceId2 = UUID.randomUUID().toString();
    //            String apiKey2 = UUID.randomUUID().toString();
    //
    //            mockTargetWorkspace(apiKey, workspaceName, workspaceId);
    //            mockTargetWorkspace(apiKey2, workspaceName2, workspaceId2);
    //
    //            var feedback1 = factory.manufacturePojo(AutomationRuleEvaluator.class);
    //
    //            var feedback2 = factory.manufacturePojo(AutomationRuleEvaluator.class);
    //
    //            create(feedback1, apiKey, workspaceName);
    //            create(feedback2, apiKey2, workspaceName2);
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, apiKey2)
    //                    .header(WORKSPACE_HEADER, workspaceName2)
    //                    .get();
    //
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluatorPage.class);
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
    //            assertThat(actualEntity.page()).isEqualTo(1);
    //            assertThat(actualEntity.size()).isEqualTo(1);
    //            assertThat(actualEntity.total()).isEqualTo(1);
    //            assertThat(actualEntity.content()).hasSize(1);
    //
    //            AutomationRuleEvaluator<CategoricalFeedbackDetail> actual = (AutomationRuleEvaluator<CategoricalFeedbackDetail>) actualEntity
    //                    .content().get(0);
    //
    //            assertThat(actual.getName()).isEqualTo(feedback2.getName());
    //            assertThat(actual.getDetails().getCategories()).isEqualTo(feedback2.getDetails().getCategories());
    //            assertThat(actual.getType()).isEqualTo(feedback2.getType());
    //        }
    //
    //        @Test
    //        @DisplayName("when searching by name and workspace, then return feedbacks")
    //        void find__whenSearchingByNameAndWorkspace__thenReturnFeedbacks() {
    //
    //            var name = UUID.randomUUID().toString();
    //
    //            var workspaceName = UUID.randomUUID().toString();
    //            var workspaceId = UUID.randomUUID().toString();
    //
    //            var workspaceName2 = UUID.randomUUID().toString();
    //            var workspaceId2 = UUID.randomUUID().toString();
    //
    //            var apiKey = UUID.randomUUID().toString();
    //            var apiKey2 = UUID.randomUUID().toString();
    //
    //            mockTargetWorkspace(apiKey, workspaceName, workspaceId);
    //            mockTargetWorkspace(apiKey2, workspaceName2, workspaceId2);
    //
    //            var feedback1 = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .name(name)
    //                    .build();
    //
    //            var feedback2 = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .name(name)
    //                    .build();
    //
    //            create(feedback1, apiKey, workspaceName);
    //            create(feedback2, apiKey2, workspaceName2);
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .queryParam("name", name)
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, apiKey2)
    //                    .header(WORKSPACE_HEADER, workspaceName2)
    //                    .get();
    //
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluatorPage.class);
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
    //            assertThat(actualEntity.page()).isEqualTo(1);
    //            assertThat(actualEntity.size()).isEqualTo(1);
    //            assertThat(actualEntity.total()).isEqualTo(1);
    //            assertThat(actualEntity.content()).hasSize(1);
    //
    //            AutomationRuleEvaluator<CategoricalFeedbackDetail> actual = (AutomationRuleEvaluator<CategoricalFeedbackDetail>) actualEntity
    //                    .content().get(0);
    //
    //            assertThat(actual.getName()).isEqualTo(feedback2.getName());
    //            assertThat(actual.getDetails().getCategories()).isEqualTo(feedback2.getDetails().getCategories());
    //            assertThat(actual.getType()).isEqualTo(feedback2.getType());
    //        }
    //
    //    }
    //
    //    @Nested
    //    @DisplayName("Get {id}:")
    //    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    //    class GetAutomationRuleEvaluator {
    //
    //        @Test
    //        @DisplayName("Success")
    //        void getById() {
    //
    //            final var feedback = factory.manufacturePojo(AutomationRuleEvaluator.class);
    //
    //            var id = create(feedback, API_KEY, TEST_WORKSPACE);
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .get();
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluator.class);
    //
    //            assertThat(actualEntity)
    //                    .usingRecursiveComparison(RecursiveComparisonConfiguration.builder()
    //                            .withIgnoredFields(IGNORED_FIELDS)
    //                            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
    //                            .build())
    //                    .isEqualTo(feedback);
    //
    //            assertThat(actualEntity.getType()).isEqualTo(FeedbackType.NUMERICAL);
    //            assertThat(actualEntity.getLastUpdatedBy()).isEqualTo(USER);
    //            assertThat(actualEntity.getCreatedBy()).isEqualTo(USER);
    //            assertThat(actualEntity.getCreatedAt()).isNotNull();
    //            assertThat(actualEntity.getCreatedAt()).isInstanceOf(Instant.class);
    //            assertThat(actualEntity.getLastUpdatedAt()).isNotNull();
    //            assertThat(actualEntity.getLastUpdatedAt()).isInstanceOf(Instant.class);
    //
    //            assertThat(actualEntity.getCreatedAt()).isAfter(feedback.getCreatedAt());
    //            assertThat(actualEntity.getLastUpdatedAt()).isAfter(feedback.getLastUpdatedAt());
    //        }
    //
    //        @Test
    //        @DisplayName("when feedback does not exist, then return not found")
    //        void getById__whenFeedbackDoesNotExist__thenReturnNotFound() {
    //
    //            var id = generator.generate();
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .get();
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(404);
    //            var actualEntity = actualResponse.readEntity(ErrorMessage.class);
    //
    //            assertThat(actualEntity.errors()).containsExactly("evaluator not found");
    //        }
    //
    //    }
    //
    //    @Nested
    //    @DisplayName("Create:")
    //    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    //    class CreateAutomationRuleEvaluator {
    //
    //        @Test
    //        @DisplayName("Success")
    //        void create() {
    //            UUID id;
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class);
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(201);
    //                assertThat(actualResponse.hasEntity()).isFalse();
    //                assertThat(actualResponse.getHeaderString("Location")).matches(Pattern.compile(URL_PATTERN));
    //
    //                id = TestUtils.getIdFromLocation(actualResponse.getLocation());
    //            }
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .get();
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
    //
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluator.class);
    //
    //            assertThat(actualEntity.getId()).isEqualTo(id);
    //        }
    //
    //        @Test
    //        @DisplayName("when feedback already exists, then return error")
    //        void create__whenFeedbackAlreadyExists__thenReturnError() {
    //
    //            NumericalAutomationRuleEvaluator feedback = factory
    //                    .manufacturePojo(AutomationRuleEvaluator.class);
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(feedback))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(201);
    //                assertThat(actualResponse.hasEntity()).isFalse();
    //            }
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(feedback))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(409);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("Feedback already exists");
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("when details is null, then return bad request")
    //        void create__whenDetailsIsNull__thenReturnBadRequest() {
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .details(null)
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(422);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("details must not be null");
    //
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("when name is null, then return bad request")
    //        void create__whenNameIsNull__thenReturnBadRequest() {
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .name(null)
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(422);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("name must not be blank");
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("when categoryName is null, then return bad request")
    //        void create__whenCategoryIsNull__thenReturnBadRequest() {
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .details(CategoricalFeedbackDetail
    //                            .builder()
    //                            .build())
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(422);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("details.categories must not be null");
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("when categoryName is empty, then return bad request")
    //        void create__whenCategoryIsEmpty__thenReturnBadRequest() {
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .details(CategoricalFeedbackDetail.builder().categories(Map.of()).build())
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(422);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("details.categories size must be between 2 and 2147483647");
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("when categoryName has one key pair, then return bad request")
    //        void create__whenCategoryHasOneKeyPair__thenReturnBadRequest() {
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .details(
    //                            CategoricalFeedbackDetail.builder()
    //                                    .categories(Map.of("yes", 1.0))
    //                                    .build())
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(422);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("details.categories size must be between 2 and 2147483647");
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("when numerical min is null, then return bad request")
    //        void create__whenNumericalMinIsNull__thenReturnBadRequest() {
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .details(NumericalAutomationRuleEvaluator.NumericalFeedbackDetail
    //                            .builder()
    //                            .max(BigDecimal.valueOf(10))
    //                            .build())
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(422);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("details.min must not be null");
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("when numerical max is null, then return bad request")
    //        void create__whenNumericalMaxIsNull__thenReturnBadRequest() {
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .details(NumericalAutomationRuleEvaluator.NumericalFeedbackDetail
    //                            .builder()
    //                            .min(BigDecimal.valueOf(10))
    //                            .build())
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(422);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("details.max must not be null");
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("when numerical max is smaller than min, then return bad request")
    //        void create__whenNumericalMaxIsSmallerThanMin__thenReturnBadRequest() {
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .details(NumericalAutomationRuleEvaluator.NumericalFeedbackDetail
    //                            .builder()
    //                            .min(BigDecimal.valueOf(10))
    //                            .max(BigDecimal.valueOf(1))
    //                            .build())
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .post(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(422);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("details.min has to be smaller than details.max");
    //            }
    //        }
    //
    //    }
    //
    //    @Nested
    //    @DisplayName("Update:")
    //    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    //    class UpdateAutomationRuleEvaluator {
    //
    //        @Test
    //
    //        void notfound() {
    //
    //            UUID id = generator.generate();
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class).toBuilder()
    //                    .details(CategoricalFeedbackDetail
    //                            .builder()
    //                            .categories(Map.of("yes", 1., "no", 0.))
    //                            .build())
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .put(Entity.json(ruleEvaluator))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(404);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //
    //                var actualEntity = actualResponse.readEntity(ErrorMessage.class);
    //                assertThat(actualEntity.errors()).containsExactly("evaluator not found");
    //            }
    //        }
    //
    //        @Test
    //        void update() {
    //
    //            String name = UUID.randomUUID().toString();
    //            String name2 = UUID.randomUUID().toString();
    //
    //            var ruleEvaluator = factory.manufacturePojo(AutomationRuleEvaluator.class)
    //                    .toBuilder()
    //                    .name(name)
    //                    .build();
    //
    //            UUID id = create(ruleEvaluator, API_KEY, TEST_WORKSPACE);
    //
    //            var ruleEvaluator1 = ruleEvaluator.toBuilder()
    //                    .name(name2)
    //                    .build();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .accept(MediaType.APPLICATION_JSON_TYPE)
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .put(Entity.json(ruleEvaluator1))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(204);
    //                assertThat(actualResponse.hasEntity()).isFalse();
    //            }
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .get();
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(200);
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluator.class);
    //
    //            assertThat(actualEntity.getName()).isEqualTo(name2);
    //            assertThat(actualEntity.getDetails().getCategories())
    //                    .isEqualTo(ruleEvaluator.getDetails().getCategories());
    //        }
    //
    //    }
    //
    //    @Nested
    //    @DisplayName("Delete:")
    //    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    //    class DeleteAutomationRuleEvaluator {
    //
    //        @Test
    //        @DisplayName("Success")
    //        void deleteById() {
    //            final UUID id = create(factory.manufacturePojo(AutomationRuleEvaluator.class),
    //                    API_KEY, TEST_WORKSPACE);
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .delete()) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(204);
    //                assertThat(actualResponse.hasEntity()).isFalse();
    //            }
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .get()) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(404);
    //                assertThat(actualResponse.hasEntity()).isTrue();
    //                assertThat(actualResponse.readEntity(ErrorMessage.class).errors())
    //                        .containsExactly("evaluator not found");
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("delete batch evaluators")
    //        void deleteBatch() {
    //            var apiKey = UUID.randomUUID().toString();
    //            var workspaceName = UUID.randomUUID().toString();
    //            var workspaceId = UUID.randomUUID().toString();
    //            mockTargetWorkspace(apiKey, workspaceName, workspaceId);
    //
    //            var ids = PodamFactoryUtils.manufacturePojoList(factory,
    //                    AutomationRuleEvaluator.class).stream()
    //                    .map(ruleEvaluator -> create(ruleEvaluator, apiKey, workspaceName))
    //                    .toList();
    //            var idsToDelete = ids.subList(0, 3);
    //            var notDeletedIds = ids.subList(3, ids.size());
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path("delete")
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, apiKey)
    //                    .header(WORKSPACE_HEADER, workspaceName)
    //                    .post(Entity.json(new BatchDelete(new HashSet<>(idsToDelete))))) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
    //                assertThat(actualResponse.hasEntity()).isFalse();
    //            }
    //
    //            var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .queryParam("size", ids.size())
    //                    .queryParam("page", 1)
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, apiKey)
    //                    .header(WORKSPACE_HEADER, workspaceName)
    //                    .get();
    //
    //            var actualEntity = actualResponse.readEntity(AutomationRuleEvaluatorPage.class);
    //
    //            assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    //            assertThat(actualEntity.size()).isEqualTo(notDeletedIds.size());
    //            assertThat(actualEntity.content().stream().map(AutomationRuleEvaluator::getId).toList())
    //                    .usingRecursiveComparison()
    //                    .ignoringCollectionOrder()
    //                    .isEqualTo(notDeletedIds);
    //        }
    //
    //        @Test
    //        @DisplayName("when id found, then return no content")
    //        void deleteById__whenIdNotFound__thenReturnNoContent() {
    //            UUID id = UUID.randomUUID();
    //
    //            try (var actualResponse = client.target(URL_TEMPLATE.formatted(baseURI))
    //                    .path(id.toString())
    //                    .request()
    //                    .header(HttpHeaders.AUTHORIZATION, API_KEY)
    //                    .header(WORKSPACE_HEADER, TEST_WORKSPACE)
    //                    .delete()) {
    //
    //                assertThat(actualResponse.getStatusInfo().getStatusCode()).isEqualTo(204);
    //                assertThat(actualResponse.hasEntity()).isFalse();
    //            }
    //        }
    //    }
}