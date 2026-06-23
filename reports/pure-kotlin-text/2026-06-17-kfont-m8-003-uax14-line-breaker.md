# KFONT-M8-003 - UAX #14 line breaker

Status: done; bounded line-break evidence refreshed.

## Scope

- emit deterministic `LineBreakMap` values from pinned Unicode line-break data
- preserve grapheme-cluster safety while recording `mandatory`, `allowed`, and
  `prohibited` opportunities
- respect `softWrap` policy and explicit newlines without claiming full
  locale-dictionary refinement or complete UAX #14 conformance
- pin `line-breaks.json` plus the downstream paragraph hash deltas caused by
  explicit `softWrap` serialization

## Files

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphLineBreaking.kt`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/line-breaks.json`
- `reports/font/fixtures/expected/paragraph/paragraph-input.json`
- `reports/font/fixtures/expected/paragraph/paragraph-layout.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-003-implement-uax-14-line-breaker.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `Uax14LineBreaker` now derives break opportunities from pinned Unicode 16.0.0
  line-break classes plus grapheme-cluster boundaries and emits deterministic
  `LineBreakMap` dumps keyed by paragraph `inputHash`.
- `line-breaks.json` now covers Latin punctuation/space/newline handling, CJK
  adjacency without spaces, Thai locale-refinement diagnostics, combining-mark
  clusters, mixed LTR/RTL spacing boundaries, and emoji ZWJ clusters.
- Optional breaks are downgraded to `soft-wrap-disabled` refusals when
  `ParagraphStyle.softWrap` is false, while hard breaks remain available.
- Missing Unicode line-break data now refuses with the stable
  `text.paragraph.line-break-data-unavailable` diagnostic instead of using host
  line breaking.
- `paragraph-input.json` and `paragraph-layout.json` now serialize
  `ParagraphStyle.softWrap`, keeping downstream hashes aligned with the
  paragraph contract after the explicit line-break policy surfaced.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.uax14LineBreakerDoesNotEmitBreakInsideCombiningMarkCluster --tests org.graphiks.kanvas.text.TextStackSurfaceTest.uax14LineBreakerRecordsWhitespacePunctuationCjkAndMandatoryBreaksDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.uax14LineBreakerSuppressesOptionalBreaksWhenSoftWrapIsDisabled --tests org.graphiks.kanvas.text.TextStackSurfaceTest.uax14LineBreakerReportsLocaleBreakRefinementUnavailableForThai --tests org.graphiks.kanvas.text.TextStackSurfaceTest.uax14LineBreakerRefusesWhenUnicodeLineBreakDataIsUnavailable --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLineBreakGoldenMatchesRepoFixture --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLineBreakGoldenPinsCasesAndNonClaims
rtk ./gradlew --no-daemon :font:text:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining Non-Claims

- This wave does not claim complete UAX #14 conformance, dictionary-based
  Thai/Lao/Khmer refinement, ellipsis insertion, selection/hit-testing
  geometry, placeholder layout parity, Skia Paragraph parity, CPU oracle
  parity, or GPU text support.
- The bounded line-break evidence is deterministic and fresh, but later tickets
  still own ellipsis/max-lines policy, placeholder layout geometry, selection,
  hit testing, and richer paragraph semantics.
