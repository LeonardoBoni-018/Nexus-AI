package com.nexusai;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Executa migrations Flyway, seed, consultas analiticas e o fluxo de acoes contra um PostgreSQL 16 REAL
 * (binarios embarcados, sem Docker), pois os demais testes usam H2 em modo PostgreSQL.
 */
class PostgresCompatibilityTest extends IntegrationTestBase {

    private static EmbeddedPostgres pg;

    static {
        try {
            pg = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel iniciar o PostgreSQL embarcado", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stop() throws IOException {
        pg.close();
    }

    @Test
    void migrationsSeedQueriesAndActionFlowWorkOnRealPostgres() throws Exception {
        String version = jdbc.sql("SHOW server_version").query(String.class).single();
        org.junit.jupiter.api.Assertions.assertTrue(version.startsWith("16"), "esperado PostgreSQL 16, obtido " + version);

        MockHttpSession analyst = login("analista@aurora.demo");
        MockHttpSession manager = login("gerente@aurora.demo");

        mvc.perform(get("/api/v1/sales/comparison").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMetrics.salesCount", greaterThan(0)));
        mvc.perform(get("/api/v1/dashboard/sales-series").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(30)));
        mvc.perform(get("/api/v1/dashboard/top-products?orderBy=revenue").session(manager)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard/stale-products").session(manager)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analytics/coverage").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        mvc.perform(get("/api/v1/products?search=50%25_x").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0)); // curingas '%' e '_' sao escapados
        mvc.perform(get("/api/v1/products?search=agua").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems", greaterThan(0)));
        mvc.perform(get("/api/v1/inventory?lowOnly=true").session(manager)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/inventory/movements?type=SALE").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThan(0))));
        mvc.perform(get("/api/v1/sales").session(manager)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/alerts").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThan(0))));

        UUID store = storeId(orgId("mercado-aurora-demo"), "Loja Centro");
        String body = mvc.perform(post("/api/v1/actions/replenishment").session(analyst).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"storeId\":\"" + store + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        for (String verb : new String[] {"submit"}) {
            mvc.perform(post("/api/v1/actions/" + id + "/" + verb).session(analyst).with(csrf())).andExpect(status().isOk());
        }
        for (String verb : new String[] {"approve", "execute", "execute"}) {
            mvc.perform(post("/api/v1/actions/" + id + "/" + verb).session(manager).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/actions/" + id).session(manager)).andExpect(jsonPath("$.status").value("EXECUTED"));
        mvc.perform(get("/api/v1/audit-events").session(manager)).andExpect(status().isOk());
        int mismatches = jdbc.sql("""
                SELECT COUNT(*) FROM inventory_balances b WHERE b.quantity <>
                (SELECT COALESCE(SUM(m.quantity_delta),0) FROM inventory_movements m
                 WHERE m.store_id = b.store_id AND m.product_id = b.product_id)
                """).query(Integer.class).single();
        assertEquals(0, mismatches);
    }
}
