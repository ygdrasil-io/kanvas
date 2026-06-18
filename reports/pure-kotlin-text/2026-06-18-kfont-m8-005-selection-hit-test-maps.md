# 2026-06-18 - KFONT-M8-005 Selection And Hit-Test Maps

## Scope

- `KFONT-M8-005 - Implement selection and hit-test maps`

## Files

- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-005-implement-selection-and-hit-test-maps.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ParagraphHitTestMapTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/hit-test-map.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`

## Evidence

- `ParagraphLayoutResult` now exposes bounded `SelectionBox`, `CaretStop`,
  `HitTestEntry`, `HitTestMap`, `SelectionQueryResult`, and
  `HitTestQueryResult` value-object contracts derived from current line boxes,
  cluster spans, and placeholder geometry.
- Selection now emits deterministic multi-line boxes and carries explicit
  `placeholderId` values for inline placeholder spans, so placeholder geometry
  no longer stops at `paragraph-layout.json`.
- Hit testing now snaps to cluster-safe boundaries with
  upstream/downstream affinity, routes through placeholder geometry even when
  a below-baseline placeholder does not participate in line height, and never
  returns a position inside the combining-mark or emoji clusters covered by
  the checked-in fixture.
- Finite out-of-bounds points now clamp to the nearest caret stop instead of
  refusing, while invalid selection ranges and non-finite hit-test points emit
  stable refusal diagnostics.
- `hit-test-map.json` checks in bounded evidence for multi-line placeholder
  selection, non-participating placeholder overflow routing, combining marks,
  emoji cluster boundaries, and clamp behavior without promoting bidi visual
  ordering, word-boundary completeness, or GPU text claims.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ParagraphHitTestMapTest --tests org.graphiks.kanvas.text.TextStackSurfaceTest.exposesExpectedPureKotlinTextStackTypes
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Gate

This evidence closes bounded selection and hit-test behavior only. It does not
yet claim paragraph-owned bidi visual ordering, explicit word-boundary query
APIs, full grapheme/word boundary dumps beyond hit-test snapping, complete
paragraph layout parity, CPU oracle parity, or GPU text support.
