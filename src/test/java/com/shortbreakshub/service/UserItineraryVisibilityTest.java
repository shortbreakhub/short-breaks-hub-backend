package com.shortbreakshub.service;

import com.shortbreakshub.controller.CommunityItineraryController;
import com.shortbreakshub.config.GlobalExceptionHandler;
import com.shortbreakshub.model.CommunityItinerary;
import com.shortbreakshub.model.Region;
import com.shortbreakshub.model.User;
import com.shortbreakshub.model.Visibility;
import com.shortbreakshub.repository.CommunityItineraryRepository;
import com.shortbreakshub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserItineraryVisibilityTest {

    private static final Long OWNER_ID = 42L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommunityItineraryRepository communityItineraryRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserItineraryService service = new UserItineraryService(userRepository, communityItineraryRepository);
        CommunityItineraryController controller = new CommunityItineraryController(service, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void anonymousPublicSlugLookupReturnsPublicItinerary() throws Exception {
        CommunityItinerary publicItinerary = itinerary(1L, "public-trip", "Spain", Visibility.PUBLIC);
        when(communityItineraryRepository.findBySlugAndVisibility("public-trip", Visibility.PUBLIC))
                .thenReturn(Optional.of(publicItinerary));

        mockMvc.perform(get("/api/community-itineraries/slug/public-trip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("public-trip"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        verify(communityItineraryRepository).findBySlugAndVisibility("public-trip", Visibility.PUBLIC);
    }

    @Test
    void anonymousPrivateSlugLookupBehavesAsNotFound() throws Exception {
        CommunityItinerary privateItinerary = itinerary(2L, "private-trip", "Japan", Visibility.PRIVATE);
        lenient().when(communityItineraryRepository.findBySlugAndVisibility("private-trip", Visibility.PRIVATE))
                .thenReturn(Optional.of(privateItinerary));
        when(communityItineraryRepository.findBySlugAndVisibility("private-trip", Visibility.PUBLIC))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/community-itineraries/slug/private-trip"))
                .andExpect(status().isNotFound());

        verify(communityItineraryRepository).findBySlugAndVisibility("private-trip", Visibility.PUBLIC);
    }

    @Test
    void anonymousRegionListingContainsOnlyPublicItineraries() throws Exception {
        CommunityItinerary publicItinerary = itinerary(1L, "public-trip", "Spain", Visibility.PUBLIC);
        CommunityItinerary privateItinerary = itinerary(2L, "private-trip", "Japan", Visibility.PRIVATE);
        lenient().when(communityItineraryRepository.findItinerariesByRegionAndVisibility("EUROPE", Visibility.PRIVATE))
                .thenReturn(List.of(privateItinerary));
        when(communityItineraryRepository.findItinerariesByRegionAndVisibility("EUROPE", Visibility.PUBLIC))
                .thenReturn(List.of(publicItinerary));

        mockMvc.perform(get("/api/community-itineraries/EUROPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("public-trip"))
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"));

        verify(communityItineraryRepository)
                .findItinerariesByRegionAndVisibility("EUROPE", Visibility.PUBLIC);
    }

    @Test
    void anonymousCountryDiscoveryDoesNotReturnCountryFromPrivateOnlyRegion() throws Exception {
        lenient().when(communityItineraryRepository.findItinerariesByRegionAndVisibility("OCEANIA", Visibility.PRIVATE))
                .thenReturn(List.of(itinerary(9L, "private-australia", "Australia", Visibility.PRIVATE)));
        when(communityItineraryRepository.findItinerariesByRegionAndVisibility("OCEANIA", Visibility.PUBLIC))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/community-itineraries/region/OCEANIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(communityItineraryRepository)
                .findItinerariesByRegionAndVisibility("OCEANIA", Visibility.PUBLIC);
    }

    @Test
    void validEmptyRegionListingReturnsEmptyArray() throws Exception {
        when(communityItineraryRepository.findItinerariesByRegionAndVisibility("EUROPE", Visibility.PUBLIC))
                .thenReturn(List.of());
        mockMvc.perform(get("/api/community-itineraries/europe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verify(communityItineraryRepository).findItinerariesByRegionAndVisibility("EUROPE", Visibility.PUBLIC);
    }

    @Test
    void nonEmptyCountryDiscoveryReturnsPublicCountries() throws Exception {
        when(communityItineraryRepository.findItinerariesByRegionAndVisibility("EUROPE", Visibility.PUBLIC))
                .thenReturn(List.of(itinerary(10L, "spain", "Spain", Visibility.PUBLIC), itinerary(11L, "france", "France", Visibility.PUBLIC)));
        mockMvc.perform(get("/api/community-itineraries/region/EUROPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("Spain"))
                .andExpect(jsonPath("$[1]").value("France"));
        verify(communityItineraryRepository).findItinerariesByRegionAndVisibility("EUROPE", Visibility.PUBLIC);
    }

    @Test
    void unsupportedRegionRemainsNotFoundOnBothCollections() throws Exception {
        for (String path : List.of("/api/community-itineraries/UNKNOWN", "/api/community-itineraries/region/UNKNOWN")) {
            mockMvc.perform(get(path)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
        verify(communityItineraryRepository, never()).findItinerariesByRegionAndVisibility("UNKNOWN", Visibility.PUBLIC);
    }

    @Test
    void serverFailuresRemainErrorsOnBothCollections() throws Exception {
        when(communityItineraryRepository.findItinerariesByRegionAndVisibility("EUROPE", Visibility.PUBLIC))
                .thenThrow(new IllegalStateException("Synthetic repository failure"));
        for (String path : List.of("/api/community-itineraries/EUROPE", "/api/community-itineraries/region/EUROPE")) {
            mockMvc.perform(get(path)).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500));
        }
    }

    @Test
    void ownerMeStillReturnsOwnersPrivateItinerary() throws Exception {
        CommunityItinerary privateItinerary = itinerary(2L, "owner-private-trip", "Japan", Visibility.PRIVATE);
        when(communityItineraryRepository.findUserItinerariesByUser_Id(eq(OWNER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(privateItinerary), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/community-itineraries/me").requestAttr("authUserId", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("owner-private-trip"))
                .andExpect(jsonPath("$.content[0].visibility").value("PRIVATE"));

        verify(communityItineraryRepository).findUserItinerariesByUser_Id(eq(OWNER_ID), any());
    }

    private static CommunityItinerary itinerary(Long id, String slug, String country, Visibility visibility) {
        User owner = new User("owner@example.com", "hash", "Owner", null, null, 1, 0);
        owner.setId(OWNER_ID);

        CommunityItinerary itinerary = new CommunityItinerary();
        itinerary.setId(id);
        itinerary.setUser(owner);
        itinerary.setSlug(slug);
        itinerary.setCountry(country);
        itinerary.setRegion(Region.EUROPE);
        itinerary.setDays(2);
        itinerary.setTitle(slug);
        itinerary.setSummary("A short itinerary");
        itinerary.setCoverPhoto("cover.jpg");
        itinerary.setHighlights("Highlights");
        itinerary.setVisibility(visibility);
        itinerary.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        itinerary.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        itinerary.setDayPlans(List.of());
        return itinerary;
    }
}
