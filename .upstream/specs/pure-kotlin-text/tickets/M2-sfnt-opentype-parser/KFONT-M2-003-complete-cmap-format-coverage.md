---
id: "KFONT-M2-003"
title: "Complete cmap format coverage"
status: "proposed"
milestone: "M2"
priority: "P0"
owner_area: "font-sfnt"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-001", "KFONT-M2-002"]
legacy_gate: null
---

# KFONT-M2-003 - Complete cmap format coverage

## PM Note

Ce ticket vérifie que les caractères Unicode trouvent des glyphes de façon stable, ou refusent proprement.

## Problem

Kanvas text cannot progress beyond parser facts unless Unicode `cmap` selection is deterministic and bounded. The target requires formats 12 and 4 as primary mappings, format 14 for variation selectors, formats 6 and 0 as legacy fallbacks, stable glyph ID `0` for missing code points, and precise diagnostics for unsupported or unusable `cmap` subtables.

## Scope

- Parse and select Unicode `cmap` format 12, format 4, format 14, format 6, and format 0 according to target priority.
- Treat format 13 as fixture-gated unless product fixtures justify many-to-one support.
- Emit `font.cmap-format-unsupported` for unsupported formats and a stable no-usable-Unicode-`cmap` diagnostic when selection fails.
- Produce `cmap-map.json` with selected subtable, platform/encoding IDs, mapped ranges, missing-codepoint behavior, and variation selector facts.
- Keep glyph ID lookup pure parser behavior; do not shape clusters or render glyphs.

## Non-Goals

- Do not implement GSUB, GPOS, bidi, segmentation, fallback runs, or paragraph layout.
- Do not support rare legacy encodings unless they are accepted into the Kanvas required script matrix.
- Do not claim glyph outline support from successful `cmap` mapping alone.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
sealed interface CMapSubtable {
    val platformId: Int
    val encodingId: Int
    fun glyphIdFor(codePoint: Int, variationSelector: Int? = null): GlyphID
}

data class CMapSelection(
    val format: Int,
    val priority: Int,
    val unicodeCoverageRanges: List<IntRange>,
    val variationSelectorSupported: Boolean,
)

class CMapTable(
    val subtables: List<CMapSubtable>,
    val diagnostics: List<SerializedFontDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Format 12 and format 4 fixtures map expected Unicode code points to stable glyph IDs.
- [ ] Format 14 fixtures expose variation selector mappings without changing base mapping semantics.
- [ ] Format 6 and format 0 fixtures are lower-priority fallbacks and do not override a usable format 12 or 4 Unicode subtable.
- [ ] Missing code points return glyph ID `0` and appear in `cmap-map.json` test evidence.
- [ ] Unsupported formats emit `font.cmap-format-unsupported` with format, platform ID, encoding ID, and source face identity.

## Required Evidence

- `cmap-map.json` for fixtures covering formats 12, 4, 14, 6, and 0.
- Unsupported-format diagnostic snapshot with exact format and subtable identity.
- Selection-priority test proving format 12 or 4 wins over legacy fallback subtables.
- Missing-codepoint test evidence showing stable glyph ID `0`.

## Fallback / Refusal Behavior

- Fonts with no usable Unicode `cmap` must refuse parser promotion with a precise diagnostic.
- Format 13 remains fixture-gated or refused until reviewed product fixtures require it.
- The parser must not call shaping or platform APIs to compensate for unsupported `cmap` data.

## Dashboard Impact

- Expected row: `cmap Unicode coverage`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. `cmap` support is parser evidence, not shaping or rendering support.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*CMap*'
```

## Status Notes

- `proposed`: `cmap` coverage requirements are specified, but no `cmap-map.json` evidence is attached yet.
- Move to `ready` after SFNT entry points and directory diagnostics are available.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M2`
- `area:font-sfnt`
- `claim:tracked-gap`
