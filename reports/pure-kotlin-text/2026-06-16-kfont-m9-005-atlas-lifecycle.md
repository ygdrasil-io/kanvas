# KFONT-M9-005 - Atlas Lifecycle

Date: 2026-06-16
Status: done; freshly validated
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-005-add-atlas-eviction-and-invalidation-tests.md`

## Scope

This checkpoint promotes atlas lifecycle evidence from planning-only stubs to
checked-in CPU dumps: deterministic A8/SDF atlas artifacts, eviction traces,
generation/invalidation facts, source-mask hashes, and explicit stale/capacity
diagnostics before any GPU upload or sampling claim.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/glyph/glyph-atlas.json`
- `reports/font/fixtures/expected/glyph/glyph-atlas-eviction-trace.json`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-005-add-atlas-eviction-and-invalidation-tests.md`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

## Evidence

- `GlyphAtlasArtifactEvidence` now emits deterministic CPU atlas dumps for A8
  and SDF atlas build results, including artifact key hashes, generation,
  dimensions, row stride, entry rects, source bounds, source-mask hashes,
  upload byte hash, budget class, lifetime class, invalidation token, and
  optional SDF distance range.
- `GlyphAtlasEvictionTrace` records eviction order, generation increments,
  invalidation-token changes, resident-byte deltas, and evicted strike-key
  hashes in checked-in `glyph-atlas-eviction-trace.json`.
- Tests now prove that atlas artifact keys rotate when variation, palette,
  renderer descriptor version, SDF spread/source resolution, or source-mask
  facts change, while stale-generation refusal remains an explicit diagnostic.
- Dump index, fixture manifest, fixture inventory, and claim dashboard now
  expose CPU atlas lifecycle evidence while keeping GPU upload/sampling and
  `dftext` retirement gated on M11.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests '*Atlas*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim WebGPU texture allocation, bind groups, upload
execution, WGSL sampling validation, renderer atlas ownership, or `dftext`
retirement. The next gates remain `KFONT-M9-006` for cache telemetry and the
M11 GPU handoff chain for any GPU atlas/SDF claim.
