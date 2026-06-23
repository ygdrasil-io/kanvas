---
id: "KFONT-M12-003"
title: "Add shaping and paragraph metrics"
status: "done"
milestone: "M12"
priority: "P1"
owner_area: "telemetry"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M12-001", "KFONT-M6-001", "KFONT-M8-002"]
legacy_gate: ["scaledemoji"]
---

# KFONT-M12-003 - Add shaping and paragraph metrics

## PM Note

Ce ticket montre où se situe le coût du texte complexe: segmentation, shaping, fallback, layout, hit testing et refus emoji.

## Problem

The shaping and paragraph specs require deterministic clusters, bidi facts, fallback runs, line breaking, ellipsis, selection boxes, and hit testing. M12 lacks telemetry that separates those costs. Without shaping and paragraph metrics, `scaledemoji` regressions, unsupported script refusals, or expensive line-break behavior can be hidden inside a single high-level text draw result.

## Scope

- Emit shaping metrics for Unicode segmentation, bidi analysis, script itemization, fallback lookup, GSUB lookup count/time, GPOS lookup count/time, glyph count, cluster count, fallback run count, and shaping diagnostic count.
- Emit paragraph metrics for style run count, line break opportunity count, shaped run count, line count, layout time, hit-test index build time, selection box query time, ellipsis attempts, and placeholder count.
- Cover fixtures for Latin kerning/ligature, Arabic joining, Devanagari reordering, mixed bidi, Thai marks, CJK variation selector, emoji VS/ZWJ or explicit `scaledemoji` refusal, rich text wrapping, and placeholder layout.
- Preserve range-level diagnostics so unsupported scripts, missing fallback, emoji sequence refusals, and paragraph-only bidi requirements remain visible in telemetry.
- Use KFONT-M12-001 aggregation and keep shaping metrics separate from paragraph metrics even when one validation command exercises both.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class ShapingMetricSample(
    val runId: ShapedRunID,
    val script: OpenTypeScriptTag,
    val glyphCount: Int,
    val clusterCount: Int,
    val fallbackRunCount: Int,
    val gsubLookupCount: Int,
    val gposLookupCount: Int,
    val segmentationMicros: MetricDistribution,
    val shapingMicros: MetricDistribution,
    val diagnostics: List<RouteDiagnostic>,
)

data class ParagraphMetricSample(
    val paragraphId: ParagraphLayoutID,
    val styleRunCount: Int,
    val lineBreakOpportunityCount: Int,
    val shapedRunCount: Int,
    val lineCount: Int,
    val layoutMicros: MetricDistribution,
    val hitTestIndexBuildMicros: MetricDistribution,
    val selectionQueryMicros: MetricDistribution,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [x] Shaping samples expose segmentation, bidi, script itemization, fallback lookup, GSUB, and GPOS costs as separate series.
- [x] Paragraph samples expose line breaking, line fitting, visual ordering, hit-test index, and selection query costs as separate series.
- [x] Glyph count and cluster count are serialized together so cluster inflation or collapse is visible.
- [x] Unsupported emoji, fallback, or script cases emit the original shaping/paragraph diagnostic plus a telemetry sample; they are not counted as successful shaped support.
- [x] The `scaledemoji` legacy gate remains open unless the required shaping, emoji fallback, color glyph, dashboard, and validation evidence are linked by the owning tickets.

## Required Evidence

- `shaping-metrics.json` covering Latin, Arabic, Devanagari, mixed bidi, Thai, CJK variation selector, and emoji VS/ZWJ or explicit refusal fixtures.
- `paragraph-metrics.json` covering rich text wrapping, max-lines ellipsis, mixed bidi lines, placeholders, hit testing, and selection boxes.
- Diagnostic snapshots for `text.shaping.emoji-sequence-unsupported`, `text.shaping.fallback-missing`, and paragraph layout refusals exercised by telemetry runs.
- Repeated cold/warm run samples showing stable run IDs, text range dimensions, and metric aggregation.
- Dashboard trend excerpt with separate shaping and paragraph rows and `scaledemoji` still visible when not retired.

## Fallback / Refusal Behavior

- Unsupported shaping ranges still produce telemetry with refusal diagnostics; they do not synthesize glyph positions through platform shaping.
- Paragraph-only bidi or line-break requirements must diagnose the missing paragraph context instead of falling back to single-run shaping.
- Legacy gate(s) `scaledemoji` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected rows: `Text shaping metrics`, `Paragraph layout metrics`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no; telemetry only makes shaping and paragraph risk visible.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.
- `done`: `shaping-metrics.json` and `paragraph-metrics.json` now serialize deterministic shaping/paragraph telemetry slices with explicit fallback and refusal diagnostics, advisory-only dashboard evidence, and no promotion of shaping, paragraph, GPU, or release-gate claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M12`
- `area:telemetry`
- `claim:tracked-gap`
- `legacy:scaledemoji`
