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
        List<Map<String,String>> arrival,
        List<String> gettingAround,
        List<Map<String,String>> dayTrips,
        List<Map<String,String>> dayMoves,
        List<String> practical
) {
    public static ItineraryRes toRes(Itinerary itinerary,
                                     ItineraryPlanningSnapshot  planning,
                                     ItineraryTransportTip transportTip) {

        return new ItineraryRes(
                itinerary.getId(),
                itinerary.getSlug(),
                itinerary.getRegion(),
                itinerary.getCountry(),
                itinerary.getCity(),
                itinerary.getTitle(),
                itinerary.getDays(),
                itinerary.getPriceFrom(),
                itinerary.getHero(),
                itinerary.getSummary(),
                itinerary.getHighlights(),
                itinerary.getDayPlans(),
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
                transportTip.getPractical()
        );
    }
}
