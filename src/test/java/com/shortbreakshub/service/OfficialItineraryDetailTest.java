package com.shortbreakshub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbreakshub.PostgresTestSupport;
import com.shortbreakshub.controller.ItineraryController;
import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.*;
import com.shortbreakshub.seeder.*;
import com.shortbreakshub.seeder.dto.DestinationCatalog;
import org.hibernate.SessionFactory;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class OfficialItineraryDetailTest extends PostgresTestSupport {
    @Autowired ItineraryService service;
    @Autowired ItineraryRepository itineraries;
    @Autowired DestinationCatalogLoader loader;
    @Autowired DestinationImportService importer;
    @Autowired ItineraryTranslationRepository translations;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EntityManagerFactory emf;
    MockMvc mvc;
    DestinationCatalog full;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE itineraries, destinations RESTART IDENTITY CASCADE");
        full = loader.load("classpath:seed/destinations.json");
        mvc = MockMvcBuilders.standaloneSetup(new ItineraryController(service))
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver()).build();
    }

    private String fixture(String name, boolean linked) {
        var entry = full.destinations().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
        String slug = entry.itinerarySlugs().getFirst();
        var itinerary = itineraries.save(new Itinerary(slug, "fixture-region", entry.country(), name,
                "Preserved title", 3, 99, "hero", "Summary", List.of("Highlight"), List.of()));
        jdbc.update("insert into itinerary_planning_snapshot(itinerary_id,city,best_time_months,best_time_note,worst_time_months,worst_time_note,tips,with_kids,updated_at) values (?,?,'April','Best','January','Worst','[\"Tip\"]','[]',now())", itinerary.getId(), name);
        jdbc.update("insert into itinerary_transport_tip(itinerary_id,arrival,getting_around,day_trips,day_moves,practical,updated_at) values (?,'[]','[\"Walk\"]','[]','[]','[]',now())", itinerary.getId());
        jdbc.update("insert into itinerary_food_recommendation(itinerary_id,must_try,areas,places,updated_at) values (?,'[\"Food\"]','[]','[]',now())", itinerary.getId());
        if (linked) {
            boolean mapped = entry.providerDecisions().getFirst().status() == DestinationMappingStatus.MAPPED;
            importer.apply(new DestinationCatalog(1, "test", 1, mapped ? 1 : 0, mapped ? 0 : 1, List.of(entry)));
        }
        return slug;
    }

    @ParameterizedTest @CsvSource({"Shanghai,china--shanghai,2", "Kyoto,japan--kyoto,734"})
    void mappedDescriptorIsExact(String name, String key, String id) throws Exception {
        String slug = fixture(name, true);
        var result = mvc.perform(get("/api/itineraries/slug/" + slug))
                .andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(json.valueToTree(Map.of("destinationKey", key, "name", name, "provider", "TRIP_COM",
                "entityType", "CITY", "status", "MAPPED", "externalId", id)), body.get("hotelDestination"));
        assertTrue(body.get("hotelDestination").get("externalId").isTextual());
        assertEquals(slug, body.get("slug").asText());
        assertEquals(name, body.get("city").asText());
        assertEquals("fixture-region", body.get("region").asText());
        assertEquals(name.equals("Shanghai") ? "China" : "Japan", body.get("country").asText());
        assertEquals(3, body.get("days").asInt());
        assertEquals("Preserved title", body.get("title").asText());
        assertEquals("Tip", body.get("tips").get(0).asText());
        assertEquals("Walk", body.get("gettingAround").get(0).asText());
        assertEquals("Food", body.get("mustTry").get(0).asText());
        assertFalse(body.has("destination"));
    }

    @Test void skippedDescriptorContainsOnlyApprovedFields() throws Exception {
        var body = json.readTree(mvc.perform(get("/api/itineraries/slug/" + fixture("Tasmania", true)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(json.readTree("{\"destinationKey\":\"australia--tasmania\",\"name\":\"Tasmania\",\"provider\":\"TRIP_COM\",\"entityType\":\"CITY\",\"status\":\"SKIPPED\",\"externalId\":null}"), body.get("hotelDestination"));
    }

    @Test void noDestinationIsExplicitNull() throws Exception {
        String slug = fixture("Shanghai", false);
        var body = json.readTree(mvc.perform(get("/api/itineraries/slug/" + slug)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(body.has("hotelDestination"));
        assertTrue(body.get("hotelDestination").isNull());
        assertEquals("Preserved title", body.get("title").asText());
    }

    @ParameterizedTest @CsvSource({"none", "mappedWithoutMapping", "skippedWithMapping", "mappingWithoutReview", "otherScope"})
    void missingOrContradictoryDecisionIsUnavailable(String state) {
        String slug = fixture(state.equals("skippedWithMapping") ? "Tasmania" : "Shanghai", true);
        if (state.equals("skippedWithMapping")) {
            jdbc.update("insert into external_destination_mappings(destination_id,provider,entity_type,external_id) select id,'TRIP_COM','CITY','unexpected' from destinations");
        } else if (state.equals("mappingWithoutReview")) {
            jdbc.update("delete from destination_mapping_reviews");
        } else if (state.equals("mappedWithoutMapping")) {
            jdbc.update("delete from external_destination_mappings");
        } else if (state.equals("otherScope")) {
            jdbc.update("update external_destination_mappings set provider='GOOGLE',entity_type='PLACE'");
            jdbc.update("update destination_mapping_reviews set provider='GOOGLE',entity_type='PLACE'");
        } else {
            jdbc.update("delete from external_destination_mappings");
            jdbc.update("delete from destination_mapping_reviews");
        }
        assertNull(service.getBySlug(slug, "en").hotelDestination());
    }

    @Test void detachedDtoSerializesWithoutQueries() throws Exception {
        String slug = fixture("Shanghai", true);
        var response = service.getBySlug(slug, "en");
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertEquals("2", json.readTree(json.writeValueAsString(response)).get("hotelDestination").get("externalId").asText());
        assertEquals(0, statistics.getPrepareStatementCount());
    }

    @Test void listContractsRemainUnchanged() throws Exception {
        fixture("Shanghai", true);
        for (String path : List.of("/api/itineraries/browse/China", "/api/itineraries/fixture-region")) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].destination").doesNotExist())
                    .andExpect(jsonPath("$[0].hotelDestination").doesNotExist());
        }
        mvc.perform(get("/api/itineraries/search").param("sort", "slug,asc").param("q", "Preserved")
                        .param("country", "China").param("daysMin", "1").param("daysMax", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].destination").doesNotExist())
                .andExpect(jsonPath("$.content[0].hotelDestination").doesNotExist());
    }

    @Test void translationPreservesCanonicalDescriptor() {
        String slug = fixture("Shanghai", true);
        var translation = new ItineraryTranslation();
        translation.setItinerary(itineraries.findBySlug(slug).orElseThrow());
        translation.setLocale("fr"); translation.setTitle("Translated title"); translation.setSummary("Translated summary");
        translation.setPlanning(Map.of("city", "Translated city", "bestTime", Map.of("months", "May", "note", "Best"),
                "worstTime", Map.of("months", "June", "note", "Worst"), "tips", List.of("Translated tip"), "withKids", List.of()));
        translation.setTransport(Map.of("arrival", List.of(), "gettingAround", List.of("Translated transport"),
                "dayTrips", List.of(), "dayMoves", List.of(), "practical", List.of()));
        translation.setFoodRecommendation(Map.of("mustTry", List.of("Translated food"), "areas", List.of(), "places", List.of()));
        translations.save(translation);
        var response = service.getBySlug(slug, "fr");
        assertEquals("Translated title", response.title());
        assertEquals("Translated summary", response.summary());
        assertEquals("Translated city", response.planningCity());
        assertEquals(List.of("Translated transport"), response.gettingAround());
        assertEquals(List.of("Translated food"), response.mustTry());
        assertEquals("Shanghai", response.city());
        assertEquals("Shanghai", response.hotelDestination().name());
        assertEquals("2", response.hotelDestination().externalId());
    }

    @Test void unknownItineraryRemainsNotFound() throws Exception {
        mvc.perform(get("/api/itineraries/slug/unknown")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"itinerary_food_recommendation,FoodRecommendation Not Found",
            "itinerary_transport_tip,TransportTip Not Found", "itinerary_planning_snapshot,Planning Not Found"})
    void missingSupportingRecordRemainsNotFound(String table, String reason) throws Exception {
        String slug = fixture("Shanghai", true);
        jdbc.update("delete from " + table);
        var result = mvc.perform(get("/api/itineraries/slug/" + slug)).andExpect(status().isNotFound()).andReturn();
        assertEquals(reason, ((org.springframework.web.server.ResponseStatusException) result.getResolvedException()).getReason());
    }
}
