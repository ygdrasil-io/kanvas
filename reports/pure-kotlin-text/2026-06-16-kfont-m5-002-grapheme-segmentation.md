# KFONT-M5-002 Grapheme Segmentation

Date: 2026-06-16
Status: done

## Scope

KFONT-M5-002 adds a bounded, deterministic UAX #29 extended grapheme cluster
segmenter for the fixture matrix required by the ticket. The implementation
segments from the pinned `UnicodeDataSet`; it does not call ICU, JDK Unicode
segmenters, regex segmenters, browser APIs, native shapers, or platform text
services.

The Unicode 16.0 source extracts remain bounded. They were expanded only with
the reviewed rows needed by the KFONT-M5-002 fixtures: CR/LF/control,
Hangul `L/V/T/LV/LVT`, `Extend`, `SpacingMark`, `Prepend`, regional
indicators, ZWJ, Extended_Pictographic, emoji modifier rows, variation
selectors, and the minimal Indic_Conjunct_Break rows for Devanagari virama.

## Evidence

- `GraphemeClusterer` emits UTF-16 ranges, code point ranges, cluster level,
  source text hash, Unicode version, diagnostics, and per-boundary rule IDs.
- `BasicTextSegmenter()` now delegates to the pinned grapheme segmenter by
  default, so `BasicOpenTypeShapingEngine` and `FallbackOpenTypeShapingEngine`
  consume KFONT-M5-002 clusters without an opt-in segmenter override.
- The pinned Unicode 16.0 source extracts are also packaged under
  `font/text/src/main/resources/org/graphiks/kanvas/text/unicode/16.0.0/`
  so the default segmenter does not depend on test-only report paths.
- `unicode-segments.json` covers:
  `grapheme-latin-combining.txt`, `grapheme-emoji-zwj.txt`,
  `grapheme-regional-indicators.txt`, `grapheme-devanagari-virama.txt`,
  `grapheme-variation-selector.txt`, `grapheme-crlf-control.txt`,
  `grapheme-prepend.txt`, and
  `grapheme-isolated-surrogate.txt`.
- `GraphemeSegmentationTest` asserts ASCII, Hangul, combining marks, emoji
  modifier, emoji ZWJ, regional indicator pairs, Indic virama, variation
  selectors, CR/LF/control, Prepend, default `BasicTextSegmenter` replacement,
  invalid scalar diagnostics, `text.unicode.cluster-boundary-invalid`,
  `text.unicode.grapheme-rule-unsupported`, version mismatch diagnostics,
  cluster invariant diagnostics, and byte-identical dump generation.
- `UnicodeDataGenerationTest` now covers the bounded source extract expansion,
  including `Indic_Conjunct_Break`, `Emoji_Modifier`,
  `Emoji_Modifier_Base`, and `Emoji_Presentation` generated table fields.

## Diff Evidence

Cluster boundaries are not derived from the JDK Unicode version. Boundary
decisions in `GraphemeSegmentation.kt` read `graphemeBreak`,
`indicConjunctBreak`, and `emojiProperties.extendedPictographic` from
`UnicodeDataSet`, and the generated tables are hashed in
`unicode-data-manifest.json`. The default segmenter loads the same bounded
Unicode 16.0 extracts from module resources, and fixture tests run offline.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests '*Grapheme*'
rtk ./gradlew --no-daemon :font:text:test --tests '*Grapheme*' --rerun-tasks
rtk ./gradlew --no-daemon :font:text:test --rerun-tasks
rtk ./gradlew --no-daemon --rerun-tasks :font:text:test --tests '*UnicodeData*'
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Review

- Independent spec review verdict: accepted after default-segmenter and
  refusal-diagnostic remediation.
- Independent code-quality review verdict: ready to merge after the runtime
  resource loader null-safety fix and explicit CR/LF/control plus Prepend
  fixture evidence.

## Non-Claims

- No complete Unicode Character Database claim.
- No complete UAX #29 claim beyond the bounded KFONT-M5-002 fixture matrix.
- No UAX #9 bidi, UAX #14 line breaking, word breaking, sentence breaking, or
  paragraph layout claim.
- No GSUB/GPOS shaping, glyph fallback, emoji rendering, color glyph rendering,
  or GPU text route promotion.

## Remaining Gates

None for KFONT-M5-002 closeout. This remains bounded fixture evidence only and
does not promote complete UAX #29, bidi, script itemization, shaping,
paragraph, emoji rendering, color glyph rendering, or GPU text support.
