# KFONT-M6-003 - GSUB Contextual Lookup Slice

Date: 2026-06-17
Status: done with bounded fixture evidence.

## Scope

This wave adds the bounded GSUB contextual slice required by
`KFONT-M6-003` without widening script support claims:

- `font/sfnt` now parses GSUB LookupType 5 format 1 glyph-sequence,
  format 2 class-based, and format 3 coverage-based contextual lookups.
- `font/text` now applies nested lookup records only when the contextual rule
  matches and stops bounded recursive re-entry with a stable
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
  `sequenceIndex` targets after earlier expansion, emits a stable
  `text.shaping.lookup-malformed` refusal when a contextual nested
  `sequenceIndex` falls outside the matched range or names a missing
  `lookupIndex`, and rolls contextual nested substitutions back atomically
  before emitting `text.shaping.cluster-invariant-failed`, including nested
  contextual lookups that would escape the outer matched cluster.
- `reports/font/fixtures/provenance/index.json` now records the checked-in
  contextual fixture bytes, hashes, and Apache-2.0 provenance derived from
  `simoncozens/test-fonts FallbackPlus-Small`.
- `reports/pure-kotlin-text/fixture-evidence-manifest.json` and
  `reports/pure-kotlin-text/dump-evidence-index.json` now link the contextual
  GSUB fixture rows to the shared Latin GSUB/GPOS evidence family while
  keeping mark/cursive positioning, feature-policy adoption, and non-Latin
  promotion explicitly gated elsewhere.
- Independent review initially found format 2 coverage, nested-position
  stability, missing nested-lookup refusals, and cluster-invariant rollback
  gaps; the remediating parser/runtime tests now pass, now cover later
  `sequenceIndex` escapes plus nested contextual outer-cluster refusals, and
  the follow-up re-review returned no remaining findings.

## Validation

```bash
rtk ./gradlew --no-daemon :font:sfnt:test :font:text:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Non-Claims

- No complete GSUB support claim.
- No GPOS contextual, mark, cursive, extension, chaining, or variation/device
  support claim.
- No script-default runtime adoption claim; `ResolvedFeatureSet` execution
  remains owned by `KFONT-M6-006`.
- No Arabic, Devanagari, Thai, CJK, Emoji, native-shaper, CPU oracle, or GPU
  support claim.

## Remaining Gate

None on this bounded ticket. Mark/cursive positioning, script-default runtime
adoption, extension/chaining lookups, and non-Latin promotion remain owned by
later KFONT-M6 tickets.
