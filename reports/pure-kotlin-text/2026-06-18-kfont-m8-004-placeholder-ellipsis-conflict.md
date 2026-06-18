# 2026-06-18 - KFONT-M8-004 Ellipsis, Truncation, And Bidi Ordering

## Scope

- `KFONT-M8-004 - Implement ellipsis and max-lines policy`

## Files

- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-004-implement-ellipsis-and-max-lines-policy.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-006-implement-placeholder-layout-metrics.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/paragraph-layout.json`
- `reports/font/fixtures/fonts/paragraph/paragraph-ellipsis-fixture.txt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/font/fixtures/provenance/index.json`

## Evidence

- `BasicParagraphLayoutEngine` now inserts ellipsis for bounded `maxLines`
  overflow, trims only cluster-safe surviving spans, records per-line
  `isEllipsized` plus `visibleRange`/`truncatedRange`, and emits the narrower
  `text.paragraph.placeholder-ellipsis-conflict`,
  `text.paragraph.ellipsis-no-room`, and
  `text.paragraph.ellipsis-glyph-missing` refusals for the remaining bounded
  failure paths.
- `TextStackSurfaceTest` proves one-line overflow truncation facts, placeholder
  tail insertion when room remains, ellipsis-only fallback when no visible
  cluster fits but the ellipsis does, retry-on-earlier-visible-style behavior
  when the current trailing style cannot shape the ellipsis, missing-glyph and
  no-room refusals, trailing-style ellipsis shaping on mixed-style content, and
  shaped-cluster safety for a multi-code-unit visible cluster.
- `TextStackSurfaceTest.paragraphLayoutKeepsSurvivingVisualOrderForEllipsizedMixedDirectionLine`
  proves that truncating a mixed-direction first line preserves the visual
  ordering of the surviving RTL cluster while still reporting the logical
  `visibleRange` and `truncatedRange`.
- `TextStackSurfaceTest.paragraphLayoutKeepsSurvivingVisualOrderForEllipsizedRtlParagraphWithLtrIsland`
  proves the bounded level-`2` case stays stable too: an RTL paragraph with an
  LTR island keeps the island in visual order before the surviving RTL tail.
- `hitTestMap()` now records the visual tail of the displayed ellipsis as a
  final caret stop, and `hitTest()` treats points inside that tail as
  in-bounds text hits that resolve to the truncated line end instead of
  falling past the visible layout surface.
- `reports/font/fixtures/expected/paragraph/paragraph-layout.json` checks in
  deterministic golden coverage for the accepted ellipsis cases, including the
  bounded `mixed-bidi-ellipsized` row's `visibleRange`/`truncatedRange` and
  ellipsis provenance, while the visual-order proof itself stays in the direct
  bidi tests above.
- Exact mixed-direction ellipsis placement remains a non-claim: this wave
  proves the surviving visible spans stay in visual order and that accepted
  ellipsis tails remain hittable, but it does not claim full logical-tail
  placement parity for every bidi truncation case.
- The terminal placeholder conflict path remains attached, so this wave keeps
  the narrower `KFONT-M8-006` gate closed while broadening only the bounded
  ellipsis/max-lines behavior claimed by `KFONT-M8-004`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutInsertsEllipsisAndRecordsTruncationFactsInResultDump --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutAppendsEllipsisWhenVisiblePlaceholderHasRoomForEllipsis --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutDiagnosesPlaceholderEllipsisConflictWhenTerminalPlaceholderCannotFitEllipsis --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutDiagnosesEllipsisNoRoomWhenMaxWidthCannotFitEllipsis --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutAllowsEllipsisOnlyWhenNoVisibleClusterFitsButEllipsisDoes --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutDiagnosesMissingEllipsisGlyphWhenShaperCannotProduceEllipsisRun --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutRetriesEllipsisWithEarlierVisibleStyleWhenTrailingStyleCannotShapeIt --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutShapesEllipsisWithTrailingVisibleStyleAfterTruncation --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutDoesNotCutInsideAShapedClusterWhenEllipsizing --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutHitTestMapIncludesVisualTailForDisplayedEllipsis --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutHitTestTreatsDisplayedEllipsisAsInsideText --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutKeepsSurvivingVisualOrderForEllipsizedMixedDirectionLine --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutKeepsSurvivingVisualOrderForEllipsizedRtlParagraphWithLtrIsland --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutGoldenPinsEllipsisCasesAndNonClaims
rtk git diff --check
```

## Remaining Non-Claims

This wave adds bounded ellipsis insertion, truncation, and surviving-span
mixed-bidi ordering evidence only. It does not claim exact mixed-direction
ellipsis placement, complete paragraph layout parity, CPU oracle parity,
shaping completeness, or GPU text support.
