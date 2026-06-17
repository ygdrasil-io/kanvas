# KFONT-M10-005 - Add COLRv1 recursion, cycle and bounds fixtures

Status: implemented with fresh deterministic fixture-manifest evidence and validation.

Files:

- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-005-add-colrv1-recursion-cycle-and-bounds-fixtures.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/colrv1-fixture-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `reports/font/fixtures/expected/color/colrv1-fixture-manifest.json` now records bounded positive COLRv1 bounds fixtures for nested glyph/COLR glyph, transform, and composite/clip coverage, plus refusal fixtures for cycle, recursion depth, expanded paint count, and malformed paint offsets.
- `ColorGlyphSurfaceTest` serializes the manifest deterministically from synthetic fixture provenance and stable diagnostic snapshots, including `text.color.COLRv1-cycle-detected` and `text.color.COLRv1-budget-exceeded` facts without promoting new runtime operation support.
- `reports/pure-kotlin-text/dump-evidence-index.json`, `reports/pure-kotlin-text/fixture-evidence-manifest.json`, and `reports/pure-kotlin-text/font-fixture-inventory.json` attach the new fixture-manifest dump to the `color-glyphs` family while keeping `coloremoji_blendmodes` open.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.colrv1FixtureManifestRecordsBoundsRoutesAndTraversalRefusals
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

Remaining gate: this is fixture provenance and refusal-evidence work only. It does not claim broader COLRv1 rendering support, dedicated parser diagnostics for malformed offsets beyond parse-null refusal, GPU composite execution, bitmap/SVG routing, emoji sequence planning, or platform/native fallback behavior.
