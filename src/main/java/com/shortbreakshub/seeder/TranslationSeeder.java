package com.shortbreakshub.seeder;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbreakshub.seeder.dto.ItineraryTranslationImportDto;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

@Configuration
public class TranslationSeeder{

    @Bean
    CommandLineRunner importFrenchTranslations(
            ObjectMapper objectMapper,
            TranslationImportService importService
    ) {
        return args -> {
            ClassPathResource resource =
                    new ClassPathResource(
                            "seed/itineraries-fr.json"
                    );

            try (InputStream inputStream = resource.getInputStream()) {
                List<ItineraryTranslationImportDto> translations =
                        objectMapper.readValue(
                                inputStream,
                                new TypeReference<>() {
                                }
                        );

                TranslationImportService.ImportResult result =
                        importService.importFrenchTranslations(
                                translations
                        );

                System.out.println(
                        "French translation import completed."
                );
                System.out.println(
                        "Created: " + result.created()
                );
                System.out.println(
                        "Updated: " + result.updated()
                );
            }
        };
    }
}
