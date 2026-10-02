package com.shortbreakshub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "destinations", uniqueConstraints = @UniqueConstraint(name = "uk_destination_key", columnNames = "destination_key"))
public class Destination {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "destination_key", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String destinationKey;

    public void setDestinationKey(String key) {
        if (destinationKey != null && !destinationKey.equals(key)) {
            throw new IllegalStateException("Destination key is immutable");
        }
        destinationKey = key;
    }

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String country;

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
