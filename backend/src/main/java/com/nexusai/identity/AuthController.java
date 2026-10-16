package com.nexusai.identity;

import com.nexusai.audit.AuditService;
import com.nexusai.common.RateLimiter;
import com.nexusai.common.exception.ApiException;
import com.nexusai.config.AppProperties;
import com.nexusai.identity.IdentityRepositories.MembershipRepository;
import com.nexusai.identity.IdentityRepositories.OrganizationRepository;
import com.nexusai.identity.IdentityRepositories.UserRepository;
import com.nexusai.security.NexusPrincipal;
import com.nexusai.security.NexusUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 10, max = 72, message = "A senha deve ter entre 10 e 72 caracteres") String password,
            @NotBlank @Size(max = 160) String organizationName) {}

    public record MembershipDto(UUID organizationId, String organizationName, boolean demo, Role role) {}

    public record MeResponse(UUID userId, String name, String email, List<MembershipDto> organizations) {}

    public record CsrfResponse(String headerName, String token) {}

    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository;
    private final NexusUserDetailsService userDetails;
    private final UserRepository users;
    private final OrganizationRepository orgs;
    private final MembershipRepository memberships;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;
    private final RateLimiter loginLimiter;

    public AuthController(AuthenticationManager authManager, SecurityContextRepository contextRepository,
            NexusUserDetailsService userDetails, UserRepository users, OrganizationRepository orgs,
            MembershipRepository memberships, PasswordEncoder encoder, AuditService audit, AppProperties props,
            Clock clock) {
        this.authManager = authManager;
        this.contextRepository = contextRepository;
        this.userDetails = userDetails;
        this.users = users;
        this.orgs = orgs;
        this.memberships = memberships;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.loginLimiter = new RateLimiter(10, 5 * 60_000L, clock);
    }

    /** Emite o cookie XSRF-TOKEN; o SPA chama antes do login. */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        String email = body.email().trim().toLowerCase();
        String key = email + "|" + req.getRemoteAddr();
        if (!loginLimiter.tryAcquire(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Muitas tentativas de login. Aguarde alguns minutos.");
        }
        Authentication auth;
        try {
            auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
        } catch (AuthenticationException e) {
            audit.record(null, null, "LOGIN_FAILED", "User", null, "email=" + mask(email));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "E-mail ou senha invalidos");
        }
        loginLimiter.reset(key);
        establishSession(auth, req, res);
        NexusPrincipal p = (NexusPrincipal) auth.getPrincipal();
        audit.record(null, p.userId(), "LOGIN", "User", p.userId(), null);
        return me(p);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        // Recarrega do banco para refletir mudancas de membership sem exigir novo login.
        NexusPrincipal fresh = (NexusPrincipal) userDetails.loadUserByUsername(((NexusPrincipal) auth.getPrincipal()).email());
        return me(fresh);
    }

    @PostMapping("/register")
    @Transactional
    public MeResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest req, HttpServletResponse res) {
        if (!props.registrationEnabled()) {
            throw ApiException.forbidden("Cadastro desabilitado neste ambiente");
        }
        String email = body.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "E-mail ja cadastrado");
        }
        Instant now = Instant.now(clock);
        User user = users.save(new User(email, body.name(), encoder.encode(body.password()), now));
        Organization org = orgs.save(new Organization(body.organizationName().trim(), uniqueSlug(body.organizationName()), false, now));
        memberships.save(new Membership(user.getId(), org.getId(), Role.ADMIN, now));
        audit.record(org.getId(), user.getId(), "USER_REGISTERED", "User", user.getId(), null);
        NexusPrincipal principal = (NexusPrincipal) userDetails.loadUserByUsername(email);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        establishSession(auth, req, res);
        return me(principal);
    }

    private void establishSession(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) {
            req.changeSessionId();
        }
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepository.saveContext(ctx, req, res);
    }

    private MeResponse me(NexusPrincipal p) {
        List<MembershipDto> list = p.memberships().entrySet().stream().map(e -> {
            Organization o = orgs.findById(e.getKey()).orElseThrow();
            return new MembershipDto(o.getId(), o.getName(), o.isDemo(), e.getValue());
        }).toList();
        return new MeResponse(p.userId(), p.name(), p.email(), list);
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "org";
        }
        String slug = base;
        while (orgs.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
