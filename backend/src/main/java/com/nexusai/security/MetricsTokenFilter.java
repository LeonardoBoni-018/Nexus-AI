package com.nexusai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autoriza o scrape de metricas via header X-Metrics-Token (comparacao em tempo constante). Sem token
 * configurado, nenhuma requisicao recebe o papel METRICS. Nao cria sessao.
 */
class MetricsTokenFilter extends OncePerRequestFilter {

    private final byte[] token;

    MetricsTokenFilter(String token) {
        this.token = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return token == null || !request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String sent = req.getHeader("X-Metrics-Token");
        if (sent != null && MessageDigest.isEqual(token, sent.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "metrics-scraper", null, List.of(new SimpleGrantedAuthority("ROLE_METRICS"))));
        }
        chain.doFilter(req, res);
    }
}
