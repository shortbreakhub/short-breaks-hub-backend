package com.shortbreakshub.controller;

import com.shortbreakshub.dto.ItineraryRes;
import com.shortbreakshub.model.Itinerary;
import com.shortbreakshub.service.ItineraryService;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/itineraries")
public class ItineraryController {

    private final ItineraryService service;

    public ItineraryController(ItineraryService service) {
        this.service = service;
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ItineraryRes> getOne(@PathVariable String slug,
                                               @RequestParam(defaultValue = "en") String lang) {
        return ResponseEntity.ok(service.getBySlug(slug,lang));
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<Object> getFindDistinctCountryByRegion(@PathVariable String region) {
        List<String> result = service.getDistinctCountryByRegion(region);
        if (result.isEmpty()) {
            Map<String, Object> body = Map.of(
                    "status", 404,
                    "error", "No countries found for region: " + region
            );
            return ResponseEntity.status(404).body(body);
        } else {
            return ResponseEntity.ok(result);
        }
    }

    @GetMapping("/browse/{country}")
    public ResponseEntity<Object> getByCountry(@PathVariable String country) {
        List<Itinerary> result = service.getByCountry(WordUtils.capitalizeFully(country));
        if (result.isEmpty()) {
            Map<String, Object> body = Map.of(
                    "status", 404,
                    "error", "No countries found for country: " + country
            );
            return ResponseEntity.status(404).body(body);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{region}")
    public ResponseEntity<Object> getByRegion(@PathVariable String region) {
        List<Itinerary> result = service.getByRegion(region);
        if (result.isEmpty()) {
            Map<String, Object> body = Map.of(
                    "status", 404,
                    "error", "No countries found for region: " + region
            );
            return ResponseEntity.status(404).body(body);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public Page<Itinerary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer daysMin,
            @RequestParam(required = false) Integer daysMax,
            @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return service.getAllItinerariesByCustomSearch(q, country, daysMin, daysMax, pageable);
    }

}

