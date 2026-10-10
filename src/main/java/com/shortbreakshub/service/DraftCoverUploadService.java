package com.shortbreakshub.service;

import com.shortbreakshub.model.DraftCoverUpload;
import com.shortbreakshub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DraftCoverUploadService {
    private final DraftCoverUploadRepository uploads;
    private final CommunityItineraryDraftRepository drafts;
    private final CloudinaryService cloudinary;
    private static final Pattern DELIVERY = Pattern.compile(
            "^/([^/]+)/image/upload/(?:[^/]+/)*(community-draft-covers/[A-Za-z0-9_-]+)(?:\\.[A-Za-z0-9]+)?$");

    @Transactional(rollbackFor = IOException.class)
    public String upload(Long ownerId, MultipartFile file) throws IOException {
        requireIdentity(ownerId);
        var asset = cloudinary.uploadDraftCover(file);
        var record = new DraftCoverUpload();
        record.setOwnerId(ownerId);
        record.setAssetId(asset.assetId());
        record.setPublicId(asset.publicId());
        record.setSecureUrl(asset.secureUrl());
        uploads.saveAndFlush(record);
        return record.getSecureUrl();
    }

    @Transactional(rollbackFor = IOException.class)
    public String replace(Long ownerId, String previousUrl, MultipartFile file) throws IOException {
        requireIdentity(ownerId);
        var record = resolve(previousUrl);
        if (record != null) {
            requireOwnedAsset(ownerId, record);
        } else if (managedDeliveryHint(previousUrl) || previousUrl == null || previousUrl.isBlank()
                || !drafts.existsByUser_IdAndCoverPhoto(ownerId, previousUrl)) {
            unavailable();
        }
        // The legacy check permits replacing an existing owned reference, not claiming its asset.
        // Never destroy the previous image, even if upload/persistence/commit subsequently fails.
        return upload(ownerId, file);
    }

    @Transactional
    public void validateReference(Long ownerId, String url) {
        validateAssignment(ownerId, url, null);
    }

    @Transactional
    public void validateAssignment(Long ownerId, String url, String existingUrl) {
        requireIdentity(ownerId);
        if (url == null || url.isBlank()) return;
        var record = resolve(url);
        if (record != null) {
            requireOwnedAsset(ownerId, record);
        } else if (managedDeliveryHint(url) || !url.equals(existingUrl)) {
            unavailable();
        }
        // Only an unchanged legacy reference may be retained; no ownership record is created.
    }

    private DraftCoverUpload resolve(String url) {
        if (url == null || url.isBlank()) return null;
        var exact = uploads.findBySecureUrlForUpdate(url);
        if (exact.isPresent()) return exact.get();
        var candidate = delivery(url);
        if (candidate == null) return null;
        var record = uploads.findByPublicIdForUpdate(candidate.publicId()).orElse(null);
        if (record == null) return null;
        var canonical = delivery(record.getSecureUrl());
        if (canonical == null || !canonical.cloud().equals(candidate.cloud())) unavailable();
        return record;
    }

    private void requireOwnedAsset(Long ownerId, DraftCoverUpload record) {
        if (!ownerId.equals(record.getOwnerId()) || record.getDeletedAt() != null
                || !cloudinary.matchesDraftCoverAsset(record.getPublicId(), record.getAssetId())) unavailable();
    }

    private record Delivery(String cloud, String publicId) { }
    private Delivery delivery(String url) {
        if (url == null) return null;
        try {
            var uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"res.cloudinary.com".equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getRawPath().contains("%")) return null;
            var match = DELIVERY.matcher(uri.getRawPath());
            if (!match.matches()) return null;
            String prefix = uri.getRawPath().substring(("/" + match.group(1) + "/image/upload/").length(),
                    uri.getRawPath().lastIndexOf("community-draft-covers/"));
            for (String part : prefix.split("/")) {
                if (!part.isEmpty() && !(part.matches("v[0-9]+") || part.startsWith("s--") || part.contains("_"))) return null;
                if (part.equals(".") || part.equals("..")) return null;
            }
            return new Delivery(match.group(1), match.group(2));
        } catch (IllegalArgumentException failure) { return null; }
    }
    private boolean managedDeliveryHint(String url) {
        if (url == null) return false;
        String decoded = url;
        try {
            for (int i = 0; i < 3; i++) {
                if (decoded.toLowerCase(java.util.Locale.ROOT).contains("community-draft-covers")) return true;
                decoded = java.net.URLDecoder.decode(decoded, java.nio.charset.StandardCharsets.UTF_8);
            }
            var uri = URI.create(url);
            return "res.cloudinary.com".equals(uri.getHost()) && uri.getRawPath().contains("%");
        } catch (IllegalArgumentException failure) { return true; }
    }
    private void unavailable() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image unavailable"); }
    private void requireIdentity(Long ownerId) {
        if (ownerId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
