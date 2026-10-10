package com.shortbreakshub.service;

import com.shortbreakshub.dto.UserItineraryRes;
import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.CommunityItineraryRepository;
import com.shortbreakshub.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserItineraryService {

    private final UserRepository userRepo;
    private final CommunityItineraryRepository userItineraryRepo;
    private final DraftCoverUploadService coverUploads;

    public UserItineraryService(UserRepository userRepo, CommunityItineraryRepository userItineraryRepo, DraftCoverUploadService coverUploads) {
        this.userRepo = userRepo;
        this.userItineraryRepo = userItineraryRepo;
        this.coverUploads = coverUploads;
    }

    public UserItineraryRes getBySlug(String slug) {
        return userItineraryRepo.findBySlugAndVisibility(slug, Visibility.PUBLIC).map(UserItineraryRes::toRes)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,"User Itinerary not found"));
    }

    @Transactional(readOnly = true)
    public Page<UserItineraryRes> getUserItineraryMe(Long userId, Pageable pageable) {
        Page<CommunityItinerary> userItinerariesMe = userItineraryRepo.findUserItinerariesByUser_Id(userId, pageable);
        return userItinerariesMe.map(UserItineraryRes::toRes);
    }

    @Transactional
    public void publishOrUpdate(Long userId, String slug, String country,
                                Region region, int days, String title,
                                String summary, String coverPhoto, String highlights,
                                Visibility visibility, Float estimatedCost, List<UserDayPlan> dayPlans) {
        coverUploads.validateReference(userId, coverPhoto);
        CommunityItinerary communityItinerary = new CommunityItinerary();
        communityItinerary.setUser(userRepo.getReferenceById(userId));
        communityItinerary.setSlug(slug);
        communityItinerary.setCountry(country);
        communityItinerary.setRegion(region);
        communityItinerary.setDays(days);
        communityItinerary.setTitle(title);
        communityItinerary.setSummary(summary);
        communityItinerary.setCoverPhoto(coverPhoto);
        communityItinerary.setHighlights(highlights);
        communityItinerary.setVisibility(visibility);
        communityItinerary.setEstimatedCost(estimatedCost);
        communityItinerary.setDayPlans(dayPlans);
        userItineraryRepo.save(communityItinerary);
    }

    public List<String> getDistinctCountryByRegion(String region) {
        return communityResults(region).stream().map(CommunityItinerary::getCountry).distinct().toList();
    }

    public List<UserItineraryRes> getByRegion(String region) {
        return communityResults(region).stream().map(UserItineraryRes::toRes).toList();
    }

    private List<CommunityItinerary> communityResults(String identifier) {
        var selection = CommunityRegionResolver.resolve(identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported community region"));
        return userItineraryRepo.findItinerariesByRegionAndVisibility(selection.region().name(), Visibility.PUBLIC)
                .stream()
                .filter(item -> item.getVisibility() == Visibility.PUBLIC && item.getRegion() == selection.region())
                .filter(item -> selection.includesCountry(item.getCountry()))
                .toList();
    }
}
