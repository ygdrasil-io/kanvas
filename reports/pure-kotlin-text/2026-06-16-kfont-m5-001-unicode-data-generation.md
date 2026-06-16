# KFONT-M5-001 Unicode Data Generation

Date: 2026-06-16
Status: done

## Scope

KFONT-M5-001 adds bounded, offline Unicode 16.0.0 seed generation for pure
Kotlin text evidence. The generator consumes checked-in source extracts and
produces deterministic manifest, table, and diagnostic fixtures.

This report is not a complete Unicode Character Database support claim and is
not a UAX #9, UAX #14, or UAX #29 conformance claim.

## Evidence

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/UnicodeDataGeneration.kt`
  defines `UnicodeDataSet`, `UnicodeDataGenerator`, `PinnedUnicodeDataGenerator`,
  source manifest records, range tables, emoji property tables, sample facts,
  and the `text.shaping.unicode-data-version-mismatch` diagnostic fixture
  helper.
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/UnicodeDataGenerationTest.kt`
  proves two generations from the same inputs are byte-identical, validates
  input SHA-256 hashes, validates generated table SHA-256 hashes, refuses
  missing and unpinned inputs, and compares generated JSON to checked-in
  expected fixtures.
- `reports/font/fixtures/expected/unicode/source-extracts/16.0.0/` contains
  narrow source extracts for `UnicodeData.txt`, `Scripts.txt`,
  `ScriptExtensions.txt`, `GraphemeBreakProperty.txt`, `LineBreak.txt`,
  `DerivedCoreProperties.txt`, `PropList.txt`, and `emoji/emoji-data.txt`.
- `reports/font/fixtures/expected/unicode/unicode-data-manifest.json` records
  Unicode version `16.0.0`, input file names, input SHA-256 hashes, generator
  version/options, generated table SHA-256 hashes, and output schema version.
- `reports/font/fixtures/expected/unicode/unicode-data-tables.json` records
  bounded sample facts for Grapheme_Cluster_Break, Bidi_Class, Script,
  Script_Extensions, Line_Break, General_Category, Default_Ignorable_Code_Point,
  emoji/Extended_Pictographic, and Variation_Selector.
- `reports/font/fixtures/expected/unicode/unicode-data-version-mismatch-diagnostic.json`
  records the stable mismatch refusal for a dump expecting a different Unicode
  data version.

## Validation

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:text:test --tests '*UnicodeData*'
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Non-Claims

- No complete UCD claim.
- No UAX #9 bidi conformance claim.
- No UAX #14 line breaking conformance claim.
- No UAX #29 grapheme segmentation conformance claim.
- No replacement of `BasicUnicodeData`.
- No grapheme segmenter, bidi resolver, Script_Extensions itemizer, shaping,
  paragraph, or GPU text route promotion.
- No paragraph support claim.
- No ICU, JDK Unicode behavior, HarfBuzz, FreeType, Fontations, AWT, JNI,
  native engine, or platform text service is used as normative behavior.

## Remaining Gates

Later M5 tickets must consume the generated `UnicodeDataSet` before replacing
the basic segmentation, bidi, or script itemization behavior. Any expansion
beyond this bounded seed requires reviewed source extract diffs, regenerated
table hash diffs, and an explicit rebaseline reason.
