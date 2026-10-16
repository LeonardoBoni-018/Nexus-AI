package com.nexusai.security;

import com.nexusai.common.exception.ApiException;
import com.nexusai.identity.Role;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injeta {@link OrgContext} nos controllers a partir da sessao e do header X-Organization-Id. */
@Component
public class OrgContextResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Organization-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return OrgContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof NexusPrincipal p)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticacao necessaria");
        }
        return resolve(p, request.getHeader(HEADER));
    }

    static OrgContext resolve(NexusPrincipal p, String header) {
        Map<UUID, Role> memberships = p.memberships();
        if (memberships.isEmpty()) {
            throw ApiException.forbidden("Usuario sem organizacao");
        }
        UUID orgId;
        if (header == null || header.isBlank()) {
            orgId = memberships.keySet().iterator().next();
        } else {
            try {
                orgId = UUID.fromString(header.trim());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Header " + HEADER + " invalido");
            }
        }
        Role role = memberships.get(orgId);
        if (role == null) {
            throw ApiException.forbidden("Voce nao pertence a esta organizacao");
        }
        return new OrgContext(p.userId(), orgId, role);
    }
}
