# 2026-06-18 - KFONT-M8-003 UAX #14 Line Breaker

## Scope

- `KFONT-M8-003 - Implement UAX #14 line breaker`

## Files

- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-003-implement-uax-14-line-breaker.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphLineBreaking.kt`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ParagraphLineBreakingTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/line-breaks.json`
- `reports/font/fixtures/expected/paragraph/paragraph-input.json`
- `reports/font/fixtures/fonts/paragraph/paragraph-line-breaking-fixture.txt`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `scripts/validate_pure_kotlin_text_dump_index.py`

## Evidence

- `DefaultUax14LineBreaker` now emits deterministic `LineBreakMap` dumps with
  pinned Unicode version, source `inputHash`, `softWrap`, break opportunities,
  cluster references, and paragraph-owned diagnostics.
- `line-breaks.json` checks in bounded evidence for spaces, punctuation,
  explicit newlines, CJK no-space boundaries, combining marks, emoji ZWJ
  sequences, mixed LTR/RTL text, and a Thai locale-refinement gap.
- `softWrap = false` suppresses optional breaks without suppressing mandatory
  hard breaks, and the paragraph input/layout dumps now serialize `softWrap`
  explicitly so the contract remains hash-stable and reviewable.
- When no optional soft break fits within the requested width, the bounded line
  fitter now falls back to the current grapheme-cluster boundary so surrogate
  pairs and emoji clusters stay intact without duplicating adjacent text during
  paragraph layout.
- The checked-in Thai case emits
  `text.paragraph.locale-break-refinement-unavailable` instead of claiming any
  dictionary-based segmentation or host fallback.
- The bounded CJK fallback remains explicit and non-promotional: it only proves
  the checked Han/kana no-space cases in `line-breaks.json` and does not claim
  complete UAX #14, complete East Asian punctuation coverage, or Thai/Lao/Khmer
  refinement support.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ParagraphLineBreakingTest --tests org.graphiks.kanvas.text.ParagraphStyleContractTest --tests org.graphiks.kanvas.text.TextStackSurfaceTest.simpleLineBreakerFallsBackToCurrentClusterBoundaryWhenNoSoftBreakFits --tests org.graphiks.kanvas.text.TextStackSurfaceTest.simpleLineBreakerKeepsSurrogatePairRangesIntactWhenWidthIsZero --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphLayoutEngineDoesNotDuplicateEmojiWhenLineBreakerOverflowsSingleCluster --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutMergesLineBreakDiagnosticsIntoResultDump --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutRefusesWhenLineBreakUnicodeDataIsUnavailable
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Gate

This evidence closes bounded paragraph line-break mapping only. It does not
claim full UAX #14 conformance, dictionary-based line breaking, bidi visual
line ordering, ellipsis insertion, hit testing, selection geometry, placeholder
geometry layout, Skia Paragraph parity, CPU oracle parity, or GPU text support.
