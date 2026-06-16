# KFONT-M9-006 - Glyph Cache Telemetry

Date: 2026-06-16
Status: done; freshly validated
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-006-add-glyph-cache-telemetry.md`

## Scope

This checkpoint promotes glyph cache observability from a milestone note to
checked-in CPU evidence: deterministic resident/evicted inventory rows,
cold/warm advisory telemetry samples, stable cache-domain budget refusals, and
an explicit non-claim that these counters do not become hidden release gates.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/glyph/glyph-cache-inventory.json`
- `reports/font/fixtures/expected/glyph/glyph-cache-telemetry.json`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-006-add-glyph-cache-telemetry.md`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`
- `reports/pure-kotlin-text/font-diagnostic-taxonomy.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

## Evidence

- `GlyphCacheInventoryDump` now records resident and evicted cache entries with
  artifact type, glyph id, stable key preimages, preimage hashes,
  route-specific strike-key hashes, resident bytes, generation, and
  invalidation tokens.
- `GlyphCacheTelemetryDump` records cold and warm advisory samples with route
  counts, cache hit/miss, eviction/invalidation counters, resident bytes,
  upload-preparation bytes, fixture timing summaries, and cache-domain budget
  refusal facts.
- `GlyphRouteDiagnostic.telemetryUnavailable(...)` now exposes the stable
  `text.glyph.telemetry-unavailable` refusal for missing telemetry collection
  without fabricating support or promoting a gate.
- Dump index, fixture manifest, fixture inventory, dashboard, and diagnostic
  taxonomy now expose cache telemetry as CPU-only advisory evidence while
  keeping GPU upload execution and `dftext` retirement gated elsewhere.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests '*GlyphCache*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim runtime GPU upload execution, WebGPU timing,
blocking performance budgets, renderer resource ownership, or `dftext`
retirement. The next gates remain the M11 GPU handoff chain for GPU text
claims and M12 if advisory cache metrics are ever promoted toward product
budget policy.
