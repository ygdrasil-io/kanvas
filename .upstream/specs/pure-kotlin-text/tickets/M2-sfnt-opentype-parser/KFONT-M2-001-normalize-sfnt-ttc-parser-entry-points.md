---
id: "KFONT-M2-001"
title: "Normalize SFNT/TTC parser entry points"
status: "review"
milestone: "M2"
priority: "P0"
owner_area: "font-sfnt"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M1-001", "KFONT-M1-004"]
legacy_gate: null
---

# KFONT-M2-001 - Normalize SFNT/TTC parser entry points

## PM Note

Ce ticket donne une porte d'entrée unique au parser font, pour que TTF et collections soient validés de la même façon.

## Problem

The target needs one bounded contract for single-face SFNT fonts, TTC collections, and OTC collections before table-specific work can be audited. If parser entry points stay split or ad hoc, dumps and diagnostics cannot reliably identify which source, collection index, and face produced a table fact.

## Scope

- Define pure Kotlin entry points for single-face SFNT, TTC, and OTC containers.
- Require every parse request to carry `FontSourceID`, source byte bounds, requested collection index, and parser generation.
- Return a container-level result that separates directory facts, face facts, table slices, and diagnostics.
- Reject invalid collection indices with stable diagnostics instead of falling through to face `0`.
- Produce `sfnt-directory.json` for a single TTF and a TTC face index without invoking scaler or shaping code.

## Non-Goals

- Do not parse glyph outlines, CFF charstrings, GSUB/GPOS lookups, or color glyph payloads.
- Do not claim support for every table whose directory record is discovered.
- Do not use platform font APIs or external parsers as normative behavior.
- Do not implement fallback font selection.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class SFNTParseRequest(
    val sourceId: FontSourceID,
    val bytes: BoundedFontBytes,
    val collectionIndex: Int?,
    val parserGeneration: Int,
)

sealed interface SFNTContainer {
    data class SingleFace(val face: SFNTFaceFacts) : SFNTContainer
    data class Collection(val version: String, val faces: List<SFNTFaceFacts>) : SFNTContainer
}

interface SFNTParser {
    fun parse(request: SFNTParseRequest): SFNTParseResult
}
```

## Acceptance Criteria

- [x] Single-face SFNT and TTC inputs use the same `SFNTParseRequest` and result type.
- [x] TTC face selection by index is deterministic and rejects out-of-range indices with `font.collection-index-invalid`.
- [x] Parser results expose table directory slices without copying arbitrary out-of-bounds data.
- [x] `sfnt-directory.json` includes source ID, container kind, collection index, table records, and diagnostics.
- [x] The parser contract does not instantiate scaler, shaper, fallback, or GPU types.

## Required Evidence

- `sfnt-directory.json` for one single TTF fixture and one TTC fixture face.
- Diagnostic snapshot for invalid TTC collection index.
- Parser API review note showing the entry point accepts bounded bytes and `FontSourceID`.
- Determinism diff for repeated parse of the same fixture.

## Fallback / Refusal Behavior

- Unknown container wrappers must emit a stable `font.outline-format-unsupported` or wrapper-specific SFNT diagnostic and stay `tracked-gap`.
- Invalid collection index must refuse the requested face; it must not silently parse another face.

## Dashboard Impact

- Expected row: `SFNT/TTC parser entry points`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. Entry-point parsing does not claim table semantics, glyph outlines, shaping, or rendering.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*SFNTParser*' --tests '*TTC*'
```

## Status Notes

- `proposed`: Entry-point contract is specified, but no parser dump evidence is attached yet.
- `review`: `SFNTParseRequest`, `BoundedFontBytes`, `DefaultSFNTParser`, and
  `SFNTDirectoryReportWriter` are implemented with checked-in
  `sfnt-directory.json` evidence for Liberation Sans, a generated TTC face, and
  invalid TTC index refusal. Classification remains `tracked-gap` with
  `claimPromotionAllowed=false`.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M2`
- `area:font-sfnt`
- `claim:tracked-gap`
