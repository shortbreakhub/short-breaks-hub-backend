# Community region collection contract

Both public collection endpoints return HTTP 200 with a JSON array, including
`[]` when a supported region has no matching PUBLIC itineraries:

- `GET /api/community-itineraries/region/{region}` — distinct original country names
- `GET /api/community-itineraries/{region}` — existing itinerary DTOs

Both use CommunityRegionResolver and the same PUBLIC itinerary selection.
Unsupported identifiers retain HTTP 404 and the existing status/error body.
Repository/server failures remain errors, not empty collections. Slug lookup,
authenticated owner endpoints, publication payloads and persisted data are unchanged.

## Identifiers (case-insensitive)

| Community identifier | Stored Region | Country filter |
| --- | --- | --- |
| southeast-asia | ASIA | Southeast Asia set below |
| east-asia-community (also east-asia) | ASIA | East Asia set below |
| EUROPE | EUROPE | None |
| americas-community | AMERICAS | None |
| anz-community | OCEANIA | Australia and New Zealand only |
| africa-community | AFRICA | None |

Canonical AFRICA, AMERICAS, ASIA, EUROPE and OCEANIA remain supported without
subregion restrictions. In particular ASIA retains all Asian itineraries and
OCEANIA retains Pacific countries; their Community subregion views are narrower.
The frontend explicitly labels `anz-community` as Australia & New Zealand.

## Country classification

Southeast Asia: Brunei, Cambodia, Indonesia, Laos, Malaysia, Myanmar,
Philippines, Singapore, Thailand, Timor-Leste and Vietnam.

East Asia: China, Hong Kong, Macao, Taiwan, Japan, Mongolia, North Korea and
South Korea. This is a travel classification, not a change to sovereignty or
the stored continent enum. South/Central/Western Asian countries are not assigned
to either category. Japan is excluded from Southeast Asia; Thailand is excluded
from East Asia.

Matching uses Unicode NFKC normalization, case folding, whitespace collapsing
(including non-breaking spaces), curly-apostrophe and selected dash normalization.
Explicit aliases:

- Brunei Darussalam → Brunei
- Lao People's Democratic Republic / Lao Peoples Democratic Republic / Lao PDR → Laos
- Burma → Myanmar; Viet Nam → Vietnam; East Timor / Timor Leste → Timor-Leste
- People's Republic of China → China
- Republic of Korea / Korea, Republic of → South Korea
- Democratic People's Republic of Korea / Korea, Democratic People's Republic of → North Korea
- Macau → Macao; Hong Kong SAR → Hong Kong; Macao SAR / Macau SAR → Macao

No substring/fuzzy matching or blanket ASIA alias is used. Ambiguous `Korea`,
unknown names, country codes and unlisted translations/official names are not
assigned to a subregion. They remain visible through canonical continent queries
if PUBLIC. New aliases require explicit review and tests. Stored country strings
and response country names are not rewritten; country discovery deduplicates
exact original strings, preserving existing response semantics. Unknown country
labels may therefore need a separately reviewed alias addition, not guessed data
migration. No database migration or frontend change is required by this contract.
