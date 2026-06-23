# KFONT-M6-004 - GPOS Single/Pair Positioning Closeout

## Scope

This wave closes the remaining `review` gate for bounded simple GPOS support:

- `font/sfnt` still parses the bounded LookupType 1 single-position and
  LookupType 2 pair-position subsets needed by the Latin fixtures.
- `font/text` still applies `xPlacement`, `yPlacement`, and `xAdvance`
  adjustments for those parsed subsets while preserving cluster ownership.
- Reviewed fixture provenance is now checked in for
  `gpos-single-adjustment.otf`, `gpos-pair-format1-kerning.otf`,
  `gpos-pair-format2-class.otf`, `gpos-valueformat-malformed.otf`, and
  `gpos-pair-out-of-range.otf`.
- `gpos-trace.json` and the shared `shaped-glyph-run.json` are now promoted
  from the contract-only placeholders to fixture-backed Latin evidence.

## Validation

Fresh validations for this wave:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedGposFixtureFontsFromRepo --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserReportsReviewedMalformedGposFixtureFontsAsDiagnostics
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedGposFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gposTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapedGlyphRunGoldenPinsFixtureBackedGsubAndGposRuns
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Outcome

- GPOS fixture provenance is now tracked in
  `reports/font/fixtures/provenance/index.json`.
- Parser tests now prove the checked-in positive and malformed GPOS fixture
  fonts rather than synthetic-only pair tables.
- Runtime tests now prove the checked-in GPOS fixture glyph IDs, cluster
  advances, and offsets.
- `gpos-trace.json` now records fixture-backed pair/single positioning facts,
  including the bounded class-pair row and parser-owned malformed diagnostics.
- `shaped-glyph-run.json` now links the GPOS output cases to the shared
  GSUB/GPOS shaped-run dump.

## Non-Claims

This wave still does not claim complete GPOS support, mark or cursive
positioning, contextual positioning, native shaper parity, CPU oracle evidence,
or GPU evidence.
