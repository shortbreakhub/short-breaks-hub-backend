package com.shortbreakshub.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryService {
    private final Cloudinary cloudinary;
    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadImage(MultipartFile file) throws IOException {
        return cloudinary
               .uploader()
               .upload(file.getBytes(), ObjectUtils.emptyMap())
               .get("secure_url")
               .toString();
    }

    public record UploadedDraftCover(String assetId, String publicId, String secureUrl) { }

    public UploadedDraftCover uploadDraftCover(MultipartFile file) throws IOException {
        String publicId = "community-draft-covers/" + UUID.randomUUID();
        Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "public_id", publicId, "overwrite", false, "resource_type", "image"));
        Object assetId = result.get("asset_id"), returnedId = result.get("public_id"), url = result.get("secure_url");
        if (!(assetId instanceof String asset) || asset.isBlank()
                || !publicId.equals(returnedId) || !(url instanceof String secureUrl) || !secureUrl.startsWith("https://")) {
            throw new IOException("Invalid draft cover upload response");
        }
        return new UploadedDraftCover(asset, publicId, secureUrl);
    }

    /** Resolve the immutable provider asset identity; failures never grant access. */
    public boolean matchesDraftCoverAsset(String publicId, String assetId) {
        try {
            Map<?, ?> resource = cloudinary.api().resource(publicId, ObjectUtils.asMap("resource_type", "image"));
            return assetId != null && assetId.equals(resource.get("asset_id"))
                    && publicId.equals(resource.get("public_id")) && "image".equals(resource.get("resource_type"));
        } catch (Exception failure) { return false; }
    }

}
