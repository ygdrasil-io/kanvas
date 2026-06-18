---
id: "KFONT-M8-006"
title: "Implement placeholder layout metrics"
status: "review"
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
- [ ] Selection and hit-test dumps reference placeholder IDs and geometry.
- [x] Invalid placeholder ranges or metrics emit `text.paragraph.invalid-placeholder` or a narrower accepted diagnostic.

## Required Evidence

- `placeholder-layout.json` fixture for baseline-aligned, above-baseline, below-baseline, and center-aligned placeholders.
- `paragraph-layout.json` fixture showing placeholder effect on line ascent, descent, width, and selection boxes.
- Negative diagnostics for non-finite dimensions, invalid range, missing baseline where required, and placeholder/ellipsis conflict.

## Fallback / Refusal Behavior

- Invalid placeholder metrics refuse paragraph layout for the affected input with stable diagnostics.
- Placeholder content is never measured by platform UI APIs.
- When placeholder geometry is missing, selection and hit testing must report the missing placeholder fact instead of returning text-only boxes.

## Dashboard Impact

- Expected row: `Paragraph placeholder layout metrics`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless placeholder geometry and invalid-metric diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*Placeholder*'
```

## Status Notes

- `review`: `PlaceholderStyle` now serializes `baseline` as nullable plus `participatesInLineHeight`, input validation refuses missing required baselines and non-finite/negative placeholder constraints, and `BasicParagraphLayoutEngine` emits deterministic `placeholderBoxes` plus line metrics for baseline, above-baseline, below-baseline, and centered cases in `placeholder-layout.json`.
- `review`: `KFONT-M8-005` now consumes placeholder IDs and geometry in deterministic selection/hit-test evidence, including a non-participating below-baseline overflow case, so placeholder layout facts are no longer isolated to line metrics and layout dumps.
- Remaining gate before `done`: the ellipsis/max-lines path must still attach the placeholder conflict evidence called out by this ticket without broadening paragraph support claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M8`
- `area:paragraph`
- `claim:tracked-gap`
