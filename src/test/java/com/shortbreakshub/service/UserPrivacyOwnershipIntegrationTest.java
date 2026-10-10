package com.shortbreakshub.service;

import com.shortbreakshub.PostgresTestSupport;
import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class UserPrivacyOwnershipIntegrationTest extends PostgresTestSupport {
    @org.junit.jupiter.api.BeforeEach void providerIdentity(){when(cloudinary.matchesDraftCoverAsset(anyString(),anyString())).thenReturn(true);}
    @Autowired UserRepository users;
    @Autowired CommunityItineraryRepository itineraries;
    @Autowired CommunityItineraryFavoriteRepository favorites;
    @Autowired CommunityItineraryFavoriteService favoriteService;
    @Autowired CommunityItineraryDraftRepository drafts;
    @Autowired CommunityItineraryDraftService draftService;
    @Autowired DraftCoverUploadRepository uploads;
    @Autowired DraftCoverUploadService coverService;
    @MockBean CloudinaryService cloudinary;

    User user(){var u=new User(UUID.randomUUID()+"@example.invalid","synthetic-hash","Synthetic",null,null,1,0);return users.saveAndFlush(u);}
    CommunityItinerary itinerary(User owner,Visibility visibility){
        var i=new CommunityItinerary();i.setSlug("privacy-"+UUID.randomUUID());i.setUser(owner);i.setVisibility(visibility);
        i.setCountry("Synthetic");i.setRegion(Region.EUROPE);i.setDays(2);i.setTitle("Fixture");i.setSummary("Summary");i.setCoverPhoto("legacy.jpg");i.setDayPlans(List.of());return itineraries.saveAndFlush(i);
    }
    @Test void favoriteQueryHidesLaterPrivateContentAndPreservesOwnerAccess(){
        var owner=user();var viewer=user();var i=itinerary(owner,Visibility.PUBLIC);
        favoriteService.addFavorite(viewer.getId(),i.getId());favoriteService.addFavorite(owner.getId(),i.getId());
        assertEquals(1,favoriteService.getUserFavorites(viewer.getId(),PageRequest.of(0,12)).getTotalElements());
        i.setVisibility(Visibility.PRIVATE);itineraries.saveAndFlush(i);
        var hidden=favoriteService.getUserFavorites(viewer.getId(),PageRequest.of(0,12));assertEquals(0,hidden.getTotalElements());assertTrue(hidden.isEmpty());
        assertEquals(1,favoriteService.getUserFavorites(owner.getId(),PageRequest.of(0,12)).getTotalElements());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->favoriteService.addFavorite(viewer.getId(),i.getId())).getStatusCode().value());
        favoriteService.removeFavorite(viewer.getId(),i.getId());assertFalse(favorites.existsByUserIdAndCommunityItineraryId(viewer.getId(),i.getId()));
    }
    @Test void favoritePagingCountsOnlyVisibleResources(){
        var owner=user();var viewer=user();var pub=itinerary(owner,Visibility.PUBLIC);var hidden=itinerary(owner,Visibility.PRIVATE);var own=itinerary(viewer,Visibility.PRIVATE);
        for(var i:List.of(pub,hidden,own)){var f=new CommunityItineraryFavorite();f.setUser(viewer);f.setCommunityItinerary(i);favorites.saveAndFlush(f);}
        var first=favoriteService.getUserFavorites(viewer.getId(),PageRequest.of(0,1));assertEquals(2,first.getTotalElements());assertEquals(2,first.getTotalPages());
        var all=favoriteService.getUserFavorites(viewer.getId(),PageRequest.of(0,12));assertEquals(2,all.getContent().size());assertFalse(all.getContent().stream().anyMatch(i->i.id().equals(hidden.getId())));
    }
    @Test void migrationPersistsTrustedUploadAndForeignUrlCannotBeClaimed()throws Exception{
        var owner=user();var attacker=user();var file=new MockMultipartFile("file","cover.png","image/png",new byte[]{1});
        var suffix=UUID.randomUUID().toString();var url="https://synthetic.invalid/"+suffix+".png";
        when(cloudinary.uploadDraftCover(file)).thenReturn(new CloudinaryService.UploadedDraftCover("asset-"+suffix,"community-draft-covers/"+suffix,url));
        assertEquals(url,coverService.upload(owner.getId(),file));
        var record=uploads.findBySecureUrlForUpdate(url).orElseThrow();assertEquals(owner.getId(),record.getOwnerId());assertEquals("asset-"+suffix,record.getAssetId());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->draftService.saveDraft(attacker.getId(),"attack-"+suffix,"Synthetic","EUROPE",2,"Attack","Summary",url,null,Visibility.PRIVATE,1f,List.of())).getStatusCode().value());
        assertEquals(0,drafts.countByUser_Id(attacker.getId()));
    }
    @Test void draftReadAndDeletionUsePersistedOwnerAndPreserveLegacy()throws Exception{
        var owner=user();var attacker=user();var d=new CommunityItineraryDraft();d.setUser(owner);d.setCountry("Synthetic");d.setRegion("EUROPE");d.setSlug("legacy-"+UUID.randomUUID());d.setTitle("Private");d.setSummary("Private summary");d.setCoverPhoto("https://legacy.invalid/cover.png");d.setVisibility(Visibility.PRIVATE);d.setEstimatedCost(1f);d.setDays(2);drafts.saveAndFlush(d);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->draftService.getOwnedDraft(d.getId(),attacker.getId())).getStatusCode().value());
        assertEquals(owner.getId(),draftService.getOwnedDraft(d.getId(),owner.getId()).userId());
        draftService.deleteOwnedDraft(d.getId(),owner.getId());assertFalse(drafts.existsById(d.getId()));verifyNoInteractions(cloudinary);
    }
    @Test void deletingLastOwnedDraftPreservesProviderAsset()throws Exception{
        var owner=user();var suffix=UUID.randomUUID().toString();var url="https://synthetic.invalid/"+suffix+".png";
        var record=new DraftCoverUpload();record.setOwnerId(owner.getId());record.setAssetId("asset-"+suffix);record.setPublicId("community-draft-covers/"+suffix);record.setSecureUrl(url);uploads.saveAndFlush(record);
        var d=draftService.saveDraft(owner.getId(),"draft-"+suffix,"Synthetic","EUROPE",2,"Private","Summary",url,null,Visibility.PRIVATE,1f,List.of());drafts.flush();
        draftService.deleteOwnedDraft(d.getId(),owner.getId());assertNull(uploads.findBySecureUrlForUpdate(url).orElseThrow().getDeletedAt());verify(cloudinary).matchesDraftCoverAsset(record.getPublicId(),record.getAssetId());verifyNoMoreInteractions(cloudinary);
    }
    @Test void deletingOneOfTwoDraftReferencesPreservesSharedAsset()throws Exception{
        var owner=user();var suffix=UUID.randomUUID().toString();var url="https://synthetic.invalid/"+suffix+".png";
        var record=new DraftCoverUpload();record.setOwnerId(owner.getId());record.setAssetId("asset-"+suffix);record.setPublicId("community-draft-covers/"+suffix);record.setSecureUrl(url);uploads.saveAndFlush(record);
        var first=draftService.saveDraft(owner.getId(),"first-"+suffix,"Synthetic","EUROPE",2,"Private","Summary",url,null,Visibility.PRIVATE,1f,List.of());
        draftService.saveDraft(owner.getId(),"second-"+suffix,"Synthetic","EUROPE",2,"Private","Summary",url,null,Visibility.PRIVATE,1f,List.of());drafts.flush();
        draftService.deleteOwnedDraft(first.getId(),owner.getId());verify(cloudinary,times(2)).matchesDraftCoverAsset(record.getPublicId(),record.getAssetId());verifyNoMoreInteractions(cloudinary);assertNull(record.getDeletedAt());
    }
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.shortbreakshub.security.JwtService jwt;
    @Autowired ItineraryRepository officialItineraries;
    @Autowired CommentRepository comments;
    @Autowired BuildInItineraryFavoriteRepository officialFavorites;

    String bearer(User user) { return "Bearer " + jwt.generateToken(user.getId(), user.getEmail(), user.getDisplayName()); }
    String draftBody() { return "{\"title\":\"Private draft\",\"region\":\"EUROPE\",\"days\":2,\"visibility\":\"PRIVATE\"}"; }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "GET,/api/auth/me", "PUT,/api/auth/me", "PUT,/api/auth/me/photo",
        "GET,/api/community-itineraries/me", "GET,/api/community-itineraries/me/favorites",
        "GET,/api/community-itineraries/draft/me", "GET,/api/community-itineraries/draft/count",
        "GET,/api/community-itineraries/draft/1", "PUT,/api/community-itineraries/draft/1",
        "DELETE,/api/community-itineraries/draft/1", "POST,/api/community-itineraries/draft/save-draft",
        "POST,/api/community-itineraries/publish-itinerary", "POST,/api/community-itineraries/1/favorite",
        "DELETE,/api/community-itineraries/1/favorite", "POST,/api/community-itineraries/1/question-threads",
        "POST,/api/community-itineraries/1/question-threads/1/messages",
        "GET,/api/itineraries/me/favorites", "POST,/api/itineraries/1/favorite",
        "DELETE,/api/itineraries/1/favorite", "POST,/api/itineraries/1/comments",
        "DELETE,/api/itineraries/1/comments", "GET,/api/itineraries/1/comments/me"
    })
    void protectedEndpointsRejectAnonymousAndInvalidTokens(String method, String path) throws Exception {
        String body = path.endsWith("publish-itinerary")
                ? "{\"title\":\"Published itinerary\",\"country\":\"Synthetic\",\"region\":\"EUROPE\",\"days\":2,\"visibility\":\"PUBLIC\",\"summary\":\"A sufficiently long summary for a valid publication request.\",\"coverPhoto\":\"https://legacy.invalid/image.png\",\"userDayPlan\":[]}"
                : path.equals("/api/auth/me") ? "{\"displayName\":\"Traveller\"}"
                : path.equals("/api/auth/me/photo") ? "{\"avatarUrl\":\"https://legacy.invalid/image.png\"}"
                : path.endsWith("/comments") ? "{\"body\":\"Comment\",\"rating\":4}"
                : path.contains("question-threads") ? "{\"content\":\"Question\"}" : draftBody();
        for (String token : List.of("", "Bearer invalid.token.signature")) {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .request(org.springframework.http.HttpMethod.valueOf(method), path)
                    .contentType("application/json").content(body).param("userId", "999");
            if (!token.isEmpty()) request.header("Authorization", token);
            mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        }
        verifyNoInteractions(cloudinary);
    }

    @Test void anonymousAndInvalidUploadsCannotReachProvider() throws Exception {
        for (String path : List.of("/api/auth/me/photo", "/api/community-itineraries/upload-itinerary-cover-photo",
                "/api/community-itineraries/draft/upload-draft-cover-photo", "/api/community-itineraries/draft/update-draft-cover-photo")) {
            for (String token : List.of("", "Bearer invalid.token.signature")) {
                var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(path)
                        .file(new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1}))
                        .param("existingCoverUrl", "https://legacy.invalid/image.png").param("ownerId", "999");
                if (!token.isEmpty()) request.header("Authorization", token);
                mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
            }
        }
        verifyNoInteractions(cloudinary);
    }

    @Test void crossUserDraftUpdateAndDeletionHideContentsAndLeaveOwnerDataIntact() throws Exception {
        var owner = user(); var attacker = user();
        var draft = draftService.saveDraft(owner.getId(), "draft-"+UUID.randomUUID(), "Synthetic", "EUROPE", 2,
                "Private title", "Private summary", null, null, Visibility.PRIVATE, 1f, List.of()); drafts.flush();
        for (String method : List.of("GET", "PUT", "DELETE")) {
            var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .request(org.springframework.http.HttpMethod.valueOf(method), "/api/community-itineraries/draft/"+draft.getId())
                    .header("Authorization", bearer(attacker)).contentType("application/json").content(draftBody()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound()).andReturn();
            assertFalse(result.getResponse().getContentAsString().contains("Private summary"));
        }
        assertEquals("Private title", drafts.findById(draft.getId()).orElseThrow().getTitle());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/community-itineraries/draft/"+draft.getId())
                .header("Authorization", bearer(owner)).contentType("application/json").content(draftBody()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals("Private draft", drafts.findById(draft.getId()).orElseThrow().getTitle());
    }

    @Test void ownProfileAndItineraryListsIgnoreClientSuppliedIdentity() throws Exception {
        var owner = user(); var attacker = user(); var hidden = itinerary(owner, Visibility.PRIVATE);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/auth/me")
                .header("Authorization", bearer(attacker)).contentType("application/json")
                .content("{\"displayName\":\"Changed caller\",\"userId\":"+owner.getId()+",\"role\":\"ADMIN\",\"avatarUrl\":\"foreign.png\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals("Synthetic", users.findById(owner.getId()).orElseThrow().getDisplayName());
        assertEquals("Changed caller", users.findById(attacker.getId()).orElseThrow().getDisplayName());
        assertNull(users.findById(attacker.getId()).orElseThrow().getAvatarUrl());
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/community-itineraries/me")
                .param("userId", owner.getId().toString()).header("Authorization", bearer(attacker)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(0)).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(hidden.getSlug()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/community-itineraries/me")
                .header("Authorization", bearer(owner)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].slug").value(hidden.getSlug()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/community-itineraries/slug/"+hidden.getSlug()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        hidden.setVisibility(Visibility.PUBLIC); itineraries.saveAndFlush(hidden);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/community-itineraries/slug/"+hidden.getSlug()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test void officialCommentsAndFavoritesRemainScopedToAuthenticatedUser() throws Exception {
        var owner=user(); var attacker=user();
        var itinerary=new Itinerary(); itinerary.setSlug("official-"+UUID.randomUUID()); itinerary.setCountry("Synthetic");
        itinerary.setCity("Synthetic"); itinerary.setRegion("europe"); itinerary.setTitle("Public fixture");
        officialItineraries.saveAndFlush(itinerary);
        var comment=new BuildInItineraryComment(); comment.setUser(owner); comment.setItinerary(itinerary); comment.setBody("Owner comment"); comments.saveAndFlush(comment);
        var favorite=new BuildInItineraryFavorite(); favorite.setUser(owner); favorite.setItinerary(itinerary); officialFavorites.saveAndFlush(favorite);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/itineraries/"+itinerary.getId()+"/comments")
                .param("userId",owner.getId().toString()).header("Authorization",bearer(attacker)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/itineraries/"+itinerary.getId()+"/favorite")
                .param("userId",owner.getId().toString()).header("Authorization",bearer(attacker)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        assertTrue(comments.existsById(comment.getId()));
        assertTrue(officialFavorites.existsByUserIdAndItineraryId(owner.getId(),itinerary.getId()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/itineraries/"+itinerary.getId()+"/comments"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].body").value("Owner comment"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/itineraries/"+itinerary.getId()+"/comments")
                .header("Authorization",bearer(owner)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        assertFalse(comments.existsById(comment.getId()));
    }

    @Autowired UserItineraryService publishing;

    @Test void publishedLegacyCoverRemainsReadableAndCannotBeClaimedThroughPublication() throws Exception {
        var owner = user(); var existing = itinerary(owner, Visibility.PUBLIC);
        String originalCover = existing.getCoverPhoto();
        assertEquals(originalCover, publishing.getBySlug(existing.getSlug()).coverPhoto());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/community-itineraries/publish-itinerary")
                .header("Authorization", bearer(owner)).contentType("application/json")
                .content("{\"title\":\"Existing legacy itinerary\",\"country\":\"Synthetic\",\"region\":\"EUROPE\",\"days\":2,\"visibility\":\"PUBLIC\",\"summary\":\"A sufficiently long summary for a valid publication request.\",\"coverPhoto\":\"legacy.jpg\",\"userDayPlan\":[]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        assertEquals(1, itineraries.findUserItinerariesByUser_Id(owner.getId(), PageRequest.of(0, 12)).getTotalElements());
        assertEquals(originalCover, publishing.getBySlug(existing.getSlug()).coverPhoto());
        assertEquals(0, uploads.count());
        verifyNoInteractions(cloudinary);
    }

    @Test void legacyDraftMustUseFreshVerifiedUploadBeforePublication() throws Exception {
        var owner = user(); String suffix = UUID.randomUUID().toString();
        var draft = new CommunityItineraryDraft(); draft.setUser(owner); draft.setSlug("legacy-" + suffix);
        draft.setTitle("Legacy title"); draft.setCountry("Synthetic"); draft.setRegion("EUROPE"); draft.setDays(2);
        draft.setSummary("Legacy summary"); draft.setVisibility(Visibility.PRIVATE); draft.setEstimatedCost(1f);
        draft.setCoverPhoto("https://legacy.invalid/cover.jpg"); drafts.saveAndFlush(draft);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> publishing.publishOrUpdate(
                owner.getId(), "publication-" + suffix, "Synthetic", Region.EUROPE, 2, "Publication title",
                "Publication summary", draft.getCoverPhoto(), null, Visibility.PUBLIC, 1f, List.of())).getStatusCode().value());
        assertEquals(0, itineraries.findUserItinerariesByUser_Id(owner.getId(), PageRequest.of(0, 12)).getTotalElements());
        assertEquals("https://legacy.invalid/cover.jpg", draftService.getOwnedDraft(draft.getId(), owner.getId()).coverPhoto());
        var file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});
        String publicId = "community-draft-covers/" + suffix;
        String freshUrl = "https://res.cloudinary.com/synthetic/image/upload/v1/" + publicId + ".png";
        when(cloudinary.uploadDraftCover(file)).thenReturn(new CloudinaryService.UploadedDraftCover("asset-" + suffix, publicId, freshUrl));
        assertEquals(freshUrl, coverService.replace(owner.getId(), draft.getCoverPhoto(), file));
        publishing.publishOrUpdate(owner.getId(), "publication-" + suffix, "Synthetic", Region.EUROPE, 2,
                "Publication title", "Publication summary", freshUrl, null, Visibility.PUBLIC, 1f, List.of());
        itineraries.flush();
        assertEquals(freshUrl, publishing.getBySlug("publication-" + suffix).coverPhoto());
        assertEquals("https://legacy.invalid/cover.jpg", draftService.getOwnedDraft(draft.getId(), owner.getId()).coverPhoto());
        assertEquals(1, uploads.count());
        verify(cloudinary).uploadDraftCover(file);
        verify(cloudinary).matchesDraftCoverAsset(publicId, "asset-" + suffix);
        verifyNoMoreInteractions(cloudinary);
    }

}
