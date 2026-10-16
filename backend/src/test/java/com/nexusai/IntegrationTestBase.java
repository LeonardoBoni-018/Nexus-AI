package com.nexusai;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.nexusai.demo.DemoDataSeeder;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    /** Faz login real (sessao + CSRF) e devolve a sessao autenticada. */
    protected MockHttpSession login(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/auth/login").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoDataSeeder.DEMO_PASSWORD + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        return session;
    }

    protected UUID orgId(String slug) {
        return jdbc.sql("SELECT id FROM organizations WHERE slug = :s").param("s", slug).query(UUID.class).single();
    }

    protected UUID storeId(UUID org, String name) {
        return jdbc.sql("SELECT id FROM stores WHERE organization_id = :o AND name = :n")
                .param("o", org).param("n", name).query(UUID.class).single();
    }
}
