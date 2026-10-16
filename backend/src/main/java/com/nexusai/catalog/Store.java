package com.nexusai.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(nullable = false)
    private String name;
    private String city;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Store(UUID organizationId, String name, String city, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.city = city;
        this.createdAt = now;
    }
}
