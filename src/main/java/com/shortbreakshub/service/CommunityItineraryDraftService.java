package com.shortbreakshub.service;


import com.shortbreakshub.dto.CommunityItineraryDraftRes;
import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.CommunityItineraryDraftRepository;
import com.shortbreakshub.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


@Service
public class CommunityItineraryDraftService {

    public final CommunityItineraryDraftRepository draftRepository;
    private final UserRepository userRepo;
    private final DraftCoverUploadService coverUploads;

    public CommunityItineraryDraftService(CommunityItineraryDraftRepository draftRepository, UserRepository userRepo, DraftCoverUploadService coverUploads) {
        this.draftRepository = draftRepository;
        this.userRepo = userRepo;
        this.coverUploads = coverUploads;
    }

    @Transactional
    public CommunityItineraryDraft saveDraft(Long userId, String slug, String country,
                          String region, int days, String title,
                          String summary, String coverPhoto, String highlights,
                          Visibility visibility, Float estimatedCost, List<UserDayPlan> dayPlans) {
        coverUploads.validateReference(userId, coverPhoto);
        CommunityItineraryDraft draft = new CommunityItineraryDraft();
        draft.setUser(userRepo.getReferenceById(userId));
        draft.setSlug(slug);
        draft.setCountry(country);
        draft.setRegion(region);
        draft.setDays(days);
        draft.setTitle(title);
        draft.setSummary(summary);
        draft.setCoverPhoto(coverPhoto);
        draft.setHighlights(highlights);
        draft.setVisibility(visibility);
        draft.setEstimatedCost(estimatedCost);
        draft.setDayPlans(dayPlans);
        return draftRepository.save(draft);
    }

    public int countDraftMe(long id) {
        return draftRepository.countByUser_Id(id);
    }

    @Transactional(readOnly = true)
    public List<CommunityItineraryDraftRes> getDraftByUserId(Long userId) {
        return draftRepository.findByUser_Id(userId).stream().map(CommunityItineraryDraftRes::toRes).toList();
    }

    @Transactional(readOnly = true)
    public CommunityItineraryDraftRes getOwnedDraft(Long draftId, Long userId) {
        CommunityItineraryDraft draftContent = draftRepository.findByIdAndUser_Id(draftId, userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        return CommunityItineraryDraftRes.toRes(draftContent);
    }

    public boolean isDraftExist(Long draftId) {
        return draftRepository.existsById(draftId);
    }

    public boolean isDraftBelongsToUser(Long draftId, Long userId) {
        return draftRepository.findByIdAndUser_Id(draftId, userId).isPresent();
    }

    @Transactional
    public void updateDraft(Long draftId, Long userId, String slug, String country,
                            String region, int days, String title,
                            String summary, String coverPhoto, String highlights,
                            Visibility visibility, Float estimatedCost,
                            List<UserDayPlan> dayPlans){
        CommunityItineraryDraft existingDraft = draftRepository.findByIdAndUser_Id(draftId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        coverUploads.validateAssignment(userId, coverPhoto, existingDraft.getCoverPhoto());
        existingDraft.setSlug(slug);
        existingDraft.setCountry(country);
        existingDraft.setRegion(region);
        existingDraft.setDays(days);
        existingDraft.setTitle(title);
        existingDraft.setSummary(summary);
        existingDraft.setCoverPhoto(coverPhoto);
        existingDraft.setHighlights(highlights);
        existingDraft.setVisibility(visibility);
        existingDraft.setEstimatedCost(estimatedCost);
        existingDraft.setDayPlans(dayPlans);
        draftRepository.save(existingDraft);
        CommunityItineraryDraftRes.toRes(existingDraft);
    }


    @Transactional(rollbackFor = java.io.IOException.class)
    public void deleteOwnedDraft(Long draftId, Long userId) throws java.io.IOException {
        var draft = draftRepository.findByIdAndUser_Id(draftId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        draftRepository.delete(draft);
        draftRepository.flush();
    }
}
