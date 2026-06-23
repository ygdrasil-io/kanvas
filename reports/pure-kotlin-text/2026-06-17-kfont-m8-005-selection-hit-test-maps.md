# KFONT-M8-005 - Selection and hit-test maps

Status: implemented and independently reviewed; kept in `review` pending the
remaining visual-order and fixture gates.

## Scope

- add bounded paragraph selection geometry and point hit testing
- expose stable caret affinity, word-boundary, and grapheme-boundary facts
- consume placeholder geometry from `KFONT-M8-006` instead of guessing
- pin deterministic interaction evidence without promoting full bidi or
  platform-text parity claims

## Files

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/hit-test-map.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-005-implement-selection-and-hit-test-maps.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `ParagraphLayoutResult.buildHitTestMap(...)` now serializes deterministic
  `caretStops`, `selectionBoxes`, `hitEntries`, `wordBoundaries`, and
  `graphemeBoundaries` derived from paragraph layout output.
- The line-local geometry path now reuses shaped cluster advances where
  `LineLayout.glyphRuns` already expose them, and keeps logical caret/selection
  geometry independent from glyph drawing offsets, so bounded pure-text hit
  testing no longer depends only on synthetic estimated widths.
- Placeholder-aware selection and hit testing now consume `PlaceholderBox`
  geometry, so inline object ranges keep stable `placeholderId` evidence in
  the checked-in dump.
- Grapheme-safe hit testing now keeps caret positions on cluster boundaries,
  records upstream/downstream affinity, and refuses non-finite points with the
  dedicated `text.paragraph.hit-test-point-non-finite` diagnostic.
- Selection ranges that cut a grapheme cluster now refuse with
  `text.paragraph.cluster-invariant-failed` instead of guessing a partial box,
  and finite out-of-bounds points clamp to the nearest line and then the
  nearest fragment boundary.
- The checked-in `hit-test-map.json` fixture now pins a multiline placeholder
  selection case plus an RTL emoji grapheme-clamp case, and the focused
  negative tests pin invalid selection-range, grapheme-cut selection, and
  non-finite hit-point refusals without promoting full bidi visual-order
  parity or platform caret behavior.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutBuildsHitTestMapWithSelectionPlaceholderAndGraphemeFacts --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutHitTestKeepsEmojiClusterBoundariesAndRtlLineDirectionBounded --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutHitTestMapReportsInvalidSelectionAndNonFinitePointDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutHitTestMapUsesShapedClusterAdvancesInsteadOfEstimatedWidths --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutHitTestMapRefusesSelectionRangeThatCutsGraphemeCluster --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutHitTestMapKeepsCaretStopsOnLogicalAdvancesDespiteGlyphOffsets --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutUsesShapedAdvancesForPlaceholderBoxesBeforeInlinePlaceholder --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphHitTestMapGoldenMatchesRepoFixture --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphHitTestMapGoldenPinsCasesAndNonClaims
rtk ./gradlew --no-daemon :font:text:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining Non-Claims

- This wave does not claim complete fallback policy, full bidi visual-order
  parity, platform caret/selection parity, Skia Paragraph parity, CPU oracle
  parity, or GPU text support.
- The bounded evidence covers multiline placeholder selection, RTL emoji
  grapheme clamping, invalid selection ranges, grapheme-cut selection refusal,
  and non-finite hit points only; broader mixed LTR/RTL plus mixed-style
  visual-order parity and structured negative dump rows remain separate gates.
