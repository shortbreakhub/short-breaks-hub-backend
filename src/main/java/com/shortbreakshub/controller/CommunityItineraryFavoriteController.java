package com.shortbreakshub.controller;

import com.shortbreakshub.dto.CommunityFavoriteRes;
import com.shortbreakshub.service.CommunityItineraryFavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/community-itineraries")
public class CommunityItineraryFavoriteController {
    private final CommunityItineraryFavoriteService communityItineraryFavoriteService;
    public CommunityItineraryFavoriteController(CommunityItineraryFavoriteService s)
    { this.communityItineraryFavoriteService = s; }

    @PostMapping("/{itineraryId}/favorite")
    public ResponseEntity<Void> favorite(@PathVariable Long itineraryId,
                                         HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("authUserId");
        if (userId == null) return ResponseEntity.status(401).build();
        communityItineraryFavoriteService.addFavorite(userId, itineraryId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{itineraryId}/favorite")
    public ResponseEntity<Void> unfavorite(@PathVariable Long itineraryId,
                                           HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("authUserId");
        if (userId == null) return ResponseEntity.status(401).build();
        communityItineraryFavoriteService.removeFavorite(userId, itineraryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{itineraryId}/favorites/count")
    public Map<String,Long> count(@PathVariable Long itineraryId, HttpServletRequest req) {
        return Map.of("count", communityItineraryFavoriteService.countItineraryFavorites(itineraryId, (Long) req.getAttribute("authUserId")));
    }

    @GetMapping("/{itineraryId}/favorites/me")
    public Map<String,Boolean> mine(@PathVariable Long itineraryId,
                                    HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("authUserId");
        boolean liked = (userId != null) && communityItineraryFavoriteService.isFavorite(userId, itineraryId);
        return Map.of("liked", liked);
    }

    @GetMapping("/me/favorites")
    public ResponseEntity <Page<CommunityFavoriteRes>> myFavorites(
            @PageableDefault(size = 12,sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest req
    ) {
        Long userId = (Long) req.getAttribute("authUserId");
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(communityItineraryFavoriteService.getUserFavorites(userId, pageable));
    }
}

