package com.shortbreakshub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "destination_mapping_reviews", uniqueConstraints = @UniqueConstraint(name = "uk_destination_mapping_reviews_scope", columnNames = {"destination_id", "provider", "entity_type"}))
public class DestinationMappingReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false, foreignKey = @ForeignKey(name = "fk_destination_mapping_reviews_destination"))
    private Destination destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExternalProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private ExternalEntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DestinationMappingStatus status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private DestinationMappingFallback fallback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
