package com.nexusai.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorios do modulo de identidade (agrupados por serem pequenos). */
public final class IdentityRepositories {

    private IdentityRepositories() {}

    public interface UserRepository extends JpaRepository<User, UUID> {
        Optional<User> findByEmail(String email);

        boolean existsByEmail(String email);
    }

    public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
        boolean existsBySlug(String slug);

        Optional<Organization> findBySlug(String slug);
    }

    public interface MembershipRepository extends JpaRepository<Membership, UUID> {
        List<Membership> findByUserId(UUID userId);

        Optional<Membership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);
    }
}
