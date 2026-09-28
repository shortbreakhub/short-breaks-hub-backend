package com.shortbreakshub.repository;

import com.shortbreakshub.dto.UserItineraryRes;
import com.shortbreakshub.model.CommunityItinerary;
import com.shortbreakshub.model.Itinerary;
import com.shortbreakshub.model.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityItineraryRepository extends JpaRepository<CommunityItinerary,Long> {

    Optional<CommunityItinerary> findBySlug(String slug);

    Optional<CommunityItinerary> findBySlugAndVisibility(String slug, Visibility visibility);

    Page<CommunityItinerary> findUserItinerariesByUser_Id(Long userId, Pageable pageable);

    @Query("select distinct i.country from CommunityItinerary i where LOWER(i.region) = lower(:region) and i.visibility = :visibility")
    List<String> findDistinctCountryByRegionAndVisibility(@Param("region") String region,
                                                          @Param("visibility") Visibility visibility);

    @Query("select i from CommunityItinerary i where LOWER(i.region) = lower(:region) and i.visibility = :visibility")
    List<CommunityItinerary> findItinerariesByRegionAndVisibility(@Param("region") String region,
                                                                  @Param("visibility") Visibility visibility);
}
