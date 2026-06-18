## KFONT-M12-004 - Glyph artifact and cache metrics

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/glyph/glyph-artifact-metrics.json`
- `reports/font/fixtures/expected/glyph/glyph-atlas-occupancy.json`
- `reports/font/fixtures/expected/glyph/glyph-cache-metrics.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `.upstream/specs/pure-kotlin-text/tickets/M12-performance-telemetry/KFONT-M12-004-add-glyph-artifact-and-cache-metrics.md`
- `.upstream/specs/pure-kotlin-text/tickets/M12-performance-telemetry/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

Evidence:

- `GlyphSurface.kt` now exposes deterministic `GlyphArtifactMetricsDump`,
  `GlyphAtlasOccupancyDump`, and `GlyphCacheMetricsDump` data structures with
  canonical JSON writers and fail-fast occupancy validation for impossible
  atlas states.
- `GlyphSurfaceTest.glyphArtifactAndCacheMetricDumpsMatchRepoFixtures()` now
  assembles deterministic cold/warm dump samples and asserts byte-identical
  checked-in dumps for `glyph-artifact-metrics.json`,
  `glyph-atlas-occupancy.json`, and `glyph-cache-metrics.json`.
- `glyph-artifact-metrics.json`, `glyph-atlas-occupancy.json`, and
  `glyph-cache-metrics.json` remain CPU-only checked-in advisory evidence; they
  do not by themselves prove a wired non-test glyph-pipeline producer.
- `font-claim-dashboard.json` now exposes `Glyph artifact metrics`,
  `Glyph atlas occupancy`, and `Glyph cache metrics` as `tracked-gap` advisory
  rows while keeping GPU route claims and `dftext` retirement explicitly open.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.GlyphSurfaceTest.glyphArtifactAndCacheMetricDumpsMatchRepoFixtures
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk git diff --check
```

Review: independent re-review accepted after remediating overclaim wording,
`nonClaims` coherence, occupancy invariants, and budget-refusal hash
coherence.

Remaining gate: wiring a non-test glyph-pipeline producer that emits these
metrics remains open. This wave is CPU-only advisory glyph/cache dump/data
evidence; it does not claim GPU upload execution, GPU text route support,
blocking performance gates, or `dftext` retirement; those remain gated on the
M11 GPU handoff chain and explicit promotion evidence.
