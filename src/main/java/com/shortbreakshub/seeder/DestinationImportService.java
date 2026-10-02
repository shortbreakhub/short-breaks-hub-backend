package com.shortbreakshub.seeder;

import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.*;
import com.shortbreakshub.seeder.dto.DestinationCatalog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DestinationImportService {
    private final DestinationCatalogLoader loader;
    private final DestinationRepository destinations;
    private final ItineraryRepository itineraries;
    private final ExternalDestinationMappingRepository mappings;
    private final DestinationMappingReviewRepository reviews;
    private final EntityManager entityManager;

    public DestinationImportService(DestinationCatalogLoader loader, DestinationRepository destinations,
                                    ItineraryRepository itineraries, ExternalDestinationMappingRepository mappings,
                                    DestinationMappingReviewRepository reviews, EntityManager entityManager) {
        this.loader = loader;
        this.destinations = destinations;
        this.itineraries = itineraries;
        this.mappings = mappings;
        this.reviews = reviews;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public DestinationImportReport dryRun(DestinationCatalog catalog) {
        return execute(catalog, false);
    }

    @Transactional
    public DestinationImportReport apply(DestinationCatalog catalog) {
        return execute(catalog, true);
    }

    private DestinationImportReport execute(DestinationCatalog catalog, boolean apply) {
        loader.validate(catalog);
        if (apply) {
            // Serialize explicit imports; the lock is released on commit or rollback.
            entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(140014)").getResultList();
        }
        Map<String, Itinerary> bySlug = itineraries.findAll().stream()
                .collect(Collectors.toMap(Itinerary::getSlug, Function.identity()));
        Set<String> assigned = new HashSet<>();
        List<Runnable> changes = new ArrayList<>();
        Counts counts = new Counts();
        for (var entry : catalog.destinations()) {
            Destination destination = destinations.findByDestinationKey(entry.destinationKey()).orElse(null);
            if (destination == null) {
                destination = new Destination();
                destination.setDestinationKey(entry.destinationKey());
                destination.setName(entry.name());
                destination.setCountry(entry.country());
                Destination created = destination;
                changes.add(() -> destinations.save(created));
                counts.destinationsCreated++;
            } else {
                if (apply) entityManager.lock(destination, LockModeType.PESSIMISTIC_WRITE);
                conflict(!destination.getCountry().equals(entry.country()), "Country differs for " + entry.destinationKey());
                if (!destination.getName().equals(entry.name())) {
                    Destination updated = destination;
                    changes.add(() -> updated.setName(entry.name()));
                    counts.destinationsUpdated++;
                } else counts.destinationsUnchanged++;
            }
            Destination target = destination;
            for (String slug : entry.itinerarySlugs()) {
                Itinerary itinerary = bySlug.get(slug);
                conflict(itinerary == null, "Unknown itinerary slug: " + slug);
                assigned.add(slug);
                if (apply) {
                    // Refresh after locking so a concurrent writer cannot hide a competing assignment.
                    entityManager.refresh(itinerary, LockModeType.PESSIMISTIC_WRITE);
                }
                if (itinerary.getDestination() == null) {
                    changes.add(() -> itinerary.setDestination(target));
                    counts.itinerariesLinked++;
                } else {
                    conflict(!itinerary.getDestination().getDestinationKey().equals(entry.destinationKey()),
                            "Competing Destination for itinerary: " + slug);
                    counts.itinerariesUnchanged++;
                }
            }
            validateDatabaseState(target);
            for (var decision : entry.providerDecisions()) {
                planDecision(target, decision, changes, counts);
            }
        }
        // All input/database conflicts are checked before the first write.
        if (apply) {
            changes.forEach(Runnable::run);
            entityManager.flush();
            for (var entry : catalog.destinations()) {
                validateDatabaseState(destinations.findByDestinationKey(entry.destinationKey()).orElseThrow());
            }
        }
        List<String> unlisted = bySlug.keySet().stream().filter(slug -> !assigned.contains(slug)).sorted().toList();
        return counts.report(catalog.catalogVersion(), !apply, unlisted, sharedIds(catalog));
    }

    private void planDecision(Destination destination, DestinationCatalog.Decision decision,
                              List<Runnable> changes, Counts counts) {
        Long id = destination.getId();
        ExternalDestinationMapping mapping = id == null ? null : mappings
                .findByDestination_IdAndProviderAndEntityType(id, decision.provider(), decision.entityType()).orElse(null);
        DestinationMappingReview review = id == null ? null : reviews
                .findByDestination_IdAndProviderAndEntityType(id, decision.provider(), decision.entityType()).orElse(null);
        boolean sameSelection = review != null && review.getStatus() == decision.status()
                && (decision.status() == DestinationMappingStatus.SKIPPED
                    || mapping != null && mapping.getExternalId().equals(decision.externalId()));
        var guard = decision.replaces();
        if (!sameSelection && (review != null || guard != null)) {
            conflict(guard == null || review == null || guard.status() != review.getStatus()
                    || !Objects.equals(guard.externalId(), mapping == null ? null : mapping.getExternalId()),
                    "Mapping/state correction precondition not matched for " + destination.getDestinationKey()
                            + "/" + decision.provider() + "/" + decision.entityType());
        }
        if (decision.status() == DestinationMappingStatus.MAPPED) {
            counts.mapped++;
            if (mapping == null) {
                ExternalDestinationMapping created = new ExternalDestinationMapping();
                created.setDestination(destination);
                created.setProvider(decision.provider());
                created.setEntityType(decision.entityType());
                created.setExternalId(decision.externalId());
                changes.add(() -> mappings.save(created));
                counts.mappingsCreated++;
            } else if (!mapping.getExternalId().equals(decision.externalId())) {
                changes.add(() -> mapping.setExternalId(decision.externalId()));
                counts.mappingsUpdated++;
            } else counts.mappingsUnchanged++;
        } else {
            counts.skipped++;
            if (mapping != null) {
                changes.add(() -> mappings.delete(mapping));
                counts.mappingsRemoved++;
            }
        }
        if (review == null) {
            DestinationMappingReview created = new DestinationMappingReview();
            created.setDestination(destination);
            created.setProvider(decision.provider());
            created.setEntityType(decision.entityType());
            setReview(created, decision);
            changes.add(() -> reviews.save(created));
            counts.reviewsCreated++;
        } else if (review.getStatus() != decision.status() || !Objects.equals(review.getReason(), decision.reason())
                || review.getFallback() != decision.fallback()) {
            changes.add(() -> setReview(review, decision));
            counts.reviewsUpdated++;
        } else counts.reviewsUnchanged++;
    }

    private void validateDatabaseState(Destination destination) {
        if (destination.getId() == null) return;
        Map<Scope, ExternalDestinationMapping> selected = mappings.findByDestination_Id(destination.getId()).stream()
                .collect(Collectors.toMap(m -> new Scope(m.getProvider(), m.getEntityType()), Function.identity()));
        for (var review : reviews.findByDestination_Id(destination.getId())) {
            var mapping = selected.remove(new Scope(review.getProvider(), review.getEntityType()));
            boolean valid = review.getStatus() == DestinationMappingStatus.MAPPED
                    ? mapping != null && review.getFallback() == null
                    : mapping == null && review.getReason() != null && !review.getReason().isBlank()
                        && review.getFallback() == DestinationMappingFallback.DEFAULT_AFFILIATE_LINK;
            conflict(!valid, "Contradictory database review/mapping for " + destination.getDestinationKey());
        }
        conflict(!selected.isEmpty(), "Mapping without review for " + destination.getDestinationKey());
    }

    private static void setReview(DestinationMappingReview review, DestinationCatalog.Decision decision) {
        review.setStatus(decision.status());
        review.setReason(decision.reason());
        review.setFallback(decision.fallback());
    }

    private List<String> sharedIds(DestinationCatalog catalog) {
        Map<SelectionScope, String> selections = new HashMap<>();
        for (var mapping : mappings.findAll()) {
            var scope = new SelectionScope(mapping.getDestination().getDestinationKey(), mapping.getProvider(), mapping.getEntityType());
            selections.put(scope, mapping.getExternalId());
        }
        for (var entry : catalog.destinations()) for (var decision : entry.providerDecisions()) {
            var scope = new SelectionScope(entry.destinationKey(), decision.provider(), decision.entityType());
            if (decision.status() == DestinationMappingStatus.MAPPED) selections.put(scope, decision.externalId());
            else selections.remove(scope);
        }
        Map<String, List<String>> ids = new TreeMap<>();
        selections.forEach((scope, externalId) -> {
            String id = scope.provider() + "/" + scope.type() + "/" + externalId;
            ids.computeIfAbsent(id, ignored -> new ArrayList<>()).add(scope.destinationKey());
        });
        return ids.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + ": " + e.getValue().stream().sorted().collect(Collectors.joining(", "))).toList();
    }

    private static void conflict(boolean condition, String message) {
        if (condition) throw new IllegalStateException(message);
    }

    private record Scope(ExternalProvider provider, ExternalEntityType type) {}
    private record SelectionScope(String destinationKey, ExternalProvider provider, ExternalEntityType type) {}

    private static class Counts {
        int destinationsCreated, destinationsUpdated, destinationsUnchanged;
        int itinerariesLinked, itinerariesUnchanged;
        int mappingsCreated, mappingsUpdated, mappingsRemoved, mappingsUnchanged;
        int reviewsCreated, reviewsUpdated, reviewsUnchanged, mapped, skipped;

        DestinationImportReport report(String version, boolean dryRun, List<String> unlisted, List<String> shared) {
            return new DestinationImportReport(version, dryRun, destinationsCreated, destinationsUpdated,
                    destinationsUnchanged, itinerariesLinked, itinerariesUnchanged, mappingsCreated, mappingsUpdated,
                    mappingsRemoved, mappingsUnchanged, reviewsCreated, reviewsUpdated, reviewsUnchanged,
                    mapped, skipped, unlisted, shared);
        }
    }
}
