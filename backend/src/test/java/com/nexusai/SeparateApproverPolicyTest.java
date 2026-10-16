package com.nexusai;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.require-separate-approver=true")
class SeparateApproverPolicyTest extends IntegrationTestBase {

    private String create(MockHttpSession s) throws Exception {
        UUID store = storeId(orgId("mercado-aurora-demo"), "Loja Centro");
        String body = mvc.perform(post("/api/v1/actions/replenishment").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"storeId\":\"" + store + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/v1/actions/" + id + "/submit").session(s).with(csrf())).andExpect(status().isOk());
        return id;
    }

    @Test
    void creatorCannotApproveOwnProposalButAnotherManagerCan() throws Exception {
        MockHttpSession manager = login("gerente@aurora.demo");
        String own = create(manager);
        mvc.perform(post("/api/v1/actions/" + own + "/approve").session(manager).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));

        String byAnalyst = create(login("analista@aurora.demo"));
        mvc.perform(post("/api/v1/actions/" + byAnalyst + "/approve").session(manager).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/actions/" + own + "/approve").session(login("admin@aurora.demo")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }
}
