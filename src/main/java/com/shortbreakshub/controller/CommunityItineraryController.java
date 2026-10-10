package com.shortbreakshub.controller;

import com.shortbreakshub.dto.PublishUserItineraryReq;
import com.shortbreakshub.dto.UserItineraryRes;
import com.shortbreakshub.model.CommunityItinerary;
import com.shortbreakshub.model.Itinerary;
import com.shortbreakshub.service.CommunityRegionResolver;
import com.shortbreakshub.service.DraftCoverUploadService;
import com.shortbreakshub.service.UserItineraryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community-itineraries")
public class CommunityItineraryController {

    private final UserItineraryService userItineraryService;
    private final DraftCoverUploadService coverUploads;

    public CommunityItineraryController(UserItineraryService userItineraryService, DraftCoverUploadService coverUploads) {
        this.userItineraryService = userItineraryService;
        this.coverUploads = coverUploads;
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<UserItineraryRes> getOne(@PathVariable String slug) {
        return ResponseEntity.ok(userItineraryService.getBySlug(slug));
    }

    @GetMapping("/me")
    public ResponseEntity <Page<UserItineraryRes>> myItineraries(
            @PageableDefault(size = 12,sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest req
    ) {
        Long userId = (Long) req.getAttribute("authUserId");
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(userItineraryService.getUserItineraryMe(userId, pageable));
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<Object> getFindDistinctCountryByRegion(@PathVariable String region) {
        if (!isSupportedRegion(region)) return unsupportedRegion(region);
        return ResponseEntity.ok(userItineraryService.getDistinctCountryByRegion(region));
    }

    @GetMapping("/{region}")
    public ResponseEntity<Object> getByRegion(@PathVariable String region) {
        if (!isSupportedRegion(region)) return unsupportedRegion(region);
        return ResponseEntity.ok(userItineraryService.getByRegion(region));
    }

    private boolean isSupportedRegion(String region) {
        return CommunityRegionResolver.resolve(region).isPresent();
    }

    private ResponseEntity<Object> unsupportedRegion(String region) {
        return ResponseEntity.status(404).body(Map.of(
                "status", 404,
                "error", "No countries found for region: " + region
        ));
    }

    @PostMapping(path = "/upload-itinerary-cover-photo",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> itineraryPhotoUpload(HttpServletRequest request, @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String itineraryPhotoUrl = coverUploads.upload(userId, file);
        return ResponseEntity.ok(itineraryPhotoUrl);
    }

    @PostMapping(path = "publish-itinerary")
    public ResponseEntity<Map<String,String>> publishItinerary(HttpServletRequest request,@Valid @RequestBody PublishUserItineraryReq publish){
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String slug = publish.title()
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");

        userItineraryService.publishOrUpdate(
                userId,
                slug,
                publish.country(),
                publish.region(),
                publish.days(),
                publish.title(),
                publish.summary(),
                publish.coverPhoto(),
                publish.highlights(),
                publish.visibility(),
                publish.estimatedCost(),
                publish.userDayPlan()
        );
        return ResponseEntity.ok(Map.of("msg","User Itinerary Published"));
    }
}
