package com.shortbreakshub.repository;

import com.shortbreakshub.model.ItineraryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItineraryTranslationRepository
        extends JpaRepository<ItineraryTranslation, Long> {

    Optional<ItineraryTranslation> findByItineraryIdAndLocale(
            Long itineraryId,
            String locale
    );

    Optional<ItineraryTranslation> findByItinerarySlugAndLocale(
            String slug,
            String locale
    );

    boolean existsByItineraryIdAndLocale(
            Long itineraryId,
            String locale
    );

    long countByLocale(String locale);
}