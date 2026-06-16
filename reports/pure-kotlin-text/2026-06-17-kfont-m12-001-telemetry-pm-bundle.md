# KFONT-M12-001 - Telemetry PM bundle advisory evidence

## Scope landed

- `pipelinePmBundle` now carries a checked-in advisory telemetry evidence pack
  for `KFONT-M12-001` alongside the existing pure Kotlin text dashboard and
  schema dumps.
- `reports/pure-kotlin-text/font-telemetry-pm-bundle.json` records one advisory
  row for each telemetry domain already covered by
  `font-telemetry-schema-fixture.json`.
- The PM evidence remains warning-only and keeps the telemetry row classified
  as `tracked-gap` with `claimPromotionAllowed=false`.

## Evidence

- The PM bundle report points at the checked-in dashboard row
  `font-telemetry-schema` and preserves `pipelinePmBundle` as the packaging
  task instead of inventing a runtime telemetry gate.
- Domain rows cover parser, scaler, shaping, paragraph, glyph artifact, and GPU
  text handoff samples with stable fixture IDs, sample counts, cache states,
  and metric names.
- The report keeps the performance posture advisory/warning-only and leaves
  producer-side wiring explicit as the remaining blocker before `done`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontTelemetrySchemaTest*'
rtk python3 scripts/validate_kfont_m12_001_telemetry_pm_evidence.py
rtk ./gradlew --no-daemon validateKfontM12001TelemetryPmEvidence
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining gate

This evidence clears the PM bundle ingestion gap only. `KFONT-M12-001` remains
in `review` until parser, scaler, shaping, paragraph, glyph artifact, and GPU
handoff producers emit the shared schema directly. The report stays
warning-only, keeps all budgets advisory, and does not promote any GPU route,
release gate, or complete telemetry support claim.
