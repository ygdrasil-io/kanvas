---
id: "KFONT-M6-001"
title: "Define `OpenTypeLayoutEngine` contract and dumps"
status: "done"
milestone: "M6"
priority: "P0"
owner_area: "shaping"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-003", "KFONT-M2-004", "KFONT-M5-002", "KFONT-M5-003", "KFONT-M5-004"]
legacy_gate: null
---

# KFONT-M6-001 - Define `OpenTypeLayoutEngine` contract and dumps

## PM Note

Ce ticket fixe le contrat du shaping avant les lookups, pour que chaque glyph run explique ses choix et ses refus.

## Problem

Kanvas needs a stable pure Kotlin shaping boundary that consumes font facts, Unicode clusters, bidi runs, script runs, fallback decisions, features, and glyph mappings. Without a typed `OpenTypeLayoutEngine` contract and canonical dumps, later GSUB/GPOS tickets cannot produce comparable evidence or precise diagnostics.

## Scope

- Define the explicit shaping pipeline contract from `TextInput` through clusters, bidi, script, fallback run splitting, `cmap`, GSUB, GPOS, and run compaction.
- Define value objects for `ShapingPlan`, `ResolvedFeatureSet`, `OpenTypeRunInput`, `ShapedGlyphRun`, cluster mappings, glyph positions, fallback-visible run facts, and route diagnostics.
- Define dump schemas for `shaping-plan.json`, `gsub-trace.json`, `gpos-trace.json`, and `shaped-glyph-run.json`.
- Require Unicode data version, typeface identity, selected script/language system, requested/enabled/disabled features, and diagnostic reason codes in every dump.
- Add refusal paths for missing font tables, unsupported scripts/features/lookups, malformed lookup tables, and cluster invariant failures.

## Non-Goals

- Do not implement GSUB or GPOS lookup behavior beyond no-op plumbing.
- Do not implement paragraph visual layout, line breaking, fallback catalog policy, glyph artifacts, or GPU handoff.
- Do not make `SkCanvas.drawString` perform implicit complex shaping.
- Do not use HarfBuzz, platform shapers, browser layout, ICU native, CoreText, or DirectWrite as normative shaping behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
interface OpenTypeLayoutEngine {
    fun shape(input: OpenTypeRunInput): ShapingResult
}

data class OpenTypeRunInput(
    val text: TextInput,
    val typeface: TypefaceID,
    val clusters: List<GraphemeCluster>,
    val bidiRun: BidiRun,
    val scriptRun: ScriptRun,
    val requestedFeatures: List<FeatureRequest>,
    val fallbackContext: FallbackRunContext?,
)

data class ShapedGlyphRun(
    val runId: ShapedRunId,
    val typeface: TypefaceID,
    val script: OpenTypeScriptTag,
    val direction: TextDirection,
    val glyphs: List<ShapedGlyph>,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Contract tests can produce a no-op shaped run from a simple mapped Latin fixture while preserving cluster ranges and diagnostics.
- [ ] Dump schemas include Unicode version, source text hash, typeface ID, script/language system, features, lookup trace references, fallback facts, and cluster mappings.
- [ ] Missing `GDEF`, `GSUB`, or `GPOS` tables diagnose precisely according to whether the requested script/feature needs them.
- [ ] Unsupported script, unsupported feature, malformed lookup, missing fallback, and cluster invariant refusals have stable reason codes.
- [ ] Direct glyph ID input can bypass GSUB while still producing synthetic cluster and GPOS-bypass facts.

## Required Evidence

- `shaping-plan.json` schema fixture for simple Latin, direct glyph run, unsupported script, and missing table cases.
- `shaped-glyph-run.json` schema fixture with glyph IDs, cluster ranges, advances/placeholders, positions, run direction, and diagnostics.
- Empty or no-op `gsub-trace.json` and `gpos-trace.json` fixtures proving trace references are stable before lookup implementation.
- Diagnostics asserted in tests: `text.shaping.script-unsupported`, `text.shaping.feature-unsupported`, `text.shaping.lookup-type-unsupported`, `text.shaping.lookup-malformed`, `text.shaping.cluster-invariant-failed`, `text.shaping.fallback-missing`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.engine-contract-missing`, `text.shaping.script-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Define OpenTypeLayoutEngine contract and dumps`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*OpenTypeLayoutEngine*'
```

## Status Notes

- `done`: Contract and dump schema implementation has fresh
  `OpenTypeLayoutEngineContractTest`, dump-index, fixture-manifest,
  claim-dashboard, independent spec review, and independent code-quality
  review evidence. This remains no-op GSUB/GPOS plumbing only and does not
  promote complex shaping support.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
