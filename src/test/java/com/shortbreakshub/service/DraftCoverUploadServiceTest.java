package com.shortbreakshub.service;

import com.shortbreakshub.model.*;
import com.shortbreakshub.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftCoverUploadServiceTest {
    DraftCoverUploadRepository uploads;
    CommunityItineraryDraftRepository drafts;
    CloudinaryService cloud;
    DraftCoverUploadService service;
    String url="https://res.cloudinary.com/synthetic/image/upload/v1/community-draft-covers/server-id.png";
    MockMultipartFile file=new MockMultipartFile("file","cover.png","image/png",new byte[]{1});
    @BeforeEach void setup(){
        uploads=mock(DraftCoverUploadRepository.class);drafts=mock(CommunityItineraryDraftRepository.class);cloud=mock(CloudinaryService.class);
        service=new DraftCoverUploadService(uploads,drafts,cloud);
    }
    DraftCoverUpload record(long owner){var r=new DraftCoverUpload();r.setOwnerId(owner);r.setAssetId("provider-asset");r.setPublicId("community-draft-covers/server-id");r.setSecureUrl(url);return r;}
    void managed(long owner){when(uploads.findBySecureUrlForUpdate(url)).thenReturn(Optional.of(record(owner)));when(uploads.findByPublicIdForUpdate("community-draft-covers/server-id")).thenReturn(Optional.of(record(owner)));when(cloud.matchesDraftCoverAsset("community-draft-covers/server-id","provider-asset")).thenReturn(true);}
    void uploadResponse()throws IOException{when(cloud.uploadDraftCover(file)).thenReturn(new CloudinaryService.UploadedDraftCover("new-asset","community-draft-covers/new-id","https://synthetic.invalid/new.png"));}
    @Test void uploadPersistsProviderIdentifiersAndAuthenticatedOwner()throws Exception{
        uploadResponse();assertEquals("https://synthetic.invalid/new.png",service.upload(1L,file));
        var capture=org.mockito.ArgumentCaptor.forClass(DraftCoverUpload.class);verify(uploads).saveAndFlush(capture.capture());
        assertEquals(1L,capture.getValue().getOwnerId());assertEquals("new-asset",capture.getValue().getAssetId());assertEquals("community-draft-covers/new-id",capture.getValue().getPublicId());
    }
    @Test void unauthenticatedUploadRejected(){assertEquals(401,assertThrows(ResponseStatusException.class,()->service.upload(null,file)).getStatusCode().value());verifyNoInteractions(cloud,uploads);}
    @ParameterizedTest @ValueSource(strings={
        "https://res.cloudinary.com/synthetic/image/upload/v1/community-draft-covers/server-id.png",
        "https://res.cloudinary.com/synthetic/image/upload/v1/community-draft-covers/server-id.jpg",
        "https://res.cloudinary.com/synthetic/image/upload/c_fill,w_200/f_auto/v1/community-draft-covers/server-id.webp",
        "https://res.cloudinary.com/synthetic/image/upload/community-draft-covers/server-id.png?download=1"})
    void deliveryVariantsUseVerifiedIdentity(String variant){managed(1);service.validateReference(1L,variant);verify(cloud).matchesDraftCoverAsset("community-draft-covers/server-id","provider-asset");verify(uploads,never()).save(any());}
    @ParameterizedTest @ValueSource(strings={
        "https://res.cloudinary.com/synthetic/image/upload/v1/community-draft-covers/server-id.png",
        "https://res.cloudinary.com/synthetic/image/upload/v1/community-draft-covers/server-id.jpg",
        "https://res.cloudinary.com/synthetic/image/upload/c_fill,w_200/v1/community-draft-covers/server-id.webp"})
    void foreignVariantsCannotClaimOrReplace(String variant)throws Exception{managed(2);assertEquals(404,assertThrows(ResponseStatusException.class,()->service.validateReference(1L,variant)).getStatusCode().value());assertThrows(ResponseStatusException.class,()->service.replace(1L,variant,file));verify(cloud,never()).uploadDraftCover(any());verify(uploads,never()).saveAndFlush(any());}
    @Test void wrongCloudCannotBorrowOwnedPublicId(){managed(1);assertThrows(ResponseStatusException.class,()->service.validateReference(1L,url.replace("/synthetic/","/foreign/")));}
    @Test void providerIdentityFailureIsClosed(){managed(1);when(cloud.matchesDraftCoverAsset(anyString(),anyString())).thenReturn(false);assertThrows(ResponseStatusException.class,()->service.validateReference(1L,url));}
    @Test void unknownManagedIdentityIsNeverLegacy(){when(drafts.existsByUser_IdAndCoverPhoto(1L,url)).thenReturn(true);assertThrows(ResponseStatusException.class,()->service.replace(1L,url,file));assertThrows(ResponseStatusException.class,()->service.validateAssignment(1L,url,url));verifyNoInteractions(cloud);}
    @Test void encodedOrUnrecognizedDeliveryCannotBeNewClaim(){for(String candidate:new String[]{url.replace("server-id","server%2Did"),url.replace("res.cloudinary.com","untrusted.invalid"),"https://legacy.invalid/new.png"})assertThrows(ResponseStatusException.class,()->service.validateReference(1L,candidate));}
    @Test void unchangedLegacyReferenceIsPreservedWithoutOwnership(){String legacy="https://legacy.invalid/old.jpg";service.validateAssignment(1L,legacy,legacy);verifyNoInteractions(cloud);verify(uploads,never()).save(any());}
    @Test void legacyReplacementRequiresExistingOwnedDraftReference()throws Exception{uploadResponse();String legacy="https://legacy.invalid/old.jpg";assertThrows(ResponseStatusException.class,()->service.replace(1L,legacy,file));when(drafts.existsByUser_IdAndCoverPhoto(1L,legacy)).thenReturn(true);assertEquals("https://synthetic.invalid/new.png",service.replace(1L,legacy,file));verify(cloud).uploadDraftCover(file);verifyNoMoreInteractions(cloud);}
    @Test void replacementNeverDestroysPriorAsset()throws Exception{managed(1);uploadResponse();service.replace(1L,url,file);verify(cloud).matchesDraftCoverAsset(anyString(),anyString());verify(cloud).uploadDraftCover(file);verifyNoMoreInteractions(cloud);verify(uploads,never()).save(any());}
    @Test void uploadFailurePreservesPreviousImage()throws Exception{managed(1);when(cloud.uploadDraftCover(file)).thenThrow(new IOException("synthetic failure"));assertThrows(IOException.class,()->service.replace(1L,url,file));verify(cloud).matchesDraftCoverAsset(anyString(),anyString());verify(cloud).uploadDraftCover(file);verifyNoMoreInteractions(cloud);verify(uploads,never()).saveAndFlush(any());}
    @Test void replacementDatabaseFailureHasNoDestructiveProviderOperation()throws Exception{managed(1);uploadResponse();when(uploads.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("synthetic failure"));assertThrows(DataIntegrityViolationException.class,()->service.replace(1L,url,file));verify(cloud).matchesDraftCoverAsset(anyString(),anyString());verify(cloud).uploadDraftCover(file);verifyNoMoreInteractions(cloud);}
    @Test void retryMayCreateOrphansButNeverDestroys()throws Exception{managed(1);uploadResponse();service.replace(1L,url,file);service.replace(1L,url,file);verify(cloud,times(2)).matchesDraftCoverAsset(anyString(),anyString());verify(cloud,times(2)).uploadDraftCover(file);verifyNoMoreInteractions(cloud);verify(uploads,times(2)).saveAndFlush(any());}
    @Test void tombstoneStillRejectsOwnership(){var r=record(1);r.setDeletedAt(Instant.now());when(uploads.findBySecureUrlForUpdate(url)).thenReturn(Optional.of(r));assertThrows(ResponseStatusException.class,()->service.validateReference(1L,url));verifyNoInteractions(cloud);}
    @Test void encodedManagedVariantIsNotPreservedAsLegacy(){String encoded=url.replace("community-draft-covers","%63ommunity-draft-covers");when(drafts.existsByUser_IdAndCoverPhoto(1L,encoded)).thenReturn(true);assertThrows(ResponseStatusException.class,()->service.validateAssignment(1L,encoded,encoded));assertThrows(ResponseStatusException.class,()->service.replace(1L,encoded,file));verifyNoInteractions(cloud);}
    @Test void malformedManagedPathsAreClosed(){managed(1);for(String candidate: new String[]{url.replace("/v1/","/../v1/"),url.replace("/v1/","/unrecognized/v1/"),url.replace("/image/upload/","/images/")})assertThrows(ResponseStatusException.class,()->service.validateReference(1L,candidate));}

}
