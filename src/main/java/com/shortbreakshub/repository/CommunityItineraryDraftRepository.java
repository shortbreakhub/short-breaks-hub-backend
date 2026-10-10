package com.shortbreakshub.repository;
import com.shortbreakshub.model.CommunityItineraryDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface CommunityItineraryDraftRepository extends JpaRepository<CommunityItineraryDraft,Long> {

    int countByUser_Id(long id);

    List<CommunityItineraryDraft> findByUser_Id(long id);

    Optional<CommunityItineraryDraft> findByIdAndUser_Id(long id,long userId);

    boolean existsByUser_IdAndCoverPhoto(Long userId, String coverPhoto);

}
