# 2026-06-18 - KFONT-M8-004 Placeholder Ellipsis Conflict

## Scope

- `KFONT-M8-004 - Implement ellipsis and max-lines policy`

## Files

- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-004-implement-ellipsis-and-max-lines-policy.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-006-implement-placeholder-layout-metrics.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`

## Evidence

- `BasicParagraphLayoutEngine` now emits the narrower
  `text.paragraph.placeholder-ellipsis-conflict` refusal when `maxLines`
  overflow plus requested ellipsis reaches a last visible line that ends in a
  placeholder and does not have enough remaining width to append the ellipsis
  without touching that placeholder.
- The generic `text.paragraph.max-lines-ellipsis-unsupported` refusal remains
  active for non-placeholder overflow paths, so this wave does not claim actual
  ellipsis insertion, truncation layout fields, or ellipsis glyph provenance.
- `TextStackSurfaceTest` now locks the placeholder-conflict dump path with an
  explicit `ParagraphBuilder` case using a visible baseline-aligned placeholder
  followed by hidden overflow text, and also proves that a visible placeholder
  with enough remaining width still falls back to the generic unsupported
  diagnostic.
- This closes the final evidence gate called out by `KFONT-M8-006` without
  broadening paragraph support claims.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutFallsBackToGenericEllipsisUnsupportedWhenVisiblePlaceholderHasRoomForEllipsis --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutDiagnosesPlaceholderEllipsisConflictWhenTerminalPlaceholderCannotFitEllipsis --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutDiagnosesMaxLineEllipsisUnsupportedInResultDump
rtk git diff --check
```

## Remaining Gate

This wave adds bounded refusal evidence only. It does not yet claim actual
ellipsis insertion, trailing-style ellipsis shaping, per-line `isEllipsized` /
`visibleRange` / `truncatedRange` dump fields, mixed-style ellipsis evidence,
bidi truncation ordering, complete paragraph layout parity, CPU oracle parity,
or GPU text support.
