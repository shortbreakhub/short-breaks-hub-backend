package com.shortbreakshub.controller;


import com.shortbreakshub.dto.CommunityItineraryDraftRes;
import com.shortbreakshub.dto.DraftUserItineraryReq;
import com.shortbreakshub.model.CommunityItineraryDraft;
import com.shortbreakshub.service.DraftCoverUploadService;
import com.shortbreakshub.service.CommunityItineraryDraftService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community-itineraries/draft")
public class CommunityItineraryDraftController {

    private final CommunityItineraryDraftService draftService;
    private final DraftCoverUploadService coverUploads;

    private String generateSlug(String title) {
        String slug = "";
        if(title != null && !title.isEmpty()){
            slug = title
                    .toLowerCase()
                    .trim()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .replaceAll("\\s+", "-");

        }
        return slug;
    }

    private ResponseEntity<Map<String, Object>> buildDraftResponse(@RequestBody @Valid DraftUserItineraryReq draft, Long userId,String message) {
        String slug = generateSlug(draft.title());
        CommunityItineraryDraft savedDraft = draftService.saveDraft(
                userId,
                slug,
                draft.country(),
                draft.region(),
                draft.days(),
                draft.title(),
                draft.summary(),
                draft.coverPhoto(),
                draft.highlights(),
                draft.visibility(),
                draft.estimatedCost(),
                draft.userDayPlan()
        );
        return ResponseEntity.ok(Map.of("msg",message,"draftId",savedDraft.getId()));
    }


    public CommunityItineraryDraftController(CommunityItineraryDraftService draftService, DraftCoverUploadService coverUploads) {
        this.draftService = draftService;
        this.coverUploads = coverUploads;
    }

    @GetMapping(path = "/count")
    public ResponseEntity<Map<String, Integer>> countDrafts(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("count",draftService.countDraftMe(userId)));
    }

    @PostMapping(path = "/upload-draft-cover-photo",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> draftPhotoUpload(HttpServletRequest request, @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String itineraryPhotoUrl = coverUploads.upload(userId, file);
        return ResponseEntity.ok(itineraryPhotoUrl);
    }

    @PostMapping(path = "/update-draft-cover-photo",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> draftPhotoUpdate(HttpServletRequest request,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam("existingCoverUrl") String photoCoverUrl
    ) throws IOException {
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String draftPhotoUrl = coverUploads.replace(userId, photoCoverUrl, file);
        return ResponseEntity.ok(draftPhotoUrl);
    }

    @PostMapping(path = "/save-draft")
    public ResponseEntity<Map<String,Object>> saveDraftItinerary(HttpServletRequest request, @Valid @RequestBody DraftUserItineraryReq draft){
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return buildDraftResponse(draft, userId,"User Draft Published");
    }


    @GetMapping(path = "/me")
    public ResponseEntity<List<CommunityItineraryDraftRes>> getMyDraft(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(draftService.getDraftByUserId(userId));
    }

    @GetMapping(path = "/{draftId}")
    public ResponseEntity<CommunityItineraryDraftRes> getMyDraft(HttpServletRequest request, @PathVariable Long draftId) {
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(draftService.getOwnedDraft(draftId, userId));
    }

    @PutMapping(path = "/{draftId}")
    public ResponseEntity<Map<String,Object>> updateMyDraft(HttpServletRequest request,
                                                            @PathVariable Long draftId,
                                                            @Valid @RequestBody DraftUserItineraryReq draft){
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        draftService.updateDraft(
                draftId,
                userId,
                generateSlug(draft.title()),
                draft.country(),
                draft.region(),
                draft.days(),
                draft.title(),
                draft.summary(),
                draft.coverPhoto(),
                draft.highlights(),
                draft.visibility(),
                draft.estimatedCost(),
                draft.userDayPlan()
        );

        return ResponseEntity.ok(Map.of("msg","Draft has been updated."));
    }

    @DeleteMapping(path = "/{draftId}")
    public ResponseEntity<Map<String,Object>> deleteMyDraft(HttpServletRequest request,
                                                            @PathVariable Long draftId) throws IOException{

        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        draftService.deleteOwnedDraft(draftId, userId);

        return ResponseEntity.ok(Map.of("msg","Draft has been deleted."));
    }
}

