---
id: "KFONT-M12-001"
title: "Define font telemetry schema"
status: "review"
milestone: "M12"
priority: "P0"
owner_area: "telemetry"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-004"]
legacy_gate: null
---

# KFONT-M12-001 - Define font telemetry schema

## PM Note

Ce ticket fixe le vocabulaire commun des mesures font/text, pour que les tendances PM comparent les mêmes signaux d'un subsystem à l'autre.

## Problem

M12 needs parser, scaler, shaping, paragraph, glyph, and GPU handoff telemetry, but the catalog does not yet define one shared sample envelope, dimension set, or aggregation policy. Without that schema, each subsystem can emit incompatible timing names or single-run numbers, and dashboard trend warnings cannot distinguish a real regression from a missing metric or host-specific run.

## Scope

- Define the canonical `FontTelemetrySample` envelope for all font/text metrics.
- Define required dimensions: environment, runtime, device or adapter when GPU is involved, font source set, Unicode data version, cache state, fixture ID, sample count, and measurement phase.
- Define typed metric families for parser, scaler, shaping, paragraph, glyph artifact/cache, and GPU handoff samples.
- Define deterministic aggregation fields for repeated samples: median, p90, max, count, warm/cold split, byte counters, memory counters, and diagnostic counters.
- Define stable schema/refusal diagnostics such as `font.telemetry.schema-domain-missing`, `font.telemetry.dimension-missing`, and `font.telemetry.single-run-budget-refused`.
- Provide the dashboard row mapping that keeps indicative budgets advisory until an explicit budget-promotion spec update exists.

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
enum class FontTelemetryDomain {
    Parser,
    Scaler,
    Shaping,
    Paragraph,
    GlyphArtifact,
    GPUTextHandoff,
)

enum class CacheState { Cold, Warm, Mixed }

data class FontTelemetrySample(
    val schemaVersion: Int,
    val domain: FontTelemetryDomain,
    val fixtureId: String,
    val fontSourceSetHash: String,
    val unicodeDataVersion: String,
    val environment: MeasurementEnvironment,
    val cacheState: CacheState,
    val sampleCount: Int,
    val metrics: List<FontMetricSeries>,
    val diagnostics: List<RouteDiagnostic>,
)

data class FontMetricSeries(
    val name: String,
    val unit: MetricUnit,
    val median: Double,
    val p90: Double,
    val max: Double,
    val counters: Map<String, Long>,
)
```

## Acceptance Criteria

- [x] The schema records every measurement dimension required by `08-performance-budgets-and-telemetry.md`, including GPU adapter facts only when GPU metrics are present.
- [x] Metric names are namespaced by domain and cannot mix CPU generation time with GPU upload time in one field.
- [x] A repeated-run fixture serializes median, p90, max, sample count, and cache state; single-run timing cannot be interpreted as a release budget.
- [x] Missing mandatory dimensions produce `font.telemetry.dimension-missing` with the domain, fixture ID, and missing field name.
- [ ] Dashboard trend warnings can ingest one sample from every telemetry domain without changing claim impact from `tracked-gap`.

## Required Evidence

- `font-telemetry-schema.json` with all required domains, metric units, dimension names, and diagnostic codes.
- `font-telemetry-schema-fixture.json` showing one parser, scaler, shaping, paragraph, glyph artifact, and GPU handoff sample.
- Repeated-run dump proving stable key order and stable dimensions for the same fixture inputs.
- Diagnostic snapshot for `font.telemetry.dimension-missing` and `font.telemetry.single-run-budget-refused`.
- `pipelinePerformanceTrendWarnings` or PM bundle excerpt showing the M12 telemetry rows as advisory `tracked-gap` rows.

## Fallback / Refusal Behavior

- Samples that omit required dimensions are refused instead of being normalized with placeholder values.
- Host-specific paths, device names, and timestamps must be normalized or excluded from deterministic dumps.
- Unsupported domains emit `font.telemetry.schema-domain-missing` and keep M12 classified as `tracked-gap`.

## Dashboard Impact

- Expected row: `Font telemetry schema`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no; this ticket enables advisory trend visibility only.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*FontTelemetrySchemaTest*'
rtk ./gradlew --no-daemon validateKfontM12001TelemetryPmEvidence
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.
- `review`: `font-telemetry-schema.json`, `font-telemetry-schema-fixture.json`, and `font-telemetry-pm-bundle.json` now define deterministic cross-domain schema evidence plus advisory `pipelinePmBundle` ingestion, but producer-side subsystem wiring remains open before `done`.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M12`
- `area:telemetry`
- `claim:tracked-gap`
