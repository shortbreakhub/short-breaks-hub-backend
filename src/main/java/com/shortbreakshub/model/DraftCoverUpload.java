package com.shortbreakshub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/** Ownership comes exclusively from the authenticated upload, never a draft URL. */
@Entity
@Table(name = "draft_cover_uploads")
@Getter @Setter @NoArgsConstructor
public class DraftCoverUpload {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private Long ownerId;
    @Column(nullable = false, unique = true, updatable = false)
    private String assetId;
    @Column(nullable = false, unique = true, updatable = false)
    private String publicId;
    @Column(nullable = false, unique = true, updatable = false, length = 2048)
    private String secureUrl;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant deletedAt;
}
