# KFONT-M9-002 - GlyphArtifactPlan Route Taxonomy

Date: 2026-06-16
Status: done; freshly validated after independent review findings were remediated
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-002-promote-glyphartifactplan-route-taxonomy.md`

## Scope

This checkpoint promotes `GlyphArtifactPlan` from a generic route chooser to a
typed decision trace that records policy inputs, placeholder plan refs,
fallback/refusal reasons, and route-scoped non-claims before A8/SDF production,
M10 color payload work, or M11 GPU handoff.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/glyph/glyph-artifact-plan.json`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-002-promote-glyphartifactplan-route-taxonomy.md`
- `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`

## Evidence

- `GlyphArtifactRouteRequest` now carries stable `policyInputs` and explicit
  per-glyph route diagnostics without allocating GPU resources or parsing M10
  payload formats.
- `GlyphArtifactPlanDecision` records typed placeholder refs for COLR, bitmap,
  and SVG routes, `CPUPreparedGPU` artifact intent for A8/SDF routes, stable
  rejected-alternative reason codes, and explicit unsupported decisions.
- `GlyphArtifactRoutePlanner` preserves explicit refusals for
  `text.glyph.SDF-transform-unsupported`,
  `text.glyph.atlas-capacity-exceeded`,
  `text.glyph.outline-unavailable`, and
  `text.glyph.LCD-future-research` instead of collapsing them into generic
  route-unavailable fallback.
- `glyph-artifact-plan.json` is checked in with outline, A8, SDF, color
  placeholder, bitmap placeholder, SVG placeholder, LCD refusal, outline
  refusal, and unsupported-route evidence.
- Dump index, fixture manifest, and claim dashboard now expose the taxonomy as
  `tracked-gap` route-planning evidence only, with claim promotion still
  disabled until A8/SDF generation, atlas lifecycle, and GPU handoff tickets
  land.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests '*GlyphArtifactPlan*'
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.GlyphSurfaceTest
rtk ./gradlew --no-daemon :font:glyph:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim COLR/bitmap/SVG payload parsing, production A8
rasterization quality, production SDF quality, atlas lifecycle support, GPU
text handoff, LCD text support, or `dftext` retirement. Next gates remain
`KFONT-M9-003`, `KFONT-M9-004`, and `KFONT-M9-005`.
