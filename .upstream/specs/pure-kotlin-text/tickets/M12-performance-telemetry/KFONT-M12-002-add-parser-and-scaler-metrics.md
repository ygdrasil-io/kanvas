---
id: "KFONT-M12-002"
title: "Add parser and scaler metrics"
status: "proposed"
milestone: "M12"
priority: "P1"
owner_area: "telemetry"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M12-001", "KFONT-M2-004", "KFONT-M3-005", "KFONT-M4-004"]
legacy_gate: null
---

# KFONT-M12-002 - Add parser and scaler metrics

## PM Note

Ce ticket rend visibles les coûts de lecture des fontes et de génération des outlines, sans confondre performance et support fonctionnel.

## Problem

Parser and scaler work is currently validated by semantic dumps, but M12 also needs stable cost signals: bytes read, table cache behavior, malformed table isolation, glyph path extraction, variation application, and cache hits/misses. Without these metrics, a parser regression can hide behind passing fixtures, and a scaler optimization can accidentally mask malformed or unsupported glyph behavior.

## Scope

- Emit parser metrics for source scan time, parse time, bytes read, table count, table cache hit/miss, malformed table count, bounds failures, and table-specific diagnostic count.
- Emit scaler metrics for glyphs scaled, bounds lookup time, metrics lookup time, outline command count, variation application time, CFF/CFF2 charstring time, scaler cache hit/miss, and `.notdef` fallback count.
- Cover deterministic fixture groups for single-face TTF, TTC, OTF/CFF, CFF2 or CFF2-gated fixtures, variable font axes, malformed directory, malformed `loca`/`glyf`, and missing required tables.
- Use the schema from KFONT-M12-001, including cold/warm cache state and repeated-sample aggregation.
- Keep external engines out of normative measurements; optional drift reports may compare parser/scaler costs only as non-product context.

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
data class ParserMetricSample(
    val typefaceId: TypefaceID,
    val fixtureId: String,
    val bytesRead: Long,
    val tablesParsed: Int,
    val malformedTables: Int,
    val tableCacheHits: Long,
    val tableCacheMisses: Long,
    val parseTimeMicros: MetricDistribution,
    val diagnostics: List<RouteDiagnostic>,
)

data class ScalerMetricSample(
    val typefaceId: TypefaceID,
    val glyphsScaled: Int,
    val outlineCommandCount: Int,
    val boundsLookupMicros: MetricDistribution,
    val metricsLookupMicros: MetricDistribution,
    val variationApplyMicros: MetricDistribution?,
    val scalerCacheHits: Long,
    val scalerCacheMisses: Long,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Parser samples expose bytes read, parsed table tags, cache hits/misses, malformed table counts, and parse-time distributions per fixture.
- [ ] Scaler samples expose glyph count, outline command count, glyph bounds/metrics lookup time, variation application time when applicable, and scaler cache hits/misses.
- [ ] Malformed fixtures still emit semantic diagnostics such as bounds/offset failures; telemetry never converts a malformed table into a support claim.
- [ ] Cold and warm cache runs are serialized separately and aggregate at least median, p90, max, and sample count.
- [ ] The dashboard can show parser and scaler rows independently, with no parser metric used as evidence for scaler support or vice versa.

## Required Evidence

- `parser-metrics.json` for TTF, TTC, OTF/CFF, variable, malformed directory, and missing-table fixtures.
- `scaler-metrics.json` for simple `glyf`, composite `glyf`, variable `glyf`, CFF, and CFF2 or CFF2-gated fixtures.
- Repeated cold/warm run samples showing stable dimensions and stable fixture IDs.
- Diagnostic snapshots for malformed table isolation and scaler `.notdef` or refusal paths.
- Trend report excerpt with separate `font.parser.*` and `font.scaler.*` series.

## Fallback / Refusal Behavior

- Metrics collection refuses unsupported scaler kinds with `font.telemetry.scaler-domain-missing` or a narrower scaler diagnostic.
- Missing parser/scaler metrics do not default to zero; absent counters are schema failures.
- Native parser or native scaler timings are not accepted as substitute evidence.

## Dashboard Impact

- Expected rows: `Font parser metrics`, `Font scaler metrics`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no; these rows make regressions visible but do not promote parser/scaler support by themselves.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M12`
- `area:telemetry`
- `claim:tracked-gap`
