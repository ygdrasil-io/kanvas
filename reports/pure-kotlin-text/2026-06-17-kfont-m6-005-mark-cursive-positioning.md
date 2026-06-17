# KFONT-M6-005 - Mark/Cursive Positioning Closeout

## Scope

This wave closes the implementation gate for bounded mark/cursive GPOS support:

- `font/sfnt` now parses bounded GDEF glyph classes and GPOS LookupType 3/4/5/6
  facts needed by the checked-in fixtures.
- `font/text` now applies bounded mark-to-base, mark-to-ligature,
  mark-to-mark, and cursive attachment offsets while preserving stable refusal
  diagnostics for missing GDEF, malformed lookup data, and ambiguous
  multi-component ligature matches without a unique runtime component choice.
- Reviewed fixture provenance is now checked in for
  `gpos-mark-to-base.otf`, `gpos-mark-to-ligature.otf`,
  `gpos-mark-to-mark.otf`, `gpos-cursive-attachment.otf`,
  `gpos-missing-gdef.otf`, and `gpos-anchor-malformed.otf`, with
  project-specific OFL records for both the Noto and Amiri upstream assets.
- `gpos-trace.json` and the shared `shaped-glyph-run.json` now include bounded
  mark/cursive evidence instead of leaving this slice as an undocumented gap.

## Validation

Fresh validations for this wave:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedMarkAndCursiveGposFixtureFontsFromRepo --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserPreservesMissingGdefAndMalformedAnchorFixtureFacts
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gposTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapedGlyphRunGoldenPinsFixtureBackedGsubAndGposRuns --tests org.graphiks.kanvas.text.TextStackSurfaceTest.arabicSeedReadinessGoldenPinsDiagnosticsWithoutSupportClaim --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedMarkAndCursiveFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineReportsReviewedMarkAndCursiveFixtureDiagnosticsFromRepo
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapingKeepsReviewedGsubClustersWhenTypefaceHasUnmatchedMarkLookups --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineRefusesAmbiguousLigatureComponentAttachments --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineDoesNotReportUnavailableWhenCursiveMatchHasZeroAdvanceDelta
rtk run "python3 scripts/validate_font_fixture_assets.py && python3 scripts/validate_pure_kotlin_text_fixture_manifest.py && python3 scripts/validate_pure_kotlin_text_dump_index.py"
rtk git diff --check
```

## Outcome

- Parser tests now prove the checked-in positive and refusal mark/cursive fonts
  rather than synthetic-only lookup fragments.
- Runtime tests now prove bounded advances, offsets, glyph classes, attachment
  vectors, stable zero-advance cursive matches, cluster preservation for
  unrelated GSUB runs, and refusal diagnostics for the checked-in mark/cursive
  fixtures.
- `gpos-trace.json` now records mark-to-base, mark-to-ligature, mark-to-mark,
  cursive attachment, missing-GDEF, and malformed-anchor cases.
- `shaped-glyph-run.json` now links the bounded mark/cursive positive cases into
  the shared GSUB/GPOS shaped-run dump.
- `arabic-seed-readiness.json` now drops the obsolete global
  mark/cursive-unavailable diagnostics while keeping the seed non-claims.

## Non-Claims

This wave still does not claim Arabic shaping support, contextual GSUB/GPOS
support, ambiguous multi-component ligature-component resolution,
variation/device-table support, native shaper parity, CPU oracle parity, or
GPU evidence.
