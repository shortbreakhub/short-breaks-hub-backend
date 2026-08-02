package com.shortbreakshub.dto;

import com.shortbreakshub.model.*;

import java.util.List;
import java.util.Map;

public record ItineraryRes(
        Long id,
        String slug,
        String region,
        String country,
        String city,
        String title,
        Integer days,
        int priceFrom,
        String hero,
        String summary,
        List<String> highlights,
        List<DayPlan> schedule,
        String planningCity,
        String bestTimeMonths,
        String bestTimeNote,
        String worstTimeMonths,
        String worstTimeNote,
        List<String> tips,
        List<String> withKids,
        List<Map<String, String>> arrival,
        List<String> gettingAround,
        List<Map<String, String>> dayTrips,
        List<Map<String, String>> dayMoves,
        List<String> practical,
        List<String> mustTry,
        List<Map<String, String>> areas,
        List<Map<String, Object>> places
) {

    public static ItineraryRes toRes(
            Itinerary itinerary,
            ItineraryPlanningSnapshot planning,
            ItineraryTransportTip transportTip,
            ItineraryFoodRecommendation foodRecommendation,
            ItineraryTranslation translation
    ) {
        String responseTitle = itinerary.getTitle();
        String responseSummary = itinerary.getSummary();
        List<String> responseHighlights = itinerary.getHighlights();
        List<DayPlan> responseSchedule = itinerary.getDayPlans();

        if (translation != null) {
            responseTitle = translation.getTitle();
            responseSummary = translation.getSummary();
            responseHighlights = translation.getHighlights();
            responseSchedule = toTranslatedSchedule(
                    translation.getSchedule()
            );

            planning = toTranslatedPlanning(
                    translation.getPlanning(),
                    itinerary
            );

            transportTip = toTranslatedTransport(
                    translation.getTransport(),
                    itinerary
            );

            foodRecommendation = toTranslatedFoodRecommendation(
                    translation.getFoodRecommendation(),
                    itinerary
            );
        }

        return new ItineraryRes(
                itinerary.getId(),
                itinerary.getSlug(),
                itinerary.getRegion(),
                itinerary.getCountry(),
                itinerary.getCity(),
                responseTitle,
                itinerary.getDays(),
                itinerary.getPriceFrom(),
                itinerary.getHero(),
                responseSummary,
                responseHighlights,
                responseSchedule,

                planning.getCity(),
                planning.getBestTimeMonths(),
                planning.getBestTimeNote(),
                planning.getWorstTimeMonths(),
                planning.getWorstTimeNote(),
                planning.getTips(),
                planning.getWithKids(),

                transportTip.getArrival(),
                transportTip.getGettingAround(),
                transportTip.getDayTrips(),
                transportTip.getDayMoves(),
                transportTip.getPractical(),

                foodRecommendation.getMustTry(),
                foodRecommendation.getAreas(),
                foodRecommendation.getPlaces()
        );
    }

    private static List<DayPlan> toTranslatedSchedule(
            List<Map<String, Object>> schedule
    ) {
        return schedule.stream()
                .map(dayMap -> {
                    DayPlan dayPlan = new DayPlan();

                    dayPlan.setDay(
                            ((Number) dayMap.get("day")).intValue()
                    );
                    dayPlan.setTitle(
                            (String) dayMap.get("title")
                    );
                    dayPlan.setSummary(
                            (String) dayMap.get("summary")
                    );
                    dayPlan.setDetails(
                            (String) dayMap.get("details")
                    );

                    return dayPlan;
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static ItineraryPlanningSnapshot toTranslatedPlanning(
            Map<String, Object> planningMap,
            Itinerary itinerary
    ) {
        ItineraryPlanningSnapshot planning =
                new ItineraryPlanningSnapshot();

        Map<String, Object> bestTime =
                (Map<String, Object>) planningMap.get("bestTime");

        Map<String, Object> worstTime =
                (Map<String, Object>) planningMap.get("worstTime");

        planning.setItinerary(itinerary);
        planning.setCity(
                (String) planningMap.get("city")
        );

        planning.setBestTimeMonths(
                (String) bestTime.get("months")
        );
        planning.setBestTimeNote(
                (String) bestTime.get("note")
        );

        planning.setWorstTimeMonths(
                (String) worstTime.get("months")
        );
        planning.setWorstTimeNote(
                (String) worstTime.get("note")
        );

        planning.setTips(
                (List<String>) planningMap.get("tips")
        );
        planning.setWithKids(
                (List<String>) planningMap.get("withKids")
        );

        return planning;
    }

    @SuppressWarnings("unchecked")
    private static ItineraryTransportTip toTranslatedTransport(
            Map<String, Object> transportMap,
            Itinerary itinerary
    ) {
        ItineraryTransportTip transportTip =
                new ItineraryTransportTip();

        transportTip.setItinerary(itinerary);

        transportTip.setArrival(
                (List<Map<String, String>>)
                        transportMap.get("arrival")
        );

        transportTip.setGettingAround(
                (List<String>)
                        transportMap.get("gettingAround")
        );

        transportTip.setDayTrips(
                (List<Map<String, String>>)
                        transportMap.get("dayTrips")
        );

        transportTip.setDayMoves(
                (List<Map<String, String>>)
                        transportMap.get("dayMoves")
        );

        transportTip.setPractical(
                (List<String>)
                        transportMap.get("practical")
        );

        return transportTip;
    }

    @SuppressWarnings("unchecked")
    private static ItineraryFoodRecommendation
    toTranslatedFoodRecommendation(
            Map<String, Object> foodMap,
            Itinerary itinerary
    ) {
        ItineraryFoodRecommendation foodRecommendation =
                new ItineraryFoodRecommendation();

        foodRecommendation.setItinerary(itinerary);

        foodRecommendation.setMustTry(
                (List<String>) foodMap.get("mustTry")
        );

        foodRecommendation.setAreas(
                (List<Map<String, String>>) foodMap.get("areas")
        );

        foodRecommendation.setPlaces(
                (List<Map<String, Object>>) foodMap.get("places")
        );

        return foodRecommendation;
    }
}