---
id: KGPU-M34-004
title: "Complex shaping integration"
status: proposed
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

Le shaping complexe (arabe, devanagari, CJK) et le BiDi sont délégués au text
stack. Ce ticket définit le contrat d'intégration.

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

- `GFX-BIDI-RUN` from `../GRAPHITE-ALGORITHM-REFERENCES.md` — study BiDi run
  ordering and per-run direction propagation.
- `GFX-SHAPING-INTEGRATION` — study script complexity classification and
  atlas budget impact.
- Boundary: references are for algorithm study only; do not port Graphite or
  Ganesh and do not treat them as Kanvas acceptance criteria.

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

- [ ] Text stack emits per-run shaping facts (script, direction, BiDi levels).
- [ ] GPU consumes `GPUBiDiRunPlan` for paint order.
- [ ] CJK class → atlas budget policy adjustment.
- [ ] Unsupported script → `RefuseDiagnostic`.

## Required Evidence

- `GPUShapingIntegrationContract` dump with Arabic or Devanagari script.
- `GPUBiDiRunPlan` dump for a mixed LTR/RTL run.
- Refusal fixture: script unsupported by text stack.
- CJK atlas budget policy evidence (budget vs. simple script budget).

## Fallback / Refusal Behavior

- Unsupported script → `unsupported.text.shaping_script_unavailable`.
- No CPU texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.text.shaping-integration`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ShapingIntegration*'
```

## Status Notes

- `proposed`: Initial ticket. Promotion to `ready` requires text stack
  shaping/BiDi output artifacts.

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
