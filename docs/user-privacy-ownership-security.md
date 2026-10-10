# User privacy and image ownership

## P0 authorization boundaries

Community favorites return explicit DTOs. The nested author contains only id,
displayName and avatarUrl; no account password hash, email, role or preferences.
Creation and retrieval allow PUBLIC itineraries or their authenticated owner.
Visibility is reevaluated on every request and included in pagination counts.
Draft reads, updates and deletion query by both ID and authenticated user ID; absent
credentials return 401, foreign/missing resources return 404. Question reads validate
parent visibility and parent/thread association. Mutation responses apply the same
parent visibility boundary to avoid leaking thread contents. PUBLIC access remains.

## Trusted image identity and assignment paths

Draft, community publishing and avatar upload endpoints share the existing V10
upload record. Cloudinary assigns asset_id; the server assigns a fresh UUID under
community-draft-covers/ with overwrite disabled. Record ownerId is taken from
validated server request context, never request parameters. Public ID, immutable
asset ID and secure URL come from the authenticated provider upload response.
V10 is unchanged; there are no additional tables/migrations or ownership backfills.

Canonical URLs resolve through their trusted upload record. Supported alternate
formats, versioned and transformation URLs on HTTPS res.cloudinary.com resolve
the full managed public ID and must match the recorded cloud. Ownership and any
existing tombstone are checked, then the authenticated provider resource API
must confirm the immutable asset_id, public_id and image resource type. Provider
lookup errors, missing/unknown IDs, mismatches and foreign owners fail closed.
Encoded, ambiguous, custom-host and unsupported managed URL forms are rejected
as new assignments rather than assumed legacy. Transformations are supported
only within the recognized delivery URL shape; no arbitrary URL fetch is used.

Draft creation/update, publishing and avatar assignment all enforce this boundary.
Photo upload endpoints retain their file parameter and string URL responses.
Replacement retains file/existingCoverUrl and returns the newly recorded URL.

## Legacy behavior

Existing URLs/data are not rewritten. An unchanged legacy URL can remain on its
existing owned draft or avatar; this preserves a reference, not asset ownership.
New assignments of unknown legacy assets are rejected. Legacy draft replacement
requires an exact existing reference on an owned persisted draft and uploads a
new verified asset. A managed-looking but unresolved URL is never treated as
legacy, including encoded variants. Unknown pending uploads without a trusted
record or existing owned legacy reference must be uploaded again. Publishing a
legacy draft requires a fresh verified upload; a legacy URL is not ownership proof.

## Physical deletion is intentionally deferred

No draft replacement, draft deletion or avatar replacement calls Cloudinary
destroy. The destructive helper/retirement path has been removed. Draft deletion
removes only the database draft and preserves both images and upload records.
Provider upload failures, persistence failures, commit failures and retries cannot
delete a previous image. This also avoids destructive reference-check races and
URL-alias omissions. There is no garbage collection, outbox, asynchronous deletion
or guessed ownership migration. References do not grant ownership or deletion rights.

Uploads and database commits are not atomic. Abandoned uploads, failed commits
and retries may accumulate orphaned/duplicate uploads. Retention is deliberate;
cleanup needs a separately approved asset-identity/reference design. Provider
identity verification adds a read dependency: failures can prevent assignment or
replacement, while existing resource reads and draft deletion remain available.
Other account audit findings (JWT/session, passwords and throttling) remain outside scope.

## Migration and rollback

V10 adds draft_cover_uploads with users FK, unique asset ID/public ID/secure URL,
owner index, timestamps and retained tombstone field. Existing records remain
readable and are not backfilled. Tests migrate V9 with legacy records to V10.
Before deployment, confirm production schema/history and back up the database.
Export/preserve ownership records before rollback. Revert dependent code before
dropping the new table and reconcile Flyway history through the approved release
process. Dropping records never deletes images, but loses ownership evidence.
Old application code reintroduces security vulnerabilities; rollback is not a
security remediation. The owner reports that a previous local application run already applied V10 to AWS
RDS. This completion does not connect to RDS or rerun that migration. The owner
must verify the existing Flyway V10 checksum/schema against this unchanged file
before release; do not edit an already applied migration or blindly repair history.
No production migration or deployment was performed in this completion.

## Validation and exact changed-file inventory

Focused security/migration and dependent regression run: 130 tests passed.
Full backend suite: 192 tests passed, zero failures/errors/skips. Migration coverage includes the new V9-to-V10
legacy-preservation test and the two historical migration tests. Provider calls
are mocked and persistence uses embedded PostgreSQL only. No production calls,
frontend changes or deployment were performed. Delivery commits these P0 files
on a dedicated feature branch and targets master for review.

## Completion review and coverage

Requirements were recovered from the five original P0 findings and the approved
asset-retention remediation, then checked against implementation and tests. A
repository GitHub issue search for security returned no matching issues; the
existing requirements were sufficient without inventing additional features.

| Boundary | Protection and regression coverage |
| --- | --- |
| Favorites | Explicit safe author DTO; PUBLIC-or-owner creation, reads and counts; later PRIVATE visibility hides both rows and pagination counts. |
| Drafts | Persisted owner scopes reads, updates and deletion; foreign and missing IDs both return 404; server identity determines creation and list/count queries. |
| Covers | Server-recorded immutable asset identity; canonical/format/transformation variants share ownership checks; foreign, unknown and provider lookup failures fail closed. |
| Legacy/retries | Only unchanged owned references remain usable; safe legacy replacement; upload/DB failures and retries never destroy previous images. |
| Questions | Parent visibility and parent/thread association checked; writes limited to asker/creator and reject a later PRIVATE parent for a foreign asker. |
| Profiles | Own profile update uses JWT filter identity; injected user/role/avatar fields cannot redirect profile writes; dedicated avatar assignment validates asset ownership. |
| User lists | Own community itineraries, favorites, drafts and built-in favorites use authenticated owner identity; PUBLIC slug access preserved, PRIVATE slug reads excluded. |
| Comments | Only built-in itinerary comments are implemented; reads are intentionally public safe DTOs, writes/deletion scope by authenticated user and itinerary. No community comment controller exists despite security matcher placeholders. |
| Authentication | Actual Spring Security chain tested for missing/invalid tokens on 22 protected routes plus all four image upload/replacement routes; invalid bearer on GET /auth/me returns 401 rather than 500. |
| V10 | Embedded PostgreSQL migration from V9 preserves legacy rows, adds no inferred ownership, enforces owner FK and uniqueness, and tests table removal without legacy data loss. |

The completion removed draft-update existence prechecks to avoid revealing
whether a foreign ID exists, and added the missing validated-identity guard for
GET /api/auth/me. It does not redesign JWT/session behavior or address the other
account audit findings concerning registration, passwords and throttling.

Remaining limitations: provider uploads and DB commits are not atomic; retries
can accumulate retained orphan images and costs. Provider identity lookups can
fail and block new assignments. Unsupported delivery forms and new unknown
legacy assignments require a fresh upload. No physical image cleanup is provided.
Production schema/checksum and provider configuration remain owner release checks.

The 29 changed/new files relative to master on this branch are:

- `docs/user-privacy-ownership-security.md`
- `src/main/java/com/shortbreakshub/controller/CommunityItineraryController.java`
- `src/main/java/com/shortbreakshub/controller/CommunityItineraryDraftController.java`
- `src/main/java/com/shortbreakshub/controller/CommunityItineraryFavoriteController.java`
- `src/main/java/com/shortbreakshub/controller/CommunityItineraryQuestionController.java`
- `src/main/java/com/shortbreakshub/controller/UserController.java`
- `src/main/java/com/shortbreakshub/dto/CommunityFavoriteRes.java`
- `src/main/java/com/shortbreakshub/model/DraftCoverUpload.java`
- `src/main/java/com/shortbreakshub/repository/CommunityItineraryDraftRepository.java`
- `src/main/java/com/shortbreakshub/repository/CommunityItineraryFavoriteRepository.java`
- `src/main/java/com/shortbreakshub/repository/DraftCoverUploadRepository.java`
- `src/main/java/com/shortbreakshub/service/AuthService.java`
- `src/main/java/com/shortbreakshub/service/CloudinaryService.java`
- `src/main/java/com/shortbreakshub/service/CommunityItineraryDraftService.java`
- `src/main/java/com/shortbreakshub/service/CommunityItineraryFavoriteService.java`
- `src/main/java/com/shortbreakshub/service/CommunityItineraryQuestionService.java`
- `src/main/java/com/shortbreakshub/service/DraftCoverUploadService.java`
- `src/main/java/com/shortbreakshub/service/UserItineraryService.java`
- `src/main/java/com/shortbreakshub/service/UserService.java`
- `src/main/resources/db/migration/V10__add_draft_cover_upload_ownership.sql`
- `src/test/java/com/shortbreakshub/seeder/DestinationCommandIntegrationTest.java`
- `src/test/java/com/shortbreakshub/seeder/DestinationMigrationTest.java`
- `src/test/java/com/shortbreakshub/service/CloudinaryDraftCoverTest.java`
- `src/test/java/com/shortbreakshub/service/CommunityRegionContractTest.java`
- `src/test/java/com/shortbreakshub/service/DraftCoverOwnershipMigrationTest.java`
- `src/test/java/com/shortbreakshub/service/DraftCoverUploadServiceTest.java`
- `src/test/java/com/shortbreakshub/service/UserItineraryVisibilityTest.java`
- `src/test/java/com/shortbreakshub/service/UserPrivacyOwnershipIntegrationTest.java`
- `src/test/java/com/shortbreakshub/service/UserPrivacyOwnershipTest.java`
