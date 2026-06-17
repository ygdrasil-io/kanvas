# KFONT-M10-007 - Bounded SVG Glyph Renderer Primitives

Date: 2026-06-17
Status: done; freshly validated.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-007-implement-bounded-svg-glyph-renderer-primitives.md`

## Scope

This checkpoint promotes a pure Kotlin, glyph-scoped, bounded `SVGGlyphPlan`
contract for static SVG-in-OpenType primitives without claiming dynamic SVG,
GPU vector routing, or native SVG renderer parity.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/svg-glyph-plan.json`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-007-implement-bounded-svg-glyph-renderer-primitives.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`

## Evidence

- `SVGGlyphPlan.fromDocument(...)` now promotes bounded parser output into a
  checked-in plan with source SHA-256, viewBox, bounds, primitive summaries,
  resource-limit facts, diagnostics, and dump hash.
- The bounded parser summary subset now includes `defs`, `g`, `symbol`,
  `linearGradient`, `radialGradient`, `clipPath`, and bounded `use`, alongside
  existing path/basic-shape support, all without invoking a native SVG engine.
- Checked-in `svg-glyph-plan.json` captures static path/basic-shape,
  gradient/transform/clip, defs/symbol/bounded-use/radial-gradient, and
  external-resource, unsupported-feature, path-command-budget,
  gradient-stop-budget, plus use-recursion refusal cases.
- Dump index, fixture manifest, and font fixture inventory now attach the new
  dump to the SVG glyph family as `tracked-gap` evidence only. KFONT-M10-008
  still owns the expanded refusal/bounds fixture corpus, and M11 still owns any
  GPU SVG route claim.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.svgGlyphPlanBundleCapturesSupportedPrimitivesAndRefusalsDeterministically
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim dynamic SVG support, native/platform SVG
fallback behavior, actual `use` graph expansion rendering, GPU SVG route
support, or retirement of any legacy renderer gate.
