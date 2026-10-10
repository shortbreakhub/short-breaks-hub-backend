package com.shortbreakshub.service;

import com.shortbreakshub.model.CommunityItineraryFavorite;
import com.shortbreakshub.model.CommunityItinerary;
import com.shortbreakshub.model.Visibility;
import com.shortbreakshub.dto.CommunityFavoriteRes;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.shortbreakshub.repository.CommunityItineraryFavoriteRepository;
import com.shortbreakshub.repository.CommunityItineraryRepository;
import com.shortbreakshub.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityItineraryFavoriteService {
    private final CommunityItineraryFavoriteRepository repo;
    private final UserRepository users;
    private final CommunityItineraryRepository itineraries;

    public CommunityItineraryFavoriteService(CommunityItineraryFavoriteRepository r,
                                             UserRepository u,
                                             CommunityItineraryRepository i) {
        this.repo = r;
        this.users = u;
        this.itineraries = i;
    }

    @Transactional
    public void addFavorite(Long userId, Long itineraryId) {
        var itin = visibleItinerary(userId, itineraryId);
        if (repo.existsByUserIdAndCommunityItineraryId(userId, itineraryId)) return;
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        var f = new CommunityItineraryFavorite();
        f.setUser(user);
        f.setCommunityItinerary(itin);
        repo.save(f);
    }

    @Transactional
    public void removeFavorite(Long userId, Long itineraryId) {
        repo.deleteByUserIdAndCommunityItineraryId(userId, itineraryId);
    }

    public long countItineraryFavorites(Long itineraryId, Long userId) {
        visibleItinerary(userId, itineraryId);
        return repo.countByCommunityItineraryId(itineraryId);
    }

    public boolean isFavorite(Long userId, Long itineraryId) {
        visibleItinerary(userId, itineraryId);
        return repo.existsByUserIdAndCommunityItineraryId(userId, itineraryId);
    }


    @Transactional(readOnly = true)
    public Page<CommunityFavoriteRes> getUserFavorites(Long userId, Pageable pageable) {
        return repo.findItinerariesFavoritedByUser(userId, Visibility.PUBLIC, pageable).map(CommunityFavoriteRes::from);
    }
    private CommunityItinerary visibleItinerary(Long userId, Long itineraryId) {
        var itinerary = itineraries.findById(itineraryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Itinerary not found"));
        if (itinerary.getVisibility() != Visibility.PUBLIC && !itinerary.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Itinerary not found");
        }
        return itinerary;
    }
}
