package com.shortbreakshub.seeder;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.shortbreakshub.model.*;
import com.shortbreakshub.seeder.dto.DestinationCatalog;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class DestinationCatalogLoader {
    private final ResourceLoader resources;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    public DestinationCatalogLoader(ResourceLoader resources) {
        this.resources = resources;
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    public DestinationCatalog load(String location) {
        try (var input = resources.getResource(location).getInputStream()) {
            DestinationCatalog catalog = mapper.readValue(input, DestinationCatalog.class);
            validate(catalog);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read destination catalog: " + location, exception);
        }
    }

    public void validate(DestinationCatalog catalog) {
        require(catalog != null && catalog.schemaVersion() == 1, "Unsupported catalog schemaVersion");
        text(catalog.catalogVersion(), 255, "catalogVersion");
        require(catalog.destinations() != null && !catalog.destinations().isEmpty(), "Empty destination catalog");
        require(catalog.expectedDestinationCount() == catalog.destinations().size(), "Destination count mismatch");
        require(catalog.expectedTripComMappedCount() >= 0 && catalog.expectedTripComSkippedCount() >= 0,
                "Negative decision counts");
        Set<String> keys = new HashSet<>();
        Set<String> labels = new HashSet<>();
        Set<String> slugs = new HashSet<>();
        int mapped = 0;
        int skipped = 0;
        for (var entry : catalog.destinations()) {
            require(entry != null, "Null destination entry");
            text(entry.destinationKey(), 255, "destinationKey");
            text(entry.name(), 255, "name");
            text(entry.country(), 255, "country");
            require(keys.add(entry.destinationKey()), "Duplicate destinationKey: " + entry.destinationKey());
            require(labels.add(entry.country() + "\u0000" + entry.name()), "Duplicate destination label: " + entry.name());
            require(entry.itinerarySlugs() != null && !entry.itinerarySlugs().isEmpty(), "Missing itinerary assignments");
            for (String slug : entry.itinerarySlugs()) {
                text(slug, 255, "itinerary slug");
                require(slugs.add(slug), "Duplicate itinerary assignment: " + slug);
            }
            require(entry.providerDecisions() != null && !entry.providerDecisions().isEmpty(), "Missing provider decisions");
            Set<String> scopes = new HashSet<>();
            for (var decision : entry.providerDecisions()) {
                require(decision != null && decision.provider() != null && decision.entityType() != null
                        && decision.status() != null, "Missing provider/type/status");
                require(scopes.add(decision.provider() + "/" + decision.entityType()), "Duplicate provider scope");
                if (decision.status() == DestinationMappingStatus.MAPPED) {
                    text(decision.externalId(), 255, "externalId");
                    require(decision.fallback() == null, "MAPPED must not specify fallback");
                    if (decision.provider() == ExternalProvider.TRIP_COM) mapped++;
                } else {
                    require(decision.externalId() == null, "SKIPPED must not specify externalId");
                    text(decision.reason(), Integer.MAX_VALUE, "SKIPPED reason");
                    require(decision.fallback() == DestinationMappingFallback.DEFAULT_AFFILIATE_LINK,
                            "SKIPPED requires DEFAULT_AFFILIATE_LINK");
                    if (decision.provider() == ExternalProvider.TRIP_COM) skipped++;
                }
                if (decision.reason() != null) text(decision.reason(), Integer.MAX_VALUE, "reason");
                if (decision.replaces() != null) {
                    require(decision.replaces().status() != null, "Replacement requires previous status");
                    if (decision.replaces().status() == DestinationMappingStatus.MAPPED) {
                        text(decision.replaces().externalId(), 255, "previous externalId");
                    } else {
                        require(decision.replaces().externalId() == null, "Previous SKIPPED cannot have externalId");
                    }
                }
            }
        }
        require(mapped == catalog.expectedTripComMappedCount(), "Trip.com MAPPED count mismatch");
        require(skipped == catalog.expectedTripComSkippedCount(), "Trip.com SKIPPED count mismatch");
    }

    private static void text(String value, int limit, String field) {
        require(value != null && !value.isBlank() && value.equals(value.strip()) && value.length() <= limit
                && value.codePoints().noneMatch(Character::isISOControl), "Invalid " + field);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
