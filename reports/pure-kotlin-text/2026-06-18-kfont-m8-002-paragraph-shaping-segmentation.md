# 2026-06-18 - KFONT-M8-002 Paragraph Shaping Segmentation

## Scope

- `KFONT-M8-002 - Implement multi-style shaping segmentation`

## Files

- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-002-implement-multi-style-shaping-segmentation.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphShapingSegmentation.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ParagraphShapingSegmentationTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/paragraph-shaping-requests.json`
- `reports/font/fixtures/expected/paragraph/paragraph-shaping-requests-goldens.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `scripts/validate_pure_kotlin_text_dump_index.py`

## Evidence

- `DefaultParagraphShapingSegmenter` converts immutable paragraph input into
  deterministic `ParagraphShapingRequest` runs split by cluster boundaries,
  script, bidi level, shaping-affecting style facts, fallback-resolved
  typefaces, variation coordinates, and placeholder exclusion.
- Cluster-boundary widening is explicit and stable: style boundaries that cut
  across a grapheme cluster emit
  `text.paragraph.cluster-boundary-violation` and widen to the leading
  cluster-safe style range instead of splitting inside the cluster.
- Missing fallback resolution now leaves the affected range unshaped and emits
  `text.paragraph.fallback-unresolved` on the exact source text range.
- `BasicParagraphLayoutEngine` consumes paragraph shaping segments instead of a
  single line-wide style, records per-line `segmentIds`, and merges
  `text.paragraph.*` plus shaping diagnostics by source text range into the
  paragraph layout dump.
- Visible placeholders and other unshaped ranges now retain deterministic
  estimated line width instead of collapsing to zero-width geometry when no
  glyph run is emitted for that range.
- `paragraph-shaping-requests.json` checks in mixed Latin/Arabic, variation
  axis, placeholder, and fallback-range evidence without promoting line
  breaking, hit testing, bidi visual ordering, or GPU text claims.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ParagraphShapingSegmentationTest --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphLayoutEngineShapesPerParagraphSegmentsAndRecordsSegmentRefs --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphShapingRequestGoldenPinsMixedScriptFallbackAndClusterCases
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining Gate

This evidence closes deterministic paragraph shaping segmentation only. It does
not claim complete paragraph layout, bidi visual line ordering, UAX #14 line
breaking, ellipsis insertion, selection geometry, hit testing, placeholder
geometry layout, CPU oracle parity, native engine parity, or GPU text support.
