# KFONT-M10-006 - PNG Bitmap Glyph Artifacts

Date: 2026-06-17
Status: done; freshly validated.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-006-promote-png-bitmap-glyph-artifacts.md`

## Scope

This checkpoint promotes embedded PNG bitmap glyphs into deterministic
`BitmapGlyphPlan` evidence for CBDT/CBLC and sbix without claiming GPU upload,
sampling, platform codec support, or emoji sequence readiness.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/bitmap-glyph-plan.json`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-006-promote-png-bitmap-glyph-artifacts.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`

## Evidence

- `BitmapGlyphPlan.fromPNG(...)` now records stable origin placement for bitmap
  glyph fixtures so sbix origin offsets and CBDT/CBLC zero-origin plans land in
  canonical JSON without font-table reads in M11.
- `BitmapGlyphPlan.strikeUnavailableDiagnostic(...)` emits stable
  `text.bitmap.strike-unavailable` refusals with requested size and available
  strike list facts, keeping fallback/gating explicit instead of collapsing
  bitmap misses into generic route absence.
- Checked-in `bitmap-glyph-plan.json` captures five deterministic cases:
  CBDT/CBLC PNG, sbix PNG, unavailable strike refusal, malformed PNG refusal,
  and non-PNG payload refusal. Positive cases include source payload and decoded
  pixel SHA-256 values; refusal cases include canonical route diagnostics.
- Dump index, fixture manifest, and font fixture inventory now attach the new
  dump to the PNG bitmap glyph family as `tracked-gap` evidence only. Claim
  promotion remains blocked on M11 GPU route proof and KFONT-M10-009 emoji
  sequence planning.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.bitmapGlyphPlanBundleCapturesCbdtSbixAndBitmapRefusalsDeterministically
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim JPEG/TIFF/BGRA bitmap support, platform/native
bitmap codec support, emoji sequence planning, GPU bitmap upload/sampling, or
retirement of `scaledemoji_rendering`.
