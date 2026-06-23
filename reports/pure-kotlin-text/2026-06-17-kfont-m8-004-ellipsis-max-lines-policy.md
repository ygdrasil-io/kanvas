# KFONT-M8-004 - Ellipsis and max-lines policy

Status: implemented with local diff review; independent subagent review could
not be started because the session hit the agent thread limit.

## Scope

- implement bounded end ellipsis when `maxLines` clips trailing content
- keep truncation grapheme-cluster safe and preserve the trailing style used to
  shape the ellipsis
- record visible/truncated logical ranges and ellipsis glyph provenance in the
  paragraph layout dump
- refuse missing ellipsis glyphs, no-room-for-ellipsis cases, and visible
  placeholder truncation conflicts with stable diagnostics

## Files

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/paragraph-layout.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-004-implement-ellipsis-and-max-lines-policy.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `BasicParagraphLayoutEngine` now shapes a bounded end ellipsis with the
  active trailing style when `maxLines` truncates hidden content, instead of
  emitting the old unsupported-policy refusal.
- `LineLayout` and `ParagraphLayoutResult.dump()` now expose
  `visibleTextRange`, `truncatedTextRange`, `isEllipsized`, and
  `ellipsisGlyphs`, so checked-in dumps name exactly what stayed visible and
  which ellipsis glyph facts were appended.
- `paragraph-layout.json` now pins a mixed-style RTL case with max-lines
  overflow, visible range `0..2`, truncated range `4..4`, ellipsis glyph
  provenance at `fontSize=14.0`, and visual direction `-1`.
- Focused tests cover one-line overflow insertion, no-room refusal, missing
  ellipsis glyph refusal, placeholder truncation conflict refusal, and the
  mixed-style RTL path without promoting head/middle truncation, full bidi
  visual-order parity, or placeholder layout geometry parity.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutEllipsizesLastVisibleLineAndRecordsRangesAndProvenance --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutRefusesWhenEllipsisCannotFitWithinMaxWidth --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutRefusesWhenEllipsisShapingFails --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutRefusesWhenEllipsisWouldDropVisiblePlaceholder --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutEllipsizesMixedStyleRtlLineWithTrailingStyleProvenance --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutGoldenMatchesRepoFixture
rtk ./gradlew --no-daemon :font:text:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining Non-Claims

- This wave does not claim head or middle truncation variants, full bidi
  visual-order parity, placeholder layout geometry parity, selection geometry,
  hit testing, Skia Paragraph parity, CPU oracle parity, or GPU text support.
- The bounded truncation evidence is deterministic and fresh, but `KFONT-M8-005`
  and `KFONT-M8-006` still own selection/hit-test semantics and placeholder
  layout metrics.
