package com.shortbreakshub.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "itineraries")
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", foreignKey = @ForeignKey(name = "fk_itinerary_destination"))
    private Destination destination;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false,length = 500)
    private String title;

    private int days;

    @Column(name = "price_from")
    private int priceFrom;

    private String hero;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "itinerary_highlights",joinColumns = @JoinColumn(name = "itinerary_id"))
    private List<String> highlights = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "itinerary_schedule",joinColumns = @JoinColumn(name = "itinerary_id"))
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "day_title",columnDefinition = "TEXT")),
            @AttributeOverride(name = "summary", column = @Column(name = "day_summary",columnDefinition = "TEXT")),
            @AttributeOverride(name = "details", column = @Column(name = "day_details",columnDefinition = "TEXT"))
    })
    @OrderBy("day ASC")
    @JsonProperty("schedule")
    private List<DayPlan> dayPlans = new ArrayList<>();

    public Itinerary() {}

    public Itinerary(
            String slug, String region, String country,String city, String title,
            int days, int priceFrom, String hero, String summary, List<String> highlights, List<DayPlan> dayPlans) {
        this.slug = slug;
        this.region = region;
        this.country = country;
        this.city = city;
        this.title = title;
        this.days = days;
        this.priceFrom = priceFrom;
        this.hero = hero;
        this.summary = summary;
        this.highlights = highlights;
        this.dayPlans = dayPlans;
    }

}
