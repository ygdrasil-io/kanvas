---
id: "KFONT-M8-004"
title: "Implement ellipsis and max-lines policy"
status: "proposed"
milestone: "M8"
priority: "P1"
owner_area: "paragraph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M8-002", "KFONT-M8-003"]
legacy_gate: null
---

# KFONT-M8-004 - Implement ellipsis and max-lines policy

## PM Note

Ce ticket évite les coupures de texte invisibles ou incohérentes quand une UI impose un nombre de lignes.

## Problem

Paragraph layout needs a deterministic truncation policy after shaping and line fitting. The current catalog does not specify how `maxLines`, ellipsis glyph shaping, bidi visual order, placeholders, and cluster-safe replacement interact. Without this ticket, a paragraph could cut inside a cluster, hide an unshaped ellipsis, or report line metrics that no longer match the glyph runs.

## Scope

- Add `EllipsisPolicy` handling to line fitting for `maxLines`, width-constrained lines, and hard/soft break boundaries.
- Shape the ellipsis string through the same segmentation path as paragraph text and record its glyph run provenance.
- Replace only cluster-safe trailing content and preserve visual-order facts for bidi lines.
- Record per-line ellipsis state, truncated source range, visible range, and ellipsis glyph descriptors in `paragraph-layout.json`.
- Emit diagnostics for missing ellipsis glyph, no room for ellipsis, invalid `maxLines`, and placeholder truncation conflicts.

## Non-Goals

- Do not implement the UAX #14 break map or shaping segmentation here.
- Do not add middle/head truncation variants unless accepted by a later policy update.
- Do not turn ellipsis into a GPU rendering support claim.
- Do not silently drop content without reporting the truncated range.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class EllipsisPolicy(
    val maxLines: Int?,
    val ellipsisText: String,
    val placement: EllipsisPlacement = EllipsisPlacement.End,
)

data class TruncatedLine(
    val lineIndex: Int,
    val visibleRange: TextRange,
    val truncatedRange: TextRange,
    val ellipsisGlyphRun: GlyphRunDescriptor?,
    val diagnostics: List<TextDiagnostic>,
)

interface ParagraphLineFitter {
    fun fitLines(
        shapedRuns: List<ShapedGlyphRun>,
        breaks: LineBreakMap,
        policy: EllipsisPolicy,
        widthPx: Float,
    ): List<LineLayout>
}
```

## Acceptance Criteria

- [ ] `maxLines` truncation never cuts inside a grapheme cluster or shaped glyph cluster.
- [ ] Ellipsis glyphs are shaped with the active trailing style and recorded as a distinct glyph run descriptor.
- [ ] Bidi lines preserve visual ordering after truncation and record visible logical ranges.
- [ ] Placeholder ranges that cannot be partially truncated produce `text.paragraph.placeholder-ellipsis-conflict`.
- [ ] `paragraph-layout.json` includes `isEllipsized`, visible range, truncated range, and ellipsis glyph provenance per affected line.

## Required Evidence

- `paragraph-layout.json` fixtures for one-line overflow, multi-line overflow, mixed style ellipsis, bidi text, and placeholder-adjacent truncation.
- Negative fixture for missing ellipsis glyph and no-room-for-ellipsis cases.
- Diagnostic snapshot using `text.paragraph.ellipsis-glyph-missing`, `text.paragraph.ellipsis-no-room`, or a narrower accepted reason.

## Fallback / Refusal Behavior

- If ellipsis cannot be shaped, the line fit refuses the ellipsized layout instead of drawing unmarked clipped text.
- If `maxLines` is invalid, layout returns `text.paragraph.invalid-max-lines`.
- Host paragraph truncation APIs are not allowed as fallback.

## Dashboard Impact

- Expected row: `Paragraph ellipsis and max-lines policy`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless truncation fixtures cover clusters, bidi, and placeholder conflict cases.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*Ellipsis*'
```

## Status Notes

- `proposed`: Depends on stable shaping requests and line-break maps.
- Move to `ready` only after the truncation dump fields and refusal codes are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M8`
- `area:paragraph`
- `claim:tracked-gap`
