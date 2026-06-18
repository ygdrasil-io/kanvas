---
id: "KFONT-M5-005"
title: "Add cluster safety regression suite"
status: "done"
milestone: "M5"
priority: "P0"
owner_area: "unicode"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M5-002", "KFONT-M5-003", "KFONT-M5-004"]
legacy_gate: ["scaledemoji"]
---

# KFONT-M5-005 - Add cluster safety regression suite

## PM Note

Ce ticket verrouille les cas qui cassent le texte visible, notamment emoji, marques et scripts complexes, avant les tickets de shaping et fallback.

## Problem

Even when segmentation, bidi, and script itemization pass individually, later shaping can regress if clusters are split between runs, fallback boundaries, or bidi/script transitions. The `scaledemoji` legacy gate also needs explicit cluster-safe evidence before emoji shaping and fallback work can claim progress.

## Scope

- Add regression fixtures that combine grapheme clusters, bidi runs, script itemization, variation selectors, emoji ZWJ sequences, combining marks, and fallback-sensitive missing glyph cases.
- Assert invariants that no grapheme cluster is split by bidi run boundaries, script run boundaries, fallback candidate ranges, or shaping input slices.
- Produce a `cluster-safety-report.json` that references `unicode-segments.json`, `bidi-runs.json`, and `script-runs.json` by hash.
- Include positive and refusal fixtures for emoji ZWJ, VS15/VS16, skin tone modifiers, Arabic marks, Devanagari clusters, Thai marks, CJK variation selectors, and mixed LTR/RTL text.
- Preserve the `scaledemoji` legacy gate as fixture-gated until emoji sequence and fallback behavior have reviewed evidence.

## Non-Goals

- Do not implement color emoji rendering or emoji glyph artifact planning.
- Do not implement GSUB/GPOS substitutions, fallback catalog selection, or paragraph layout.
- Do not retire the `scaledemoji` gate; this ticket only supplies cluster-safety evidence needed by later work.
- Do not use platform emoji segmentation as a normative oracle.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class ClusterSafetyFixture(
    val name: String,
    val input: TextInput,
    val expectedClusters: List<IntRange>,
    val requiredInvariants: Set<ClusterInvariantKind>,
    val legacyGate: String?,
)

data class ClusterInvariantResult(
    val invariant: ClusterInvariantKind,
    val clusterRange: IntRange,
    val segmentRanges: List<IntRange>,
    val passed: Boolean,
    val diagnostic: RouteDiagnostic?,
)

class ClusterSafetySuite {
    fun evaluate(fixture: ClusterSafetyFixture, facts: TextSegmentationFacts): ClusterSafetyReport
}
```

## Acceptance Criteria

- [x] Combined segmentation/bidi/script fixtures prove that cluster ranges are not split by downstream run boundaries.
- [x] Emoji ZWJ, emoji modifier, VS15/VS16, Arabic mark, Devanagari conjunct, Thai tone mark, CJK variation selector, and mixed RTL/LTR fixtures are present.
- [x] At least one negative fixture intentionally splits a cluster and emits `text.shaping.cluster-invariant-failed`.
- [x] `cluster-safety-report.json` records source dump hashes, invariant names, pass/fail status, affected ranges, and legacy gate references.
- [x] `scaledemoji` remains fixture-gated until later emoji shaping/fallback tickets add route evidence beyond cluster safety.

## Required Evidence

- `cluster-safety-report.json` linked to `unicode-segments.json`, `bidi-runs.json`, and `script-runs.json` by content hash.
- Fixtures: `cluster-emoji-family-zwj.txt`, `cluster-emoji-skin-tone.txt`, `cluster-vs15-vs16.txt`, `cluster-arabic-mark.txt`, `cluster-devanagari-conjunct.txt`, `cluster-thai-tone.txt`, `cluster-cjk-variation-selector.txt`, `cluster-cjk-ivs-han.txt`, `cluster-cjk-ivs-mixed-script.txt`, `cluster-cjk-ivs-isolated.txt`, `cluster-mixed-bidi.txt`, `cluster-negative-split.txt`.
- Diagnostics asserted in tests: `text.shaping.cluster-invariant-failed`, `text.shaping.emoji-sequence-unsupported`, `text.shaping.unicode-data-version-mismatch`.
- Dashboard note showing `scaledemoji` is still blocked on emoji shaping/fallback/rendering evidence, not merely segmentation.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.cluster-split-forbidden`, `text.shaping.emoji-sequence-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `fixture-gated` until the listed evidence and validation pass.
- Legacy gate mapping remains visible in dashboard output until the ticket evidence retires it explicitly.

## Dashboard Impact

- Expected row: `Add cluster safety regression suite`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*ClusterSafety*' --tests '*Grapheme*' --tests '*Bidi*' --tests '*ScriptItem*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Status Notes

- `proposed`: Regression suite depends on the concrete M5 segmentation, bidi, and itemization outputs.
- Move to `ready` only after fixture list and legacy gate wording are reviewed.
- `done` (2026-06-18): merged PR `#1730` now closes with checked-in broader CJK IVS review coverage via `cluster-cjk-ivs-han.txt`, `cluster-cjk-ivs-mixed-script.txt`, and `cluster-cjk-ivs-isolated.txt`, while keeping the bounded context row `cluster-cjk-variation-selector.txt`. `cluster-safety-report.json` links checked-in `unicode-segments`, `bidi-runs`, and `script-runs` dumps by content hash, proves stable grapheme/bidi/script boundary handling across Arabic/Devanagari/Thai/CJK/emoji/bidi cases plus a synthetic negative split, and records stable `text.shaping.script-run-ambiguous` refusals for the supplementary-IVS rows instead of implying mixed-script or full CJK shaping support. `KFONT-M7-004` supplies the explicit `text.shaping.emoji-sequence-unsupported` refusal row and fallback-boundary evidence; no remaining gate remains on `KFONT-M5-005` itself.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M5`
- `area:unicode`
- `claim:fixture-gated`
