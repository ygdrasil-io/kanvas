---
id: "KFONT-M7-002"
title: "Add fallback decision trace"
status: "done"
milestone: "M7"
priority: "P0"
owner_area: "fallback"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M7-001", "KFONT-M6-001"]
legacy_gate: null
---

# KFONT-M7-002 - Add fallback decision trace

## PM Note

Ce ticket rend chaque choix de fonte lisible, y compris les fontes tentees et les refus de glyph manquant.

## Problem

Fallback is currently too easy to hide behind a final glyph run. The target requires every missing glyph, family mismatch, script fallback, locale hint, emoji preference, and refusal to be serialized so support claims can prove there is no hidden platform fallback.

## Scope

- Define `FallbackDecisionTrace` and `ResolvedFontRun` dumps consumed by shaping and diagnostics.
- Rank bundled catalog candidates by requested family, generic family, style, script, locale, emoji/color capability, variation support, and glyph coverage.
- Record candidate list, scoring reasons, selected typeface, rejected candidates, missing glyphs, and final refusal code.
- Emit `fallback-decision-trace.json` and `resolved-font-runs.json` for positive, partial, and refusal cases.
- Propagate fallback facts into `TextRouteDiagnostics` and M6 `ShapedGlyphRun` evidence.

## Non-Goals

- Do not implement variable-axis-specific compatibility; KFONT-M7-003 owns axis-aware fallback.
- Do not add host system scans; KFONT-M7-005 owns host-dependent sources.
- Do not split clusters for fallback; KFONT-M7-004 owns cluster-safety tests.
- Do not render or rasterize selected glyphs.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class FallbackDecisionTrace(
    val request: FontFallbackRequest,
    val candidates: List<FallbackCandidateScore>,
    val selected: FallbackDecision?,
    val diagnostics: List<RouteDiagnostic>,
)

data class FallbackDecision(
    val typefaceId: TypefaceID,
    val coveredTextRange: IntRange,
    val coveredClusters: IntRange,
    val reason: FallbackReason,
    val hostDependent: Boolean,
)

data class ResolvedFontRun(
    val textRange: IntRange,
    val clusterRange: IntRange,
    val typefaceId: TypefaceID,
    val fallbackReason: FallbackReason?,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [x] Missing glyph fixture records every attempted typeface, coverage check, score reason, selected fallback, and final resolved run.
- [x] Requested family and generic family fixtures distinguish family fallback from glyph fallback.
- [x] Script and locale hints influence candidate ranking and appear in the trace.
- [x] Emoji preference is recorded as a candidate fact without claiming emoji rendering.
- [x] Complete miss emits `font.fallback-glyph-unavailable` or `font.fallback-family-unavailable` with the affected text and cluster range.

## Required Evidence

- `fallback-decision-trace.json` with request facts, candidate scores, selected/rejected candidates, missing glyph IDs/code points, script/locale/emoji reasons, and diagnostics.
- `resolved-font-runs.json` with text ranges, cluster ranges, selected typeface IDs, host-dependent markers, fallback reasons, and refusal `diagnosticRanges` for bounded complete-miss evidence.
- `fallback-shaped-glyph-run.json` with per-fixture `decisionTraceRef`, `resolvedRunsRef`, `fixtureAssetRef`, selected/rejected typeface facts, bounded glyph runs, and shaping diagnostics for the same deterministic fallback slice.
- Fixtures: `fallback-family-generic.json`, `fallback-script-arabic.json`, `fallback-locale-serbian.json`, `fallback-emoji-preference.json`, `fallback-missing-glyph.json`, `fallback-family-unavailable.json`.
- Diagnostics asserted in tests: `font.fallback-family-unavailable`, `font.fallback-glyph-unavailable`, `text.shaping.fallback-missing`, `text.shaping.script-unsupported`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.fallback.family-missing`, `text.fallback.glyph-missing`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add fallback decision trace`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecisionDump*'
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecision*'
rtk ./gradlew --no-daemon :font:core:test
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.FallbackShapingEvidenceTest
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Status Notes

- `proposed`: Decision trace builds on the bundled catalog and M6 shaping contract.
- Move to `ready` only after scoring fields and dump schemas are reviewed.
- `done`: `fallback-decision-trace.json`, `resolved-font-runs.json`, `fallback-shaped-glyph-run.json`, and the dedicated per-fixture fallback assets now cover deterministic generic-family, script, locale, emoji, missing-glyph, and family-unavailable cases with stable candidate reasons, complete-miss cluster ranges, refusal `diagnosticRanges`, and shaping-linked `decisionTraceRef`/`resolvedRunsRef`/`fixtureAssetRef` facts. Broader fallback promotion remains owned by `KFONT-M7-003`, `KFONT-M7-004`, and `KFONT-M7-005`; this ticket stays bounded fallback-trace evidence only and does not promote cluster-safe, platform, renderer, or GPU fallback claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M7`
- `area:fallback`
- `claim:tracked-gap`
