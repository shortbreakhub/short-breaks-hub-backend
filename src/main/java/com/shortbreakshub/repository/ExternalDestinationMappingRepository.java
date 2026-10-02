package com.shortbreakshub.repository;

import com.shortbreakshub.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExternalDestinationMappingRepository extends JpaRepository<ExternalDestinationMapping, Long> {
    List<ExternalDestinationMapping> findByDestination_Id(Long destinationId);
    Optional<ExternalDestinationMapping> findByDestination_IdAndProviderAndEntityType(
            Long destinationId, ExternalProvider provider, ExternalEntityType entityType);
}
