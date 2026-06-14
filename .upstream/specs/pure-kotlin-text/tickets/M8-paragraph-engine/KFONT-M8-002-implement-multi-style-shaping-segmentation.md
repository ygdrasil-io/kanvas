---
id: "KFONT-M8-002"
title: "Implement multi-style shaping segmentation"
status: "proposed"
milestone: "M8"
priority: "P0"
owner_area: "paragraph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M8-001", "KFONT-M6-001", "KFONT-M7-003"]
legacy_gate: null
---

# KFONT-M8-002 - Implement multi-style shaping segmentation

## PM Note

Ce ticket garantit que le texte riche est découpé en runs façonnables sans casser les clusters ni masquer les fallbacks.

## Problem

Paragraph layout cannot feed the shaping engine by sending the entire string as one run. Style changes, fallback faces, script boundaries, bidi runs, variation settings, and placeholder ranges all affect shaping and glyph identity. The missing gap is a deterministic segmentation layer that converts `ParagraphInput` into `ParagraphShapingRequest` values while preserving cluster safety and attaching fallback diagnostics to exact text ranges.

## Scope

- Convert `ParagraphInput` style ranges into ordered shaping segments using grapheme cluster boundaries from M5.
- Split by text style, paragraph direction, script itemization, fallback font resolution, OpenType feature set, variation coordinates, palette choice, and placeholder exclusion ranges.
- Produce `ParagraphShapingRequest` values consumed by the M6 shaping engine without parsing fonts in the paragraph module.
- Merge `text.shaping.*`, `text.fallback.*`, and `text.paragraph.*` diagnostics by source text range in the paragraph dump.
- Emit deterministic `paragraph-shaping-requests.json` and include segment references in `paragraph-layout.json`.

## Non-Goals

- Do not implement GSUB/GPOS lookup behavior or fallback font selection policy in this ticket.
- Do not implement line fitting, ellipsis, selection boxes, or GPU artifact planning.
- Do not silently split inside grapheme clusters to satisfy a style boundary.
- Do not use native text engines as pass/fail oracles.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class ParagraphShapingRequest(
    val range: TextRange,
    val text: String,
    val style: TextStyle,
    val paragraphDirection: TextDirection,
    val script: UnicodeScript,
    val locale: LocaleTag?,
    val typeface: TypefaceID,
    val fallbackTraceId: StableId,
    val clusterBoundaryPolicy: ClusterBoundaryPolicy,
)

interface ParagraphShapingSegmenter {
    fun segment(
        input: ParagraphInput,
        unicodeFacts: UnicodeSegmentationFacts,
        fallbackRuns: List<ResolvedFontRun>,
        bidiRuns: List<BidiRun>,
    ): ParagraphShapingPlan
}
```

## Acceptance Criteria

- [ ] Adjacent ranges with identical shaping-affecting style and typeface facts coalesce into one `ParagraphShapingRequest`.
- [ ] A style boundary inside a grapheme cluster is refused or widened according to a documented cluster policy, with `text.paragraph.cluster-boundary-violation`.
- [ ] Fallback segmentation preserves the original cluster order and records every attempted family/typeface for missing glyph ranges.
- [ ] Placeholders are excluded from shaping requests and leave explicit placeholder tokens in the paragraph input trace.
- [ ] Bidi/script/style/fallback splits are deterministic and dumpable for the same input, font catalog, and Unicode data version.

## Required Evidence

- `paragraph-shaping-requests.json` fixture with mixed Latin/Arabic text, two text styles, one variation axis change, and one fallback range.
- Cluster-boundary negative fixture using combining marks or emoji ZWJ sequence across a style boundary.
- Diagnostic snapshot showing shaping and fallback diagnostics merged by text range.

## Fallback / Refusal Behavior

- Missing fallback data keeps the affected range unshaped and emits `text.paragraph.fallback-unresolved` or a narrower `text.fallback.*` reason.
- Unsupported script or shaping dependency remains visible as a range diagnostic; the paragraph engine must not substitute host shaping.
- The dashboard stays `tracked-gap` until segmentation fixtures cover mixed style, fallback, bidi, and placeholder boundaries.

## Dashboard Impact

- Expected row: `Paragraph multi-style shaping segmentation`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless segmentation dumps and range diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*ParagraphShaping*'
```

## Status Notes

- `proposed`: Depends on the paragraph style contract and the M6 shaping request boundary.
- Move to `ready` only after cluster-boundary and fallback trace formats are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M8`
- `area:paragraph`
- `claim:tracked-gap`
