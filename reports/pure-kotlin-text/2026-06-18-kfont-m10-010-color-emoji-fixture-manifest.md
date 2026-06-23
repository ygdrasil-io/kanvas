### KFONT-M10-010: Add color/emoji fixture manifest

Status: done; freshly validated.

Files:

- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/color-emoji-fixture-manifest.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_color_emoji_fixture_manifest.py`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_color_emoji_fixture_manifest.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-010-add-color-emoji-fixture-manifest.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `color-emoji-fixture-manifest.json` now checks in 39 deterministic rows that
  converge the M10 COLRv0, COLRv1, bitmap PNG, SVG, and emoji evidence into a
  single provenance-aware manifest.
- Each row records fixture family, font source or synthetic provenance,
  license note, source hash, generated source recipe when applicable, expected
  route, expected diagnostics, expected dump files, linked legacy gates, and
  whether adapter-backed GPU evidence is still required.
- Legacy gates `scaledemoji`, `scaledemoji_rendering`, and
  `coloremoji_blendmodes` now map to specific fixture IDs with explicit
  remaining-evidence text, so CPU/text-only evidence cannot retire them.
- The rebaseline policy now requires reviewed old/new manifest diffs, linked
  dump diffs, and a stated reason before any golden update is checked in.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.ColorEmojiFixtureManifestMatchesRepoFixture
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_color_emoji_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_color_emoji_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate: this manifest is coordination and CPU/text fixture evidence
only. It does not claim GPU COLR/bitmap/SVG/emoji execution, platform emoji
parity, or retirement of `scaledemoji`, `scaledemoji_rendering`, or
`coloremoji_blendmodes`; those remain gated on M11 adapter-backed GPU proof.
