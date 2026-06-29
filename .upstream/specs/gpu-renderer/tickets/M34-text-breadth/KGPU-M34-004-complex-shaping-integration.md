---
id: KGPU-M34-004
title: "Complex shaping integration"
status: blocked
milestone: M34
priority: P1
owner_area: text
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "legacy drawText"
---

# KGPU-M34-004 - Complex shaping integration

## PM Note

Le shaping complexe et le BiDi (UAX #9) sont implémentés et testés dans le text
stack (fixtures arabe/devanagari/thai/CJK). Les facts de shaping (script,
bidiLevel) sont portés par `GPUGlyphRunDescriptor` jusqu'au handoff GPU. La
consommation GPU de ces facts pour l'ordre de paint (`GPUBiDiRunPlan`,
`GPUDrawLayerPlanner`) et le rendu des scripts complexes restent
DependencyGated (M6 par-script, exécution GPU M10/M11).

## Claim Split & Re-Scope (2026-06-29)

Audit `fichier:ligne` : les artefacts text-stack supposés manquants existent en
réalité. Le motif « gated on pure-kotlin-text shaping/BiDi output artifacts »
est faux et corrigé.

**Implémenté mais non promu — reste `DependencyGated` (handoff + facts portés + refus stable) :**

- Moteur shaping OpenType (segmentation → script runs → bidi → cmap → GSUB →
  GPOS → clusters) :
  `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/ShapingTypes.kt:684`.
- Résolveur BiDi UAX #9 (X2–X9, W1–W7, N0–N2, run building) :
  `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/BidiSegmentation.kt:84`.
- Fixtures arabe / devanagari / thai-CJK :
  `font/text/src/test/kotlin/org/graphiks/kanvas/text/ArabicShapingFixtureTest.kt`,
  `DevanagariShapingFixtureTest.kt`, `ThaiCjkBoundaryFixtureTest.kt`.
- Facts de shaping portés au handoff GPU : `GPUGlyphRunDescriptor.script` et
  `.bidiLevel`
  (`font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifacts.kt:86-87`).
- Validation : `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/ShapingIntegrationHandoffRouteTest.kt` (3 tests PASSED).

**Dependency-Gated (non livré) :**

- Contrats GPU `GPUShapingIntegrationContract`, `GPUBiDiRunPlan`,
  `GPUScriptComplexityClass`, `GPUTextDirection` : absents (sketch de spec).
- Consommation BiDi pour l'ordre de paint : `GPUDrawLayerPlanner` est un stub
  (`gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/LayerContracts.kt:653` = `TODO`).
- Politique de budget atlas CJK ; code
  `unsupported.text.shaping_script_unavailable`.
- Rendu GPU des scripts complexes (par-script M6, exécution GPU M10/M11).
  `product_activation` reste `false`.

## Problem

Complex scripts (Arabic, Devanagari, Thai, etc.) and bidirectional text
require shaping engines and BiDi algorithms that produce per-run direction,
cluster maps, and script classifications. The GPU renderer must consume these
facts for draw-layer paint ordering and atlas budget policy, but must not
implement shaping, BiDi, or script detection itself.

## Scope

- `GPUShapingIntegrationContract` — script, language, direction (LTR/RTL/TTB),
  BiDi levels, cluster map.
- `GPUBiDiRunPlan` — per-run BiDi level, visual order index, reordering token.
- `GPUScriptComplexityClass` — Simple, Complex, CJK classification.
- Consumed by `GPUDrawLayerPlanner` for paint order.

## Non-Goals

- No shaping, BiDi, or script detection in `:gpu-renderer`.
- No ICU, HarfBuzz, or CoreText integration in `:gpu-renderer`.
- No glyph substitution or reordering — the text stack delivers final glyph
  sequences.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- [`GFX-SUBRUN-DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24); Carry a subspan of an atlas subrun, mask bounds, mask-to-device matrix, glyph range, SDF/LCD metadata, and renderer data as geometry.
- [`GFX-DRAWLIST-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-record) - source [DrawList.cpp:21](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:21); One high-level draw expands to one sort key per RenderStep; each key combines render step ID, paint ID, uniform data, and texture binding data.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
enum class GPUTextDirection {
    LTR, RTL, TTB,
}

enum class GPUScriptComplexityClass {
    Simple, Complex, CJK,
}

data class GPUShapingIntegrationContract(
    val script: String,
    val language: String?,
    val direction: GPUTextDirection,
    val bidiLevels: List<Int>,
    val clusterMap: Map<Int, Int>,
)

data class GPUBiDiRunPlan(
    val runIndex: Int,
    val bidiLevel: Int,
    val visualOrderIndex: Int,
    val direction: GPUTextDirection,
)
```

## Acceptance Criteria

> Scope (2026-06-29) : le sous-scope borné « Claim Split » (facts de shaping
> portés au handoff) est implémenté et testé, mais ne promeut pas le ticket. La
> consommation BiDi GPU et le rendu des scripts complexes ci-dessous restent
> `DependencyGated` (M6/M10/M11) — le ticket reste `blocked`.

- [ ] Text stack emits per-run shaping facts (script, direction, BiDi levels).
- [ ] GPU consumes `GPUBiDiRunPlan` for paint order.
- [ ] CJK class → atlas budget policy adjustment.
- [ ] Unsupported script → `RefuseDiagnostic`.

## Required Evidence

> Scope borné couvert par `ShapingIntegrationHandoffRouteTest`. Les preuves
> ci-dessous (dump contrat GPU, `GPUBiDiRunPlan`, budget CJK) restent
> `DependencyGated` (M6/M10/M11).

- `GPUShapingIntegrationContract` dump with Arabic or Devanagari script.
- `GPUBiDiRunPlan` dump for a mixed LTR/RTL run.
- Refusal fixture: script unsupported by text stack.
- CJK atlas budget policy evidence (budget vs. simple script budget).

## Fallback / Refusal Behavior

- Unsupported script → `unsupported.text.shaping_script_unavailable`.
- No CPU texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.text.shaping-integration`
- Expected classification: `DependencyGated` (consommation BiDi GPU + rendu
  scripts complexes). Shaping/BiDi text-stack + facts portés au handoff
  implémentés/testés mais ne promeuvent pas le ticket.
- Claim promotion allowed: no — aucun claim de consommation BiDi GPU ni de rendu
  de scripts complexes.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ShapingIntegration*'
```

## Status Notes

- `proposed`: Initial ticket. Promotion to `ready` requires text stack
  shaping/BiDi output artifacts.
- `proposed → blocked` (2026-06-28): Blocked on pure-kotlin-text shaping/BiDi output artifacts.
- `reste blocked` (2026-06-29): correction du motif. Le motif « pure-kotlin-text
  shaping/BiDi output artifacts » est faux : moteur shaping OpenType + BiDi
  UAX #9 + fixtures arabe/devanagari/thai-CJK sont livrés. Le ticket **reste
  `blocked` / `DependencyGated`** car l'évidence de rendu GPU est toujours KO
  (`product_activation: false`, contrats GPU `GPUBiDiRunPlan` /
  `GPUShapingIntegrationContract` absents, `GPUDrawLayerPlanner` = stub `TODO`).
  Le sous-scope borné facts de shaping
  (`GPUGlyphRunDescriptor.script` / `bidiLevel`) portés au handoff est implémenté
  et testé (`ShapingIntegrationHandoffRouteTest`) mais ne promeut pas le ticket.
  Vrai gate : exécution GPU M6/M10/M11.

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
