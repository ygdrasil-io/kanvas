---
id: "KFONT-M7-004"
title: "Add cluster-safe fallback segmentation tests"
status: "done"
milestone: "M7"
priority: "P0"
owner_area: "fallback"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M5-005", "KFONT-M7-002", "KFONT-M6-001"]
legacy_gate: ["scaledemoji"]
---

# KFONT-M7-004 - Add cluster-safe fallback segmentation tests

## PM Note

Ce ticket garantit que le fallback change de fonte sans casser les clusters visibles, surtout pour emoji et scripts complexes.

## Problem

Fallback often happens at missing glyph boundaries, but the target requires cluster-safe behavior. Emoji ZWJ sequences, variation selectors, combining marks, Indic clusters, Arabic marks, and mixed-script clusters must never be split across fallback font runs unless a reviewed refusal says the whole cluster is unsupported.

## Scope

- Add fallback fixtures that reuse M5 cluster-safety cases and route them through fallback decision traces.
- Assert that `ResolvedFontRun` boundaries align with grapheme cluster boundaries and preserve original UTF-16 ranges.
- Cover emoji ZWJ, skin tone, VS15/VS16, combining Latin marks, Arabic mark clusters, Devanagari conjuncts, Thai tone marks, CJK variation selectors, and mixed bidi text.
- Emit `fallback-segmentation-report.json` linking cluster-safety, fallback decision, resolved run, and shaped run dumps.
- Keep `scaledemoji` fixture-gated until emoji shaping, fallback, and color/outline route evidence exists.

## Non-Goals

- Do not implement color emoji rendering, COLR/bitmap/SVG dispatch, or emoji glyph artifacts.
- Do not retire the `scaledemoji` legacy gate.
- Do not add host system font scanning.
- Do not use platform emoji fallback or browser segmentation as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class ClusterSafeFallbackCase(
    val name: String,
    val text: TextInput,
    val clusters: List<GraphemeCluster>,
    val requestedFamily: FontFamilyName,
    val expectedRunBoundaries: List<IntRange>,
    val expectedDiagnostics: Set<String>,
)

data class FallbackSegmentationInvariant(
    val clusterRange: IntRange,
    val resolvedRuns: List<ResolvedFontRun>,
    val passed: Boolean,
    val diagnostic: RouteDiagnostic?,
)
```

## Acceptance Criteria

- [ ] Fallback run boundaries never split a grapheme cluster in positive fixtures.
- [ ] Negative fixture that would require splitting an emoji ZWJ or combining-mark cluster refuses the whole cluster.
- [ ] `fallback-segmentation-report.json` links `cluster-safety-report.json`, `fallback-decision-trace.json`, `resolved-font-runs.json`, and `shaped-glyph-run.json`.
- [ ] `scaledemoji` remains explicitly fixture-gated because cluster-safe fallback is not color emoji rendering support.
- [ ] Host-dependent fallback is marked when any host-scanned source participates in a non-normative fixture.

## Required Evidence

- `fallback-segmentation-report.json` with input hash, Unicode version, cluster ranges, fallback run ranges, invariant results, dump hashes, legacy gate references, and diagnostics.
- `fallback-decision-trace.json`, `resolved-font-runs.json`, `cluster-safety-report.json`, and `shaped-glyph-run.json` references for every fixture.
- Fixtures: `fallback-cluster-emoji-zwj.txt`, `fallback-cluster-skin-tone.txt`, `fallback-cluster-vs15-vs16.txt`, `fallback-cluster-latin-mark.txt`, `fallback-cluster-arabic-mark.txt`, `fallback-cluster-devanagari.txt`, `fallback-cluster-thai.txt`, `fallback-cluster-cjk-vs.txt`, `fallback-cluster-negative-split.txt`.
- Diagnostics asserted in tests: `text.shaping.cluster-invariant-failed`, `text.shaping.emoji-sequence-unsupported`, `text.shaping.fallback-missing`, `font.fallback-glyph-unavailable`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.fallback.cluster-split-forbidden`, `text.fallback.emoji-fallback-unavailable`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `fixture-gated` until the listed evidence and validation pass.
- Legacy gate mapping remains visible in dashboard output until the ticket evidence retires it explicitly.

## Dashboard Impact

- Expected row: `Add cluster-safe fallback segmentation tests`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecision*'
rtk ./gradlew --no-daemon :font:text:test --tests '*FallbackSegmentation*' --tests '*ClusterSafety*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Status Notes

- `proposed`: Fallback cluster safety depends on M5 cluster invariants and M7 decision traces.
- `review`: `fallback-segmentation-report.json` now links the checked-in M5 cluster report and M7/M6 dump hashes to nine bounded fallback cluster fixtures. Positive Arabic, Devanagari, Thai, CJK, Latin-mark, skin-tone, VS15/VS16, and emoji ZWJ rows preserve whole-cluster run boundaries, while `fallback-cluster-negative-split.txt` keeps `scaledemoji` explicit and now records a whole-cluster refusal path with `text.fallback.cluster-split-forbidden`, `text.fallback.emoji-fallback-unavailable`, and `text.shaping.emoji-sequence-unsupported` without promoting emoji rendering or fallback support.
- `done`: the checked-in `fallback-segmentation-report.json` now carries dedicated per-fixture fallback asset refs for all nine bounded `fallback-cluster-*` cases plus a checked non-normative host-dependent marker row that references `host-dependent-system-fallback` without polluting the shared fallback dumps.
- Move to `ready` only after fixture list and legacy gate wording are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M7`
- `area:fallback`
- `claim:fixture-gated`
