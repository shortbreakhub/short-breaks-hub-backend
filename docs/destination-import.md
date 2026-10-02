# Destination import

`seed/destinations.json` is the versioned runtime catalog: 201 explicit internal
Destination keys, 201 exact itinerary slug assignments, 190 Trip.com mappings,
and 11 researched skips. Keys are checked in and never regenerated at runtime.
`seed/trip-com-destination-mapping.json` remains research provenance, including
provider display names and accommodation-base notes. It is not read by the importer.

## Deployment and execution

Deploy/apply Flyway V9 first through the normal schema deployment process.
The import command **cannot run Flyway** and Hibernate requires the schema to
already exist. Assigned Official Itineraries must already be present. The
command does not run either development seeder or create itinerary content.

Build the application, then invoke its dedicated entry point, using the same
reviewed datasource environment/configuration as the target deployment:

```sh
java -Dloader.main=com.shortbreakshub.command.DestinationImportCommand \
  -cp target/shortbreakshub-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --destination-import.mode=dry-run
```

Review the JSON report, then run the same command with
`--destination-import.mode=apply`. An optional
`--destination-import.catalog=file:/absolute/path/destinations.json` selects a
reviewed later catalog. Do not put database credentials in command arguments.

The entry point explicitly uses non-web mode and `prod,destination-import`
profiles and imports only persistence/import components. Normal production
startup does not activate the runner. **Do not enable `destination-import` on
the normal web application.** A missing/invalid mode fails instead of applying.
Both legacy development seeders exclude `destination-import` independently of
`prod`, so import-profile activation cannot enable them.
Failure exits nonzero; successful apply prints its report after commit.

## Validation and updates

Parsing rejects unknown fields/enums, duplicate JSON fields, scalar coercions,
duplicate keys/labels/assignments/scopes, invalid decisions, and incorrect
declared counts. Every assigned itinerary is resolved by exact slug. Missing
slugs or competing existing links fail. Unlisted database itineraries are
reported and preserved. No Country/city or fuzzy matching occurs at runtime.

Dry-run performs validation and reports proposed counts without writes. Apply
revalidates against current DB state, serializes imports with a PostgreSQL
transaction advisory lock, and performs the entire import in one transaction.
Any failure rolls back all changes. Repeating unchanged input preserves IDs,
timestamps, content, and selected mappings.

Existing keys are immutable. Changed display names update in place; Country
changes conflict. New keys create Destinations. Existing differing selected
IDs/statuses require an explicit guard; otherwise they conflict. Contradictory
database mapping/review states are rejected rather than repaired.

For a correction to a genuinely previously imported mapping, add:

```json
{
  "provider": "TRIP_COM",
  "entityType": "CITY",
  "status": "MAPPED",
  "externalId": "corrected-id",
  "replaces": { "status": "MAPPED", "externalId": "previous-id" }
}
```

The guard is a strict correction precondition within that Destination/provider/type
scope. Its previous status and ID must match the existing selection, or the
database must already match the target selection for an idempotent rerun.
An absent scope, including a new Destination, fails guarded input; it does not
authorize creation. A different provider/type cannot satisfy the guard.
A previous SKIPPED guard specifies `status: SKIPPED` and no ID.
A guarded transition to SKIPPED removes only that explicitly selected mapping.
Initial V1 contains no replacement guards: research-stage mistakes were never
imported into these tables.

SKIPPED requires a reason, null ID, and `DEFAULT_AFFILIATE_LINK` intent; it
creates a review, not a fake mapping or URL. Changed reasons are reported.
Absent entries never imply deletion. Shared external IDs are reported without
merging internal identities. Provider/type additions require coordinated enum
and schema constraint updates.

## Rollback and limitations

A failed apply is atomic. A successful apply is not automatically undone by
rolling back the application: retain additive schema and nullable links, use
an explicitly reviewed reverse data change, or restore a database backup.
Keep the deployed catalog version and dry-run/apply reports with the release.

The legacy 210-row itinerary bootstrap has duplicate slugs with differing
content. Repair is a separate issue; this command requires existing unique DB
itineraries and never selects one duplicate's content over another. Direct SQL
can violate cross-table review/mapping invariants; use the importer for changes.
