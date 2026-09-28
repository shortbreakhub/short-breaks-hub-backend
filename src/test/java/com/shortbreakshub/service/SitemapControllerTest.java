package com.shortbreakshub.service;

import com.shortbreakshub.controller.SitemapController;
import com.shortbreakshub.repository.ItineraryRepository;
import com.shortbreakshub.repository.ItinerarySitemapProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SitemapControllerTest {

    private static final String CANONICAL_ORIGIN = "https://www.shortbreakhub.com";
    private static final String WRONG_HISTORICAL_ORIGIN = "https://www.shortbreakshub.com";

    @Mock
    private ItineraryRepository itineraryRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SitemapService sitemapService = new SitemapService(itineraryRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(new SitemapController(sitemapService)).build();
        lenient().when(itineraryRepository.findSitemapLocations()).thenReturn(List.of());
    }

    @Test
    void endpointReturnsSuccessfulUtf8XmlWithCanonicalOrigin() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().contentType("application/xml;charset=UTF-8"));

        verify(itineraryRepository).findSitemapLocations();
    }

    @Test
    void sitemapIncludesOfficialItinerariesWithoutRequiringOptionalSupportingData() throws Exception {
        Row france = new Row("paris-weekend", "europe", "France");
        Row franceWithoutOptionalData = new Row("lyon-weekend", "europe", "France");
        Row invalidSlug = new Row("../bad-slug", "europe", "Brokenland");

        when(itineraryRepository.findSitemapLocations())
                .thenReturn(List.of(france, franceWithoutOptionalData, invalidSlug));

        Set<String> urls = sitemapUrls();

        assertTrue(urls.contains(CANONICAL_ORIGIN + "/"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/contact"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/privacy"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/terms"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/europe"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/browse/France"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/itinerary/paris-weekend"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/itinerary/lyon-weekend"));
        assertFalse(urls.contains(CANONICAL_ORIGIN + "/itinerary/../bad-slug"));
        assertFalse(urls.contains(CANONICAL_ORIGIN + "/browse/Brokenland"));
    }

    @Test
    void countryRoutesThatTheFrontendCannotResolveAreExcluded() throws Exception {
        Row hyphenatedCountry = new Row("timor-trip", "asia", "Timor-Leste");
        when(itineraryRepository.findSitemapLocations()).thenReturn(List.of(hyphenatedCountry));

        Set<String> urls = sitemapUrls();

        assertTrue(urls.contains(CANONICAL_ORIGIN + "/itinerary/timor-trip"));
        assertFalse(urls.contains(CANONICAL_ORIGIN + "/browse/Timor-Leste"));
    }

    @Test
    void sitemapDoesNotExposeCommunityPrivateAccountAuthenticationOrApiRoutes() throws Exception {
        Row eligible = new Row("public-trip", "europe", "France");
        when(itineraryRepository.findSitemapLocations()).thenReturn(List.of(eligible));

        Set<String> urls = sitemapUrls();

        assertFalse(urls.stream().anyMatch(url -> url.contains("/user-itinerary/")));
        assertFalse(urls.stream().anyMatch(url -> url.contains("/community-itineraries/")));
        assertFalse(urls.stream().anyMatch(url -> url.contains("/api/")));
        assertFalse(urls.contains(CANONICAL_ORIGIN + "/api"));
        assertFalse(urls.contains(CANONICAL_ORIGIN + "/login"));
        assertFalse(urls.contains(CANONICAL_ORIGIN + "/register"));
        assertFalse(urls.contains(CANONICAL_ORIGIN + "/profile"));
        assertFalse(urls.contains(CANONICAL_ORIGIN + "/me"));
        assertFalse(urls.stream().anyMatch(url -> url.contains("/reset-password")));
        assertFalse(urls.stream().anyMatch(url -> url.contains("/forgot-password")));
        assertTrue(urls.stream().allMatch(url -> url.startsWith(CANONICAL_ORIGIN + "/")));
        assertFalse(urls.stream().anyMatch(url -> url.startsWith(WRONG_HISTORICAL_ORIGIN)));
    }

    @Test
    void sitemapXmlIsWellFormedEscapedAndContainsNoInventedMetadata() throws Exception {
        Row escaped = new Row("harbor & hills", "north & west", "France");
        when(itineraryRepository.findSitemapLocations()).thenReturn(List.of(escaped));

        String xml = responseXml();
        Set<String> urls = parseUrls(xml);

        assertTrue(xml.contains("&amp;"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/north%20&%20west"));
        assertTrue(urls.contains(CANONICAL_ORIGIN + "/itinerary/harbor%20&%20hills"));
        assertFalse(xml.contains("<lastmod>"));
        assertFalse(xml.contains("<priority>"));
        assertFalse(xml.contains("<changefreq>"));
    }

    @Test
    void sitemapReflectsDatabaseChangesOnTheNextRequest() throws Exception {
        Row itinerary = new Row("newly-created-trip", "europe", "France");
        when(itineraryRepository.findSitemapLocations()).thenReturn(List.of(itinerary), List.of());

        assertTrue(parseUrls(responseXml()).contains(CANONICAL_ORIGIN + "/itinerary/newly-created-trip"));
        assertFalse(parseUrls(responseXml()).contains(CANONICAL_ORIGIN + "/itinerary/newly-created-trip"));
    }

    @Test
    void sitemapUrlsAreSortedAndDeduplicated() throws Exception {
        Row first = new Row("first-trip", "europe", "France");
        Row second = new Row("second-trip", "europe", "France");
        when(itineraryRepository.findSitemapLocations()).thenReturn(List.of(first, second));

        List<String> urls = parseUrlList(responseXml());

        assertEquals(urls.stream().sorted().toList(), urls);
        assertEquals(urls.size(), new HashSet<>(urls).size());
    }

    private String responseXml() throws Exception {
        return mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Set<String> sitemapUrls() throws Exception {
        return parseUrls(responseXml());
    }

    private static Set<String> parseUrls(String xml) throws Exception {
        return Set.copyOf(parseUrlList(xml));
    }

    private static List<String> parseUrlList(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var nodes = document.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9", "loc");
        return java.util.stream.IntStream.range(0, nodes.getLength())
                .mapToObj(index -> nodes.item(index).getTextContent())
                .toList();
    }

    private record Row(String slug, String region, String country) implements ItinerarySitemapProjection {
        @Override
        public String getSlug() {
            return slug;
        }

        @Override
        public String getRegion() {
            return region;
        }

        @Override
        public String getCountry() {
            return country;
        }
    }
}
