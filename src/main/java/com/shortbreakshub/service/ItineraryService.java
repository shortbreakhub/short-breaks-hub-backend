package com.shortbreakshub.service;

import com.shortbreakshub.dto.ItineraryRes;
import com.shortbreakshub.dto.HotelDestinationRes;
import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.ExternalDestinationMappingRepository;
import com.shortbreakshub.repository.DestinationMappingReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.shortbreakshub.repository.ItineraryFoodRecommendationRepository;
import com.shortbreakshub.repository.ItineraryPlanningSnapshotRepository;
import com.shortbreakshub.repository.ItineraryRepository;
import com.shortbreakshub.repository.ItineraryTransportTipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.shortbreakshub.repository.ItineraryTranslationRepository;

import java.util.List;

@Service
public class ItineraryService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryService.class);
    private final ExternalDestinationMappingRepository mappingRepo;
    private final DestinationMappingReviewRepository reviewRepo;
    private final ItineraryRepository itineraryRepo;
    private final ItineraryPlanningSnapshotRepository planningRepo;
    private final ItineraryTransportTipRepository transportTipRepo;
    private final ItineraryFoodRecommendationRepository foodRecommendationRepo;
    private final ItineraryTranslationRepository translationRepo;

    public ItineraryService(ItineraryRepository itineraryRepo,
                            ItineraryPlanningSnapshotRepository planningRepo,
                            ItineraryTransportTipRepository transportTipRepo,
                            ItineraryFoodRecommendationRepository foodRecommendationRepo,
                            ItineraryTranslationRepository translationRepo,
                            ExternalDestinationMappingRepository mappingRepo,
                            DestinationMappingReviewRepository reviewRepo) {
        this.mappingRepo = mappingRepo;
        this.reviewRepo = reviewRepo;
        this.itineraryRepo = itineraryRepo;
        this.planningRepo = planningRepo;
        this.transportTipRepo = transportTipRepo;
        this.foodRecommendationRepo = foodRecommendationRepo;
        this.translationRepo = translationRepo;
    }


    @Transactional(readOnly = true)
    public ItineraryRes getBySlug(String slug, String locale) {
        Itinerary itinerary = itineraryRepo.findBySlug(slug).orElse(null);
        if (itinerary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Itinerary Not Found");
        }
        ItineraryPlanningSnapshot planning = planningRepo.findByItinerary_Id(itinerary.getId()).orElse(null);
        if (planning == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Planning Not Found");
        }
        ItineraryTransportTip transportTip = transportTipRepo.findByItinerary_Id(itinerary.getId()).orElse(null);
        if (transportTip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"TransportTip Not Found");
        }
        ItineraryFoodRecommendation foodRecommendation = foodRecommendationRepo.findByItinerary_Id(itinerary.getId()).orElse(null);
        if (foodRecommendation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"FoodRecommendation Not Found");
        }
        ItineraryTranslation translation = translationRepo
                .findByItineraryIdAndLocale(itinerary.getId(), locale)
                .orElse(null);

        return ItineraryRes.toRes(itinerary,planning,transportTip,foodRecommendation,translation,resolveHotelDestination(itinerary));
    }

    private HotelDestinationRes resolveHotelDestination(Itinerary itinerary) {
        Destination destination = itinerary.getDestination();
        if (destination == null) return null;
        var review = reviewRepo.findByDestination_IdAndProviderAndEntityType(
                destination.getId(), ExternalProvider.TRIP_COM, ExternalEntityType.CITY).orElse(null);
        var mapping = mappingRepo.findByDestination_IdAndProviderAndEntityType(
                destination.getId(), ExternalProvider.TRIP_COM, ExternalEntityType.CITY).orElse(null);
        if (review == null && mapping == null) return null;
        boolean validMapped = review != null && review.getStatus() == DestinationMappingStatus.MAPPED
                && review.getFallback() == null && mapping != null
                && mapping.getExternalId() != null && !mapping.getExternalId().isBlank();
        boolean validSkipped = review != null && review.getStatus() == DestinationMappingStatus.SKIPPED
                && mapping == null && review.getReason() != null && !review.getReason().isBlank()
                && review.getFallback() == DestinationMappingFallback.DEFAULT_AFFILIATE_LINK;
        if (!validMapped && !validSkipped) {
            log.warn("Inconsistent TRIP_COM/CITY destination decision for itinerary {} and destination {}",
                    itinerary.getSlug(), destination.getDestinationKey());
            return null;
        }
        return new HotelDestinationRes(destination.getDestinationKey(), destination.getName(),
                ExternalProvider.TRIP_COM, ExternalEntityType.CITY, review.getStatus(),
                validMapped ? mapping.getExternalId() : null);
    }

    public List<String> getDistinctCountryByRegion(String region) {
        return itineraryRepo.findDistinctCountryByRegion(region);
    }

    public List<Itinerary> getByCountry(String country) {
        return itineraryRepo.findByCountry(country);
    }

    public List<Itinerary> getByRegion(String region) {
        return itineraryRepo.findItineraryEntitiesByRegion(region);
    }

    @Transactional(readOnly = true)
    public Page<Itinerary> getAllItinerariesByCustomSearch(
            String q, String country,
            Integer daysMin, Integer daysMax,
            Pageable pageable
    ) {
        return itineraryRepo.findAllItinerariesByCustomSearch(q, country, daysMin, daysMax,pageable);
    }

}

