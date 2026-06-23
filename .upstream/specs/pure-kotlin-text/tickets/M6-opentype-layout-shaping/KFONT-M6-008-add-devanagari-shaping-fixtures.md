---
id: "KFONT-M6-008"
title: "Add Devanagari shaping fixtures"
status: "blocked"
milestone: "M6"
priority: "P0"
owner_area: "shaping"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M5-002", "KFONT-M5-004", "KFONT-M6-003", "KFONT-M6-005", "KFONT-M6-006"]
legacy_gate: null
---

# KFONT-M6-008 - Add Devanagari shaping fixtures

## PM Note

Ce ticket donne les preuves minimales pour Devanagari: syllabes, reph, matra pre-base et placement des marques.

## Problem

Devanagari requires script-specific syllable formation, feature phases, reordering, contextual substitutions, and mark positioning. Generic lookup tests cannot prove that clusters remain intact or that required Indic features are applied in the right order.

## Scope

- Add Devanagari fixture fonts and text inputs for consonant clusters, reph, pre-base matra, below-base form, half form, mark placement, and unsupported syllable/refusal cases.
- Record an `indic-syllable-plan.json` or equivalent section in `shaping-plan.json` with cluster, syllable type, feature phase, and reorder decisions.
- Assert required feature policy for `deva` and `dev2`: `nukt`, `akhn`, `rphf`, `blwf`, `half`, `pstf`, `vatu`, `pres`, `abvs`, `blws`, `psts`, `haln`, `dist`, `abvm`, and `blwm`.
- Link GSUB and GPOS traces to each syllable fixture.
- Include refusal fixtures for unsupported syllable state, missing required feature, malformed lookup, and cluster invariant failure.

## Non-Goals

- Do not implement the full Indic shaping roadmap for scripts outside the required matrix.
- Do not implement paragraph line breaking, fallback catalog policy, or glyph rendering.
- Do not use HarfBuzz as the normative oracle for syllable phases.
- Do not split a Devanagari cluster to recover from missing glyphs.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class IndicSyllable(
    val clusterRange: IntRange,
    val script: OpenTypeScriptTag,
    val type: IndicSyllableType,
    val reorderedGlyphs: List<GlyphId>,
    val featurePhases: List<IndicFeaturePhase>,
)

data class DevanagariFixtureCase(
    val name: String,
    val text: TextInput,
    val expectedSyllables: List<IndicSyllableExpectation>,
    val requiredFeatures: Set<OpenTypeFeatureTag>,
)
```

## Acceptance Criteria

- [ ] Fixtures cover consonant cluster, reph, pre-base matra, below-base form, half form, mark placement, and negative unsupported syllable cases.
- [ ] Shaping plan records Devanagari syllable boundaries, reorder decisions, feature phases, and Unicode cluster references.
- [ ] GSUB/GPOS traces show required lookups for substitutions and mark positioning used by each fixture.
- [ ] Missing required Indic feature or unsupported syllable state refuses the affected cluster.
- [ ] Cluster ranges from M5 remain intact through reordering and substitutions.

## Required Evidence

- `devanagari-shaping-report.json` with fixture provenance, expected syllables, feature phases, dump hashes, and pass/refusal status.
- `shaping-plan.json` or `indic-syllable-plan.json`, `gsub-trace.json`, `gpos-trace.json`, `shaped-glyph-run.json`, and `unicode-segments.json` for each fixture.
- Fixtures: `devanagari-consonant-cluster.otf`, `devanagari-reph.otf`, `devanagari-prebase-matra.otf`, `devanagari-below-base.otf`, `devanagari-mark-placement.otf`, `devanagari-unsupported-syllable.otf`.
- Diagnostics asserted in tests: `text.shaping.cluster-invariant-failed`, `text.shaping.feature-unsupported`, `text.shaping.mark-positioning-unavailable`, `text.shaping.lookup-malformed`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.indic-syllable-unsupported`, `text.shaping.devanagari-phase-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `fixture-gated` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add Devanagari shaping fixtures`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontFixtureManifestTest
rtk ./gradlew --no-daemon :font:text:test --tests '*DevanagariShaping*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Status Notes

- `proposed`: Devanagari evidence depends on M5 clusters/itemization, contextual GSUB, mark positioning, and feature policy.
- `blocked`: `DevanagariShapingFixtureTest` plus `devanagari-shaping-report.json` now prove bounded vendored-font evidence for pinned Script_Extensions `Deva` script selection on the pre-base matra case, consonant-cluster preservation, reph-like shaping, and mark placement on `NotoSansDevanagari-Regular.ttf` without promoting Devanagari or Indic shaping support.
- `blocked` (2026-06-23 resource-seed wave): the named Devanagari fixture resources `devanagari-consonant-cluster.otf`, `devanagari-reph.otf`, `devanagari-prebase-matra.otf`, `devanagari-below-base.otf`, `devanagari-mark-placement.otf`, and `devanagari-unsupported-syllable.otf` are now checked in under reviewed provenance as ticket-local subset/refusal seeds, but the ticket still needs phase-aware runtime assertions and ticket-local trace dumps before it can leave `blocked`.
- `blocked`: This wave intentionally keeps `indic-syllable-plan.json` or equivalent phase evidence, the full required `deva` / `dev2` feature set, dedicated unsupported-syllable and phase refusal fixtures/codes, and ticket-local `gsub-trace.json` / `gpos-trace.json` / `shaped-glyph-run.json` / `unicode-segments.json` families as explicit remaining gates.
- Move to `ready` only after the checked-in resource seeds have refreshed syllable/phase assertions and ticket-local dump fields reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:fixture-gated`
