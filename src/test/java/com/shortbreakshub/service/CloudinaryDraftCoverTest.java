package com.shortbreakshub.service;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudinaryDraftCoverTest {
    @Test void draftUploadUsesServerIdentifierAndProviderResponse()throws Exception{
        var cloud=mock(Cloudinary.class,RETURNS_DEEP_STUBS);
        when(cloud.uploader().upload(any(byte[].class),anyMap())).thenAnswer(invocation->{
            Map options=invocation.getArgument(1);assertEquals(false,options.get("overwrite"));assertEquals("image",options.get("resource_type"));
            assertTrue(options.get("public_id").toString().matches("community-draft-covers/[a-f0-9-]{36}"));
            return Map.of("asset_id","provider-asset","public_id",options.get("public_id"),"secure_url","https://synthetic.invalid/cover.png");
        });
        var result=new CloudinaryService(cloud).uploadDraftCover(new MockMultipartFile("file",new byte[]{1}));
        assertEquals("provider-asset",result.assetId());assertEquals("https://synthetic.invalid/cover.png",result.secureUrl());
    }
    @Test void malformedProviderResultCannotCreateOwnershipRecord()throws Exception{
        var cloud=mock(Cloudinary.class,RETURNS_DEEP_STUBS);when(cloud.uploader().upload(any(byte[].class),anyMap())).thenReturn(Map.of("secure_url","https://synthetic.invalid/cover.png"));
        assertThrows(IOException.class,()->new CloudinaryService(cloud).uploadDraftCover(new MockMultipartFile("file",new byte[]{1})));
    }
    @Test void immutableAssetIdentityMustMatchProvider()throws Exception{
        var cloud=mock(Cloudinary.class,RETURNS_DEEP_STUBS);var resource=mock(com.cloudinary.api.ApiResponse.class);when(resource.get("asset_id")).thenReturn("provider-asset");when(resource.get("public_id")).thenReturn("community-draft-covers/server-id");when(resource.get("resource_type")).thenReturn("image");when(cloud.api().resource(eq("community-draft-covers/server-id"),anyMap())).thenReturn(resource);
        var service=new CloudinaryService(cloud);assertTrue(service.matchesDraftCoverAsset("community-draft-covers/server-id","provider-asset"));assertFalse(service.matchesDraftCoverAsset("community-draft-covers/server-id","different-asset"));
        verify(cloud.uploader(),never()).destroy(anyString(),anyMap());
    }
    @Test void providerIdentityLookupFailureIsClosed()throws Exception{
        var cloud=mock(Cloudinary.class,RETURNS_DEEP_STUBS);when(cloud.api().resource(anyString(),anyMap())).thenThrow(new IOException("synthetic failure"));assertFalse(new CloudinaryService(cloud).matchesDraftCoverAsset("community-draft-covers/server-id","asset"));
    }
    @Test void ordinaryUploadsKeepExistingProviderContract()throws Exception{
        var cloud=mock(Cloudinary.class,RETURNS_DEEP_STUBS);when(cloud.uploader().upload(any(byte[].class),eq(Map.of()))).thenReturn(Map.of("secure_url","https://synthetic.invalid/avatar.png"));
        assertEquals("https://synthetic.invalid/avatar.png",new CloudinaryService(cloud).uploadImage(new MockMultipartFile("file",new byte[]{1})));
    }
}
