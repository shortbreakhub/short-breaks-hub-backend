package com.shortbreakshub.seeder;

import com.shortbreakshub.model.Itinerary;
import com.shortbreakshub.model.ItineraryTranslation;
import com.shortbreakshub.repository.ItineraryRepository;
import com.shortbreakshub.repository.ItineraryTranslationRepository;
import com.shortbreakshub.seeder.dto.ItineraryTranslationImportDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TranslationImportService {

    private static final String FRENCH_LOCALE = "fr";

    private final ItineraryRepository itineraryRepository;
    private final ItineraryTranslationRepository translationRepository;

    public TranslationImportService(
            ItineraryRepository itineraryRepository,
            ItineraryTranslationRepository translationRepository
    ) {
        this.itineraryRepository = itineraryRepository;
        this.translationRepository = translationRepository;
    }

    @Transactional
    public ImportResult importFrenchTranslations(
            List<ItineraryTranslationImportDto> translations
    ) {
        int created = 0;
        int updated = 0;

        for (ItineraryTranslationImportDto dto : translations) {
            Itinerary itinerary = itineraryRepository
                    .findBySlug(dto.getSlug())
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Original itinerary not found for slug: "
                                            + dto.getSlug()
                            )
                    );

            ItineraryTranslation translation =
                    translationRepository
                            .findByItineraryIdAndLocale(
                                    itinerary.getId(),
                                    FRENCH_LOCALE
                            )
                            .orElseGet(ItineraryTranslation::new);

            boolean isNew = translation.getId() == null;

            translation.setItinerary(itinerary);
            translation.setLocale(FRENCH_LOCALE);
            translation.setTitle(dto.getTitle());
            translation.setSummary(dto.getSummary());
            translation.setPriceNote(dto.getPriceNote());
            translation.setHighlights(dto.getHighlights());
            translation.setSchedule(dto.getSchedule());
            translation.setPlanning(dto.getPlanning());
            translation.setTransport(dto.getTransport());
            translation.setFoodRecommendation(
                    dto.getFoodRecommendation()
            );

            translationRepository.save(translation);

            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        return new ImportResult(created, updated);
    }

    public record ImportResult(
            int created,
            int updated
    ) {
    }
}