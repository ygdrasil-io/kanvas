# KFONT-M3-003 - Phantom Point Metrics Slice

Status: review.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

Evidence:

- `ParsedTrueTypeGlyphScaler.metrics(...)` now applies bounded horizontal phantom-point `gvar` deltas to `advanceX` while keeping default metrics unchanged when no deltas apply.
- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json` now includes a `phantomMetricCase` with min/default/max `glyph-metrics.json`-style facts, phantom-point deltas, and a `TrueTypeGlyfScaler` warning snapshot when `HVAR` bytes are present but still unimplemented.
- Focused tests cover direct phantom-delta decoding, adjusted `advanceX`, removal of the stale `truetype.phantom-metrics-unavailable` warning when the phantom slice is resolved, and the retained `font.metrics-variation-unavailable` warning for faces that still expose raw `HVAR` data.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests '*Gvar*' --tests '*PhantomPoint*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
```

Remaining gate:

- `HVAR`/`VVAR`/`MVAR` parsing and application are still unimplemented.
- The current evidence proves the bounded horizontal phantom-point `gvar` slice only; it does not claim full metrics-variation parity, vertical metrics, hinted native parity, or GPU text routing.
