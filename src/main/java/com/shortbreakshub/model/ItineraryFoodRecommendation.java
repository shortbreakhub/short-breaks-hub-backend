package com.shortbreakshub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "itinerary_food_recommendation")
public class ItineraryFoodRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id", nullable = false, unique = true)
    private Itinerary itinerary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "must_try", columnDefinition = "jsonb", nullable = false)
    private List<String> mustTry = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name ="areas",columnDefinition = "jsonb", nullable = false)
    private List<Map<String,String>> areas = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name ="places",columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> places = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = OffsetDateTime.now();
    }
}