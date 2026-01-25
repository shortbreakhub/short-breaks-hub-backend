package com.shortbreakshub.repository;

import com.shortbreakshub.model.ItineraryFoodRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItineraryFoodRecommendationRepository extends JpaRepository<ItineraryFoodRecommendation,Long> {

    Optional<ItineraryFoodRecommendation> findByItinerary_Id(Long itineraryId);

    boolean existsByItinerary_Id(Long itineraryId);
}
