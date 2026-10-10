package com.shortbreakshub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbreakshub.config.GlobalExceptionHandler;
import com.shortbreakshub.controller.*;
import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.*;
import com.shortbreakshub.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserPrivacyOwnershipTest {
    UserRepository users;
    CommunityItineraryRepository itineraries;
    CommunityItineraryFavoriteRepository favorites;
    CommunityItineraryDraftRepository drafts;
    CommunityItineraryQuestionThreadRepository threads;
    DraftCoverUploadRepository uploads;
    CloudinaryService cloudinary;
    DraftCoverUploadService covers;
    CommunityItineraryDraftService draftService;
    MockMvc mvc;
    JwtService jwt;
    ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        users = mock(UserRepository.class); itineraries = mock(CommunityItineraryRepository.class);
        favorites = mock(CommunityItineraryFavoriteRepository.class); drafts = mock(CommunityItineraryDraftRepository.class);
        threads = mock(CommunityItineraryQuestionThreadRepository.class); uploads = mock(DraftCoverUploadRepository.class);
        cloudinary = mock(CloudinaryService.class);
        covers = new DraftCoverUploadService(uploads, drafts, cloudinary);
        draftService = new CommunityItineraryDraftService(drafts, users, covers);
        jwt = new JwtService("synthetic-privacy-test-key-at-least-32-characters", 60000);
        mvc = MockMvcBuilders.standaloneSetup(
                new CommunityItineraryDraftController(draftService, covers),
                new UserController(new UserService(users,covers,mock(EmailService.class),mock(EmailVerificationService.class)),covers),
                new CommunityItineraryController(new UserItineraryService(users,itineraries,covers),covers),
                new CommunityItineraryFavoriteController(new CommunityItineraryFavoriteService(favorites, users, itineraries)),
                new CommunityItineraryQuestionController(new CommunityItineraryQuestionService(threads, itineraries), mock(UserService.class)))
                .addFilters(new JwtAuthFilter(jwt)).setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }
    String bearer(long id) { return "Bearer " + jwt.generateToken(id, "synthetic@example.invalid", "Traveller"); }
    static User user(long id) {
        var u = new User("private@example.invalid", "SENSITIVE_PASSWORD_HASH", "Public name", "PRIVATE_LOCATION", "PRIVATE_BIO", 2, 1);
        u.setId(id); u.setAvatarUrl("public-avatar.jpg"); return u;
    }
    static CommunityItinerary itinerary(long id, long owner, Visibility visibility) {
        var i = new CommunityItinerary(); i.setId(id); i.setUser(user(owner)); i.setVisibility(visibility);
        i.setSlug("synthetic-trip-"+id); i.setTitle("Public title"); i.setCountry("Synthetic"); i.setRegion(Region.EUROPE);
        i.setDays(2); i.setSummary("Summary"); i.setCoverPhoto("cover.jpg"); i.setDayPlans(List.of()); return i;
    }
    CommunityItineraryDraft draft() {
        var d = new CommunityItineraryDraft(); d.setId(9L); d.setUser(user(2)); d.setEstimatedCost(1f);
        d.setTitle("PRIVATE_DRAFT"); d.setSummary("PRIVATE_NOTES"); d.setVisibility(Visibility.PRIVATE); return d;
    }

    @ParameterizedTest @CsvSource({"1,404", "2,200"})
    void draftReadRequiresActualOwner(long viewer, int status) throws Exception {
        if(viewer == 2) when(drafts.findByIdAndUser_Id(9,2)).thenReturn(Optional.of(draft()));
        var body = mvc.perform(get("/api/community-itineraries/draft/9").header("Authorization",bearer(viewer)))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        if(viewer != 2) assertFalse(body.contains("PRIVATE_"));
        verify(drafts).findByIdAndUser_Id(9,viewer); verify(drafts,never()).findById(any());
    }
    @Test void anonymousDraftReadReturns401() throws Exception {
        mvc.perform(get("/api/community-itineraries/draft/9")).andExpect(status().isUnauthorized()); verifyNoInteractions(drafts);
    }
    @ParameterizedTest @CsvSource({"PUBLIC,1,200", "PUBLIC,2,200", "PRIVATE,1,404", "PRIVATE,2,200"})
    void favoriteCreationChecksVisibilityBeforeExistingFavorite(Visibility visibility,long viewer,int status) throws Exception {
        when(itineraries.findById(5L)).thenReturn(Optional.of(itinerary(5,2,visibility)));
        when(users.findById(viewer)).thenReturn(Optional.of(user(viewer)));
        mvc.perform(post("/api/community-itineraries/5/favorite").header("Authorization",bearer(viewer)))
                .andExpect(status().is(status));
        if(status == 404) verifyNoInteractions(favorites); else verify(favorites).save(any());
    }
    @Test void existingFavoriteDoesNotAuthorizePrivateResource() throws Exception {
        when(itineraries.findById(5L)).thenReturn(Optional.of(itinerary(5,2,Visibility.PRIVATE)));
        mvc.perform(post("/api/community-itineraries/5/favorite").header("Authorization",bearer(1))).andExpect(status().isNotFound());
        verifyNoInteractions(favorites);
    }
    @Test void favoriteMutationAndListRequireAuthentication() throws Exception {
        mvc.perform(post("/api/community-itineraries/5/favorite")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/community-itineraries/me/favorites")).andExpect(status().isUnauthorized());
        verifyNoInteractions(favorites, itineraries);
    }
    @Test void favoriteSerializationExposesOnlyPublicAuthorFields() throws Exception {
        var page = new PageImpl<>(List.of(itinerary(5,2,Visibility.PUBLIC)),PageRequest.of(0,12),1);
        when(favorites.findItinerariesFavoritedByUser(eq(1L),eq(Visibility.PUBLIC),any())).thenReturn(page);
        var response=mvc.perform(get("/api/community-itineraries/me/favorites").header("Authorization",bearer(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].region").value("EUROPE"))
                .andExpect(jsonPath("$.content[0].user.id").value(2))
                .andExpect(jsonPath("$.content[0].user.displayName").value("Public name"))
                .andReturn().getResponse().getContentAsString();
        var author=json.readTree(response).get("content").get(0).get("user");
        assertEquals(Set.of("id","displayName","avatarUrl"),new HashSet<>(json.convertValue(author,Map.class).keySet()));
        for(String sensitive:List.of("passwordHash","email","bio","location","role","emailVerified","currency","adults","children","SENSITIVE_PASSWORD_HASH","PRIVATE_BIO","PRIVATE_LOCATION")) assertFalse(response.contains(sensitive),sensitive);
    }
    @Test void privateFavoriteCountCannotBeReadByOtherUserOrAnonymous() throws Exception {
        when(itineraries.findById(5L)).thenReturn(Optional.of(itinerary(5,2,Visibility.PRIVATE)));
        mvc.perform(get("/api/community-itineraries/5/favorites/count")).andExpect(status().isNotFound());
        mvc.perform(get("/api/community-itineraries/5/favorites/count").header("Authorization",bearer(1))).andExpect(status().isNotFound());
        mvc.perform(get("/api/community-itineraries/5/favorites/count").header("Authorization",bearer(2))).andExpect(status().isOk());
        verify(favorites,times(1)).countByCommunityItineraryId(5L);
    }
    @ParameterizedTest @CsvSource({"PUBLIC,0,200", "PUBLIC,1,200", "PRIVATE,0,404", "PRIVATE,1,404", "PRIVATE,2,200"})
    void questionReadsEnforceParentVisibility(Visibility visibility,long viewer,int status) throws Exception {
        var parent=itinerary(5,2,visibility); when(itineraries.findById(5L)).thenReturn(Optional.of(parent));
        var thread=new CommunityItineraryQuestionThread(); thread.setId(7L);thread.setAsker(user(1));thread.setCommunityItinerary(parent);
        when(threads.findById(7L)).thenReturn(Optional.of(thread));when(threads.findByCommunityItineraryId(5L)).thenReturn(List.of(thread));
        for(String path:List.of("/api/community-itineraries/5/question-threads","/api/community-itineraries/5/question-threads/7")) {
            var req=get(path);if(viewer!=0)req.header("Authorization",bearer(viewer));
            var body=mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
            if(status==404)assertFalse(body.contains("Public name"));
        }
        if(status==404)verifyNoInteractions(threads);
    }
    @Test void mismatchedParentThreadReturns404ForPublicParent() throws Exception {
        when(itineraries.findById(5L)).thenReturn(Optional.of(itinerary(5,2,Visibility.PUBLIC)));
        var thread=new CommunityItineraryQuestionThread();thread.setCommunityItinerary(itinerary(6,3,Visibility.PRIVATE));
        when(threads.findById(7L)).thenReturn(Optional.of(thread));
        mvc.perform(get("/api/community-itineraries/5/question-threads/7")).andExpect(status().isNotFound());
    }
    @Test void missingParentReturns404BeforeThreadLookup() throws Exception {
        mvc.perform(get("/api/community-itineraries/5/question-threads/7")).andExpect(status().isNotFound());verifyNoInteractions(threads);
    }
    @Test void crossUserDraftDeletionNeverTouchesAssets() throws Exception {
        mvc.perform(delete("/api/community-itineraries/draft/9").header("Authorization",bearer(1))).andExpect(status().isNotFound());
        verifyNoInteractions(cloudinary,uploads);verify(drafts,never()).delete(any());
    }
    @Test void ownerCanDeleteLegacyDraftWithoutDeletingLegacyImage() throws Exception {
        var d=draft();d.setCoverPhoto("https://legacy.invalid/cover.jpg");when(drafts.findByIdAndUser_Id(9,2)).thenReturn(Optional.of(d));
        mvc.perform(delete("/api/community-itineraries/draft/9").header("Authorization",bearer(2))).andExpect(status().isOk());
        verify(drafts).delete(d);verify(drafts).flush();verifyNoInteractions(cloudinary);
    }
    @Test void anonymousCoverUploadAndReplacementNeverReachProvider() throws Exception {
        var file=new MockMultipartFile("file","cover.png","image/png",new byte[]{1});
        for(String path:List.of("upload-draft-cover-photo","update-draft-cover-photo"))
            mvc.perform(multipart("/api/community-itineraries/draft/"+path).file(file).param("existingCoverUrl","legacy.jpg")).andExpect(status().isUnauthorized());
        verifyNoInteractions(cloudinary,uploads);
    }
    @Test void userCannotClaimForeignCoverThroughDraftCreation() throws Exception {
        var record=new DraftCoverUpload();record.setOwnerId(2L);record.setSecureUrl("https://synthetic.invalid/foreign.png");
        when(uploads.findBySecureUrlForUpdate(record.getSecureUrl())).thenReturn(Optional.of(record));
        mvc.perform(post("/api/community-itineraries/draft/save-draft").header("Authorization",bearer(1))
                .contentType("application/json").content("{\"title\":\"Attack\",\"country\":\"Synthetic\",\"region\":\"EUROPE\",\"days\":2,\"visibility\":\"PRIVATE\",\"coverPhoto\":\"https://synthetic.invalid/foreign.png\"}"))
                .andExpect(status().isNotFound());
        verify(drafts,never()).save(any());verifyNoInteractions(cloudinary);assertEquals(2L,record.getOwnerId());
    }
    @Test void userCannotClaimForeignCoverThroughOwnedDraftUpdate() throws Exception {
        var d=draft();d.setUser(user(1));when(drafts.existsById(9L)).thenReturn(true);
        when(drafts.findByIdAndUser_Id(9,1)).thenReturn(Optional.of(d));
        var record=new DraftCoverUpload();record.setOwnerId(2L);record.setSecureUrl("https://synthetic.invalid/foreign.png");
        when(uploads.findBySecureUrlForUpdate(record.getSecureUrl())).thenReturn(Optional.of(record));
        mvc.perform(put("/api/community-itineraries/draft/9").header("Authorization",bearer(1)).contentType("application/json")
                .content("{\"title\":\"Attack\",\"country\":\"Synthetic\",\"region\":\"EUROPE\",\"days\":2,\"visibility\":\"PRIVATE\",\"coverPhoto\":\"https://synthetic.invalid/foreign.png\"}"))
                .andExpect(status().isNotFound());verify(drafts,never()).save(any());assertNull(d.getCoverPhoto());
    }
    @Test void replacementCannotAuthorizeByForeignClientUrl() throws Exception {
        var record=new DraftCoverUpload();record.setOwnerId(2L);record.setSecureUrl("https://synthetic.invalid/foreign.png");when(uploads.findBySecureUrlForUpdate(record.getSecureUrl())).thenReturn(Optional.of(record));
        mvc.perform(multipart("/api/community-itineraries/draft/update-draft-cover-photo")
                .file(new MockMultipartFile("file","cover.png","image/png",new byte[]{1})).param("existingCoverUrl",record.getSecureUrl()).param("ownerId","2")
                .header("Authorization",bearer(1))).andExpect(status().isNotFound());verifyNoInteractions(cloudinary);
    }
    @Test void uploadIgnoresClientSuppliedOwnerId() throws Exception {
        when(cloudinary.uploadDraftCover(any())).thenReturn(new CloudinaryService.UploadedDraftCover("server-asset","community-draft-covers/server-id","https://synthetic.invalid/new.png"));
        mvc.perform(multipart("/api/community-itineraries/draft/upload-draft-cover-photo")
                .file(new MockMultipartFile("file","cover.png","image/png",new byte[]{1})).param("ownerId","2").header("Authorization",bearer(1)))
                .andExpect(status().isOk()).andExpect(content().string("https://synthetic.invalid/new.png"));
        var cap=org.mockito.ArgumentCaptor.forClass(DraftCoverUpload.class);verify(uploads).saveAndFlush(cap.capture());assertEquals(1L,cap.getValue().getOwnerId());
    }
    @Test void threadMutationResponsesCannotBypassPrivateParentReadRestriction() throws Exception {
        var parent=itinerary(5,2,Visibility.PRIVATE);when(itineraries.findById(5L)).thenReturn(Optional.of(parent));
        var thread=new CommunityItineraryQuestionThread();thread.setId(7L);thread.setAsker(user(1));thread.setCommunityItinerary(parent);when(threads.findById(7L)).thenReturn(Optional.of(thread));
        var service=new CommunityItineraryQuestionService(threads,itineraries);
        assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.createThread(user(1),new com.shortbreakshub.dto.CreateQuestionThreadRequest(5L,"Question"))).getStatusCode().value());
        assertEquals(404,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.addMessageToThread(user(1),new com.shortbreakshub.dto.AddMessageRequest(5L,7L,"Reply"))).getStatusCode().value());
        verify(threads,never()).save(any());
    }

    @Test void publicQuestionCreationAndCreatorReplyRemainAllowed() {
        var parent=itinerary(5,2,Visibility.PUBLIC);when(itineraries.findById(5L)).thenReturn(Optional.of(parent));
        when(threads.save(any())).thenAnswer(invocation->{CommunityItineraryQuestionThread thread=invocation.getArgument(0);thread.setId(7L);return thread;});
        var service=new CommunityItineraryQuestionService(threads,itineraries);
        var created=service.createThread(user(1),new com.shortbreakshub.dto.CreateQuestionThreadRequest(5L,"Question"));assertEquals(1,created.messages().size());
        var capture=org.mockito.ArgumentCaptor.forClass(CommunityItineraryQuestionThread.class);verify(threads).save(capture.capture());
        when(threads.findById(7L)).thenReturn(Optional.of(capture.getValue()));
        var replied=service.addMessageToThread(user(2),new com.shortbreakshub.dto.AddMessageRequest(5L,7L,"Reply"));assertEquals(2,replied.messages().size());
    }
    @Test void unrelatedUserCannotWritePublicThread() {
        var parent=itinerary(5,2,Visibility.PUBLIC);when(itineraries.findById(5L)).thenReturn(Optional.of(parent));
        var thread=new CommunityItineraryQuestionThread();thread.setId(7L);thread.setAsker(user(1));thread.setCommunityItinerary(parent);when(threads.findById(7L)).thenReturn(Optional.of(thread));
        var service=new CommunityItineraryQuestionService(threads,itineraries);
        assertEquals(403,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.addMessageToThread(user(3),new com.shortbreakshub.dto.AddMessageRequest(5L,7L,"Reply"))).getStatusCode().value());verify(threads,never()).save(any());
    }

    @Test void draftDeletionDatabaseFailureCannotTouchProvider() throws Exception {
        var d=draft();d.setCoverPhoto("https://res.cloudinary.com/synthetic/image/upload/community-draft-covers/server-id.png");
        when(drafts.findByIdAndUser_Id(9,2)).thenReturn(Optional.of(d));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("synthetic failure")).when(drafts).flush();
        mvc.perform(delete("/api/community-itineraries/draft/9").header("Authorization",bearer(2))).andExpect(status().isBadRequest());
        verifyNoInteractions(cloudinary,uploads);
    }
    @Test void publishingAndAvatarCannotAssignForeignDeliveryVariant() throws Exception {
        String url="https://res.cloudinary.com/synthetic/image/upload/v1/community-draft-covers/server-id.png";
        var record=new DraftCoverUpload();record.setOwnerId(2L);record.setPublicId("community-draft-covers/server-id");record.setAssetId("provider-asset");record.setSecureUrl(url);
        when(uploads.findByPublicIdForUpdate(record.getPublicId())).thenReturn(Optional.of(record));
        var publishing=new UserItineraryService(users,itineraries,covers);
        var avatar=new UserService(users,covers,mock(EmailService.class),mock(EmailVerificationService.class));
        var caller=user(1);when(users.findById(1L)).thenReturn(Optional.of(caller));
        for(String variant:List.of(url.replace(".png",".jpg"),url.replace("/v1/","/c_fill,w_200/v1/"))) {
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->publishing.publishOrUpdate(1L,"trip","Synthetic",Region.EUROPE,2,"Trip","Summary",variant,null,Visibility.PUBLIC,1f,List.of()));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->avatar.updateOwnAvatarById(1L,new com.shortbreakshub.dto.UpdateAvatarReq(variant)));
        }
        verify(itineraries,never()).save(any());verify(users,never()).save(any());verifyNoInteractions(cloudinary);
    }
    @Test void ownerCanPublishAndAssignAvatarUsingVerifiedVariant() throws Exception {
        String url="https://res.cloudinary.com/synthetic/image/upload/v1/community-draft-covers/server-id.png";
        var record=new DraftCoverUpload();record.setOwnerId(1L);record.setPublicId("community-draft-covers/server-id");record.setAssetId("provider-asset");record.setSecureUrl(url);
        when(uploads.findByPublicIdForUpdate(record.getPublicId())).thenReturn(Optional.of(record));when(cloudinary.matchesDraftCoverAsset(record.getPublicId(),record.getAssetId())).thenReturn(true);
        var caller=user(1);when(users.findById(1L)).thenReturn(Optional.of(caller));
        String variant=url.replace("/v1/","/c_fill,w_200/v1/").replace(".png",".jpg");
        new UserItineraryService(users,itineraries,covers).publishOrUpdate(1L,"trip","Synthetic",Region.EUROPE,2,"Trip","Summary",variant,null,Visibility.PUBLIC,1f,List.of());
        new UserService(users,covers,mock(EmailService.class),mock(EmailVerificationService.class)).updateOwnAvatarById(1L,new com.shortbreakshub.dto.UpdateAvatarReq(variant));
        verify(itineraries).save(any());verify(users).save(caller);assertEquals(variant,caller.getAvatarUrl());verify(cloudinary,times(2)).matchesDraftCoverAsset(record.getPublicId(),record.getAssetId());verifyNoMoreInteractions(cloudinary);
    }
    @Test void legacyAvatarMayStayUnchangedButUnknownNewUrlIsRejected() throws Exception {
        var caller=user(1);String legacy=caller.getAvatarUrl();when(users.findById(1L)).thenReturn(Optional.of(caller));
        var service=new UserService(users,covers,mock(EmailService.class),mock(EmailVerificationService.class));
        service.updateOwnAvatarById(1L,new com.shortbreakshub.dto.UpdateAvatarReq(legacy));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateOwnAvatarById(1L,new com.shortbreakshub.dto.UpdateAvatarReq("https://legacy.invalid/new.jpg")));
        assertEquals(legacy,caller.getAvatarUrl());verifyNoInteractions(cloudinary);
    }

    @Test void allImageUploadEndpointsRecordAuthenticatedOwner() throws Exception {
        when(cloudinary.uploadDraftCover(any())).thenReturn(new CloudinaryService.UploadedDraftCover("provider-asset","community-draft-covers/server-id","https://res.cloudinary.com/synthetic/image/upload/community-draft-covers/server-id.png"));
        for(String path:List.of("/api/auth/me/photo","/api/community-itineraries/upload-itinerary-cover-photo","/api/community-itineraries/draft/upload-draft-cover-photo")) {
            mvc.perform(multipart(path).file(new MockMultipartFile("file","cover.png","image/png",new byte[]{1})).param("ownerId","2").header("Authorization",bearer(1))).andExpect(status().isOk());
        }
        var cap=org.mockito.ArgumentCaptor.forClass(DraftCoverUpload.class);verify(uploads,times(3)).saveAndFlush(cap.capture());for(var record:cap.getAllValues())assertEquals(1L,record.getOwnerId());
        verify(cloudinary,times(3)).uploadDraftCover(any());verifyNoMoreInteractions(cloudinary);
    }
    @Test void ownedLegacyDraftUpdateMayRetainExistingReference() {
        var d=draft();d.setUser(user(1));d.setCoverPhoto("https://legacy.invalid/old.jpg");when(drafts.findByIdAndUser_Id(9,1)).thenReturn(Optional.of(d));
        draftService.updateDraft(9L,1L,"private","Synthetic","EUROPE",2,"Private","Summary",d.getCoverPhoto(),null,Visibility.PRIVATE,1f,List.of());verify(drafts).save(d);verifyNoInteractions(cloudinary);
    }

}
