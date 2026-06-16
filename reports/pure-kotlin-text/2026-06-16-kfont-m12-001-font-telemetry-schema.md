# KFONT-M12-001 - Font telemetry schema

## Scope landed

- `font/core` now exposes a deterministic telemetry schema surface through
  `FontTelemetryDomain`, `FontTelemetryUnit`, `FontTelemetryCacheState`, and
  `FontTelemetryEvidenceWriter`.
- Two checked-in canonical dumps now anchor the bounded M12 slice:
  `reports/pure-kotlin-text/font-telemetry-schema.json` and
  `reports/pure-kotlin-text/font-telemetry-schema-fixture.json`.
- The fixture dump covers one repeated-run advisory sample for parser, scaler,
  shaping, paragraph, glyph artifact, and GPU text handoff, plus stable refusal
  cases for missing dimensions and single-run budget misuse.

## Evidence

- The schema records shared measurement dimensions, domain-specific metric
  namespaces, GPU-only adapter/backend fields, stable aggregation fields, and
  stable telemetry refusal codes.
- The fixture dump proves deterministic key ordering, repeated-run median/p90
  /max aggregation, advisory-only cache-state semantics, and conditional GPU
  adapter facts for the GPU handoff domain only.
- `FontTelemetrySchemaTest` asserts byte-identical checked-in dumps and verifies
  the required domains plus stable telemetry refusal diagnostics without
  HarfBuzz or FreeType wording.
- Advisory PM bundle ingestion is now captured separately in
  `reports/pure-kotlin-text/font-telemetry-pm-bundle.json` and
  `reports/pure-kotlin-text/2026-06-17-kfont-m12-001-telemetry-pm-bundle.md`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontTelemetrySchemaTest*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining gate

This slice keeps `KFONT-M12-001` in `review`, not `done`. The repo still lacks
producer-side wiring from parser/scaler/shaping/paragraph/glyph/GPU subsystems
into this schema. No performance budget, GPU route, or release-gate claim is
promoted by this evidence.
