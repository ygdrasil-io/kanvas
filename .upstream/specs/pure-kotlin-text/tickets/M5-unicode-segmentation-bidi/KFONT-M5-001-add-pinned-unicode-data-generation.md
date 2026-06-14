---
id: "KFONT-M5-001"
title: "Add pinned Unicode data generation"
status: "proposed"
milestone: "M5"
priority: "P0"
owner_area: "unicode"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-004"]
legacy_gate: null
---

# KFONT-M5-001 - Add pinned Unicode data generation

## PM Note

Ce ticket fixe la version Unicode utilisee par Kanvas, pour que le shaping ne change pas selon le JDK ou la machine de test.

## Problem

The text target requires UAX #29 grapheme rules, UAX #9 bidi classes, Script and Script_Extensions data, line-break facts, emoji properties, and variation selector facts. Current or prototype behavior cannot be promoted if it reads implicit platform/JDK Unicode data or omits the version and source hashes from dumps.

## Scope

- Add a reproducible pure Kotlin Unicode data generation flow for the pinned Unicode version selected by the font/text roadmap.
- Generate compact tables for Grapheme_Cluster_Break, Bidi_Class, Script, Script_Extensions, Line_Break, General_Category facts needed by segmentation, and Emoji/Extended_Pictographic/Variation_Selector properties.
- Write a manifest with Unicode version, input file names, input hashes, generator version, generation options, and output hashes.
- Expose the generated data through a `UnicodeDataSet` contract consumed by M5-002, M5-003, M5-004, and M6 shaping.
- Emit version mismatch diagnostics when a dump or fixture references a different Unicode data version.

## Non-Goals

- Do not implement grapheme segmentation, bidi resolution, or script itemization rules in this ticket.
- Do not download Unicode data during ordinary tests unless an explicit regeneration task is requested.
- Do not use ICU, JDK internals, HarfBuzz, browser APIs, or platform text services as normative Unicode data.
- Do not update expected dumps automatically when the pinned Unicode version changes.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class UnicodeDataSet(
    val version: UnicodeVersion,
    val sourceManifest: UcdSourceManifest,
    val graphemeBreak: UnicodeRangeTable<GraphemeBreakClass>,
    val bidiClass: UnicodeRangeTable<BidiClass>,
    val script: UnicodeRangeTable<ScriptCode>,
    val scriptExtensions: UnicodeRangeTable<Set<ScriptCode>>,
    val lineBreak: UnicodeRangeTable<LineBreakClass>,
    val emojiProperties: UnicodeEmojiProperties,
)

data class UcdSourceManifest(
    val unicodeVersion: String,
    val inputs: List<UcdInputFile>,
    val generatorVersion: String,
    val outputHash: Sha256,
)

interface UnicodeDataGenerator {
    fun generate(inputs: List<UcdInputFile>): UnicodeDataSet
}
```

## Acceptance Criteria

- [ ] Two clean generations from the same pinned inputs produce byte-identical generated tables and manifest output.
- [ ] Generated tables include the properties required by grapheme, bidi, script itemization, line breaking, emoji sequences, and variation selectors.
- [ ] Unit tests fail if generated output references a Unicode version different from the fixture expectation.
- [ ] The generator records input hashes and refuses unpinned or missing input files.
- [ ] Shaping-related dumps can serialize the Unicode version without depending on the JDK runtime version.

## Required Evidence

- `unicode-data-manifest.json` with Unicode version, input file list, input SHA-256 hashes, generator version, generated table hashes, and output schema version.
- Generated table fixtures for Grapheme_Cluster_Break, Bidi_Class, Script, Script_Extensions, Line_Break, emoji properties, and variation selectors.
- Regression fixture showing `text.shaping.unicode-data-version-mismatch` for a dump generated with the wrong Unicode version.
- Review diff proving expected outputs are checked in or versioned and not overwritten by ordinary test runs.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.unicode-data-version-mismatch`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add pinned Unicode data generation`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*UnicodeData*'
```

## Status Notes

- `proposed`: Foundation ticket for all M5 segmentation and M6 shaping rules.
- Move to `ready` only after pinned Unicode version and manifest fields are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M5`
- `area:unicode`
- `claim:tracked-gap`
