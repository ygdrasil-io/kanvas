# KFONT-M6-003 - GSUB Contextual Lookup Slice

Date: 2026-06-17
Status: done with bounded fixture evidence and post-review claim narrowing.

## Scope

This wave adds the bounded GSUB contextual slice required by
`KFONT-M6-003` without widening script support claims:

- `font/sfnt` now parses GSUB LookupType 5 format 1 glyph-sequence,
  format 2 class-based, and format 3 coverage-based contextual lookups for
  mono-format contextual lookups backed by the checked-in Latin fixtures.
- `font/text` now applies nested lookup records only when the contextual rule
  matches, keeps nested-only referenced lookups available for contextual
  application without top-level activation, and stops nested lookup cycles with
  a stable
  `text.shaping.lookup-cycle-detected` diagnostic.
- Reviewed fixture provenance is now checked in for
  `gsub-context-format1.otf`, `gsub-context-format2-class.otf`,
  `gsub-context-format3-coverage.otf`,
  `gsub-context-malformed-classdef.otf`, and
  `gsub-context-nested-cycle.otf`.
- `gsub-trace.json` and `shaped-glyph-run.json` now include positive
  contextual match, negative no-match, malformed-class-definition, and
  nested-cycle rows for the bounded Latin fixture family.

## Evidence

- `SFNTSurfaceTest` now proves the checked-in contextual fixture fonts parse
  into deterministic LookupType 5 structures with the expected nested lookup
  records for formats 1, 2, and 3.
- The malformed contextual fixture records a stable
  `font.sfnt.optional-table-malformed` parser diagnostic that names the
  `ClassDef` failure instead of silently dropping the table.
- `BasicOpenTypeShapingEngine` now applies the contextual fixtures with
  deterministic glyph IDs and preserved cluster ranges for the positive
  format 1/2/3 cases, keeps the negative no-match case unchanged, and emits a
  stable `text.shaping.lookup-cycle-detected` refusal for recursive nested
  lookup re-entry.
- The runtime now enforces the GSUB format 2 first-glyph `Coverage` gate,
  keeps format 2 subtables isolated inside one lookup, preserves later nested
  `sequenceIndex` targets after earlier expansion, and emits a stable
  `text.shaping.lookup-malformed` refusal when a contextual nested
  `sequenceIndex` falls outside the matched range.
- `OpenTypeGsubTableParser` now retains nested-only lookups that are only
  reachable through `SubstLookupRecord`, and `BasicOpenTypeShapingEngine`
  skips those blank-feature-tag lookups at top level while still allowing
  contextual nested execution.
- `reports/font/fixtures/provenance/index.json` now records the checked-in
  contextual fixture bytes, hashes, and Apache-2.0 provenance derived from
  `simoncozens/test-fonts FallbackPlus-Small`.
- `reports/pure-kotlin-text/fixture-evidence-manifest.json` and
  `reports/pure-kotlin-text/dump-evidence-index.json` now link the contextual
  GSUB fixture rows to the shared Latin GSUB/GPOS evidence family while
  keeping mark/cursive positioning, feature-policy adoption, and non-Latin
  promotion explicitly gated elsewhere.
- Independent review initially found format 2 coverage, nested-position
  stability, and out-of-range nested-index gaps; a follow-up review then found
  nested-only lookup preservation plus two scope-overclaim issues. The
  nested-only parser/runtime regression is now fixed and covered by focused
  tests, while mixed-format LookupType 5 subtables inside one lookup and any
  acyclic deep re-entry budget remain explicit non-claims on this bounded
  slice.

## Validation

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.m6SimpleLayoutFixturesAreCheckedInWithSyntheticProvenance --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedGsubContextFixtureFontsFromRepo --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserReportsReviewedMalformedGsubContextFixturesAsDiagnostics --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserKeepsNestedOnlyGsubLookupsReachableFromContextRules
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedGsubFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedGsubContextFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedGposFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineReservesNestedOnlyLookupsForContextMatches --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gsubTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gposTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapedGlyphRunGoldenPinsFixtureBackedGsubAndGposRuns
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Non-Claims

- No complete GSUB support claim.
- No GPOS contextual, mark, cursive, extension, chaining, or variation/device
  support claim.
- No mixed-format LookupType 5 same-lookup support claim.
- No acyclic deep re-entry budget or generalized recursion-limit support claim.
- No script-default runtime adoption claim; `ResolvedFeatureSet` execution
  remains owned by `KFONT-M6-006`.
- No Arabic, Devanagari, Thai, CJK, Emoji, native-shaper, CPU oracle, or GPU
  support claim.

## Remaining Gate

Mixed-format LookupType 5 subtables inside one lookup and any acyclic deep
re-entry budget remain unimplemented and explicitly non-claiming on this
bounded ticket. Mark/cursive positioning, script-default runtime adoption,
extension/chaining lookups, and non-Latin promotion remain owned by later
KFONT-M6 tickets.
