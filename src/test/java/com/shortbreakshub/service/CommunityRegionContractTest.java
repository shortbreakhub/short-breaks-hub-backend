package com.shortbreakshub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbreakshub.config.GlobalExceptionHandler;
import com.shortbreakshub.controller.CommunityItineraryController;
import com.shortbreakshub.model.CommunityItinerary;
import com.shortbreakshub.model.Region;
import com.shortbreakshub.model.Visibility;
import com.shortbreakshub.model.User;
import com.shortbreakshub.repository.CommunityItineraryRepository;
import com.shortbreakshub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityRegionContractTest {
    private CommunityItineraryRepository repository;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        repository = mock(CommunityItineraryRepository.class);
        var service = new UserItineraryService(mock(UserRepository.class), repository);
        mvc = MockMvcBuilders.standaloneSetup(new CommunityItineraryController(service, null))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"southeast-asia", "east-asia-community", "EUROPE", "americas-community",
            "anz-community", "africa-community", "AFRICA", "AMERICAS", "ASIA", "OCEANIA", "europe", "east-asia"})
    void supportedIdentifiersReturnEmptyArraysOnBothEndpoints(String identifier) throws Exception {
        var selection = CommunityRegionResolver.resolve(identifier).orElseThrow();
        when(repository.findItinerariesByRegionAndVisibility(selection.region().name(), Visibility.PUBLIC)).thenReturn(List.of());
        for (String path : paths(identifier)) mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(repository, times(2)).findItinerariesByRegionAndVisibility(selection.region().name(), Visibility.PUBLIC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"southeast-asia", "east-asia-community", "EUROPE", "americas-community",
            "anz-community", "africa-community", "AFRICA", "AMERICAS", "ASIA", "OCEANIA"})
    void bothEndpointsUseTheSamePublicClassification(String identifier) throws Exception {
        Region region = CommunityRegionResolver.resolve(identifier).orElseThrow().region();
        List<String> countries = switch (region) {
            case ASIA -> List.of("Japan", "Thailand", "India", "Korea", "  VIET NAM  ", "Republic of Korea", "Macau", "East Timor");
            case OCEANIA -> List.of("Australia", "New Zealand", "Fiji");
            case EUROPE -> List.of("Spain", "France");
            case AFRICA -> List.of("Kenya", "Morocco");
            case AMERICAS -> List.of("Canada", "Brazil");
        };
        List<String> expected = switch (identifier) {
            case "southeast-asia" -> List.of("Thailand", "  VIET NAM  ", "East Timor");
            case "east-asia-community" -> List.of("Japan", "Republic of Korea", "Macau");
            case "anz-community" -> List.of("Australia", "New Zealand");
            default -> countries;
        };
        // Defense-in-depth also excludes PRIVATE rows if a repository implementation returns one.
        var fixtures = Stream.concat(countries.stream().map(country -> itinerary(region, country, Visibility.PUBLIC)),
                Stream.of(itinerary(region, "Private destination", Visibility.PRIVATE))).toList();
        when(repository.findItinerariesByRegionAndVisibility(region.name(), Visibility.PUBLIC)).thenReturn(fixtures);
        var countryResponse = mvc.perform(get(paths(identifier).get(0))).andExpect(status().isOk()).andReturn();
        assertEquals(expected, json.readValue(countryResponse.getResponse().getContentAsString(), List.class));
        var itineraryResponse = mvc.perform(get(paths(identifier).get(1))).andExpect(status().isOk()).andReturn();
        var entries = json.readTree(itineraryResponse.getResponse().getContentAsString());
        assertEquals(expected.size(), entries.size());
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(expected.get(i), entries.get(i).get("country").asText());
            assertEquals("PUBLIC", entries.get(i).get("visibility").asText());
        }
        verify(repository, times(2)).findItinerariesByRegionAndVisibility(region.name(), Visibility.PUBLIC);
        verify(repository, never()).findDistinctCountryByRegionAndVisibility(anyString(), any());
    }

    @Test
    void countryNormalizationIsExplicitAndNeverGuessesAmbiguousNames() {
        var sea = CommunityRegionResolver.resolve("southeast-asia").orElseThrow();
        for (String country : List.of("Brunei Darussalam", "Lao People’s Democratic Republic", "Burma", "Viet Nam", "Timor–Leste", "  THAILAND\u00a0"))
            assertTrue(sea.includesCountry(country), country);
        var east = CommunityRegionResolver.resolve("east-asia-community").orElseThrow();
        for (String country : List.of("People's Republic of China", "Republic of Korea", "Democratic People’s Republic of Korea", "Hong Kong SAR", "Macau"))
            assertTrue(east.includesCountry(country), country);
        for (String country : List.of("Korea", "Asia", "Congo", "Unknown", "")) {
            assertFalse(sea.includesCountry(country));
            assertFalse(east.includesCountry(country));
        }
        assertFalse(sea.includesCountry(null));
        assertFalse(sea.includesCountry("Japan"));
        assertFalse(east.includesCountry("Thailand"));
        var anz = CommunityRegionResolver.resolve("anz-community").orElseThrow();
        assertTrue(anz.includesCountry(" new\u00a0zealand "));
        assertFalse(anz.includesCountry("Fiji"));
        assertFalse(anz.includesCountry("Australasia"));
    }

    @Test
    void unsupportedRegionsRemain404OnBothEndpoints() throws Exception {
        for (String identifier : List.of("unknown", "asian", "southeast", "ANZ"))
            for (String path : paths(identifier)) mvc.perform(get(path)).andExpect(status().isNotFound());
        verifyNoInteractions(repository);
    }

    private static List<String> paths(String identifier) {
        return List.of("/api/community-itineraries/region/" + identifier, "/api/community-itineraries/" + identifier);
    }

    private static CommunityItinerary itinerary(Region region, String country, Visibility visibility) {
        var item = new CommunityItinerary();
        var owner = new User("owner@example.com", "hash", "Owner", null, null, 1, 0);
        owner.setId(42L);
        item.setUser(owner);
        item.setRegion(region);
        item.setCountry(country);
        item.setSlug("test-trip");
        item.setTitle("Test itinerary");
        item.setVisibility(visibility);
        item.setDayPlans(List.of());
        return item;
    }
}
