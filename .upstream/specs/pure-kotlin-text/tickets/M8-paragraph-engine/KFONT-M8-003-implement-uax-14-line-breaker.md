---
id: "KFONT-M8-003"
title: "Implement UAX #14 line breaker"
status: "proposed"
milestone: "M8"
priority: "P0"
owner_area: "paragraph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M5-001", "KFONT-M8-002"]
legacy_gate: null
---

# KFONT-M8-003 - Implement UAX #14 line breaker

## PM Note

Ce ticket rend le wrapping prévisible pour les textes UI, y compris les scripts mixtes et les clusters complexes.

## Problem

Paragraph wrapping needs Unicode line break opportunities, not width-only heuristics. The gap is a UAX #14 based line-break map that respects the pinned Unicode data version, explicit newlines, grapheme cluster boundaries, soft-wrap settings, and locale gaps. Without this, paragraph fixtures cannot prove where a line may break before line fitting, ellipsis, selection boxes, or hit testing.

## Scope

- Build a `LineBreakMap` from Unicode line break classes and grapheme cluster facts supplied by M5.
- Mark mandatory breaks, allowed soft breaks, prohibited breaks, and break reasons for each source offset.
- Honor explicit newline handling, soft-wrap enabled/disabled mode, and paragraph width-constrained fitting inputs.
- Emit diagnostics for unsupported dictionary/language-specific refinement instead of claiming full locale-sensitive line breaking.
- Dump `line-breaks.json` with Unicode data version, input hash, break opportunities, cluster references, and diagnostics.

## Non-Goals

- Do not implement dictionary-based Thai/Lao/Khmer refinement unless a separate accepted ticket supplies the data.
- Do not shape glyphs, choose fallback fonts, or compute final line metrics in this ticket.
- Do not use browser, ICU native, CoreText, or Skia Paragraph output as normative line-break evidence.
- Do not apply ellipsis or max-line truncation here.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class LineBreakOpportunity(
    val offset: TextOffset,
    val kind: LineBreakKind,
    val reason: Uax14RuleId,
    val clusterId: GraphemeClusterId,
    val localeRefinement: LocaleBreakStatus,
)

data class LineBreakMap(
    val inputHash: StableHash,
    val unicodeVersion: UnicodeVersion,
    val opportunities: List<LineBreakOpportunity>,
    val diagnostics: List<TextDiagnostic>,
)

interface Uax14LineBreaker {
    fun analyze(input: ParagraphInput, clusters: GraphemeClusterMap): LineBreakMap
}
```

## Acceptance Criteria

- [ ] `line-breaks.json` records mandatory, allowed, and prohibited break positions for hard newlines, spaces, punctuation, CJK text, combining marks, and emoji clusters.
- [ ] No line break is emitted inside a grapheme cluster.
- [ ] Soft-wrap disabled mode still records hard breaks but suppresses optional wrapping opportunities for line fitting.
- [ ] Unsupported locale-specific refinement emits `text.paragraph.locale-break-refinement-unavailable` with locale and range.
- [ ] Repeated runs with the same Unicode data version and input produce identical break maps.

## Required Evidence

- `line-breaks.json` fixtures for Latin UI text, CJK text without spaces, mixed LTR/RTL text, explicit newlines, combining marks, and emoji ZWJ sequences.
- Negative diagnostic fixture for a locale requiring dictionary refinement that is not yet implemented.
- Review diff showing the Unicode data version in each line-break dump.

## Fallback / Refusal Behavior

- When dictionary refinement is missing, the engine may use base UAX #14 classes only if the dump marks the range as refinement-limited.
- Invalid or missing Unicode data refuses with `text.paragraph.line-break-data-unavailable`.
- Silent host line-breaking fallback is not allowed.

## Dashboard Impact

- Expected row: `UAX #14 paragraph line breaker`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless UAX #14 fixtures and locale-refinement diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*LineBreak*'
```

## Status Notes

- `proposed`: Blocks reliable wrapping, ellipsis, selection, and placeholder line placement.
- Move to `ready` only after the dump schema and locale-refinement diagnostic names are accepted.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M8`
- `area:paragraph`
- `claim:tracked-gap`
