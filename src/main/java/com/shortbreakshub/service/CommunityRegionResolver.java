package com.shortbreakshub.service;

import com.shortbreakshub.model.Region;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Community navigation aliases do not change persisted continent values. */
public final class CommunityRegionResolver {
    private CommunityRegionResolver() { }

    private static final Set<String> SOUTHEAST_ASIA = Set.of(
            "brunei", "cambodia", "indonesia", "laos", "malaysia", "myanmar",
            "philippines", "singapore", "thailand", "timor-leste", "vietnam");
    private static final Set<String> EAST_ASIA = Set.of(
            "china", "hong kong", "macao", "taiwan", "japan", "mongolia", "north korea", "south korea");
    private static final Set<String> ANZ = Set.of("australia", "new zealand");
    private static final Map<String, String> COUNTRY_ALIASES = Map.ofEntries(
            Map.entry("brunei darussalam", "brunei"),
            Map.entry("lao people's democratic republic", "laos"),
            Map.entry("lao peoples democratic republic", "laos"),
            Map.entry("lao pdr", "laos"), Map.entry("burma", "myanmar"),
            Map.entry("viet nam", "vietnam"), Map.entry("east timor", "timor-leste"),
            Map.entry("timor leste", "timor-leste"),
            Map.entry("people's republic of china", "china"),
            Map.entry("republic of korea", "south korea"), Map.entry("korea, republic of", "south korea"),
            Map.entry("democratic people's republic of korea", "north korea"),
            Map.entry("korea, democratic people's republic of", "north korea"),
            Map.entry("macau", "macao"), Map.entry("hong kong sar", "hong kong"),
            Map.entry("macao sar", "macao"), Map.entry("macau sar", "macao"));

    public record Selection(Region region, Set<String> countries) {
        public boolean includesCountry(String country) {
            return countries == null || countries.contains(normalizeCountry(country));
        }
    }

    public static Optional<Selection> resolve(String identifier) {
        if (identifier == null) return Optional.empty();
        return switch (identifier.toLowerCase(Locale.ROOT)) {
            case "southeast-asia" -> Optional.of(new Selection(Region.ASIA, SOUTHEAST_ASIA));
            case "east-asia-community", "east-asia" -> Optional.of(new Selection(Region.ASIA, EAST_ASIA));
            case "americas-community" -> Optional.of(new Selection(Region.AMERICAS, null));
            case "africa-community" -> Optional.of(new Selection(Region.AFRICA, null));
            case "anz-community" -> Optional.of(new Selection(Region.OCEANIA, ANZ));
            default -> {
                try { yield Optional.of(new Selection(Region.valueOf(identifier.toUpperCase(Locale.ROOT)), null)); }
                catch (IllegalArgumentException ex) { yield Optional.empty(); }
            }
        };
    }

    public static String normalizeCountry(String country) {
        if (country == null) return "";
        String normalized = Normalizer.normalize(country, Normalizer.Form.NFKC)
                .replace('\u2019', '\'').replace('\u2018', '\'')
                .replace('\u2010', '-').replace('\u2011', '-').replace('\u2013', '-')
                .replaceAll("[\\p{Z}\\s]+", " ").strip().toLowerCase(Locale.ROOT);
        return COUNTRY_ALIASES.getOrDefault(normalized, normalized);
    }
}
