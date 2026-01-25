package com.shortbreakshub.seeder.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SeedItineraryDTO {
    private String slug;
    private String region;
    private String country;
    private String city;
    private String title;
    private int days;
    private int priceFrom;
    private String hero;

    private String summary;
    private List<String> highlights;

    private List<SeedDayDTO> schedule;

    private SeedPlanningDTO planning;

    private SeedTransportTipDTO transport;

    private SeedFoodRecommendationDTO foodRecommendation;
}
