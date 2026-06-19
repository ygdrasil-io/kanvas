# KFONT-M12-001 - Telemetry PM bundle advisory evidence

## Scope landed

- `pipelinePmBundle` now carries a checked-in advisory telemetry evidence pack
  for `KFONT-M12-001` alongside the existing pure Kotlin text dashboard and
  schema dumps.
- `reports/pure-kotlin-text/font-telemetry-pm-bundle.json` records one advisory
  row for each telemetry domain already covered by
  `font-telemetry-schema-fixture.json`, and the PM bundle now also ships the
  checked-in `parser-metrics.json` and `scaler-metrics.json` telemetry slices.
- The PM evidence remains warning-only and keeps the telemetry row classified
  as `tracked-gap` with `claimPromotionAllowed=false`.

## Evidence

- The PM bundle report points at the checked-in dashboard row
  `font-telemetry-schema` and preserves `pipelinePmBundle` as the packaging
  task instead of inventing a runtime telemetry gate.
- Domain rows cover parser, scaler, shaping, paragraph, glyph artifact, and GPU
  text handoff samples with stable fixture IDs, sample counts, cache states,
  and metric names.
- The report keeps the performance posture advisory/warning-only and points
  downstream producer emission to `KFONT-M12-003`, `KFONT-M12-004`, and
  `KFONT-M12-005`, while `KFONT-M12-002` now contributes the checked-in parser
  and scaler telemetry dumps separately instead of keeping the schema ticket
  open.

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

No schema-local gate remains for `KFONT-M12-001`. Downstream producer emission
into the shared schema is now owned by `KFONT-M12-003`, `KFONT-M12-004`, and
`KFONT-M12-005`; parser/scaler producer evidence is attached separately under
`KFONT-M12-002`. The report stays warning-only, keeps all budgets advisory, and
does not promote any GPU route, release gate, or complete telemetry support
claim.
