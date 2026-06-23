# KFONT-M6-002 - GSUB Single/Multiple/Ligature Lookup Slice

## Scope

This wave closes the remaining `review` gate for bounded simple GSUB support:

- `font/sfnt` still parses LookupType 1 single substitution, LookupType 2
  multiple substitution, and LookupType 4 ligature substitution into
  `OpenTypeLayoutTables.gsub`.
- `font/text` still applies those parsed lookups during basic shaping while
  preserving cluster ranges for one-to-one, one-to-many, and many-to-one
  substitutions.
- Reviewed fixture provenance is now checked in for
  `gsub-single-substitution.otf`, `gsub-multiple-substitution.otf`,
  `gsub-ligature-fi.otf`, `gsub-coverage-malformed.otf`, and
  `gsub-ligature-bad-component.otf`.
- `gsub-trace.json` and `shaped-glyph-run.json` are now promoted from the
  old contract-only placeholders to fixture-backed Latin evidence.

## Validation

Fresh validations for this wave:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedGsubFixtureFontsFromRepo --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserReportsReviewedMalformedGsubFixtureFontsAsDiagnostics
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedGsubFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gsubTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapedGlyphRunGoldenPinsFixtureBackedGsubAndGposRuns
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Outcome

- GSUB fixture provenance is now tracked in
  `reports/font/fixtures/provenance/index.json`.
- Parser tests now prove the checked-in positive and malformed GSUB fixture
  fonts rather than synthetic-only lookup tables.
- Runtime tests now prove the checked-in GSUB fixture glyph IDs and cluster
  mappings.
- `gsub-trace.json` now records fixture-backed lookup order and malformed
  parser refusals for this bounded Latin slice.
- `shaped-glyph-run.json` now links the GSUB output cases to the shared
  GSUB/GPOS shaped-run dump.

## Non-Claims

This wave still does not claim complete GSUB support, contextual lookups, full
default feature policy, Greek/Cyrillic/Hebrew readiness promotion, complex
script shaping, native shaper parity, CPU oracle evidence, or GPU evidence.
