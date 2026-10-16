package com.nexusai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class SecurityHardeningTest extends IntegrationTestBase {

    @Test
    void hstsIsSentOnSecureRequestsOnly() throws Exception {
        mvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
        mvc.perform(get("/actuator/health")).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void metricsRequireTheTokenAndStaySeparateFromUserSessions() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("X-Metrics-Token", "errado")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("X-Metrics-Token", "test-metrics-token")).andExpect(status().isOk());
        MockHttpSession admin = login("admin@aurora.demo");
        mvc.perform(get("/actuator/metrics").session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sales/summary").header("X-Metrics-Token", "test-metrics-token"))
                .andExpect(status().isUnauthorized());
    }

}
