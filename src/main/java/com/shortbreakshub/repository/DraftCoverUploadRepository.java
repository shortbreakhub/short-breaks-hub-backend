package com.shortbreakshub.repository;

import com.shortbreakshub.model.DraftCoverUpload;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface DraftCoverUploadRepository extends JpaRepository<DraftCoverUpload, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from DraftCoverUpload u where u.secureUrl = :url")
    Optional<DraftCoverUpload> findBySecureUrlForUpdate(@Param("url") String url);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from DraftCoverUpload u where u.publicId = :publicId")
    Optional<DraftCoverUpload> findByPublicIdForUpdate(@Param("publicId") String publicId);
}
