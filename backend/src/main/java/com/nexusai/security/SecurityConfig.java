package com.nexusai.security;

import com.nexusai.config.AppProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/**
 * Sessao com cookie HttpOnly + protecao CSRF (cookie XSRF-TOKEN lido pelo SPA e reenviado no header
 * X-XSRF-TOKEN). Nenhum token de longa duracao fica acessivel a scripts.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig implements WebMvcConfigurer {

    private final OrgContextResolver orgContextResolver;

    public SecurityConfig(OrgContextResolver orgContextResolver) {
        this.orgContextResolver = orgContextResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(orgContextResolver);
    }

    /**
     * Cookie da sessao persistida (Spring Session): HttpOnly, SameSite=Lax e Secure configuravel. Definido
     * explicitamente para nao depender dos padroes do Spring Session (cookie SESSION sem HttpOnly).
     */
    @Bean
    org.springframework.session.web.http.CookieSerializer cookieSerializer(
            @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        org.springframework.session.web.http.DefaultCookieSerializer c =
                new org.springframework.session.web.http.DefaultCookieSerializer();
        c.setCookieName("NEXUSSESSION");
        c.setCookiePath("/");
        c.setUseHttpOnlyCookie(true);
        c.setSameSite("Lax");
        c.setUseSecureCookie(secure);
        return c;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(NexusUserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository repo, AppProperties props) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieName("XSRF-TOKEN");
        csrfRepo.setHeaderName("X-XSRF-TOKEN");
        csrfRepo.setCookiePath("/");

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .securityContext(sc -> sc.securityContextRepository(repo))
            .sessionManagement(sm -> sm.sessionFixation(f -> f.changeSessionId()))
            .addFilterBefore(new MetricsTokenFilter(props.metricsToken()),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**").hasRole("METRICS")
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> writeProblem(res, HttpStatus.UNAUTHORIZED,
                    "UNAUTHENTICATED", "Autenticacao necessaria"))
                .accessDeniedHandler((req, res, ex) -> writeProblem(res, HttpStatus.FORBIDDEN,
                    "FORBIDDEN", "Acesso negado")));
        return http.build();
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse res, HttpStatus status, String code,
            String detail) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase()
            + "\",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(props.corsAllowedOrigins().split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Organization-Id", "X-Request-Id"));
        cfg.setExposedHeaders(List.of("X-Request-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
