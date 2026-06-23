# KFONT-M10-009 - Emoji Sequence Planner

Date: 2026-06-17
Status: done; freshly validated.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-009-implement-emoji-sequence-planner.md`

## Scope

This checkpoint promotes bounded pure Kotlin emoji route planning from shaped
sequence facts into deterministic route-trace evidence without claiming
platform emoji engines, complete emoji shaping, or GPU rendering support.

## Files

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/ShapingTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/emoji-route-trace.json`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-009-implement-emoji-sequence-planner.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

## Evidence

- `EmojiSequenceShaper.sequenceFacts(...)` now classifies variation-selector,
  skin-tone, ZWJ, keycap, flag, and unsupported emoji sequence shapes with
  deterministic UTF-16 text ranges and code-point lists.
- `SimpleEmojiSequencePlanner.plan(...)` now emits `EmojiRouteTrace` with
  selected typeface/family facts, selected route, monochrome-fallback flag,
  fallback attempts, optional evidence refs, and stable refusal diagnostics.
- Checked-in `emoji-route-trace.json` covers variation-selector to COLR,
  skin-tone to bitmap, represented skin-tone role ZWJ to COLR, ZWJ family to
  outline fallback, keycap to PNG, flag to SVG, fallback-unavailable,
  color-glyph-unavailable, and
  unsupported-sequence cases.
- Dump index, fixture manifest, and font fixture inventory now classify this as
  `tracked-gap` CPU-side route-trace evidence only, keeping
  `scaledemoji`, `KFONT-M10-010`, and M11 GPU route proof explicit.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.emojiSequenceShaperRecognizesKeycapAndFlagFixturesAsSingleClusters --tests org.graphiks.kanvas.text.TextStackSurfaceTest.emojiSequenceShaperExposesTypedFactsForPlannerSequenceKinds --tests org.graphiks.kanvas.text.TextStackSurfaceTest.emojiSequenceShaperDumpsVS15SkinToneAndZwjFamilyFixtures --tests org.graphiks.kanvas.text.TextStackSurfaceTest.emojiSequenceShaperKeepsRepresentedSkinToneRoleSequenceAsZwj
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.emojiSequencePlannerGoldenMatchesRepoFixture --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.exposesColorGlyphPipelineSurface
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim complete emoji shaping support, complete
color-glyph fallback support, platform emoji-engine parity, GPU emoji route
support, or retirement of the milestone-wide color/emoji fixture convergence
gate owned by KFONT-M10-010.
