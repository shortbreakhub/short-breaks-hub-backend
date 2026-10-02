package com.shortbreakshub.seeder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbreakshub.command.DestinationImportCommand;
import com.shortbreakshub.command.DestinationImportRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import com.shortbreakshub.repository.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DestinationImportCommandTest {
    @Test
    void runnerRequiresExplicitModeAndDispatchesDryRunOrApply() throws Exception {
        var loader = mock(DestinationCatalogLoader.class);
        var importer = mock(DestinationImportService.class);
        var environment = new MockEnvironment();
        var runner = new DestinationImportRunner(loader, importer, environment, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> runner.run(new DefaultApplicationArguments()));
        verifyNoInteractions(loader, importer);
        var catalog = new DestinationCatalogLoader(new org.springframework.core.io.DefaultResourceLoader()).load("classpath:seed/destinations.json");
        when(loader.load("classpath:seed/destinations.json")).thenReturn(catalog);
        environment.setProperty("destination-import.mode", "dry-run");
        runner.run(new DefaultApplicationArguments());
        verify(importer).dryRun(catalog);
        verify(importer, never()).apply(any());
        environment.setProperty("destination-import.mode", "apply");
        runner.run(new DefaultApplicationArguments());
        verify(importer).apply(catalog);
    }

    @Test
    void ordinaryProdCannotRegisterRunnerButExplicitImportProfileCan() {
        var contexts = new ApplicationContextRunner().withUserConfiguration(RunnerConfiguration.class)
                .withBean(DestinationCatalogLoader.class, () -> mock(DestinationCatalogLoader.class))
                .withBean(DestinationImportService.class, () -> mock(DestinationImportService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new);
        contexts.withPropertyValues("spring.profiles.active=prod").run(context ->
                assertTrue(context.getBeansOfType(DestinationImportRunner.class).isEmpty()));
        contexts.withPropertyValues("spring.profiles.active=prod,destination-import").run(context ->
                assertEquals(1, context.getBeansOfType(DestinationImportRunner.class).size()));
        contexts.withPropertyValues("spring.profiles.active=destination-import").run(context ->
                assertTrue(context.getBeansOfType(DestinationImportRunner.class).isEmpty()));
    }

    @Test
    void realLegacySeederBeansAreExcludedFromEveryImportProfileCombination() {
        var contexts = new ApplicationContextRunner()
                .withUserConfiguration(ItinerarySeeder.class, TranslationSeeder.class, DestinationImportRunner.class)
                .withBean(ItineraryRepository.class, () -> mock(ItineraryRepository.class))
                .withBean(ItineraryPlanningSnapshotRepository.class, () -> mock(ItineraryPlanningSnapshotRepository.class))
                .withBean(ItineraryTransportTipRepository.class, () -> mock(ItineraryTransportTipRepository.class))
                .withBean(ItineraryFoodRecommendationRepository.class, () -> mock(ItineraryFoodRecommendationRepository.class))
                .withBean(TranslationImportService.class, () -> mock(TranslationImportService.class))
                .withBean(DestinationCatalogLoader.class, () -> mock(DestinationCatalogLoader.class))
                .withBean(DestinationImportService.class, () -> mock(DestinationImportService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new);
        for (String profiles : new String[]{"", "test", "dev", "prod", "destination-import",
                "dev,destination-import", "prod,destination-import", "prod,destination-import,extra"}) {
            boolean legacy = profiles.isEmpty() || profiles.equals("test") || profiles.equals("dev");
            boolean importing = profiles.contains("prod") && profiles.contains("destination-import");
            contexts.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
                assertNull(context.getStartupFailure(), profiles);
                assertEquals(legacy, !context.getBeansOfType(ItinerarySeeder.class).isEmpty(), profiles);
                assertEquals(legacy, !context.getBeansOfType(TranslationSeeder.class).isEmpty(), profiles);
                assertEquals(legacy, context.containsBean("importFrenchTranslations"), profiles);
                assertEquals(importing, !context.getBeansOfType(DestinationImportRunner.class).isEmpty(), profiles);
            });
        }
    }

    @Test
    void dedicatedConfigurationImportsNoDevelopmentSeedersAndExcludesFlyway() {
        var imports = DestinationImportCommand.class.getAnnotation(Import.class).value();
        assertFalse(java.util.Arrays.asList(imports).contains(ItinerarySeeder.class));
        assertFalse(java.util.Arrays.asList(imports).contains(TranslationSeeder.class));
        assertTrue(java.util.Arrays.asList(DestinationImportCommand.class.getAnnotation(EnableAutoConfiguration.class).exclude())
                .contains(FlywayAutoConfiguration.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DestinationImportRunner.class)
    static class RunnerConfiguration {}
}
