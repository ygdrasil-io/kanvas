---
id: "KFONT-M12-004"
title: "Add glyph artifact and cache metrics"
status: "review"
milestone: "M12"
priority: "P1"
owner_area: "telemetry"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M12-001", "KFONT-M9-006", "KFONT-M10-010"]
legacy_gate: ["dftext"]
---

# KFONT-M12-004 - Add glyph artifact and cache metrics

## PM Note

Ce ticket rend auditables les coûts d'artifacts glyph, d'atlas et de cache avant toute décision sur `dftext`.

## Problem

The glyph target introduces representation decisions, `GlyphStrikeKey`, A8 masks, SDF masks, color/bitmap/SVG plans, atlas packing, eviction, and invalidation. M12 lacks metrics that show which route was selected, how expensive artifact generation was, and whether cache pressure or atlas churn caused a regression. Without that visibility, `dftext` and SDF-related work can be overclaimed by cache hits or hidden CPU preparation.

## Scope

- Emit representation route counts for outline, A8, SDF, COLR, PNG bitmap, SVG, and unsupported glyph routes.
- Emit artifact generation metrics for A8 generation time, SDF generation time, COLR paint graph evaluation time, PNG decode time, SVG glyph evaluation time, and source mask/hash count.
- Emit cache and atlas metrics for `GlyphStrikeKey` count, key preimage hash, atlas occupancy, pack time, hit/miss/eviction, stale generation refusal count, invalidation token changes, cache memory, upload byte expectation, and artifact budget refusals.
- Cover fixtures for A8 Latin masks, SDF-eligible outlines, SDF transform refusal, color/bitmap/SVG route plans or explicit gates, atlas capacity pressure, and stale atlas generation.
- Keep cache telemetry deterministic: no live texture handles, process object identities, or host paths in dumps.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class GlyphArtifactMetricSample(
    val glyphRunId: GlyphRunDescriptorID,
    val routeCounts: Map<GlyphRepresentationRoute, Int>,
    val a8GenerationMicros: MetricDistribution?,
    val sdfGenerationMicros: MetricDistribution?,
    val colorPlanMicros: MetricDistribution?,
    val bitmapDecodeMicros: MetricDistribution?,
    val svgPlanMicros: MetricDistribution?,
    val diagnostics: List<RouteDiagnostic>,
)

data class GlyphCacheMetricSample(
    val atlasArtifactId: GlyphAtlasArtifactID,
    val strikeKeyCount: Int,
    val cacheHits: Long,
    val cacheMisses: Long,
    val evictions: Long,
    val occupancyRatio: Double,
    val packMicros: MetricDistribution,
    val memoryBytes: Long,
    val staleGenerationRefusals: Int,
)
```

## Acceptance Criteria

- [ ] Each glyph route produces a route count, generation-time series when applicable, and refusal count when unsupported from a non-test glyph-pipeline producer.
- [ ] A8 and SDF metrics are separated in emitted producer data; an A8 cache hit cannot satisfy SDF or `dftext` evidence.
- [ ] Atlas metrics include occupancy, pack time, generation token, stale-generation refusal count, memory bytes, and eviction count in emitted producer data.
- [ ] `GlyphStrikeKey` telemetry exposes deterministic preimage hashes without leaking font bytes or live GPU handles in emitted producer data.
- [x] The `dftext` legacy gate remains open unless SDF contract, atlas/cache telemetry, transform policy, CPU evidence, GPU evidence when claimed, and dashboard updates are all linked.

## Required Evidence

- `glyph-artifact-metrics.json` covering outline, A8, SDF-eligible, SDF-refused, COLR/bitmap/SVG planned or gated routes.
- `glyph-cache-metrics.json` covering hit, miss, eviction, stale generation, atlas capacity pressure, and invalidation token changes.
- `glyph-atlas-occupancy.json` or equivalent dump with stable atlas artifact ID, key hashes, dimensions, entry count, and occupancy ratio.
- Diagnostic snapshots for `text.glyph.SDF-transform-unsupported`, `text.glyph.atlas-capacity-exceeded`, and `text.glyph.atlas-generation-stale`.
- Dashboard trend excerpt with glyph artifact and glyph cache rows, plus `dftext` still visible when not retired.

## Fallback / Refusal Behavior

- Unsupported artifact routes must record route-specific diagnostics rather than being counted as A8 fallback success.
- Atlas overflow may record split/refusal facts; it must not silently drop glyphs or reset occupancy counters.
- Legacy gate(s) `dftext` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected rows: `Glyph artifact metrics`, `Glyph cache metrics`, `Glyph atlas occupancy`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no; cache telemetry supports future gate decisions but is not itself `dftext` support.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.GlyphSurfaceTest.glyphArtifactAndCacheMetricDumpsMatchRepoFixtures
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- `review`: bounded deterministic dump/data structures plus checked-in fixture evidence are freshly revalidated and independently re-reviewed, while wiring a non-test glyph-pipeline producer for these metrics remains the remaining gate before the ticket can close.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M12`
- `area:telemetry`
- `claim:tracked-gap`
- `legacy:dftext`
