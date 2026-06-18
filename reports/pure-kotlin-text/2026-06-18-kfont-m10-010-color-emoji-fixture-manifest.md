# KFONT-M10-010 - Color/Emoji Fixture Manifest

Status: done; freshly validated.

## Summary

`KFONT-M10-010` closes the milestone-wide color/emoji fixture convergence gap
without widening any support claim. The new
`reports/font/fixtures/expected/color/color-emoji-fixture-manifest.json` links
the COLRv0/COLRv1, bitmap PNG, SVG glyph, and emoji route families to their
expected dumps, legacy gates, provenance notes, source hashes where applicable,
and the explicit M11 GPU evidence that still remains open.

## Evidence

- `color-emoji-fixture-manifest.json` records four fixture families:
  `color-glyphs`, `png-bitmap-glyphs`, `svg-glyphs`, and `emoji`.
- The manifest cross-links the checked-in component dumps
  `color-glyph-plan.json`, `color-glyph-composite-plan.json`,
  `colrv1-paint-graph.json`, `colrv1-fixture-manifest.json`,
  `bitmap-glyph-plan.json`, `svg-glyph-plan.json`,
  `svg-glyph-fixture-manifest.json`, `emoji-route-trace.json`, and
  `color-svg-emoji-goldens.json` with deterministic body SHA-256 values.
- Legacy gate coverage is explicit for `coloremoji_blendmodes`,
  `scaledemoji`, and `scaledemoji_rendering`, with `gpuEvidenceRequired=true`
  on every row and remaining M11 route-execution gates called out precisely.
- The dump evidence index, fixture evidence manifest, claim dashboard, and
  fixture provenance index all reference the new convergence manifest.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.colorEmojiFixtureManifestConvergesM10FamiliesLegacyGatesAndRemainingGpuEvidence
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk git diff --check
```

## Remaining Gate

This is convergence evidence only. It does not claim GPU color glyph, bitmap,
SVG, or emoji route execution. `coloremoji_blendmodes`, `scaledemoji`, and
`scaledemoji_rendering` remain open until M11 lands reviewed renderer-route and
GPU evidence.
