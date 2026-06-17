# KFONT-M9-003 - A8 Outline Rasterization

Date: 2026-06-16
Status: done; freshly validated
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-003-implement-quadratic-cubic-outline-rasterization-for-a8.md`

## Scope

This checkpoint promotes the CPU A8 route from rectangle-only line coverage to
deterministic outline rasterization for quadratic and cubic contours, with
stable refusal diagnostics, source outline hashes, and checked-in
`a8-glyph-mask.json` evidence.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/glyph/a8-glyph-mask.json`
- `reports/font/fixtures/expected/glyph/glyph-artifact-plan.json`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-003-implement-quadratic-cubic-outline-rasterization-for-a8.md`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`

## Evidence

- `GlyphMaskGenerator` now flattens quadratic and cubic contours into
  deterministic strike-space edges before A8 rasterization, preserving stable
  bounds, row stride, origin, and coverage-byte hashes.
- Empty outlines, malformed contours, unsupported fill rules, and coverage
  overflow now return stable empty A8 masks with
  `text.glyph.A8-generation-failed` diagnostics instead of propagating raw
  exceptions.
- `A8GlyphMask` and `A8GlyphMaskArtifactEvidence` now carry
  `sourceOutlineSha256` plus stable diagnostic snapshots, so checked-in dumps
  can link CPU mask bytes back to outline facts.
- `A8GlyphMaskEvidenceDump` produces checked-in `a8-glyph-mask.json` evidence
  for quadratic, cubic, composite-derived, empty `.notdef`, malformed, and
  unsupported-fill fixtures.
- `GlyphArtifactPlanDecision` now records `sourceRepresentationSha256`, linking
  `glyph-artifact-plan.json` A8 route entries to their source representation
  hashes without claiming atlas lifecycle or GPU handoff.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests '*A8*'
rtk ./gradlew --no-daemon :font:glyph:test --tests '*GlyphArtifactPlan*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim SDF quality, atlas lifecycle support, GPU text
handoff, LCD subpixel support, external rasterizer parity, or `dftext`
retirement. Next gates remain `KFONT-M9-004` and `KFONT-M9-005`.
