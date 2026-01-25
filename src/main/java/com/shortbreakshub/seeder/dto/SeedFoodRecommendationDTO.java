package com.shortbreakshub.seeder.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SeedFoodRecommendationDTO {

    private List<String> mustTry;

    private List<Map<String,String>> areas;

    private List<Map<String, Object>> places;

}