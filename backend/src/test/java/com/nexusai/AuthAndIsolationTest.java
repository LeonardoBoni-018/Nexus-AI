package com.nexusai;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AuthAndIsolationTest extends IntegrationTestBase {

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aurora.demo\",\"password\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordReturns401WithoutLeakingDetails() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aurora.demo\",\"password\":\"errada-errada\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void loginReturnsMembershipsAndSessionGrantsAccess() throws Exception {
        MockHttpSession s = login("admin@aurora.demo");
        mvc.perform(get("/api/v1/auth/me").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations", hasSize(1)))
                .andExpect(jsonPath("$.organizations[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.organizations[0].demo").value(true));
        mvc.perform(get("/api/v1/sales/summary").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.salesCount", greaterThan(0)));
    }

    @Test
    void userCannotActInAnotherOrganizationViaHeader() throws Exception {
        MockHttpSession casa = login("admin@casaecia.demo");
        UUID aurora = orgId("mercado-aurora-demo");
        mvc.perform(get("/api/v1/sales/summary").session(casa).header("X-Organization-Id", aurora.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resourcesOfAnotherOrganizationAreNotFound() throws Exception {
        UUID auroraSale = jdbc.sql("SELECT id FROM sales WHERE organization_id = :o LIMIT 1")
                .param("o", orgId("mercado-aurora-demo")).query(UUID.class).single();
        UUID auroraStore = storeId(orgId("mercado-aurora-demo"), "Loja Centro");
        MockHttpSession casa = login("admin@casaecia.demo");
        mvc.perform(get("/api/v1/sales/" + auroraSale).session(casa)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/inventory?storeId=" + auroraStore).session(casa)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sales/summary?storeId=" + auroraStore).session(casa)).andExpect(status().isNotFound());
    }

    @Test
    void dashboardNumbersAreScopedPerOrganization() throws Exception {
        MockHttpSession aurora = login("admin@aurora.demo");
        MockHttpSession casa = login("admin@casaecia.demo");
        String a = mvc.perform(get("/api/v1/sales/summary").session(aurora)).andReturn().getResponse().getContentAsString();
        String c = mvc.perform(get("/api/v1/sales/summary").session(casa)).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(a, c);
    }

    @Test
    void invalidPeriodIsRejected() throws Exception {
        MockHttpSession s = login("admin@aurora.demo");
        mvc.perform(get("/api/v1/sales/summary?from=2026-02-10&to=2026-02-01").session(s))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sales/summary?from=2020-01-01&to=2026-02-01").session(s))
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewerCannotCreateReplenishment() throws Exception {
        MockHttpSession viewer = login("leitor@aurora.demo");
        UUID store = storeId(orgId("mercado-aurora-demo"), "Loja Centro");
        mvc.perform(post("/api/v1/actions/replenishment").session(viewer).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"storeId\":\"" + store + "\"}"))
                .andExpect(status().isForbidden());
    }
}
