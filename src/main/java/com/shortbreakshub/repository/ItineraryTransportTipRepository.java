package com.shortbreakshub.repository;


import com.shortbreakshub.model.ItineraryTransportTip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItineraryTransportTipRepository extends JpaRepository<ItineraryTransportTip, Long> {

    Optional<ItineraryTransportTip> findByItinerary_Id(Long itineraryId);

    boolean existsByItinerary_Id(Long itineraryId);
}