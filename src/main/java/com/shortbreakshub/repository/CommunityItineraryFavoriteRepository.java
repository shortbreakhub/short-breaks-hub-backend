package com.shortbreakshub.repository;
import com.shortbreakshub.model.CommunityItineraryFavorite;
import com.shortbreakshub.model.CommunityItinerary;
import com.shortbreakshub.model.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityItineraryFavoriteRepository extends JpaRepository<CommunityItineraryFavorite, Long> {

    boolean existsByUserIdAndCommunityItineraryId(Long userId, Long communityItineraryId);

    long countByCommunityItineraryId(Long itineraryId);

    void deleteByUserIdAndCommunityItineraryId(Long userId, Long itineraryId);

    @Query(value = "select i from CommunityItineraryFavorite f join f.communityItinerary i join fetch i.user "
            + "where f.user.id = :userId and (i.visibility = :visibility or i.user.id = :userId) order by f.createdAt desc",
            countQuery = "select count(f) from CommunityItineraryFavorite f join f.communityItinerary i "
            + "where f.user.id = :userId and (i.visibility = :visibility or i.user.id = :userId)")
    Page<CommunityItinerary> findItinerariesFavoritedByUser(@Param("userId") Long userId,
                                                          @Param("visibility") Visibility visibility, Pageable page);
}
