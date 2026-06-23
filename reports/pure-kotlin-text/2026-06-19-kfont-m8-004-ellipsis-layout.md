# 2026-06-19 - KFONT-M8-004 Ellipsis And Max-Lines Layout

## Scope

- `KFONT-M8-004 - Implement ellipsis and max-lines policy`

## Files

- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-004-implement-ellipsis-and-max-lines-policy.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ParagraphEllipsisLayoutTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/paragraph-layout.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`

## Evidence

- `BasicParagraphLayoutEngine` now performs bounded end-ellipsis insertion for
  `maxLines` overflow by replacing only cluster-safe trailing content on the
  last visible line instead of emitting the generic unsupported diagnostic for
  every non-placeholder overflow.
- `LineLayout` now records deterministic `isEllipsized`, `visibleRange`,
  `truncatedRange`, and `ellipsisGlyphRun` facts, and
  `ParagraphLayoutResult.dump()` serializes those fields without promoting
  complete paragraph, CPU-oracle, or GPU text claims.
- `paragraph-layout.json` checks in bounded one-line, multi-line, mixed-style,
  RTL-direction, placeholder-conflict, no-room, and missing-glyph evidence,
  including trailing-style provenance and stable refusal diagnostics.
- Independent review now also pins the refusal precedence rule that
  `text.paragraph.ellipsis-glyph-missing` wins when the ellipsis cannot be
  shaped, instead of letting placeholder-width heuristics mask that missing
  glyph path with `text.paragraph.placeholder-ellipsis-conflict`.
- Placeholder-ended overflow still emits the narrower
  `text.paragraph.placeholder-ellipsis-conflict` refusal when the ellipsis
  cannot fit without truncating a visible placeholder, while `text.paragraph.ellipsis-no-room`
  and `text.paragraph.ellipsis-glyph-missing` now cover the two new negative
  paths.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ParagraphEllipsisLayoutTest --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutShapesAndSerializesEllipsisFactsForSimpleMaxLineOverflow --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutEllipsizesVisiblePlaceholderLineWhenPlaceholderHasRoomForEllipsis --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutDiagnosesPlaceholderEllipsisConflictWhenTerminalPlaceholderCannotFitEllipsis
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Gate

This closes bounded `KFONT-M8-004` ellipsis/max-lines behavior only. It does
not yet claim complete bidi visual ordering, explicit word/grapheme boundary
query APIs, complete paragraph layout parity, CPU oracle parity, or GPU text
support.
