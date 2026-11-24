package io.github.shinnjyuu.henkou.example;

import io.github.shinnjyuu.henkou.AuditChange;
import io.github.shinnjyuu.henkou.AuditContext;
import io.github.shinnjyuu.henkou.extra.AdditionalController;
import io.github.shinnjyuu.henkou.spring.AuditCoverageVerifier;
import io.github.shinnjyuu.henkou.spring.AuditRecorder;
import io.github.shinnjyuu.henkou.spring.ExternalHttpEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static io.github.shinnjyuu.henkou.AuditPolicy.RECORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "henkou.mvc.controller-packages=io.github.shinnjyuu.henkou.extra")
@Import(AdditionalController.class)
class AuditIntegrationIT {
    @Autowired
    private WebApplicationContext web;

    @Autowired
    private FilterRegistrationBean<?> logoutFilter;

    @Autowired
    private ExternalHttpEndpoint logoutEndpoint;

    @Autowired
    private AuditCoverageVerifier verifier;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private AuditRecorder recorder;

    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM example_role");
        jdbc.update("DELETE FROM henkou_audit_log");
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(logoutFilter.getFilter()).build();
    }

    @Test
    void verifiesTheApplicationRouteDeclarations() {
        verifier.verify();
    }

    @Test
    void suppliesTheAuditPolicyToControllersInAdditionalPackages() throws Exception {
        mvc.perform(get("/additional-controller")).andExpect(status().isNoContent());
        assertThat(auditCount()).isZero();
    }

    @Test
    void reportsEveryMissingRouteWithMethodPathAndHandler() throws NoSuchMethodException {
        var read = mapping(RequestMethod.GET, "/missing-read", "/missing-read-alias");
        var write = mapping(RequestMethod.POST, "/missing-write");
        var handler = new UndeclaredController();
        var method = UndeclaredController.class.getDeclaredMethod("handle");
        try {
            handlerMapping.registerMapping(read, handler, method);
            handlerMapping.registerMapping(write, handler, method);

            assertThatThrownBy(verifier::verify)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GET /missing-read ->")
                    .hasMessageContaining("GET /missing-read-alias ->")
                    .hasMessageContaining("POST /missing-write ->")
                    .hasMessageContaining("UndeclaredController#handle");
        } finally {
            handlerMapping.unregisterMapping(read);
            handlerMapping.unregisterMapping(write);
        }
    }

    @Test
    void recordsCreateUpdateAndDeleteButSkipsReadsAndUnchangedSaves() throws Exception {
        String roleId = createRole();
        var created = auditRow(roleId, "CREATE");
        assertThat(created.route()).isEqualTo("/roles");
        assertThat(created.method()).isEqualTo("POST");
        assertThat(created.actor()).isEqualTo("admin");
        assertThat(created.occurredAtEpochMs()).isPositive();
        assertThat(created.changes().get(0).path("after").asString()).isEqualTo("Reviewer");
        assertThat(created.changes().get(0).path("beforePresent").asBoolean()).isFalse();

        mvc.perform(get("/roles").principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(roleId));
        assertThat(auditCount()).isEqualTo(1);

        mvc.perform(put("/roles/{id}/permissions", roleId).principal(() -> "admin")
                        .contentType(APPLICATION_JSON).content("""
                                {"permissions":["EDIT","VIEW"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[1]").value("EDIT"));

        var updated = auditRow(roleId, "UPDATE");
        assertThat(updated.route()).isEqualTo("/roles/{id}/permissions");
        assertThat(updated.action()).isEqualTo("assign_permissions");
        assertThat(updated.changes().size()).isEqualTo(1);
        var permissions = updated.changes().get(0);
        assertThat(permissions.path("field").asString()).isEqualTo("permissions");
        assertThat(permissions.path("before")).isEqualTo(json.readTree("""
                ["VIEW"]
                """));
        assertThat(permissions.path("after")).isEqualTo(json.readTree("""
                ["VIEW","EDIT"]
                """));

        mvc.perform(put("/roles/{id}/permissions", roleId).contentType(APPLICATION_JSON).content("""
                        {"permissions":["VIEW","EDIT","VIEW"]}
                        """))
                .andExpect(status().isOk());
        assertThat(auditCount()).isEqualTo(2);

        mvc.perform(delete("/roles/{id}", roleId)).andExpect(status().isNoContent());
        var deleted = auditRow(roleId, "DELETE");
        assertThat(deleted.actor()).isEqualTo("anonymous");
        assertThat(deleted.changes().get(0).path("before").asString()).isEqualTo("Reviewer");
        assertThat(deleted.changes().get(0).path("afterPresent").asBoolean()).isFalse();
        assertThat(auditCount()).isEqualTo(3);
        assertThat(roleCount()).isZero();
    }

    @Test
    void rollsBackTheAuditRecordWhenBusinessRollsBack() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            createRole();
            assertThat(auditCount()).isEqualTo(1);
            assertThat(roleCount()).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(auditCount()).isZero();
        assertThat(roleCount()).isZero();
    }

    @Test
    void rollsBackTheBusinessWriteEvenWhenTheServiceCatchesAnAuditInsertFailure() {
        var context = new AuditContext(RECORD, "a".repeat(256), "POST", "/roles");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO example_role (id, name) VALUES (?, ?)", "test-role", "Reviewer");
            assertThatThrownBy(() -> recorder.record(AuditChange.event("role", "test-role", "create_role"), context))
                    .isInstanceOf(DataAccessException.class);
        })).isInstanceOf(UnexpectedRollbackException.class);

        assertThat(auditCount()).isZero();
        assertThat(roleCount()).isZero();
    }

    @Test
    void storesEventMetadataWithNoFieldValues() {
        var context = new AuditContext(RECORD, "admin", "PUT", "/accounts/{id}/password");
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                recorder.record(AuditChange.event("account", "42", "change_password"), context));

        var event = auditRow("42", "EVENT");
        assertThat(event.action()).isEqualTo("change_password");
        assertThat(event.actor()).isEqualTo("admin");
        assertThat(event.changes().isArray()).isTrue();
        assertThat(event.changes().size()).isZero();
    }

    @Test
    void honorsTheFilterEndpointDeclarationAndResolvesItsContext() throws Exception {
        var request = new MockHttpServletRequest("POST", "/example/session/logout");
        request.setContextPath("/example");
        request.setUserPrincipal(() -> "admin");
        var context = logoutEndpoint.context(request);
        assertThat(context.actor()).isEqualTo("admin");
        assertThat(context.routeTemplate()).isEqualTo("/session/logout");
        recorder.record(AuditChange.event("session", "42", "logout"), context);

        var session = new MockHttpSession();
        mvc.perform(post("/session/logout").session(session)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        assertThat(auditCount()).isZero();
    }

    @Test
    void requiresAnExistingWriteTransactionForRecord() {
        var context = new AuditContext(RECORD, "admin", "PUT", "/accounts/{id}/password");
        var change = AuditChange.event("account", "42", "change_password");
        assertThatThrownBy(() -> recorder.record(change, context)).isInstanceOf(IllegalTransactionStateException.class);

        var readOnly = new TransactionTemplate(transactions);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> recorder.record(change, context)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(auditCount()).isZero();
    }

    private RequestMappingInfo mapping(RequestMethod method, String... paths) {
        return RequestMappingInfo.paths(paths).methods(method)
                .options(handlerMapping.getBuilderConfiguration()).build();
    }

    private String createRole() {
        try {
            var response = mvc.perform(post("/roles").principal(() -> "admin").contentType(APPLICATION_JSON).content("""
                            {"name":"Reviewer","permissions":["VIEW"]}
                            """))
                    .andExpect(status().isCreated()).andReturn().getResponse();
            return json.readTree(response.getContentAsByteArray()).path("id").asString();
        } catch (Exception failure) {
            throw new AssertionError("Creating the example role failed", failure);
        }
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM henkou_audit_log", Integer.class);
    }

    private int roleCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM example_role", Integer.class);
    }

    private AuditRow auditRow(String id, String operation) {
        return jdbc.queryForObject("""
                SELECT route_template, http_method, actor, occurred_at_epoch_ms, action, changes_json
                FROM henkou_audit_log
                WHERE object_id = ? AND operation = ?
                """, (row, ignored) -> new AuditRow(row.getString("route_template"), row.getString("http_method"),
                row.getString("actor"), row.getLong("occurred_at_epoch_ms"), row.getString("action"),
                json.readTree(row.getString("changes_json"))), id, operation);
    }

    private record AuditRow(
            String route, String method, String actor, long occurredAtEpochMs, String action, JsonNode changes) {
    }

    static class UndeclaredController {
        public void handle() {
        }
    }
}
