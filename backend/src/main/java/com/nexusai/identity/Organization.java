package com.nexusai.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Organization(String name, String slug, boolean demo, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.slug = slug;
        this.demo = demo;
        this.createdAt = now;
    }
}
