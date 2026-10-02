package com.shortbreakshub.seeder;

import java.util.List;

public record DestinationImportReport(
        String catalogVersion, boolean dryRun,
        int destinationsCreated, int destinationsUpdated, int destinationsUnchanged,
        int itinerariesLinked, int itinerariesUnchanged,
        int mappingsCreated, int mappingsUpdated, int mappingsRemoved, int mappingsUnchanged,
        int reviewsCreated, int reviewsUpdated, int reviewsUnchanged,
        int mapped, int skipped, List<String> unlistedItinerarySlugs, List<String> sharedExternalIds
) {}
