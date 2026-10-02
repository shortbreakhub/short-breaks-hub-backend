package com.shortbreakshub.command;

import com.shortbreakshub.model.Destination;
import com.shortbreakshub.repository.DestinationRepository;
import com.shortbreakshub.seeder.DestinationCatalogLoader;
import com.shortbreakshub.seeder.DestinationImportService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Explicit non-web entry point. Ordinary application startup does not activate this configuration. */
@Configuration(proxyBeanMethods = false)
@Profile("prod & destination-import")
@EnableAutoConfiguration(exclude = FlywayAutoConfiguration.class)
@EntityScan(basePackageClasses = Destination.class)
@EnableJpaRepositories(basePackageClasses = DestinationRepository.class)
@Import({DestinationCatalogLoader.class, DestinationImportService.class, DestinationImportRunner.class})
public class DestinationImportCommand {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DestinationImportCommand.class);
        application.setAdditionalProfiles("prod", "destination-import");
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run(args)) {
            // Closing the context releases the persistence pool after the runner completes.
        } catch (RuntimeException failure) {
            System.err.println("Destination import failed; no successful apply was reported.");
            System.exit(1);
        }
    }
}
