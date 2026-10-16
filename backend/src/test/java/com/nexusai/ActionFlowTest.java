package com.nexusai;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexusai.actions.ActionService;
import com.nexusai.identity.Role;
import com.nexusai.security.OrgContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ActionFlowTest extends IntegrationTestBase {

    @Autowired
    ActionService actionService;

    private UUID createDraft(MockHttpSession analyst, UUID store) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/actions/replenishment").session(analyst).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"storeId\":\"" + store + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items", hasSize(greaterThan(0)))).andReturn();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
    }

    private void act(MockHttpSession s, UUID id, String verb, String body, int expected) throws Exception {
        mvc.perform(post("/api/v1/actions/" + id + "/" + verb).session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }

    private int stock(UUID store) {
        return jdbc.sql("SELECT COALESCE(SUM(quantity),0) FROM inventory_balances WHERE store_id = :s")
                .param("s", store).query(Integer.class).single();
    }

    @Test
    @Order(1)
    void fullApprovalFlowExecutesOnceAndIsAudited() throws Exception {
        UUID org = orgId("mercado-aurora-demo");
        UUID store = storeId(org, "Loja Centro");
        MockHttpSession analyst = login("analista@aurora.demo");
        MockHttpSession manager = login("gerente@aurora.demo");

        UUID id = createDraft(analyst, store);
        // nao aprova sem submeter
        act(manager, id, "approve", "{}", 409);
        act(analyst, id, "submit", "{}", 200);
        act(analyst, id, "submit", "{}", 409); // transicao invalida
        act(analyst, id, "approve", "{}", 403); // analista nao aprova
        act(manager, id, "execute", "{}", 409); // ainda nao aprovada

        int before = stock(store);
        int units = jdbc.sql("SELECT SUM(approved_quantity) FROM action_items WHERE action_id = :a")
                .param("a", id).query(Integer.class).single();
        act(manager, id, "approve", "{}", 200);
        act(manager, id, "execute", "{}", 200);
        assertEquals(before + units, stock(store));

        // execucao repetida e idempotente: nao duplica movimentos nem saldo
        act(manager, id, "execute", "{}", 200);
        assertEquals(before + units, stock(store));
        int movements = jdbc.sql("SELECT COUNT(*) FROM inventory_movements WHERE reference_id = :a")
                .param("a", id).query(Integer.class).single();
        int items = jdbc.sql("SELECT COUNT(*) FROM action_items WHERE action_id = :a").param("a", id)
                .query(Integer.class).single();
        assertEquals(items, movements);

        mvc.perform(get("/api/v1/actions/" + id).session(manager)).andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.approvals", hasSize(1)));
        int audit = jdbc.sql("SELECT COUNT(*) FROM audit_events WHERE entity_id = :a").param("a", id.toString())
                .query(Integer.class).single();
        assertTrue(audit >= 4, "esperado ao menos criar/submeter/aprovar/executar, obtido " + audit);
    }

    @Test
    @Order(2)
    void rejectRequiresReasonAndIsTerminal() throws Exception {
        UUID store = storeId(orgId("mercado-aurora-demo"), "Loja Pinheiros");
        MockHttpSession analyst = login("analista@aurora.demo");
        MockHttpSession manager = login("gerente@aurora.demo");
        UUID id = createDraft(analyst, store);
        act(analyst, id, "submit", "{}", 200);
        act(manager, id, "reject", "{}", 400);
        act(manager, id, "reject", "{\"reason\":\"Orcamento do mes esgotado\"}", 200);
        act(manager, id, "approve", "{}", 409);
        act(manager, id, "execute", "{}", 409);
    }

    @Test
    @Order(3)
    void otherOrganizationCannotSeeOrActOnAction() throws Exception {
        UUID store = storeId(orgId("mercado-aurora-demo"), "Loja Campinas");
        UUID id = createDraft(login("analista@aurora.demo"), store);
        MockHttpSession outsider = login("admin@casaecia.demo");
        mvc.perform(get("/api/v1/actions/" + id).session(outsider)).andExpect(status().isNotFound());
        act(outsider, id, "submit", "{}", 404);
    }

    @Test
    @Order(4)
    void concurrentExecutionsApplyStockOnlyOnce() throws Exception {
        UUID org = orgId("mercado-aurora-demo");
        UUID store = storeId(org, "Loja Campinas");
        MockHttpSession analyst = login("analista@aurora.demo");
        UUID id = createDraft(analyst, store);
        UUID userId = jdbc.sql("SELECT id FROM users WHERE email = 'gerente@aurora.demo'").query(UUID.class).single();
        OrgContext manager = new OrgContext(userId, org, Role.MANAGER);
        actionService.submit(manager, id);
        actionService.approve(manager, id, null);

        int before = stock(store);
        int units = jdbc.sql("SELECT SUM(approved_quantity) FROM action_items WHERE action_id = :a")
                .param("a", id).query(Integer.class).single();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Callable<Boolean> task = () -> {
                actionService.execute(manager, id);
                return true;
            };
            futures.add(pool.submit(task));
        }
        for (Future<Boolean> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertEquals(before + units, stock(store), "estoque deve subir exatamente uma vez");
        int balanceVsMovements = jdbc.sql("""
                SELECT COUNT(*) FROM inventory_balances b WHERE b.quantity <>
                (SELECT COALESCE(SUM(m.quantity_delta),0) FROM inventory_movements m
                 WHERE m.store_id = b.store_id AND m.product_id = b.product_id)
                """).query(Integer.class).single();
        assertEquals(0, balanceVsMovements, "saldo deve ser igual a soma dos movimentos");
    }
}
