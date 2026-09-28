package com.shortbreakshub.service;

import com.shortbreakshub.repository.ItineraryRepository;
import com.shortbreakshub.repository.ItinerarySitemapProjection;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SitemapService {

    private static final String CANONICAL_ORIGIN = "https://www.shortbreakhub.com";
    private static final String SITEMAP_NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final Set<String> STATIC_PUBLIC_PATHS = Set.of("/", "/contact", "/privacy", "/terms");

    private final ItineraryRepository itineraryRepository;

    public SitemapService(ItineraryRepository itineraryRepository) {
        this.itineraryRepository = itineraryRepository;
    }

    @Transactional(readOnly = true)
    public String generateXml() {
        List<ItinerarySitemapProjection> itineraries = itineraryRepository.findSitemapLocations();

        Set<String> paths = new TreeSet<>(STATIC_PUBLIC_PATHS);

        itineraries.stream()
                .map(ItinerarySitemapProjection::getRegion)
                .filter(SitemapService::isUsableSegment)
                .map(region -> "/" + encodeSegment(region.toLowerCase(Locale.ROOT)))
                .forEach(paths::add);

        Map<String, List<ItinerarySitemapProjection>> byCountry = itineraries.stream()
                .filter(row -> row.getCountry() != null)
                .collect(Collectors.groupingBy(ItinerarySitemapProjection::getCountry));

        byCountry.keySet().stream()
                .filter(SitemapService::isUsableSegment)
                .filter(country -> allRowsForCountryHaveUsableSlugs(country, byCountry))
                .map(SitemapService::countryPathIfResolvable)
                .filter(Objects::nonNull)
                .forEach(paths::add);

        itineraries.stream()
                .map(ItinerarySitemapProjection::getSlug)
                .filter(SitemapService::isUsableSegment)
                .map(slug -> "/itinerary/" + encodeSegment(slug))
                .forEach(paths::add);

        return writeXml(paths);
    }

    private static String countryPathIfResolvable(String country) {
        String backendCountry = WordUtils.capitalizeFully(country.replaceFirst("-", " "));
        return country.equals(backendCountry) ? "/browse/" + encodeSegment(country) : null;
    }

    private static boolean allRowsForCountryHaveUsableSlugs(
            String country,
            Map<String, List<ItinerarySitemapProjection>> byCountry
    ) {
        List<ItinerarySitemapProjection> rows = byCountry.get(country);
        return rows != null && !rows.isEmpty()
                && rows.stream().allMatch(row -> isUsableSegment(row.getSlug()));
    }

    private static boolean isUsableSegment(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.equals(".") || value.equals("..")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '/' || character == '\\' || character == '?' || character == '#'
                    || character == '%' || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    private static String encodeSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private static String writeXml(Set<String> paths) {
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("urlset");
            writer.writeDefaultNamespace(SITEMAP_NAMESPACE);

            for (String path : paths) {
                writer.writeStartElement("url");
                writer.writeStartElement("loc");
                writer.writeCharacters(CANONICAL_ORIGIN + path);
                writer.writeEndElement();
                writer.writeEndElement();
            }

            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
            return output.toString();
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("Unable to generate sitemap XML", exception);
        }
    }
}
