package com.shortbreakshub.repository;

import com.shortbreakshub.model.Itinerary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {
    Optional<Itinerary> findBySlug(String slug);

    @Query("select i.slug as slug, i.region as region, i.country as country from Itinerary i")
    List<ItinerarySitemapProjection> findSitemapLocations();

    @Query("select distinct i.country from Itinerary i where LOWER(i.region) = lower(:region) ")
    List<String> findDistinctCountryByRegion(@Param("region") String region);

    @Query("select i from Itinerary i where LOWER(i.region) = lower(:region) ")
    List<Itinerary> findItineraryEntitiesByRegion (@Param("region") String region);

    @Query(
            value = """
    select *
    from itineraries i
    where
      (:q is null or
        i.title   ilike concat('%', :q, '%') or
        i.summary ilike concat('%', :q, '%') or
        i.country ilike concat('%', :q, '%') or
        i.city    ilike concat('%', :q, '%')
      )
      and (:country  is null or i.country ilike :country)
      and (:daysMin  is null or i.days      >= :daysMin)
      and (:daysMax  is null or i.days      <= :daysMax)
  """,
            countQuery = """
    select count(*)
    from itineraries i
    where
      (:q is null or
        i.title   ilike concat('%', :q, '%') or
        i.summary ilike concat('%', :q, '%') or
        i.country ilike concat('%', :q, '%') or
        i.city    ilike concat('%', :q, '%')
      )
      and (:country  is null or i.country ilike :country)
      and (:daysMin  is null or i.days      >= :daysMin)
      and (:daysMax  is null or i.days      <= :daysMax)
  """,
            nativeQuery = true
    )
    Page<Itinerary> findAllItinerariesByCustomSearch(String q,String country, Integer daysMin,
                                                     Integer daysMax, Pageable pageable);


    List<Itinerary> findByCountry(String country);
}

