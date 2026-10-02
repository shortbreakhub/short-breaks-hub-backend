package com.shortbreakshub.dto;

import com.shortbreakshub.model.DestinationMappingStatus;
import com.shortbreakshub.model.ExternalEntityType;
import com.shortbreakshub.model.ExternalProvider;

public record HotelDestinationRes(
        String destinationKey,
        String name,
        ExternalProvider provider,
        ExternalEntityType entityType,
        DestinationMappingStatus status,
        String externalId
) {}
