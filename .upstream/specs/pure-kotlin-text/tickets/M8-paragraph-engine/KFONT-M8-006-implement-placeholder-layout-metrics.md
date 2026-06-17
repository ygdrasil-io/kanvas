---
id: "KFONT-M8-006"
title: "Implement placeholder layout metrics"
status: "done"
milestone: "M8"
priority: "P1"
owner_area: "paragraph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M8-001", "KFONT-M8-003"]
legacy_gate: null
---

# KFONT-M8-006 - Implement placeholder layout metrics

## PM Note

Ce ticket permet aux objets inline de réserver une place fiable dans les paragraphes sans perturber les métriques du texte.

## Problem

The paragraph target includes placeholders for inline objects, but current ticket text does not specify how placeholder dimensions, baselines, line-height participation, range mapping, selection, or hit testing are represented. Without a concrete metric contract, layout could overlap placeholders, misplace baselines, or hand the renderer geometry that cannot be traced back to source text ranges.

## Scope

- Define `PlaceholderStyle` fields for width, height, alignment, baseline, above-baseline, below-baseline, and line-height participation.
- Insert placeholder ranges into `ParagraphInput` and exclude them from normal shaping requests.
- Compute `PlaceholderBox` geometry during line fitting, including text range, line index, baseline offset, and visual bounds.
- Feed placeholder boxes into selection and hit-test maps.
- Emit `placeholder-layout.json` or a dedicated section of `paragraph-layout.json` with metrics, alignment policy, and diagnostics.

## Non-Goals

- Do not render placeholder content or define its draw command payload.
- Do not implement general inline widgets or layout negotiation outside paragraph metrics.
- Do not use host UI toolkit placeholder measurement.
- Do not claim GPU rendering support for placeholder content.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class PlaceholderStyle(
    val widthPx: Float,
    val heightPx: Float,
    val alignment: PlaceholderAlignment,
    val baseline: TextBaseline?,
    val baselineOffsetPx: Float?,
)

data class PlaceholderBox(
    val placeholderId: PlaceholderId,
    val sourceRange: TextRange,
    val lineIndex: Int,
    val bounds: RectF,
    val baselineOffsetPx: Float,
    val participatesInLineHeight: Boolean,
)

interface PlaceholderLayoutResolver {
    fun resolve(lines: List<LineLayout>, placeholders: List<StyleRange<PlaceholderStyle>>): List<PlaceholderBox>
}
```

## Acceptance Criteria

- [x] Placeholder dimensions are finite, non-negative, and validated before line fitting.
- [x] Placeholder ranges map to exactly one placeholder token and are excluded from shaping requests.
- [x] Baseline, above-baseline, below-baseline, and centered alignments affect line ascent/descent deterministically.
- [x] `ParagraphLayoutResult.dump()` and `placeholder-layout.json` now reference placeholder IDs and geometry so `KFONT-M8-005` can consume stable placeholder facts later.
- [x] Invalid placeholder ranges or metrics emit narrower existing refusals such as `text.paragraph.invalid-constraint`, `text.paragraph.invalid-style-range`, `text.paragraph.unsupported-policy`, and `text.paragraph.placeholder-ellipsis-conflict`.

## Required Evidence

- `placeholder-layout.json` fixture for baseline-aligned, above-baseline, below-baseline, and center-aligned placeholders, plus a focused non-participating line-height test.
- `paragraph-layout.json` and deterministic dump coverage showing `placeholderBoxes` in the shared paragraph layout schema without promoting selection or hit-test claims.
- Negative diagnostics for non-finite dimensions, invalid range, unsupported baseline policy, and placeholder/ellipsis conflict.

## Fallback / Refusal Behavior

- Invalid placeholder metrics refuse paragraph layout for the affected input with stable diagnostics.
- Placeholder content is never measured by platform UI APIs.
- `KFONT-M8-005` remains responsible for consuming placeholder geometry in selection and hit-testing APIs; this ticket does not guess text-only boxes when placeholder facts are absent downstream.

## Dashboard Impact

- Expected row: `Paragraph placeholder layout metrics`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless placeholder geometry and invalid-metric diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphLayoutEngineComputesPlaceholderBoxesAndExpandsLineMetrics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphLayoutEngineKeepsNonParticipatingPlaceholderOutOfLineHeight --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphPlaceholderLayoutGoldenMatchesRepoFixture --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphPlaceholderLayoutGoldenPinsCasesAndNonClaims
rtk ./gradlew --no-daemon :font:text:test
```

## Status Notes

- `done`: bounded placeholder geometry is now emitted by `PlaceholderStyle(participatesInLineHeight=...)`, `PlaceholderBox`, `ParagraphLayoutResult.placeholderBoxes`, and `placeholder-layout.json`. The runtime adjusts line ascent/descent deterministically for baseline, above-baseline, below-baseline, and middle-aligned placeholders, while keeping non-participating placeholder boxes out of line-height expansion. Remaining non-claims stay explicit: `KFONT-M8-005` still owns selection/hit-test consumption, full bidi visual ordering parity is not promoted, and this slice does not claim placeholder rendering or Skia Paragraph parity.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M8`
- `area:paragraph`
- `claim:tracked-gap`
