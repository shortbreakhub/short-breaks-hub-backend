package com.shortbreakshub.dto;

import com.shortbreakshub.model.*;
import java.time.Instant;
import java.util.List;

/** Preserve favorite itinerary fields, but expose only the author's public identity. */
public record CommunityFavoriteRes(Long id, String slug, String country, Region region, int days,
        String title, String summary, String coverPhoto, Instant createdAt, PublicAuthor user,
        String highlights, Instant updatedAt, Visibility visibility, Float estimatedCost, List<UserDayPlan> schedule) {
    public record PublicAuthor(Long id, String displayName, String avatarUrl) { }

    public static CommunityFavoriteRes from(CommunityItinerary itinerary) {
        var author = itinerary.getUser();
        return new CommunityFavoriteRes(itinerary.getId(), itinerary.getSlug(), itinerary.getCountry(),
                itinerary.getRegion(), itinerary.getDays(), itinerary.getTitle(), itinerary.getSummary(),
                itinerary.getCoverPhoto(), itinerary.getCreatedAt(),
                new PublicAuthor(author.getId(), author.getDisplayName(), author.getAvatarUrl()),
                itinerary.getHighlights(), itinerary.getUpdatedAt(), itinerary.getVisibility(),
                itinerary.getEstimatedCost(), itinerary.getDayPlans());
    }
}
