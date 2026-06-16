# KFONT-M3-003 - Phantom Point Metrics Slice

Status: done.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json`
- `reports/font/fixtures/expected/scaler/truetype-vertical-metrics.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

Evidence:

- `ParsedTrueTypeGlyphScaler.metrics(...)` now applies bounded horizontal phantom-point `gvar` deltas to `advanceX` while keeping default metrics unchanged when no deltas apply.
- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json` now includes a `phantomMetricCase` with min/default/max `glyph-metrics.json`-style facts, phantom-point deltas, a bounded `HVAR`-applied advance snapshot, and a malformed-`HVAR` diagnostic snapshot.
- `reports/font/fixtures/expected/scaler/truetype-vertical-metrics.json` now complements the phantom slice with bounded `MVAR` vertical global-metric deltas and malformed-`MVAR` diagnostics while preserving the existing bounded `VVAR` evidence.
- Focused tests cover direct phantom-delta decoding, adjusted `advanceX`, bounded `HVAR` advance-width application, bounded `MVAR` vertical-metric application, and malformed `HVAR`/`MVAR` diagnostics without broadening layout, shaping, or GPU claims.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests '*Gvar*' --tests '*PhantomPoint*' --tests '*AdvanceDelta*' --tests '*Hvar*' --tests '*Mvar*' --tests '*Vvar*' --tests '*VerticalMetric*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
```

Remaining non-claims:

- The current evidence proves a bounded TrueType metrics-variation slice only; it does not claim complete `HVAR`/`VVAR`/`MVAR` parity, vertical shaping/layout, hinted native parity, or GPU text routing.
