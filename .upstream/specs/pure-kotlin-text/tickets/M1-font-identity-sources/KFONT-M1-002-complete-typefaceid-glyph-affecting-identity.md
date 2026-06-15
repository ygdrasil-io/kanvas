---
id: "KFONT-M1-002"
title: "Complete `TypefaceID` glyph-affecting identity"
status: "done"
milestone: "M1"
priority: "P0"
owner_area: "font-core"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M1-001"]
legacy_gate: ["typeface"]
---

# KFONT-M1-002 - Complete `TypefaceID` glyph-affecting identity

## PM Note

Ce ticket évite de confondre deux faces qui se ressemblent mais produisent des glyphes différents.

## Problem

The target requires `TypefaceID` to change whenever a fact that can affect glyph output changes. Current identity cannot be considered complete until it includes source identity, collection index, family/style facts, PostScript name, outline format, variation coordinates, palette, selected `cmap`, scaler mode, fallback catalog generation, and content hash or fixture identity.

## Scope

- Define the `TypefaceID` preimage for one face inside a font or collection.
- Include `FontSourceID`, collection index, PostScript name, family/style metadata, outline format, selected `cmap`, scaler mode, variation coordinates, palette index/overrides, fallback catalog generation, and table availability facts.
- Ensure variable coordinates and palette differences create distinct IDs even when family names match.
- Add diagnostics for incomplete identity facts, invalid collection index, or missing usable `cmap`.
- Keep legacy gate `typeface` open until target-shaped evidence replaces the current contract.

## Non-Goals

- Do not implement OpenType shaping, fallback selection, paragraph layout, or glyph artifact caching.
- Do not make family/style names alone identity handles.
- Do not retire the `typeface` legacy gate without evidence and dashboard update.
- Do not depend on platform-native typeface handles.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class TypefaceIdentityPreimage(
    val sourceId: FontSourceID,
    val collectionIndex: Int,
    val postScriptName: String?,
    val familyName: String,
    val styleName: String,
    val outlineFormat: OutlineFormat,
    val variationCoordinates: SortedMap<String, Double>,
    val palette: PaletteSelection?,
    val selectedCMap: CMapSelection,
    val scalerMode: ScalerMode,
    val fallbackCatalogGeneration: Int?,
    val tableFactsHash: String,
)

@JvmInline
value class TypefaceID(val uuid: kotlin.uuid.Uuid)
```

## Acceptance Criteria

- [x] Different collection indices produce different `TypefaceID` values.
- [x] Different variation coordinates or palette selections produce different `TypefaceID` values.
- [x] The same face selected twice from the same fixture source produces identical IDs and sorted preimage output.
- [x] Missing or invalid collection index emits `font.collection-index-invalid`.
- [x] The `typeface` legacy gate remains visible until evidence links this identity contract to the facade migration.

## Required Evidence

- `typeface-id.json` dump for single-face TTF, TTC face index, variable-font axis change, and palette change when a palette fixture exists.
- Determinism diff showing stable preimage ordering and UUID output.
- Diagnostic snapshot for invalid collection index or no usable Unicode `cmap`.
- Dashboard row showing `typeface` still open as a legacy gate.

## Fallback / Refusal Behavior

- If a face lacks required identity facts, refuse identity promotion with a precise `font.source.*` or `font.sfnt.*` diagnostic instead of generating an anonymous ID.
- Legacy gate(s) `typeface` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `TypefaceID glyph-affecting identity`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. Identity evidence does not claim glyph rendering.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*Typeface*'
```

## Status Notes

- `done`: Merged into `master` by PR #1656 (`68897c182`) and revalidated on 2026-06-15 in `reports/pure-kotlin-text/2026-06-15-kfont-review-closeout.md`. Remaining non-claims and later gates stay active.
- `proposed`: Typeface identity fields are specified, but no `typeface-id.json` evidence is attached yet.
- Move to `ready` after KFONT-M1-001 lands the source identity model.
- `review` 2026-06-15: `TypefaceIdentityPreimage` now records source ID,
  collection index, PostScript name, family/style metadata, outline format,
  selected Unicode `cmap`, scaler mode, variation coordinates, palette facts,
  fallback catalog generation, table tags, and stable diagnostics.
- `review` 2026-06-15: `reports/pure-kotlin-text/typeface-id.json` is asserted
  against `defaultTypefaceIdentityReport().toCanonicalJson()` and includes
  single-face, TTC index, variation, palette, invalid collection-index, and
  no-usable-Unicode-`cmap` rows using `font.sfnt.cmap-unusable`.
- Validation run:
  `rtk ./gradlew --no-daemon :font:core:test --tests '*Typeface*'` failed
  first at `:font:core:compileTestKotlin` for missing `TypefaceIdentity*`
  contract symbols.
- Validation run:
  `rtk ./gradlew --no-daemon --rerun-tasks :font:core:test --tests '*Typeface*'`
  passed after implementation and checked-in evidence alignment, including
  `-0.0` variation-coordinate normalization.
- Validation run:
  `rtk ./gradlew --no-daemon --rerun-tasks :font:core:test` passed 31
  `font/core` tests.
- Review run: spec compliance and code quality reviews both approved after
  remediation of the `font.sfnt.cmap-unusable` taxonomy and variation
  zero-normalization findings.
- Non-claim: this remains identity/evidence only and does not close the
  `typeface` legacy gate.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M1`
- `area:font-core`
- `claim:tracked-gap`
- `legacy:typeface`
