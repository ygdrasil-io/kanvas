# KFONT-M3-003 - Phantom Point Metrics Slice

Status: done.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

Evidence:

- `TrueTypeGlyfScaler.metrics(...)` now returns the same variation-adjusted
  `GlyphMetrics` surfaced by `scaledGlyphEvidence(...)`, so the direct metrics
  API and the checked-in evidence route stay aligned for phantom and metrics
  variation slices.
- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json` now includes
  `phantomMetricCase`, `hvarAdvanceCase`, and `mvarVerticalCase`, plus
  `malformedHvar` and `malformedMvar` diagnostic snapshots. The old
  `HVAR`-unimplemented warning snapshot is retired.
- Focused tests cover bounded `HVAR` advance-width deltas, malformed `HVAR`
  fallback with stable diagnostics, bounded `MVAR` vertical-global metric
  deltas, malformed `MVAR` fallback with stable diagnostics, and the refreshed
  golden dump. Bounded `VVAR` advance-height evidence remains checked in under
  `reports/font/fixtures/expected/scaler/truetype-vertical-metrics.json`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.truetypeGvarIupGoldenMatchesGeneratedEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.trueTypeGlyfEvidenceAppliesHvarAdvanceWidthDeltas --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.trueTypeGlyfEvidenceReportsMalformedHvarWithoutDroppingBaseMetrics --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.trueTypeGlyfEvidenceAppliesMvarVerticalMetricDeltas --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.trueTypeGlyfEvidenceReportsMalformedMvarWithoutDroppingBaseVerticalMetrics
rtk ./gradlew --no-daemon :font:scaler:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

Remaining gate:

- This slice now proves bounded phantom-point `gvar`, `HVAR`, and `MVAR`
  behavior, with bounded `VVAR` evidence covered by the companion
  `KFONT-M3-004` vertical-metrics slice.
- It does not claim complete variable-font parity, complete `HVAR`/`VVAR`/
  `MVAR` parity beyond the bounded checked-in fixtures, vertical shaping or
  layout, hinted native parity, or GPU text routing.
