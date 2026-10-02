package com.shortbreakshub.repository;

import com.shortbreakshub.model.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DestinationRepository extends JpaRepository<Destination, Long> {
    Optional<Destination> findByDestinationKey(String destinationKey);
}
