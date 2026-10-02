package com.shortbreakshub.seeder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbreakshub.PostgresTestSupport;
import com.shortbreakshub.command.DestinationImportRunner;
import com.shortbreakshub.controller.ItineraryController;
import com.shortbreakshub.dto.ItineraryRes;
import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.*;
import com.shortbreakshub.seeder.dto.DestinationCatalog;
import com.shortbreakshub.service.ItineraryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class DestinationImportIntegrationTest extends PostgresTestSupport {
    @Autowired DestinationCatalogLoader loader;
    @Autowired DestinationImportService importer;
    @Autowired DestinationRepository destinations;
    @Autowired DestinationMappingReviewRepository reviews;
    @Autowired ExternalDestinationMappingRepository mappings;
    @Autowired ItineraryRepository itineraries;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext context;
    @Autowired ItineraryService itineraryService;

    DestinationCatalog full;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE TABLE itineraries, destinations RESTART IDENTITY CASCADE");
        full = loader.load("classpath:seed/destinations.json");
    }

    @Test
    void fullBackfillDryRunAndRerunPreserveContentIdsTimestampsAndSkipReasons() {
        fixtures(full);
        Itinerary unlisted = fixture("unlisted", "Elsewhere", "Unused");
        Map<String, Object> before = jdbc.queryForMap("select * from itineraries where slug = ?", slug("New York City"));
        var preview = importer.dryRun(full);
        assertEquals(201, preview.destinationsCreated());
        assertEquals(190, preview.mappingsCreated());
        assertEquals(201, preview.itinerariesLinked());
        assertEquals(List.of("unlisted"), preview.unlistedItinerarySlugs());
        assertEquals(0, destinations.count());
        assertEquals(0, mappings.count());
        var applied = importer.apply(full);
        assertFalse(applied.dryRun());
        assertEquals(201, destinations.count());
        assertEquals(190, mappings.count());
        assertEquals(201, reviews.count());
        assertEquals(11, jdbc.queryForObject("select count(*) from destination_mapping_reviews where status='SKIPPED'", Integer.class));
        assertEquals(201, jdbc.queryForObject("select count(*) from itineraries where destination_id is not null", Integer.class));
        assertNull(itineraries.findById(unlisted.getId()).orElseThrow().getDestination());
        assertId("New York City", "633");
        assertId("New Orleans", "1186");
        assertId("Malacca", "62");
        Map<String, Object> after = jdbc.queryForMap("select * from itineraries where slug = ?", slug("New York City"));
        after.put("destination_id", null);
        assertEquals(before, after);
        var snapshots = jdbc.queryForList("select id, destination_key, created_at, updated_at from destinations order by id");
        var mappingSnapshots = jdbc.queryForList("select * from external_destination_mappings order by id");
        var reviewSnapshots = jdbc.queryForList("select * from destination_mapping_reviews order by id");
        var rerun = importer.apply(full);
        assertEquals(0, rerun.destinationsCreated());
        assertEquals(0, rerun.destinationsUpdated());
        assertEquals(201, rerun.destinationsUnchanged());
        assertEquals(201, rerun.itinerariesUnchanged());
        assertEquals(190, rerun.mappingsUnchanged());
        assertEquals(201, rerun.reviewsUnchanged());
        assertEquals(snapshots, jdbc.queryForList("select id, destination_key, created_at, updated_at from destinations order by id"));
        assertEquals(mappingSnapshots, jdbc.queryForList("select * from external_destination_mappings order by id"));
        assertEquals(reviewSnapshots, jdbc.queryForList("select * from destination_mapping_reviews order by id"));
        for (var entry : full.destinations()) {
            var decision = entry.providerDecisions().getFirst();
            if (decision.status() == DestinationMappingStatus.SKIPPED) {
                Long id = destinations.findByDestinationKey(entry.destinationKey()).orElseThrow().getId();
                var review = reviews.findByDestination_IdAndProviderAndEntityType(id, decision.provider(), decision.entityType()).orElseThrow();
                assertEquals(decision.reason(), review.getReason());
                assertEquals(DestinationMappingFallback.DEFAULT_AFFILIATE_LINK, review.getFallback());
                assertTrue(mappings.findByDestination_Id(id).isEmpty());
            }
        }
    }

    @Test
    void unknownSlugFailsWithoutPartialWritesAndMissingCatalogLeavesExistingState() {
        var entry = full.destinations().getFirst();
        fixture(entry.itinerarySlugs().getFirst(), entry.country(), entry.name());
        var catalog = catalog(List.of(entry, full.destinations().get(1)));
        assertThrows(IllegalStateException.class, () -> importer.apply(catalog));
        assertEquals(0, destinations.count());
        assertNull(itineraries.findAll().getFirst().getDestination());
        importer.apply(catalog(List.of(entry)));
        assertThrows(IllegalArgumentException.class, () -> loader.load("classpath:seed/does-not-exist.json"));
        assertEquals(1, destinations.count());
        assertEquals(1, mappings.count());
    }

    @Test
    void competingLinkAndCountryChangeFailWithoutSideEffects() {
        var entry = full.destinations().getFirst();
        var single = catalog(List.of(entry));
        fixtures(single);
        importer.apply(single);
        var renamedKey = new DestinationCatalog.Entry("different-explicit-key", entry.name(), entry.country(), entry.itinerarySlugs(), entry.providerDecisions());
        assertThrows(IllegalStateException.class, () -> importer.apply(catalog(List.of(renamedKey))));
        assertEquals(1, destinations.count());
        var changedCountry = new DestinationCatalog.Entry(entry.destinationKey(), entry.name(), "Other country", entry.itinerarySlugs(), entry.providerDecisions());
        assertThrows(IllegalStateException.class, () -> importer.apply(catalog(List.of(changedCountry))));
        assertEquals(entry.country(), destinations.findAll().getFirst().getCountry());
    }

    @Test
    void explicitKeysSurviveDisplayRenameAndMultipleItinerariesShareDestination() {
        var entry = full.destinations().getFirst();
        fixture("another-trip", entry.country(), entry.name());
        fixtures(catalog(List.of(entry)));
        var shared = new DestinationCatalog.Entry(entry.destinationKey(), entry.name(), entry.country(),
                List.of(entry.itinerarySlugs().getFirst(), "another-trip"), entry.providerDecisions());
        importer.apply(catalog(List.of(shared)));
        assertEquals(1, destinations.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from itineraries where destination_id is not null", Integer.class));
        Long id = destinations.findAll().getFirst().getId();
        var renamed = new DestinationCatalog.Entry(shared.destinationKey(), "Changed display name", shared.country(), shared.itinerarySlugs(), shared.providerDecisions());
        assertEquals(1, importer.apply(catalog(List.of(renamed))).destinationsUpdated());
        assertEquals(id, destinations.findByDestinationKey(entry.destinationKey()).orElseThrow().getId());
        assertThrows(IllegalStateException.class, () -> destinations.findAll().getFirst().setDestinationKey("another-key"));
    }

    @Test
    void conflictingIdFailsAndGuardedCorrectionIsIdempotent() {
        var entry = entry("Malacca");
        var oldDecision = mapped("534322", null);
        var old = withDecision(entry, oldDecision);
        fixtures(old);
        importer.apply(old);
        assertThrows(IllegalStateException.class, () -> importer.apply(catalog(List.of(entry))));
        assertId("Malacca", "534322");
        var badGuard = mapped("62", new DestinationCatalog.Replacement(DestinationMappingStatus.MAPPED, "different-id"));
        assertThrows(IllegalStateException.class, () -> importer.apply(withDecision(entry, badGuard)));
        var correction = withDecision(entry, mapped("62", new DestinationCatalog.Replacement(DestinationMappingStatus.MAPPED, "534322")));
        assertEquals(1, importer.apply(correction).mappingsUpdated());
        assertId("Malacca", "62");
        var snapshot = jdbc.queryForList("select * from external_destination_mappings order by id");
        var rerun = importer.apply(correction);
        assertEquals(0, rerun.mappingsUpdated());
        assertEquals(1, rerun.mappingsUnchanged());
        assertEquals(1, rerun.reviewsUnchanged());
        assertEquals(snapshot, jdbc.queryForList("select * from external_destination_mappings order by id"));
    }

    @Test
    void replacementGuardsFailWhenPreviousScopeIsAbsentIncludingNewDestinations() {
        var entry = entry("Malacca");
        fixtures(catalog(List.of(entry)));
        var guarded = withDecision(entry, mapped("62",
                new DestinationCatalog.Replacement(DestinationMappingStatus.MAPPED, "534322")));
        var failure = assertThrows(IllegalStateException.class, () -> importer.apply(guarded));
        assertTrue(failure.getMessage().contains("precondition not matched"));
        assertThrows(IllegalStateException.class, () -> importer.dryRun(guarded));
        assertEquals(0, destinations.count());

        Destination existing = new Destination();
        existing.setDestinationKey(entry.destinationKey());
        existing.setName(entry.name());
        existing.setCountry(entry.country());
        destinations.save(existing);
        var snapshot = jdbc.queryForList("select * from destinations order by id");
        var skippedGuard = withDecision(entry, mapped("62",
                new DestinationCatalog.Replacement(DestinationMappingStatus.SKIPPED, null)));
        var skippedTarget = withDecision(entry, new DestinationCatalog.Decision(
                ExternalProvider.TRIP_COM, ExternalEntityType.CITY, DestinationMappingStatus.SKIPPED,
                null, "Guarded skip", DestinationMappingFallback.DEFAULT_AFFILIATE_LINK,
                new DestinationCatalog.Replacement(DestinationMappingStatus.MAPPED, "534322")));
        for (var input : List.of(guarded, skippedGuard, skippedTarget)) {
            assertThrows(IllegalStateException.class, () -> importer.apply(input));
            assertThrows(IllegalStateException.class, () -> importer.dryRun(input));
        }
        assertEquals(snapshot, jdbc.queryForList("select * from destinations order by id"));
        assertEquals(0, mappings.count());
        assertEquals(0, reviews.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from itineraries where destination_id is not null", Integer.class));
    }

    @Test
    void replacementGuardCannotUseAnotherProviderOrEntityScope() {
        var entry = entry("Malacca");
        var old = withDecision(entry, mapped("534322", null));
        fixtures(old);
        importer.apply(old);
        var guard = new DestinationCatalog.Replacement(DestinationMappingStatus.MAPPED, "534322");
        for (var decision : List.of(
                new DestinationCatalog.Decision(ExternalProvider.GOOGLE, ExternalEntityType.CITY,
                        DestinationMappingStatus.MAPPED, "62", null, null, guard),
                new DestinationCatalog.Decision(ExternalProvider.TRIP_COM, ExternalEntityType.PLACE,
                        DestinationMappingStatus.MAPPED, "62", null, null, guard))) {
            assertThrows(IllegalStateException.class, () -> importer.apply(withDecision(entry, decision)));
        }
        assertEquals(1, mappings.count());
        assertEquals(1, reviews.count());
        assertId("Malacca", "534322");
    }

    @Test
    void guardedSkipTransitionsRemoveOnlyAuthorizedMappingAndPreserveAbsentScopes() {
        var entry = entry("Malacca");
        fixtures(catalog(List.of(entry)));
        importer.apply(catalog(List.of(entry)));
        var skip = new DestinationCatalog.Decision(ExternalProvider.TRIP_COM, ExternalEntityType.CITY,
                DestinationMappingStatus.SKIPPED, null, "Reviewed skip", DestinationMappingFallback.DEFAULT_AFFILIATE_LINK, null);
        assertThrows(IllegalStateException.class, () -> importer.apply(withDecision(entry, skip)));
        var guardedSkip = new DestinationCatalog.Decision(skip.provider(), skip.entityType(), skip.status(), null,
                skip.reason(), skip.fallback(), new DestinationCatalog.Replacement(DestinationMappingStatus.MAPPED, "62"));
        assertEquals(1, importer.apply(withDecision(entry, guardedSkip)).mappingsRemoved());
        assertEquals(0, mappings.count());
        var repeatedSkip = importer.apply(withDecision(entry, guardedSkip));
        assertEquals(0, repeatedSkip.mappingsRemoved());
        assertEquals(1, repeatedSkip.reviewsUnchanged());
        var changedReason = new DestinationCatalog.Decision(skip.provider(), skip.entityType(), skip.status(), null,
                "Updated researched reason", skip.fallback(), null);
        assertEquals(1, importer.apply(withDecision(entry, changedReason)).reviewsUpdated());
        var restore = mapped("62", new DestinationCatalog.Replacement(DestinationMappingStatus.SKIPPED, null));
        importer.apply(withDecision(entry, restore));
        var repeatedRestore = importer.apply(withDecision(entry, restore));
        assertEquals(0, repeatedRestore.mappingsCreated());
        assertEquals(1, repeatedRestore.mappingsUnchanged());
        assertEquals(1, repeatedRestore.reviewsUnchanged());
        // A new supported scope does not erase the existing Trip.com decision.
        var other = new DestinationCatalog.Decision(ExternalProvider.GOOGLE, ExternalEntityType.PLACE,
                DestinationMappingStatus.MAPPED, "opaque:place-id", null, null, null);
        importer.apply(withDecision(entry, other));
        assertEquals(2, mappings.count());
        assertId("Malacca", "62");
    }

    @Test
    void existingStateDryRunProposesUpdatesWithoutChangingValuesOrTimestamps() {
        var skipped = entry("Tasmania");
        var mapped = entry("Malacca");
        var initial = catalog(List.of(skipped, mapped));
        fixtures(initial);
        importer.apply(initial);
        var decision = skipped.providerDecisions().getFirst();
        var proposed = new DestinationCatalog.Entry(skipped.destinationKey(), "Updated Tasmania display name",
                skipped.country(), skipped.itinerarySlugs(), List.of(new DestinationCatalog.Decision(
                decision.provider(), decision.entityType(), decision.status(), null,
                "Updated researched skip reason", decision.fallback(), null)));
        var destinationSnapshot = jdbc.queryForList("select * from destinations order by id");
        var mappingSnapshot = jdbc.queryForList("select * from external_destination_mappings order by id");
        var reviewSnapshot = jdbc.queryForList("select * from destination_mapping_reviews order by id");
        var itinerarySnapshot = jdbc.queryForList("select * from itineraries order by id");

        var preview = importer.dryRun(catalog(List.of(proposed, mapped)));
        assertTrue(preview.dryRun());
        assertEquals(1, preview.destinationsUpdated());
        assertEquals(1, preview.reviewsUpdated());
        assertEquals(1, preview.mappingsUnchanged());
        assertEquals(2, preview.itinerariesUnchanged());
        assertEquals(destinationSnapshot, jdbc.queryForList("select * from destinations order by id"));
        assertEquals(mappingSnapshot, jdbc.queryForList("select * from external_destination_mappings order by id"));
        assertEquals(reviewSnapshot, jdbc.queryForList("select * from destination_mapping_reviews order by id"));
        assertEquals(itinerarySnapshot, jdbc.queryForList("select * from itineraries order by id"));
    }

    @Test
    void concurrentIdenticalAppliesSerializeAndLeaveOneConsistentSelectionPerScope() throws Exception {
        var input = catalog(List.of(entry("Malacca"), entry("New York City"), entry("Tasmania")));
        fixtures(input);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2);
             var gate = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            gate.setAutoCommit(false);
            try (var statement = gate.createStatement()) {
                statement.execute("select pg_advisory_xact_lock(140014)");
            }
            java.util.concurrent.Callable<DestinationImportReport> apply = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Import start gate timed out");
                return importer.apply(input);
            };
            var first = workers.submit(apply);
            var second = workers.submit(apply);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            // Observe both real database transactions waiting, rather than assuming overlap after a sleep.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(2,
                    jdbc.queryForObject("select count(*) from pg_locks where locktype='advisory' "
                            + "and classid=0 and objid=140014 and objsubid=1 and not granted "
                            + "and database=(select oid from pg_database where datname=current_database())", Integer.class)));
            gate.commit();
            var reports = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(List.of(0, 3), reports.stream().map(DestinationImportReport::destinationsCreated).sorted().toList());
            assertEquals(List.of(0, 2), reports.stream().map(DestinationImportReport::mappingsCreated).sorted().toList());
            assertEquals(List.of(0, 3), reports.stream().map(DestinationImportReport::reviewsCreated).sorted().toList());
            assertEquals(List.of(0, 3), reports.stream().map(DestinationImportReport::itinerariesLinked).sorted().toList());
        } finally {
            start.countDown();
        }
        assertEquals(3, destinations.count());
        assertEquals(2, mappings.count());
        assertEquals(3, reviews.count());
        for (var entry : input.destinations()) {
            assertEquals(entry.destinationKey(), jdbc.queryForObject("select d.destination_key from itineraries i "
                    + "join destinations d on d.id=i.destination_id where i.slug=?", String.class, entry.itinerarySlugs().getFirst()));
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from (select destination_key from destinations "
                + "group by destination_key having count(*)>1) duplicates", Integer.class));
        for (String table : List.of("external_destination_mappings", "destination_mapping_reviews")) {
            assertEquals(0, jdbc.queryForObject("select count(*) from (select destination_id,provider,entity_type from "
                    + table + " group by destination_id,provider,entity_type having count(*)>1) duplicates", Integer.class));
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from destination_mapping_reviews r "
                + "left join external_destination_mappings m using (destination_id,provider,entity_type) "
                + "where (r.status='MAPPED' and m.id is null) or (r.status='SKIPPED' and m.id is not null)", Integer.class));
    }

    @Test
    void opaqueIdsAndSharedExternalIdsDoNotMergeDestinations() {
        var a = full.destinations().getFirst();
        var b = full.destinations().get(1);
        var decision = mapped("opaque:ABC-007", null);
        var catalog = catalog(List.of(replaceDecision(a, decision), replaceDecision(b, decision)));
        fixtures(catalog);
        var report = importer.apply(catalog);
        assertEquals(2, destinations.count());
        assertEquals(2, mappings.count());
        assertEquals(1, report.sharedExternalIds().size());
        assertTrue(mappings.findAll().stream().allMatch(m -> m.getExternalId().equals("opaque:ABC-007")));
    }

    @Test
    void contradictoryDatabaseStateIsRejectedRatherThanRepaired() {
        var entry = full.destinations().getFirst();
        var catalog = catalog(List.of(entry));
        fixtures(catalog);
        importer.apply(catalog);
        jdbc.update("delete from destination_mapping_reviews");
        assertThrows(IllegalStateException.class, () -> importer.apply(catalog));
        assertEquals(0, reviews.count());
        assertEquals(1, mappings.count());
        jdbc.update("insert into destination_mapping_reviews(destination_id, provider, entity_type, status, reason, fallback) "
                + "values (?, 'TRIP_COM', 'CITY', 'SKIPPED', 'manual contradictory row', 'DEFAULT_AFFILIATE_LINK')", destinations.findAll().getFirst().getId());
        assertThrows(IllegalStateException.class, () -> importer.apply(catalog));
        assertEquals(1, mappings.count());
    }

    @Test
    void lateWriteFailureRollsBackEntireApply() {
        var small = catalog(full.destinations().subList(0, 3));
        fixtures(small);
        jdbc.execute("CREATE FUNCTION issue14_fail_late() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF (SELECT count(*) FROM destinations) >= 3 THEN RAISE EXCEPTION 'Injected late failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER issue14_fail_late BEFORE INSERT ON external_destination_mappings FOR EACH ROW EXECUTE FUNCTION issue14_fail_late()");
        try {
            RuntimeException failure = assertThrows(RuntimeException.class, () -> importer.apply(small));
            assertTrue(org.springframework.core.NestedExceptionUtils.getMostSpecificCause(failure).getMessage().contains("Injected late failure"));
        } finally {
            jdbc.execute("DROP TRIGGER issue14_fail_late ON external_destination_mappings");
            jdbc.execute("DROP FUNCTION issue14_fail_late()");
        }
        assertEquals(0, destinations.count());
        assertEquals(0, mappings.count());
        assertEquals(0, reviews.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from itineraries where destination_id is not null", Integer.class));
    }

    @Test
    void databaseScopeConstraintsAndDeleteSemanticsAreEnforced() {
        var entry = full.destinations().getFirst();
        var catalog = catalog(List.of(entry));
        fixtures(catalog);
        importer.apply(catalog);
        Long id = destinations.findAll().getFirst().getId();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into external_destination_mappings(destination_id,provider,entity_type,external_id) values (?,'TRIP_COM','CITY','competing')", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into destination_mapping_reviews(destination_id,provider,entity_type,status) values (?,'TRIP_COM','CITY','MAPPED')", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from destinations where id=?", id));
        itineraries.deleteAll();
        jdbc.update("delete from destinations where id=?", id);
        assertEquals(0, mappings.count());
        assertEquals(0, reviews.count());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into destinations(destination_key,name,country) values (' ','name','country')"));
    }

    @Test
    void prodDoesNotRunImporterOrDevelopmentSeeders() {
        assertTrue(context.getBeansOfType(DestinationImportRunner.class).isEmpty());
        assertTrue(context.getBeansOfType(ItinerarySeeder.class).isEmpty());
        assertTrue(context.getBeansOfType(TranslationSeeder.class).isEmpty());
        assertEquals(0, destinations.count());
        // Independent importer remains available despite no development seeder being present.
        var single = catalog(List.of(full.destinations().getFirst()));
        fixtures(single);
        assertEquals(1, importer.apply(single).destinationsCreated());
    }

    @Test
    void legacyEntityAndDtoSerializationDoNotExposeOrInitializeDestination() throws Exception {
        var single = catalog(List.of(full.destinations().getFirst()));
        fixtures(single);
        String slug = single.destinations().getFirst().itinerarySlugs().getFirst();
        var transaction = new TransactionTemplate(transactions);
        String before = transaction.execute(status -> {
            try { return json.writeValueAsString(itineraries.findBySlug(slug).orElseThrow()); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        importer.apply(single);
        transaction.executeWithoutResult(status -> {
            entityManager.clear();
            Itinerary itinerary = itineraries.findBySlug(slug).orElseThrow();
            assertFalse(Hibernate.isInitialized(itinerary.getDestination()));
            var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            statistics.clear();
            try {
                assertEquals(before, json.writeValueAsString(itinerary));
                assertFalse(Hibernate.isInitialized(itinerary.getDestination()));
                assertEquals(0, statistics.getPrepareStatementCount());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        assertFalse(Arrays.stream(ItineraryRes.class.getRecordComponents()).anyMatch(c -> c.getName().equals("destination")));
        var mvc = MockMvcBuilders.standaloneSetup(new ItineraryController(itineraryService))
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver()).build();
        mvc.perform(get("/api/itineraries/browse/" + single.destinations().getFirst().country()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].destination").doesNotExist())
                .andExpect(jsonPath("$[0].slug").value(slug));
        mvc.perform(get("/api/itineraries/fixture-region"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].destination").doesNotExist());
        mvc.perform(get("/api/itineraries/search").param("sort", "slug,asc")
                        .param("q", "Preserved").param("country", single.destinations().getFirst().country())
                        .param("daysMin", "1").param("daysMax", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].destination").doesNotExist());
    }

    private void fixtures(DestinationCatalog catalog) {
        for (var entry : catalog.destinations()) for (String slug : entry.itinerarySlugs()) fixture(slug, entry.country(), entry.name());
    }

    private Itinerary fixture(String slug, String country, String city) {
        return itineraries.save(new Itinerary(slug, "fixture-region", country, city, "Preserved title", 3, 99,
                "preserved-hero", "Preserved summary", List.of("Preserved highlight"), List.of()));
    }

    private DestinationCatalog.Entry entry(String name) {
        return full.destinations().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private String slug(String name) { return entry(name).itinerarySlugs().getFirst(); }

    private void assertId(String name, String expected) {
        Long id = destinations.findByDestinationKey(entry(name).destinationKey()).orElseThrow().getId();
        assertEquals(expected, mappings.findByDestination_IdAndProviderAndEntityType(id, ExternalProvider.TRIP_COM, ExternalEntityType.CITY).orElseThrow().getExternalId());
    }

    private DestinationCatalog.Decision mapped(String id, DestinationCatalog.Replacement guard) {
        return new DestinationCatalog.Decision(ExternalProvider.TRIP_COM, ExternalEntityType.CITY,
                DestinationMappingStatus.MAPPED, id, null, null, guard);
    }

    private DestinationCatalog.Entry replaceDecision(DestinationCatalog.Entry entry, DestinationCatalog.Decision decision) {
        return new DestinationCatalog.Entry(entry.destinationKey(), entry.name(), entry.country(), entry.itinerarySlugs(), List.of(decision));
    }

    private DestinationCatalog withDecision(DestinationCatalog.Entry entry, DestinationCatalog.Decision decision) {
        return catalog(List.of(replaceDecision(entry, decision)));
    }

    private DestinationCatalog catalog(List<DestinationCatalog.Entry> entries) {
        int mapped = 0, skipped = 0;
        for (var e : entries) for (var d : e.providerDecisions()) if (d.provider() == ExternalProvider.TRIP_COM) {
            if (d.status() == DestinationMappingStatus.MAPPED) mapped++; else skipped++;
        }
        return new DestinationCatalog(1, "test-catalog", entries.size(), mapped, skipped, entries);
    }
}
