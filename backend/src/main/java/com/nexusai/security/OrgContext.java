package com.nexusai.security;

import com.nexusai.common.exception.ApiException;
import com.nexusai.identity.Role;
import java.util.UUID;

/**
 * Contexto autorizado da requisicao: usuario autenticado, organizacao ativa (validada contra as
 * memberships) e papel. Nunca derivado de dados informados pelo modelo de IA.
 */
public record OrgContext(UUID userId, UUID organizationId, Role role) {

    public void require(Role minimum) {
        if (!role.atLeast(minimum)) {
            throw ApiException.forbidden("Permissao insuficiente: requer perfil " + minimum);
        }
    }
}
