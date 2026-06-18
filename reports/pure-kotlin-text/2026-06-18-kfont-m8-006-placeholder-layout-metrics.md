# 2026-06-18 - KFONT-M8-006 Placeholder Layout Metrics

## Scope

- `KFONT-M8-006 - Implement placeholder layout metrics`

## Files

- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-006-implement-placeholder-layout-metrics.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ParagraphPlaceholderLayoutTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ParagraphStyleContractTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/paragraph-input.json`
- `reports/font/fixtures/expected/paragraph/placeholder-layout.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`

## Evidence

- `PlaceholderStyle` now serializes nullable `baseline` plus
  `participatesInLineHeight`, so placeholder line-height policy is visible in
  the paragraph input contract and pinned by `paragraph-input.json`.
- Paragraph input validation now refuses missing required baselines for
  `baseline`, `above-baseline`, and `below-baseline` placeholder alignment,
  along with non-finite placeholder metrics and negative placeholder baseline
  offsets.
- `BasicParagraphLayoutEngine` now emits deterministic `placeholderBoxes` with
  stable IDs, source ranges, line indices, bounds, baseline offsets, serialized
  alignment/baseline facts, and line-height participation flags.
- Participating placeholders now adjust line ascent/descent deterministically
  for baseline, above-baseline, and below-baseline placement, while
  non-participating placeholders preserve line metrics and still expose their
  out-of-band geometry.
- `placeholder-layout.json` checks in bounded fixture evidence for baseline,
  above-baseline, below-baseline, and centered placeholder cases, and
  `ParagraphLayoutResult.dump()` now serializes `placeholderBoxes` explicitly.
- `KFONT-M8-005` now consumes placeholder IDs and geometry in deterministic
  selection/hit-test evidence, and `KFONT-M8-004` now contributes bounded
  ellipsis insertion plus the narrower
  `text.paragraph.placeholder-ellipsis-conflict` refusal required by this
  ticket for the case where the last visible line ends in a placeholder and
  cannot fit the requested ellipsis without touching it.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ParagraphPlaceholderLayoutTest --tests org.graphiks.kanvas.text.ParagraphStyleContractTest --tests org.graphiks.kanvas.text.ParagraphHitTestMapTest --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutAppendsEllipsisWhenVisiblePlaceholderHasRoomForEllipsis --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutDiagnosesPlaceholderEllipsisConflictWhenTerminalPlaceholderCannotFitEllipsis
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Non-Claim

This ticket is now closed on bounded placeholder metric evidence. It does not
itself claim bidi visual-order preservation under truncation, complete
paragraph layout parity, CPU oracle parity, or GPU text support.
