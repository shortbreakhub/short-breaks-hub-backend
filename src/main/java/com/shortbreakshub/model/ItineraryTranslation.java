package com.shortbreakshub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "itinerary_translation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_itinerary_translation_itinerary_locale",
                        columnNames = {"itinerary_id", "locale"}
                )
        }
)
public class ItineraryTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "itinerary_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_itinerary_translation_itinerary"
            )
    )
    private Itinerary itinerary;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "price_note", columnDefinition = "TEXT")
    private String priceNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "highlights",
            columnDefinition = "jsonb",
            nullable = false
    )
    private List<String> highlights = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "schedule",
            columnDefinition = "jsonb",
            nullable = false
    )
    private List<Map<String, Object>> schedule = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "planning",
            columnDefinition = "jsonb"
    )
    private Map<String, Object> planning = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "transport",
            columnDefinition = "jsonb"
    )
    private Map<String, Object> transport = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "food_recommendation",
            columnDefinition = "jsonb"
    )
    private Map<String, Object> foodRecommendation = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}