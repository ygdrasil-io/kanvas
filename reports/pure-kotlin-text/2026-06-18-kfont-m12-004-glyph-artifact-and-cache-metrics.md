## KFONT-M12-004 - Glyph artifact and cache metrics

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/glyph/glyph-artifact-metrics.json`
- `reports/font/fixtures/expected/glyph/glyph-atlas-occupancy.json`
- `reports/font/fixtures/expected/glyph/glyph-cache-inventory.json`
- `reports/font/fixtures/expected/glyph/glyph-cache-metrics.json`
- `reports/font/fixtures/expected/glyph/glyph-cache-telemetry.json`
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
  `GlyphAtlasOccupancyDump`, and `GlyphCacheMetricsDump` data structures plus
  runtime-sample producers that validate route/hash coherence against
  `GlyphArtifactPlan.decisions` and reject atlas diagnostics that are not
  scoped to the sample glyph set before emitting canonical JSON.
- `GlyphSurfaceTest.glyphArtifactAndCacheMetricDumpsMatchRepoFixtures()` now
  assembles deterministic decision traces plus sample-scoped cold/warm atlas
  diagnostics and asserts byte-identical checked-in dumps for
  `glyph-artifact-metrics.json`, `glyph-atlas-occupancy.json`, and
  `glyph-cache-metrics.json`.
- `glyph-cache-inventory.json` and `glyph-cache-telemetry.json` were refreshed
  in the same wave so the checked-in M9 cache evidence matches the current
  stable strike-key hashes and `nonClaims` used by the reviewed M12 producers.
- `glyph-artifact-metrics.json`, `glyph-atlas-occupancy.json`,
  `glyph-cache-inventory.json`, `glyph-cache-telemetry.json`, and
  `glyph-cache-metrics.json` remain CPU-only checked-in advisory evidence; they
  do not by themselves prove a wired non-test glyph-pipeline producer.
- `font-claim-dashboard.json` now exposes `Glyph artifact metrics`,
  `Glyph atlas occupancy`, and `Glyph cache metrics` as `tracked-gap` advisory
  rows while keeping GPU route claims and `dftext` retirement explicitly open.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py scripts/test_validate_pure_kotlin_text_claim_dashboard.py scripts/test_validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

Review: independent re-review accepted after remediating decision-trace
route/hash validation, sample-scoped atlas diagnostics, stale cache fixture
drift, and earlier overclaim / `nonClaims` coherence issues.

Remaining gate: wiring a non-test glyph-pipeline producer that emits these
metrics remains open. This wave is CPU-only advisory glyph/cache dump/data
evidence; it does not claim GPU upload execution, GPU text route support,
blocking performance gates, or `dftext` retirement; those remain gated on the
M11 GPU handoff chain and explicit promotion evidence.
