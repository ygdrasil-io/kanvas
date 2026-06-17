# KFONT-M8-006 - Placeholder layout metrics

Status: implemented with local diff review; independent subagent review could
not be started because the session hit the agent thread limit.

## Scope

- add bounded placeholder geometry to paragraph layout output
- make placeholder line-height participation explicit in `PlaceholderStyle`
- expose stable placeholder IDs, bounds, baseline offset, and alignment facts
- pin deterministic placeholder layout evidence without promoting selection or
  hit-test claims

## Files

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ParagraphStyleContractTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/paragraph-input.json`
- `reports/font/fixtures/expected/paragraph/paragraph-layout.json`
- `reports/font/fixtures/expected/paragraph/placeholder-layout.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-006-implement-placeholder-layout-metrics.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `PlaceholderStyle` now serializes `participatesInLineHeight`, so paragraph
  input evidence states whether a placeholder is allowed to expand line
  metrics.
- `BasicParagraphLayoutEngine` now emits deterministic `PlaceholderBox`
  entries with `placeholderId`, `textRange`, `lineIndex`, `left`, `top`,
  `right`, `bottom`, `baselineOffset`, `alignment`, `baseline`, and
  `participatesInLineHeight`.
- Shared paragraph layout dumps now expose `placeholderBoxes`, and
  `paragraph-layout.json` records the new field even when no placeholder is
  present.
- `placeholder-layout.json` now pins four bounded alignment cases:
  baseline-aligned, above-baseline, below-baseline, and middle-aligned
  placeholders.
- Focused tests prove line-metric expansion for participating placeholders and
  prove that non-participating placeholders keep their geometry without
  stretching line height.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ParagraphStyleContractTest --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphLayoutEngineComputesPlaceholderBoxesAndExpandsLineMetrics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphLayoutEngineKeepsNonParticipatingPlaceholderOutOfLineHeight --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutGoldenMatchesRepoFixture --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphPlaceholderLayoutGoldenMatchesRepoFixture --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphPlaceholderLayoutGoldenPinsCasesAndNonClaims
rtk ./gradlew --no-daemon :font:text:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining Non-Claims

- This wave does not claim selection boxes, hit-testing APIs, full bidi
  visual-order parity, placeholder rendering support, Skia Paragraph parity,
  CPU oracle parity, or GPU text support.
- `KFONT-M8-005` still owns selection/hit-test consumption of placeholder
  geometry.
