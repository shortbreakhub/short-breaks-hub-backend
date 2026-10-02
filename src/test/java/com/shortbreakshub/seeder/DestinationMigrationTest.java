package com.shortbreakshub.seeder;

import com.shortbreakshub.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DestinationMigrationTest extends PostgresTestSupport {
    @Test
    void populatedV8UpgradesToV9WithoutChangingItineraryContent() throws Exception {
        String database = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        var source = new DriverManagerDataSource("jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/" + database, "postgres", "");
        Flyway.configure().dataSource(source).target("8").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into itineraries(slug,country,city,region,title,days) values ('existing-trip','Country','City','region','Original title',3)");
        Long id = jdbc.queryForObject("select id from itineraries where slug='existing-trip'", Long.class);
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_name='itineraries' and column_name='destination_id'", Integer.class));
        var migrated = Flyway.configure().dataSource(source).load().migrate();
        assertEquals(1, migrated.migrationsExecuted);
        assertEquals(id, jdbc.queryForObject("select id from itineraries where slug='existing-trip'", Long.class));
        assertEquals("Original title", jdbc.queryForObject("select title from itineraries where slug='existing-trip'", String.class));
        assertNull(jdbc.queryForObject("select destination_id from itineraries where slug='existing-trip'", Long.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from destinations", Integer.class));
        jdbc.update("insert into destinations(destination_key,name,country) values ('key','Name','Country')");
        Long destination = jdbc.queryForObject("select id from destinations", Long.class);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into destination_mapping_reviews(destination_id,provider,entity_type,status,reason,fallback) values (?,'TRIP_COM','CITY','SKIPPED','reason',null)", destination));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into destination_mapping_reviews(destination_id,provider,entity_type,status,reason,fallback) values (?,'TRIP_COM','CITY','SKIPPED',' ', 'DEFAULT_AFFILIATE_LINK')", destination));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into external_destination_mappings(destination_id,provider,entity_type,external_id) values (?,'TRIP_HOTEL','CITY','id')", destination));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into external_destination_mappings(destination_id,provider,entity_type,external_id) values (?,'TRIP_COM','CITY',' ')", destination));
    }

    @Test
    void cleanV1ThroughV9MigrationHasNoBusinessRecords() throws Exception {
        String database = "clean_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        var source = new DriverManagerDataSource("jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/" + database, "postgres", "");
        var result = Flyway.configure().dataSource(source).load().migrate();
        assertEquals(9, result.migrationsExecuted);
        var jdbc = new JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("select count(*) from destinations", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from external_destination_mappings", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from destination_mapping_reviews", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where tablename='itineraries' and indexname='idx_itineraries_destination_id'", Integer.class));
    }
}
