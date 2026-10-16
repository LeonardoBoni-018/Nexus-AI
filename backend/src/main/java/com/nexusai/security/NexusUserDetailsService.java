package com.nexusai.security;

import com.nexusai.identity.IdentityRepositories.MembershipRepository;
import com.nexusai.identity.IdentityRepositories.UserRepository;
import com.nexusai.identity.Membership;
import com.nexusai.identity.Role;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NexusUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final MembershipRepository memberships;

    public NexusUserDetailsService(UserRepository users, MembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        var user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        LinkedHashMap<UUID, Role> map = new LinkedHashMap<>();
        memberships.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(Membership::getCreatedAt))
                .forEach(m -> map.put(m.getOrganizationId(), m.getRole()));
        return new NexusPrincipal(user.getId(), user.getEmail(), user.getName(), user.getPasswordHash(),
                user.isEnabled(), map);
    }
}
