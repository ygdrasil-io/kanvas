---
id: "KFONT-M6-009"
title: "Add Thai and CJK shaping boundaries"
status: "proposed"
milestone: "M6"
priority: "P1"
owner_area: "shaping"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M5-004", "KFONT-M6-004", "KFONT-M6-005", "KFONT-M6-006"]
legacy_gate: null
---

# KFONT-M6-009 - Add Thai and CJK shaping boundaries

## PM Note

Ce ticket separe clairement ce que le shaping Thai et CJK doit prouver de ce qui appartient plus tard au paragraph layout.

## Problem

Thai and CJK have important shaping boundaries that are easy to overclaim. Thai requires mark behavior and script itemization but dictionary word breaking is paragraph-owned. CJK requires direct mapping, variation selector handling, localized/vertical forms, and vertical alternate diagnostics without implying ruby, complex line breaking, or full paragraph support.

## Scope

- Add Thai fixtures for base plus above/below marks, tone marks, mixed Latin/Thai script runs, and missing mark-positioning diagnostics.
- Add CJK fixtures for Han, kana, hangul, localized form where fixture data exists, vertical `vert`/`vrt2` alternates, and `cmap` format 14 variation selector behavior.
- Record shaping plans, GSUB/GPOS traces, script runs, feature policy choices, and shaped glyph runs.
- Add explicit diagnostics that dictionary word breaking, ruby layout, and East Asian line-breaking refinement are paragraph-owned and not M6 support claims.
- Include negative fixtures for unsupported variation selector, missing vertical alternate, and unsupported script boundary.

## Non-Goals

- Do not implement Thai dictionary word breaking or paragraph line wrapping.
- Do not implement ruby, kinsoku, justification, or East Asian paragraph metrics.
- Do not implement color emoji or fallback font policy.
- Do not use platform CJK/Thai shapers as normative output.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class ShapingBoundaryFixture(
    val name: String,
    val script: ScriptCode,
    val text: TextInput,
    val requestedFeatures: Set<OpenTypeFeatureTag>,
    val paragraphOwnedDiagnostics: Set<String>,
)

data class CjkVariationSelectorCase(
    val baseCodePoint: Int,
    val variationSelector: Int,
    val expectedGlyphId: GlyphId?,
    val diagnosticWhenMissing: String?,
)
```

## Acceptance Criteria

- [ ] Thai fixtures prove mark positioning for above/below/tone mark cases and preserve mixed Latin/Thai script boundaries.
- [ ] Thai dictionary word breaking is diagnosed as paragraph-owned and is not claimed by shaping evidence.
- [ ] CJK fixtures cover Han direct mapping, kana/hira script tags, hangul direct mapping, vertical alternate request, and variation selector mapping through `cmap` format 14.
- [ ] Missing vertical alternate or unsupported variation selector emits stable diagnostics without corrupting the base glyph mapping.
- [ ] `shaped-glyph-run.json` records script tags, feature choices, cluster ranges, glyph IDs, positions, and diagnostics for each boundary fixture.

## Required Evidence

- `thai-cjk-boundary-report.json` with fixture provenance, script run hashes, feature policy hashes, dump hashes, and paragraph-owned non-claim diagnostics.
- `script-runs.json`, `shaping-plan.json`, `gsub-trace.json`, `gpos-trace.json`, `shaped-glyph-run.json`, and `cmap-map.json` references for fixtures.
- Fixtures: `thai-base-marks.otf`, `thai-tone-marks.otf`, `thai-latin-mixed.txt`, `cjk-han-variation-selector.otf`, `cjk-kana-vertical.otf`, `cjk-hangul-direct.otf`, `cjk-missing-vertical-alt.otf`.
- Diagnostics asserted in tests: `text.shaping.mark-positioning-unavailable`, `text.shaping.feature-unsupported`, `text.shaping.script-unsupported`, `text.shaping.paragraph-boundary-required`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.thai-mark-unsupported`, `text.shaping.cjk-variation-selector-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `fixture-gated` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add Thai and CJK shaping boundaries`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*Thai*' --tests '*Cjk*'
```

## Status Notes

- `proposed`: Boundary fixtures depend on script itemization, feature policy, and positioning support.
- Current blocker audit (2026-06-19): `KFONT-M6-004` and `KFONT-M6-005` are `done`, and `KFONT-M6-006` is now `blocked` only on the still-absent per-script fixture families. The Thai/CJK boundary fixture set `thai-base-marks.otf`, `thai-tone-marks.otf`, `thai-latin-mixed.txt`, `cjk-han-variation-selector.otf`, `cjk-kana-vertical.otf`, `cjk-hangul-direct.otf`, and `cjk-missing-vertical-alt.otf` is still not present in-repo, and the refreshed asset/license audit at `reports/pure-kotlin-text/2026-06-19-kfont-m6-fixture-asset-license-audit.md` did not identify a reviewed ticket-local pack that would clear this gate. Remaining gate is retain the bounded positioning and policy slices, then add reviewed Thai/CJK fixture provenance with paragraph-owned non-claim diagnostics.
- Move to `ready` only after paragraph-owned diagnostics and fixture scope are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:fixture-gated`
