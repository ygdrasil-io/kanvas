---
id: "KFONT-M6-008"
title: "Add Devanagari shaping fixtures"
status: "proposed"
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
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*DevanagariShaping*'
```

## Status Notes

- `proposed`: Devanagari evidence depends on M5 clusters/itemization, contextual GSUB, mark positioning, and feature policy.
- Current blocker audit (2026-06-18): `KFONT-M6-006` remains in `review` with per-script fixture and `drawString` evidence gates still open; `KFONT-M6-003` remains gated by its own missing contextual fixture family; and the Devanagari fixture set `devanagari-consonant-cluster.otf`, `devanagari-reph.otf`, `devanagari-prebase-matra.otf`, `devanagari-below-base.otf`, `devanagari-mark-placement.otf`, and `devanagari-unsupported-syllable.otf` is not present in-repo. Remaining gate is complete the contextual dependency, retain the bounded feature-policy slice, then add reviewed Indic fixture provenance and syllable-plan dumps.
- Move to `ready` only after fixture coverage and syllable dump fields are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:fixture-gated`
