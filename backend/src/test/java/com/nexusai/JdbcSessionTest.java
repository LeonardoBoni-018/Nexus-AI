package com.nexusai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexusai.demo.DemoDataSeeder;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Sessao persistida no banco (Spring Session JDBC): o cookie e a unica referencia mantida pelo cliente. */
@TestPropertySource(properties = "spring.autoconfigure.exclude=")
class JdbcSessionTest extends IntegrationTestBase {

    private int sessionRows() {
        return jdbc.sql("SELECT COUNT(*) FROM spring_session").query(Integer.class).single();
    }

    @Test
    void loginStoresSessionInDatabaseAndLogoutRemovesIt() throws Exception {
        int before = sessionRows();
        var res = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aurora.demo\",\"password\":\"" + DemoDataSeeder.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie session = res.getCookie("NEXUSSESSION");
        if (session == null) {
            throw new AssertionError("Set-Cookie=" + res.getHeaders("Set-Cookie") + " cookies=" + java.util.Arrays.toString(res.getCookies()));
        }
        assertNotNull(session, "cookie de sessao deve ser emitido");
        org.junit.jupiter.api.Assertions.assertTrue(session.isHttpOnly(), "cookie deve ser HttpOnly");
        org.junit.jupiter.api.Assertions.assertTrue(res.getHeader("Set-Cookie").contains("SameSite=Lax"));
        assertEquals(before + 1, sessionRows());

        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@aurora.demo"));

        mvc.perform(post("/api/v1/auth/logout").cookie(session).with(csrf())).andExpect(status().isOk());
        assertEquals(before, sessionRows());
        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }
}
