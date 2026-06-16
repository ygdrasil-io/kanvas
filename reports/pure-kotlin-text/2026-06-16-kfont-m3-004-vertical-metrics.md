# KFONT-M3-004 - TrueType Vertical Metrics Slice

Status: done.

Files:

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`
- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json`
- `reports/font/fixtures/expected/scaler/truetype-vertical-metrics.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

Evidence:

- `OpenTypeMetricsTableParser` now parses bounded optional `vhea` and `vmtx`
  facts into `MetricsTables`, including vertical ascender/descender/line gap,
  `advanceHeightMax`, `numberOfVMetrics`, and per-glyph vertical metrics.
- `DefaultOpenTypeFaceParser` preserves horizontal metrics when optional
  vertical tables are malformed and records
  `font.sfnt.optional-table-malformed` diagnostics instead of dropping the
  entire metric slice.
- `TrueTypeGlyfScaler.scaledGlyphEvidence(...)` now emits vertical metric facts
  in `glyph-metrics.json` with explicit `present`, `fallback`, and
  `diagnostic` states, plus stable absent/malformed diagnostics.
- `reports/font/fixtures/expected/scaler/truetype-vertical-metrics.json`
  records positive `vhea`/`vmtx` evidence, explicit absent-table fallback
  evidence, bounded `VVAR` advance-height deltas, bounded `MVAR` vertical
  global-metric deltas, plus malformed `VVAR` and `MVAR` diagnostic snapshots.
- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json` now shows the
  explicit vertical fallback state on the bounded variation dump that still has
  no vertical tables.

Validation:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test
rtk ./gradlew --no-daemon :font:scaler:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate:

- This slice proves bounded vertical metric extraction and diagnostics only.
- It does not claim vertical shaping, vertical substitution, line layout,
  paragraph layout, complete `HVAR`/`VVAR`/`MVAR` parity, native scaler parity, or GPU
  text routing.
