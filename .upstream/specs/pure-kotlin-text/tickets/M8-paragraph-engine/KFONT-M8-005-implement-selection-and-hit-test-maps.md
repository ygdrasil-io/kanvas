---
id: "KFONT-M8-005"
title: "Implement selection and hit-test maps"
status: "review"
milestone: "M8"
priority: "P1"
owner_area: "paragraph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M8-002", "KFONT-M8-003"]
legacy_gate: null
---

# KFONT-M8-005 - Implement selection and hit-test maps

## PM Note

Ce ticket rend les interactions texte sélection/caret testables, notamment aux frontières bidi et multi-lignes.

## Problem

Paragraph layout is not useful for editors or selectable UI text unless it can map text ranges to boxes and points back to text positions. The gap is a deterministic `HitTestMap` and selection-box model derived from shaped glyph clusters, visual line order, placeholders, and bidi levels. A generic layout dump cannot prove caret positions, affinity, or selection geometry.

## Scope

- Build selection boxes for arbitrary text ranges from `ParagraphLayoutResult`, including multi-line and bidi ranges.
- Build point hit testing with caret affinity, grapheme boundary snapping, placeholder boxes, and visual line order.
- Expose word and grapheme boundary query facts sourced from M5 segmentation data.
- Dump `hit-test-map.json` with line IDs, glyph cluster boxes, caret stops, text positions, affinities, and diagnostics.
- Add diagnostics for coordinates outside finite layout bounds, invalid text ranges, and cluster invariant failures.

## Non-Goals

- Do not implement rendering of selection highlights or carets.
- Do not invent platform-specific caret movement behavior not represented in the dump.
- Do not implement paragraph shaping, line breaking, or placeholder metrics in this ticket.
- Do not use native accessibility/text APIs as normative evidence.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class TextPosition(
    val offset: TextOffset,
    val affinity: TextAffinity,
)

data class HitTestEntry(
    val point: Vec2,
    val lineIndex: Int,
    val position: TextPosition,
    val clusterId: GlyphClusterId?,
    val placeholderId: PlaceholderId?,
)

data class HitTestMap(
    val caretStops: List<CaretStop>,
    val selectionBoxes: List<TextBox>,
    val hitEntries: List<HitTestEntry>,
    val diagnostics: List<TextDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Selection boxes are stable for single-line, multi-line, bidi, and mixed-style ranges.
- [x] Hit testing at glyph cluster boundaries records upstream/downstream affinity and never returns an offset inside a grapheme cluster.
- [x] Placeholder boxes participate in selection and hit testing with explicit placeholder IDs.
- [x] Out-of-bounds points clamp or refuse according to a documented policy and emit diagnostics for non-finite coordinates.
- [x] `hit-test-map.json` is deterministic and references line IDs from `paragraph-layout.json`.

## Current Slice

- `ParagraphLayoutResult.buildHitTestMap(...)` now derives bounded line-local selection and hit-test facts from shaped cluster advances when they are already available on `LineLayout.glyphRuns`, while keeping logical caret and selection geometry independent from glyph drawing offsets.
- Selection ranges that cut a grapheme cluster now refuse with `text.paragraph.cluster-invariant-failed` instead of guessing a partial box.
- The checked-in fixture currently covers multiline placeholder selection, RTL emoji grapheme clamping, invalid selection ranges, and non-finite hit points only.

## Required Evidence

- `hit-test-map.json` fixture for mixed LTR/RTL text, multi-line selection, combining marks, emoji cluster boundaries, and placeholder ranges.
- `selection-boxes.json` or equivalent section in `hit-test-map.json` showing per-line boxes for a cross-line range.
- Negative fixture for invalid range and non-finite point diagnostics.

## Fallback / Refusal Behavior

- Invalid ranges refuse with `text.paragraph.invalid-selection-range`.
- Finite points outside the laid-out bounds clamp to the nearest line and then the nearest fragment boundary; non-finite points refuse with `text.paragraph.hit-test-point-non-finite`.
- Cluster invariant failures refuse with `text.paragraph.cluster-invariant-failed`; the engine must not guess a host caret offset.
- Missing placeholder metrics keep placeholder hit testing refused until KFONT-M8-006 supplies the box facts.

## Dashboard Impact

- Expected row: `Paragraph selection and hit-test maps`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless hit-test and selection fixtures cover bidi, cluster, and placeholder cases.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*HitTest*'
```

## Status Notes

- `proposed`: Depends on shaped run segmentation and line-break facts; placeholder cases depend on M8-006 for full geometry.
- `review`: bounded paragraph interaction evidence now exposes deterministic `HitTestMap` dumps with `caretStops`, `selectionBoxes`, `hitEntries`, `wordBoundaries`, and `graphemeBoundaries`, consumes `PlaceholderBox` geometry from `KFONT-M8-006`, reuses shaped cluster advances where available, and refuses invalid ranges, grapheme-cut selection boundaries, or non-finite points with stable diagnostics. Remaining gate before `done`: add reviewed mixed LTR/RTL plus mixed-style fixture coverage and explicit multi-run visual-order evidence, and promote structured negative dump rows beyond the current bounded labels/tests without widening support claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M8`
- `area:paragraph`
- `claim:tracked-gap`
