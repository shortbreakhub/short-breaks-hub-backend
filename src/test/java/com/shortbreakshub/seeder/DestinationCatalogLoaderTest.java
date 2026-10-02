package com.shortbreakshub.seeder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shortbreakshub.model.*;
import com.shortbreakshub.seeder.dto.DestinationCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DestinationCatalogLoaderTest {
    private final DestinationCatalogLoader loader = new DestinationCatalogLoader(new DefaultResourceLoader());
    private final ObjectMapper json = new ObjectMapper();
    @TempDir Path temporary;

    @Test
    void checkedInCatalogMatchesApprovedResearchAndExactBootstrapAssignments() throws Exception {
        var catalog = loader.load("classpath:seed/destinations.json");
        assertEquals(201, catalog.destinations().size());
        assertEquals(201, catalog.destinations().stream().map(DestinationCatalog.Entry::destinationKey).distinct().count());
        Set<String> slugs = catalog.destinations().stream().flatMap(e -> e.itinerarySlugs().stream()).collect(Collectors.toSet());
        assertEquals(201, slugs.size());
        JsonNode bootstrap = resource("seed/itineraries.json");
        Set<String> expectedSlugs = new HashSet<>();
        Map<String, Set<String>> labels = new HashMap<>();
        for (var row : bootstrap) {
            expectedSlugs.add(row.get("slug").asText());
            labels.computeIfAbsent(row.get("country").asText() + "/" + row.get("city").asText(), k -> new HashSet<>())
                    .add(row.get("slug").asText());
        }
        assertEquals(expectedSlugs, slugs);
        JsonNode research = resource("seed/trip-com-destination-mapping.json");
        assertEquals(201, research.get("expectedOfficialDestinationCount").asInt());
        Map<String, JsonNode> records = new HashMap<>();
        for (var row : research.get("mappings")) records.put(row.get("destinationName").asText(), row);
        int mapped = 0, skipped = 0;
        for (var entry : catalog.destinations()) {
            assertEquals(component(entry.country()) + "--" + component(entry.name()), entry.destinationKey());
            assertEquals(labels.get(entry.country() + "/" + entry.name()), new HashSet<>(entry.itinerarySlugs()));
            assertEquals(1, entry.providerDecisions().size());
            var decision = entry.providerDecisions().getFirst();
            JsonNode original = records.get(entry.name());
            assertEquals(original.get("status").asText(), decision.status().name());
            assertEquals(original.get("provider").asText(), decision.provider().name());
            assertEquals(original.get("entityType").asText(), decision.entityType().name());
            assertNull(decision.replaces());
            if (decision.status() == DestinationMappingStatus.MAPPED) {
                mapped++;
                assertEquals(original.get("externalId").asText(), decision.externalId());
            } else {
                skipped++;
                assertNull(decision.externalId());
                assertEquals(original.get("reason").asText(), decision.reason());
                assertEquals(DestinationMappingFallback.DEFAULT_AFFILIATE_LINK, decision.fallback());
            }
        }
        assertEquals(190, mapped);
        assertEquals(11, skipped);
    }

    @Test
    void strictParsingRejectsMalformedUnknownDuplicateAndCoercedValues() throws Exception {
        assertRejected("{");
        String valid = json.writeValueAsString(resource("seed/destinations.json"));
        assertRejected(valid + " {}");
        assertRejected(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"));
        assertRejected(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""));
        assertRejected(valid.replace("\"schemaVersion\":1,", ""));
        assertRejected(valid.replace("\"externalId\":\"36549\"", "\"externalId\":36549"));
        assertRejected(valid.replaceFirst("TRIP_COM", "TRIP_HOTEL"));
        assertRejected(valid.replaceFirst("\"entityType\":\"CITY\"", "\"entityType\":0"));
        ObjectNode unknown = (ObjectNode) resource("seed/destinations.json");
        unknown.put("unexpected", true);
        assertRejected(json.writeValueAsString(unknown));
    }

    @Test
    void validationRejectsDuplicateKeysAssignmentsScopesAndContradictoryDecisions() throws Exception {
        ObjectNode root = (ObjectNode) resource("seed/destinations.json");
        var entries = root.withArray("destinations");
        ObjectNode first = (ObjectNode) entries.get(0);
        ObjectNode second = (ObjectNode) entries.get(1);
        String originalKey = second.get("destinationKey").asText();
        second.put("destinationKey", first.get("destinationKey").asText());
        assertRejected(json.writeValueAsString(root));
        second.put("destinationKey", originalKey);
        second.withArray("itinerarySlugs").add(first.get("itinerarySlugs").get(0).asText());
        assertRejected(json.writeValueAsString(root));

        root = (ObjectNode) resource("seed/destinations.json");
        ObjectNode decision = (ObjectNode) root.get("destinations").get(0).get("providerDecisions").get(0);
        decision.putNull("externalId");
        assertRejected(json.writeValueAsString(root));

        root = (ObjectNode) resource("seed/destinations.json");
        for (var entry : root.get("destinations")) {
            ObjectNode skip = (ObjectNode) entry.get("providerDecisions").get(0);
            if (skip.get("status").asText().equals("SKIPPED")) {
                skip.put("externalId", "fake");
                assertRejected(json.writeValueAsString(root));
                skip.putNull("externalId");
                skip.put("reason", " ");
                assertRejected(json.writeValueAsString(root));
                break;
            }
        }
        root = (ObjectNode) resource("seed/destinations.json");
        var decisions = ((ObjectNode) root.get("destinations").get(0)).withArray("providerDecisions");
        decisions.add(decisions.get(0).deepCopy());
        assertRejected(json.writeValueAsString(root));
        root = (ObjectNode) resource("seed/destinations.json");
        root.put("expectedDestinationCount", 200);
        assertRejected(json.writeValueAsString(root));
    }

    @Test
    void rejectsFloatingPointValuesForEveryIntegerMetadataField() throws Exception {
        Map<String, Double> invalidValues = Map.of(
                "schemaVersion", 1.9,
                "expectedDestinationCount", 201.9,
                "expectedTripComMappedCount", 190.1,
                "expectedTripComSkippedCount", 11.5);
        for (var invalid : invalidValues.entrySet()) {
            ObjectNode root = (ObjectNode) resource("seed/destinations.json");
            root.put(invalid.getKey(), invalid.getValue());
            assertRejected(json.writeValueAsString(root));
        }
        assertEquals(201, loader.load("classpath:seed/destinations.json").expectedDestinationCount());
    }

    @Test
    void missingCatalogFailsRatherThanReturningEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> loader.load("file:" + temporary.resolve("missing.json")));
    }

    private void assertRejected(String content) throws Exception {
        Path file = temporary.resolve("invalid.json");
        Files.writeString(file, content);
        assertThrows(IllegalArgumentException.class, () -> loader.load(file.toUri().toString()), content.substring(0, Math.min(100, content.length())));
    }

    private JsonNode resource(String path) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) { return json.readTree(input); }
    }

    private static String component(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
