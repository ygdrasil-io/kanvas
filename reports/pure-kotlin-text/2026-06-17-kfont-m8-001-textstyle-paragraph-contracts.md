# KFONT-M8-001 - TextStyle and paragraph style contracts

Status: done; deterministic contract evidence refreshed.

## Scope

- expand the paragraph input contract with rich `TextStyle`, `ParagraphStyle`, and `PlaceholderStyle` facts
- emit deterministic `paragraph-input.json` snapshots with `unicodeVersion`, `inputHash`, style runs, placeholder runs, and refusal diagnostics
- refuse invalid paragraph input before shaping without promoting downstream paragraph support claims

## Files

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ParagraphStyleContractTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/paragraph-input.json`
- `reports/font/fixtures/expected/paragraph/paragraph-input-goldens.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-001-expand-textstyle-and-paragraph-style-contracts.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `ParagraphBuilder.build()` now returns immutable input snapshots whose `dumpInput()` output pins Unicode version, stable input hash, style runs, placeholder runs, and input diagnostics.
- `TextStyle` now records font families, fallback preference, weight/width/slant, synthetic style policy, locale/script hints, feature settings, variation coordinates, palette selection, decorations, and spacing facts in the dump preimage.
- Input validation now rejects negative or non-finite numeric constraints, invalid variation coordinates, invalid/overlapping ranges, unsupported placeholder baselines, and unsupported strut policy before shaping.
- `paragraph-input.json` covers multiple style spans, OpenType feature settings, variation coordinates, palette selection, decoration facts, and placeholder metadata.
- `paragraph-input-goldens.json` now names `KFONT-M8-001` alongside `PKT-09C` and records the richer dump-field expectations plus the new invalid-font-size, invalid-variation-coordinate, and unsupported-strut-policy negative cases.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ParagraphStyleContractTest --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphInputGoldenPinsSchemaCasesAndNonClaims
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining Non-Claims

- This wave does not claim multi-style shaping segmentation, bidi visual line ordering, line breaking, ellipsis insertion, placeholder geometry layout, hit testing, selection boxes, glyph artifact planning, CPU oracle parity, or GPU text support.
- The pre-existing `TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesStandardFiLigatureWhenAvailable` failure still reproduces on a clean `origin/master` checkout and is outside `KFONT-M8-001`.
