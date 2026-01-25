package com.shortbreakshub.seeder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.ItineraryFoodRecommendationRepository;
import com.shortbreakshub.repository.ItineraryPlanningSnapshotRepository;
import com.shortbreakshub.repository.ItineraryTransportTipRepository;
import com.shortbreakshub.seeder.dto.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.shortbreakshub.repository.ItineraryRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ItinerarySeeder implements CommandLineRunner {

    private final ItineraryRepository itineraryRepository;
    private final ObjectMapper objectMapper;
    private final ItineraryPlanningSnapshotRepository snapshotRepository;
    private final ItineraryTransportTipRepository transportTipRepository;
    private final ItineraryFoodRecommendationRepository foodRecommendationRepository;

    public ItinerarySeeder(ItineraryRepository itineraryRepository,
                           ObjectMapper objectMapper,
                           ItineraryPlanningSnapshotRepository snapshotRepository,
                           ItineraryTransportTipRepository transportTipRepository,
                           ItineraryFoodRecommendationRepository foodRecommendationRepository) {
        this.itineraryRepository = itineraryRepository;
        this.objectMapper = objectMapper;
        this.snapshotRepository = snapshotRepository;
        this.transportTipRepository = transportTipRepository;
        this.foodRecommendationRepository = foodRecommendationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        ClassPathResource resource = new ClassPathResource("seed/itineraries.json");
        try (InputStream in =  resource.getInputStream() ) {
            List<SeedItineraryDTO> seedItineraries =
                    objectMapper.readValue(in, new TypeReference<>() {
                    });


            if (itineraryRepository.count() == 0) {
                List<Itinerary> itineraries = seedItineraries.stream()
                        .map(this::toItineraryEntity)
                        .toList();
                itineraryRepository.saveAll(itineraries);
            } else {
                System.out.println("Itinerary Already Exists (" + itineraryRepository.count() + ")");
            }

            Map<String, Itinerary> itineraryBySlug = itineraryRepository.findAll().stream()
                    .collect(Collectors.toMap(Itinerary::getSlug, Function.identity(),(a,b)->a));

            int created = 0;
            for (SeedItineraryDTO dto : seedItineraries) {

                if (dto.getFoodRecommendation() == null) {
                    System.out.println("❌ Food is NULL for itinerary: " + dto.getSlug());
                    continue; // or handle later
                }

                if (dto.getPlanning() == null && dto.getTransport() == null && dto.getFoodRecommendation() == null) continue;

                Itinerary itinerary = itineraryBySlug.get(dto.getSlug());
                if (itinerary == null) continue;

                ItineraryPlanningSnapshot snap = toSnapshotEntity(dto.getPlanning(), itinerary);
                ItineraryTransportTip transportTip = toTransportTipEntity(dto.getTransport(), itinerary);
                ItineraryFoodRecommendation foodRecommendation = toFoodRecommendation(dto.getFoodRecommendation(), itinerary);

                if (!snapshotRepository.existsByItinerary_Id(itinerary.getId())){
                    snapshotRepository.save(snap);
                }
                if (!transportTipRepository.existsByItinerary_Id(itinerary.getId())){
                    transportTipRepository.save(transportTip);
                }
                if(!foodRecommendationRepository.existsByItinerary_Id(itinerary.getId())){
                    foodRecommendationRepository.save(foodRecommendation);
                }

                created++;
            }
            System.out.println("Seeded snapshots = " + created);
        }
    }

    private Itinerary toItineraryEntity(SeedItineraryDTO dto) {
        Itinerary itinerary = new Itinerary();

        itinerary.setSlug(dto.getSlug());
        itinerary.setRegion(dto.getRegion());
        itinerary.setCountry(dto.getCountry());
        itinerary.setCity(dto.getCity());
        itinerary.setTitle(dto.getTitle());
        itinerary.setDays(dto.getDays());
        itinerary.setPriceFrom(dto.getPriceFrom());
        itinerary.setHero(dto.getHero());
        itinerary.setSummary(dto.getSummary());
        itinerary.setHighlights(dto.getHighlights());
        itinerary.setDayPlans(
                dto.getSchedule().stream()
                        .map(this::toDayPlanEmbeddable)
                        .toList()
        );

        return itinerary;
    }

    private DayPlan toDayPlanEmbeddable(SeedDayDTO dayDto) {
        DayPlan dayPlan = new DayPlan();
        dayPlan.setDay(dayDto.getDay());
        dayPlan.setTitle(dayDto.getTitle());
        dayPlan.setSummary(dayDto.getSummary());
        dayPlan.setDetails(dayDto.getDetails());
        return dayPlan;
    }

    private ItineraryPlanningSnapshot toSnapshotEntity(SeedPlanningDTO planning, Itinerary itinerary) {

        ItineraryPlanningSnapshot snap = new ItineraryPlanningSnapshot();

        snap.setItinerary(itinerary);

        snap.setCity(planning.getCity());

        snap.setBestTimeMonths(planning.getBestTime().getMonths());
        snap.setBestTimeNote(planning.getBestTime().getNote());

        snap.setWorstTimeMonths(planning.getWorstTime().getMonths());
        snap.setWorstTimeNote(planning.getWorstTime().getNote());

        snap.setTips(planning.getTips());
        snap.setWithKids(planning.getWithKids());

        return snap;
    }

    private ItineraryTransportTip toTransportTipEntity(SeedTransportTipDTO transport, Itinerary itinerary) {

        ItineraryTransportTip transportTip = new ItineraryTransportTip();

        transportTip.setItinerary(itinerary);

        transportTip.setArrival(transport.getArrival());

        transportTip.setGettingAround(transport.getGettingAround());

        transportTip.setDayTrips(transport.getDayTrips());

        transportTip.setDayMoves(transport.getDayMoves());

        transportTip.setPractical(transport.getPractical());

        return transportTip;
    }

    private ItineraryFoodRecommendation toFoodRecommendation(SeedFoodRecommendationDTO food, Itinerary itinerary) {

        ItineraryFoodRecommendation foodRecommendation = new ItineraryFoodRecommendation();

        foodRecommendation.setItinerary(itinerary);

        foodRecommendation.setMustTry(food.getMustTry());

        foodRecommendation.setAreas(food.getAreas());

        foodRecommendation.setPlaces(food.getPlaces());

        return foodRecommendation;
    }
}
