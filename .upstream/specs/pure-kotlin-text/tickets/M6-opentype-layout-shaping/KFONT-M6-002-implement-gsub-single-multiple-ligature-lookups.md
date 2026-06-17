---
id: "KFONT-M6-002"
title: "Implement GSUB single/multiple/ligature lookups"
status: "blocked"
milestone: "M6"
priority: "P0"
owner_area: "shaping"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M6-001", "KFONT-M2-003"]
legacy_gate: null
---

# KFONT-M6-002 - Implement GSUB single/multiple/ligature lookups

## PM Note

Ce ticket couvre les substitutions OpenType de base, comme les ligatures, avec une trace qui relie chaque glyph au texte d'origine.

## Problem

The shaping engine cannot support even simple OpenType substitutions until GSUB single, multiple, and ligature lookup behavior is implemented with coverage checks, lookup ordering, cluster preservation, and malformed-table diagnostics.

## Scope

- Implement GSUB LookupType 1 single substitution, LookupType 2 multiple substitution, and LookupType 4 ligature substitution for supported coverage formats.
- Apply lookups in the feature order supplied by `ShapingPlan` and record before/after glyph sequence facts.
- Preserve cluster mappings through one-to-one, one-to-many, and many-to-one substitutions.
- Emit `gsub-trace.json` events with lookup index, feature tag, coverage match, input glyphs, output glyphs, cluster merge/split decision, and diagnostic.
- Refuse malformed coverage, sequence, ligature set, and glyph ID references with stable diagnostics.

## Non-Goals

- Do not implement contextual, chaining contextual, alternate, reverse chaining, or extension substitutions here.
- Do not implement GPOS positioning or mark attachment.
- Do not invent script default feature policy; KFONT-M6-006 controls which features are enabled.
- Do not compare against HarfBuzz as a pass/fail oracle.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
class GsubSubstitutionEngine(
    private val table: GsubTable,
    private val diagnostics: MutableList<RouteDiagnostic>,
) {
    fun apply(run: MutableGlyphSequence, features: ResolvedFeatureSet): GsubResult
}

data class GsubTraceEvent(
    val lookupIndex: Int,
    val lookupType: GsubLookupType,
    val featureTag: OpenTypeFeatureTag,
    val inputGlyphs: List<GlyphId>,
    val outputGlyphs: List<GlyphId>,
    val clusterAction: ClusterAction,
    val diagnostic: RouteDiagnostic?,
)
```

## Acceptance Criteria

- [ ] Single substitution fixture maps one glyph to another and records the feature and lookup index.
- [ ] Multiple substitution fixture maps one glyph to multiple glyphs while preserving the original cluster range.
- [ ] Ligature fixture maps `f` + `i` or equivalent fixture glyphs into one glyph with a merged cluster range.
- [ ] Malformed coverage, invalid sequence length, and invalid ligature component fixtures emit `text.shaping.lookup-malformed`.
- [ ] Substitution output is deterministic and independent of external shaping engines.

## Current Slice

- Parsed `GSUB` table facts now surface LookupType 1 single substitution, LookupType 2 multiple substitution, and LookupType 4 ligature substitution through `OpenTypeLayoutTables`.
- `BasicOpenTypeShapingEngine` now applies those parsed lookups in active feature traversal order, preserves cluster ranges across one-to-one, one-to-many, and many-to-one substitutions, and honors explicit feature disable requests such as `liga=0`.
- This slice does not yet promote `OpenTypeLayoutEngineContract` trace dumps, malformed/refusal fixtures, or full `ShapingPlan`-driven feature-order behavior.

## Required Evidence

- `gsub-trace.json` for single, multiple, ligature, no-match, and malformed lookup fixtures.
- `shaped-glyph-run.json` showing cluster mapping before and after substitutions.
- Fixtures: `gsub-single-substitution.otf`, `gsub-multiple-substitution.otf`, `gsub-ligature-fi.otf`, `gsub-coverage-malformed.otf`, `gsub-ligature-bad-component.otf`.
- Diagnostics asserted in tests: `text.shaping.lookup-malformed`, `text.shaping.lookup-type-unsupported`, `text.shaping.cluster-invariant-failed`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.lookup-type-unsupported`, `text.shaping.gsub-ligature-malformed`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement GSUB single/multiple/ligature lookups`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserExposesParsedGsubSingleMultipleAndLigatureLookupsInLayout
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesParsedGsubSingleMultipleAndLigatureLookups --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineRespectsDisabledParsedGsubLigatureFeature
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest
```

## Status Notes

- `proposed`: Basic GSUB behavior depends on the M6 contract and M2 table facts.
- `review`: Bounded parser/runtime support for GSUB LookupType 1/2/4 is implemented and freshly validated in `font/sfnt` and `font/text` surface tests.
- `blocked`: 2026-06-18 asset audit confirmed reviewed real-font candidates for ligature and single-substitution slices (`Source Serif 4` under `SIL-OFL-1.1`) plus contextual/reference GSUB fixtures in `unicode-org/text-rendering-tests` under `Unicode-3.0`, but no reviewed in-repo asset yet proves a simple GSUB LookupType 2 multiple-substitution fixture for this ticket. Remaining gate: add reviewed provenance and expected dumps for `gsub-single-substitution.otf`, `gsub-multiple-substitution.otf`, `gsub-ligature-fi.otf`, `gsub-coverage-malformed.otf`, and `gsub-ligature-bad-component.otf`, with `gsub-multiple-substitution.otf` specifically backed by a real simple LookupType 2 fixture rather than a contextual-only substitute, then promote `gsub-trace.json` / `shaped-glyph-run.json` beyond the current M6-001 contract goldens with explicit `ShapingPlan` ordering.
- Move to `ready` only after fixture fonts and trace fields are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
