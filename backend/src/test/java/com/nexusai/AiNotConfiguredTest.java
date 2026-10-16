package com.nexusai;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Sem credenciais de IA o sistema sobe, indica a necessidade de configuracao e nao afeta o restante. */
class AiNotConfiguredTest extends IntegrationTestBase {

    @Test
    void assistantReportsMissingConfigurationWhileTheRestKeepsWorking() throws Exception {
        MockHttpSession s = login("analista@aurora.demo");
        mvc.perform(get("/api/v1/assistant/status").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(false)));
        String conv = mvc.perform(post("/api/v1/assistant/conversations").session(s).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(conv, "$.id");
        mvc.perform(post("/api/v1/assistant/conversations/" + id + "/messages").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Ola\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code", is("AI_NOT_CONFIGURED")));
        mvc.perform(get("/api/v1/dashboard/summary").session(s)).andExpect(status().isOk());
    }
}
