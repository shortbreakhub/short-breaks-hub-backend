package com.shortbreakshub.seeder.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class ItineraryTranslationImportDto {

    // Used to locate the existing English itinerary
    private String slug;

    // Optional values used to validate that the correct itinerary was matched
    private String region;
    private String country;
    private String city;

    // Translated fields
    private String title;
    private String summary;
    private String priceNote;

    private List<String> highlights = new ArrayList<>();

    private List<Map<String, Object>> schedule = new ArrayList<>();

    private Map<String, Object> planning = new LinkedHashMap<>();

    private Map<String, Object> transport = new LinkedHashMap<>();

    private Map<String, Object> foodRecommendation = new LinkedHashMap<>();
}