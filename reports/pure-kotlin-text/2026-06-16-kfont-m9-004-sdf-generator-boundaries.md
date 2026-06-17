# KFONT-M9-004 - SDF Generator Boundaries

Date: 2026-06-16
Status: done; freshly validated
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-004-implement-production-sdf-generator-boundaries.md`

## Scope

This checkpoint hardens the CPU SDF producer contract before any atlas, GPU,
or `dftext` claim promotion: closed-outline eligibility, stable spread/source
resolution facts, deterministic padding and bounds, checked-in golden dump
evidence, and refusal coverage that keeps unsupported transforms and LCD
requests explicit.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/glyph/sdf-glyph-artifact.json`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-004-implement-production-sdf-generator-boundaries.md`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

## Evidence

- `generateLinearOutlineSDF(...)` now derives the SDF distance range from
  `GlyphStrikeKey.sdfSpreadPx` or the stabilized default spread `8f`, preserves
  bounds/origin facts on empty and non-empty outputs, and records
  `sourceOutlineSha256` on CPU SDF masks.
- The SDF contour parser now requires closed contours terminated by `Z` for the
  SDF route only, turning malformed open contours into deterministic
  `text.glyph.SDF-generation-failed` evidence instead of silently reusing A8
  contour rules.
- `SDFGlyphArtifactEvidence` and `SDFGlyphArtifactEvidenceDump` emit checked-in
  `sdf-glyph-artifact.json` evidence for a default-spread and widened-spread
  outline, including spread, source resolution, atlas padding
  `ceil(spreadPx) + 1`, normalization formula version, addressable pixel count,
  distance-field SHA-256, source-outline SHA-256, and stable dump hashes.
- Focused tests cover the 0.5-edge normalization behavior, per-strike spread
  overrides, non-closed contour refusal, and the checked-in SDF dump contract.
- Dump index, fixture manifest, fixture inventory, and claim dashboard now
  expose this as CPU-only SDF artifact evidence while keeping atlas lifecycle,
  GPU handoff, LCD promotion, and `dftext` retirement as explicit remaining
  non-claims.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests '*SDF*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim atlas lifecycle support, GPU sampling or WGSL
reconstruction, GPU text-route handoff, LCD support promotion, unsupported
color-glyph SDF production, or `dftext` retirement. The next gates remain
`KFONT-M9-005` for atlas/cache lifecycle evidence and the M11 GPU handoff
ticket chain for any GPU SDF claim.
