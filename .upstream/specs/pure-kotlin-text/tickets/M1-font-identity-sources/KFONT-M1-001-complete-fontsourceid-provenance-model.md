---
id: "KFONT-M1-001"
title: "Complete `FontSourceID` provenance model"
status: "done"
milestone: "M1"
priority: "P0"
owner_area: "font-core"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-003", "KFONT-M0-004"]
legacy_gate: null
---

# KFONT-M1-001 - Complete `FontSourceID` provenance model

## PM Note

Ce ticket rend chaque source de fonte traçable, pour savoir si une preuve vient d'une fixture fiable ou de la machine locale.

## Problem

Kanvas cannot use font fixtures, user bytes, streams, files, or system scans as normative evidence until each source has stable identity and provenance. The current target requires `FontSourceID` to capture source kind, content hash when available, host dependence, face count, parser generation, table tags, and diagnostics for skipped or malformed sources.

## Scope

- Define `FontSourceID` as a stable UUID-backed value derived from provenance facts, not object identity.
- Model `BundledFontSource`, `GeneratedFixtureFontSource`, `UserDataFontSource`, `UserStreamFontSource`, `UserFileFontSource`, and `SystemScannedFontSource`.
- Include content hash when bytes are available and a host-dependent marker when a system scan is involved.
- Track face count, supported table tags, parser generation, and source diagnostics.
- Add deterministic equality and dump preimage behavior for repeated scans of identical inputs.

## Non-Goals

- Do not parse glyph outlines or shaping tables beyond the source-level table-tag facts needed for identity.
- Do not treat host system fonts as normative fixtures unless their bytes are captured.
- Do not introduce platform-native font APIs as source providers.
- Do not implement fallback family selection in this ticket.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
enum class FontSourceKind { Bundled, GeneratedFixture, UserData, UserStream, UserFile, SystemScanned }

data class FontSourceProvenance(
    val kind: FontSourceKind,
    val declaredName: String?,
    val licenseId: String?,
    val hostDependent: Boolean,
    val originPath: String?,
)

data class FontSourceIdentityPreimage(
    val provenance: FontSourceProvenance,
    val contentSha256: String?,
    val byteLength: Long?,
    val faceCount: Int,
    val tableTags: List<String>,
    val parserGeneration: Int,
)

@JvmInline
value class FontSourceID(val uuid: kotlin.uuid.Uuid)
```

## Acceptance Criteria

- [x] The same bundled or generated fixture bytes produce the same `FontSourceID` across repeated runs.
- [x] Two different byte streams with the same display name produce different source IDs.
- [x] `SystemScannedFontSource` records `hostDependent = true` unless bytes are captured into a fixture manifest.
- [x] Source diagnostics use stable codes such as `font.source.unreadable`, `font.source.host-dependent`, or `font.source.duplicate-face`.
- [x] No dump contains memory addresses, unordered map output, absolute temp paths, or other nondeterministic fields.

## Required Evidence

- `font-source.json` dump for one bundled fixture, one generated fixture, one user-data source, and one system-scanned source marked host-dependent.
- Determinism diff showing two runs over the same fixture produce identical source IDs and dump ordering.
- Diagnostic snapshot for unreadable source bytes or skipped system-scan entry.
- Source identity preimage documentation listing every field used to derive the UUID.

## Fallback / Refusal Behavior

- A source without bytes and without stable provenance must emit `font.source.host-dependent` or a more precise refusal and remain non-normative.
- Unreadable or malformed source inputs must not be converted into anonymous fallback fonts.

## Dashboard Impact

- Expected row: `FontSourceID provenance`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. Source identity is prerequisite evidence, not rendering support.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*FontSource*'
```

## Status Notes

- `done`: Merged into `master` by PR #1655 (`b85796d50`) and revalidated on 2026-06-15 in `reports/pure-kotlin-text/2026-06-15-kfont-review-closeout.md`. Remaining non-claims and later gates stay active.
- `review` (2026-06-15): Added `FontSourceIdentityPreimage`, deterministic `FontSourceID` derivation, target source-kind serialization, and checked-in `reports/pure-kotlin-text/font-source.json` evidence.
- TDD evidence: red `:font:core:compileTestKotlin` on missing source identity contract, then green `rtk ./gradlew --no-daemon :font:core:test --tests '*FontSource*'` with 6 focused tests.
- Remaining gate: this is source identity/provenance evidence only; `TypefaceID`, bundled fixture manifest, SFNT parser promotion, scaler, shaping, fallback, glyph artifacts, and GPU routes remain in later tickets.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M1`
- `area:font-core`
- `claim:tracked-gap`
