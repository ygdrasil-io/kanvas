# KFONT-M8-002 - Multi-style shaping segmentation

Status: done; bounded segmentation evidence refreshed.

## Scope

- split paragraph layout shaping into deterministic `ParagraphShapingRequest` runs
- coalesce adjacent shaping-equivalent style ranges without crossing grapheme clusters
- exclude placeholders from shaping requests while preserving line-width accounting
- pin `paragraph-shaping-requests.json` and `paragraph-layout.json` without promoting complete paragraph or fallback-policy support

## Files

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphShapingSegmentation.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/font/fixtures/expected/paragraph/paragraph-layout.json`
- `reports/font/fixtures/expected/paragraph/paragraph-shaping-requests.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/KFONT-M8-002-implement-multi-style-shaping-segmentation.md`
- `.upstream/specs/pure-kotlin-text/tickets/M8-paragraph-engine/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `BasicParagraphShapingSegmenter` now walks grapheme clusters, excludes placeholder ranges from shaping requests, and emits deterministic `ParagraphShapingRequest` values keyed by shaping-affecting `TextStyle` facts plus paragraph direction.
- Adjacent clusters with identical shaping-affecting facts now coalesce into a single request, so style boundaries do not create redundant shaping work.
- A style boundary that intersects a grapheme cluster now widens to the leading style range and emits the stable `text.paragraph.cluster-boundary-violation` diagnostic instead of silently splitting the cluster.
- `BasicParagraphLayoutEngine` now shapes each request independently, merges paragraph and shaping diagnostics, restores placeholder widths into the line metrics, and records `segmentRefs` in the layout dump.
- `paragraph-shaping-requests.json` pins a mixed Latin/Arabic plus placeholder case and a cluster-boundary negative case, including requested family lists, variation axes, placeholder ranges, and non-claims.
- `paragraph-layout.json` now pins the resulting segmented layout dump with deterministic input hash, placeholder accounting, and line-level `segmentRefs`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphLayoutEngineSplitsMixedStyleRangesIntoSeparateShapingRequests --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphShapingSegmenterCoalescesAdjacentEquivalentStyleRuns --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicParagraphShapingSegmenterReportsClusterBoundaryViolationsWithoutSplittingCluster --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutResultDumpsCurrentSemanticLayoutFactsDeterministically --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphLayoutGoldenMatchesRepoFixture --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphShapingRequestsGoldenMatchesRepoFixture --tests org.graphiks.kanvas.text.TextStackSurfaceTest.paragraphShapingRequestsGoldenPinsCasesAndNonClaims
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining Non-Claims

- This wave does not claim complete fallback selection policy, complete bidi visual ordering, UAX #14 line breaking, ellipsis insertion, hit testing, selection boxes, placeholder geometry layout, CPU oracle parity, native text-engine parity, or GPU text support.
- Requested family/typeface facts are now segmented and dumped deterministically, but later tickets still own full fallback-policy behavior, bidi visual line ordering, and richer paragraph semantics.
