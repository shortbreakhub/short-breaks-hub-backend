package com.shortbreakshub.repository;

import com.shortbreakshub.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DestinationMappingReviewRepository extends JpaRepository<DestinationMappingReview, Long> {
    List<DestinationMappingReview> findByDestination_Id(Long destinationId);
    Optional<DestinationMappingReview> findByDestination_IdAndProviderAndEntityType(
            Long destinationId, ExternalProvider provider, ExternalEntityType entityType);
}
