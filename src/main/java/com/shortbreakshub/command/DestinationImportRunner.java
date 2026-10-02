package com.shortbreakshub.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbreakshub.seeder.DestinationCatalogLoader;
import com.shortbreakshub.seeder.DestinationImportService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod & destination-import")
public class DestinationImportRunner implements ApplicationRunner {
    private final DestinationCatalogLoader loader;
    private final DestinationImportService importer;
    private final Environment environment;
    private final ObjectMapper mapper;

    public DestinationImportRunner(DestinationCatalogLoader loader, DestinationImportService importer,
                                   Environment environment, ObjectMapper mapper) {
        this.loader = loader;
        this.importer = importer;
        this.environment = environment;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String mode = environment.getProperty("destination-import.mode");
        if (!"dry-run".equals(mode) && !"apply".equals(mode)) {
            throw new IllegalArgumentException("Explicit destination-import.mode=dry-run or apply is required");
        }
        var catalog = loader.load(environment.getProperty("destination-import.catalog", "classpath:seed/destinations.json"));
        var report = "apply".equals(mode) ? importer.apply(catalog) : importer.dryRun(catalog);
        // apply() is transactional on a separate bean: this output occurs only after its commit succeeds.
        System.out.println(mapper.writeValueAsString(report));
    }
}
