package com.nexusai.security;

import com.nexusai.identity.Role;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Identidade autenticada. As memberships definem a que organizacoes o usuario pertence. */
public record NexusPrincipal(
        UUID userId,
        String email,
        String name,
        String passwordHash,
        boolean enabled,
        LinkedHashMap<UUID, Role> memberships) implements UserDetails, Serializable {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public Map<UUID, Role> membershipsView() {
        return memberships;
    }
}
