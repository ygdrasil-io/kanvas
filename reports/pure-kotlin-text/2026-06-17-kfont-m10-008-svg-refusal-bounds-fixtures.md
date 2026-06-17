# KFONT-M10-008 - SVG Refusal Classes And Bounds Fixtures

Date: 2026-06-17
Status: done; freshly validated.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-008-implement-svg-glyph-refusal-classes-and-bounds-fixtures.md`

## Scope

This checkpoint hardens the bounded pure Kotlin SVG-in-OpenType slice with
deterministic positive bounds fixtures and explicit refusal classes without
claiming dynamic SVG, native renderer parity, or GPU vector routing.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/svg-glyph-fixture-manifest.json`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-008-implement-svg-glyph-refusal-classes-and-bounds-fixtures.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `scripts/validate_pure_kotlin_text_font_fixtures.py`
- `scripts/test_validate_pure_kotlin_text_font_fixtures.py`

## Evidence

- `BasicSVGGlyphParser.documentMalformedDiagnostic(...)` now emits stable
  `text.SVG.document-malformed` evidence using glyph ID, source SHA-256,
  failure class, and failure message while preserving the bounded parser as the
  only normative source.
- Checked-in `svg-glyph-fixture-manifest.json` records positive bounds hashes
  for static-path, gradient/transform/clip, and defs/symbol/bounded-use cases.
- The same manifest records explicit refusal rows for script,
  external-resource, network-reference, animation, filter, `foreignObject`,
  embedded-text, unsupported CSS selector, malformed-document/path-data, and
  budget overflow categories with expected route `refusal`, expected diagnostic
  codes, expected dump files, and fixture provenance.
- Dump index, fixture manifest, and font fixture inventory now classify SVG
  glyph evidence as `fixture-gated`, making the remaining non-claims explicit
  instead of leaving them implicit in the older `svg-glyph-plan.json` dump
  alone.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsSVGDocumentMalformedDiagnostic
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.svgGlyphFixtureManifestCapturesBoundsRefusalsAndProvenanceDeterministically
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_font_fixtures.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk git diff --check
```

## Remaining Gate

This checkpoint does not claim dynamic SVG support, native/platform SVG
fallback behavior, complete SVG rendering support, GPU SVG route support, or
retirement of the milestone-wide color/emoji fixture convergence gate owned by
KFONT-M10-010.
