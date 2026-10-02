package com.shortbreakshub.seeder;

import com.shortbreakshub.PostgresTestSupport;
import com.shortbreakshub.command.DestinationImportCommand;
import com.shortbreakshub.command.DestinationImportRunner;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DestinationCommandIntegrationTest extends PostgresTestSupport {
    @Test
    void dedicatedNonWebCommandRunsDryRunThenApplyWithoutFlywayOrLegacySeeders() throws Exception {
        String database = "command_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        String url = "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/" + database;
        var source = new DriverManagerDataSource(url, "postgres", "");
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var catalog = new DestinationCatalogLoader(new DefaultResourceLoader()).load("classpath:seed/destinations.json");
        for (var entry : catalog.destinations()) for (var slug : entry.itinerarySlugs()) {
            jdbc.update("insert into itineraries(slug,country,city,region,title,days,price_from) values (?,?,?,?,?,3,0)",
                    slug, entry.country(), entry.name(), "test-region", "Untouched command fixture");
        }
        for (String mode : new String[]{"dry-run", "apply"}) {
            SpringApplication app = new SpringApplication(DestinationImportCommand.class);
            app.setAdditionalProfiles("prod", "destination-import");
            app.setWebApplicationType(WebApplicationType.NONE);
            try (var context = app.run("--spring.datasource.url=" + url,
                    "--spring.datasource.username=postgres", "--spring.datasource.password=",
                    "--destination-import.mode=" + mode, "--spring.main.banner-mode=off")) {
                assertEquals(1, context.getBeansOfType(DestinationImportRunner.class).size());
                assertTrue(context.getBeansOfType(ItinerarySeeder.class).isEmpty());
                assertTrue(context.getBeansOfType(TranslationSeeder.class).isEmpty());
                assertTrue(context.getBeansOfType(Flyway.class).isEmpty());
                assertFalse(context instanceof org.springframework.boot.web.context.WebServerApplicationContext);
            }
            assertEquals(mode.equals("apply") ? 201 : 0, jdbc.queryForObject("select count(*) from destinations", Integer.class));
            assertEquals(9, jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class));
        }
        assertEquals(190, jdbc.queryForObject("select count(*) from external_destination_mappings", Integer.class));
        assertEquals(11, jdbc.queryForObject("select count(*) from destination_mapping_reviews where status='SKIPPED'", Integer.class));
        assertEquals(201, jdbc.queryForObject("select count(*) from itineraries where title='Untouched command fixture'", Integer.class));
    }
}
