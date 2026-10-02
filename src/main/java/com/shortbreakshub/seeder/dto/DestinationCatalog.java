package com.shortbreakshub.seeder.dto;

import com.shortbreakshub.model.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DestinationCatalog(
        @JsonProperty(required = true) int schemaVersion,
        @JsonProperty(required = true) String catalogVersion,
        @JsonProperty(required = true) int expectedDestinationCount,
        @JsonProperty(required = true) int expectedTripComMappedCount,
        @JsonProperty(required = true) int expectedTripComSkippedCount,
        @JsonProperty(required = true) List<Entry> destinations
) {
    public record Entry(String destinationKey, String name, String country,
                        List<String> itinerarySlugs, List<Decision> providerDecisions) {}

    public record Decision(ExternalProvider provider, ExternalEntityType entityType,
                           DestinationMappingStatus status, String externalId, String reason,
                           DestinationMappingFallback fallback, Replacement replaces) {}

    public record Replacement(DestinationMappingStatus status, String externalId) {}
}
