---
id: KGPU-M5-004
title: "Add filter DAG refusal matrix"
status: done
milestone: M5
priority: P1
owner_area: filters-validation
claim_impact: RefuseRequired
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M5-003]
legacy_gate: null
---

# KGPU-M5-004 - Add filter DAG refusal matrix

## PM Note

Ce ticket rend les filtres hors périmètre visibles comme refus stables.

## Problem

Arbitrary filter DAGs, unbounded intermediates, and picture prepasses must not
be mistaken for supported routes.

## Scope

- Add filter DAG support/refusal matrix.
- Add stable diagnostics for unsupported nodes, recursion, bounds, and
  intermediates.

## Non-Goals

- Do not implement arbitrary DAG support.
- Do not weaken thresholds.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`

## Graphite Algorithm References

- [`GFX-FILTER-RESOLVE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-resolve) - source [SkImageFilterTypes.cpp:1334](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkImageFilterTypes.cpp:1334); Use resolve/deferred decision points to define stable filter-DAG refusals.
- [`GFX-FILTER-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source [TextureUtils.cpp:720](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:720); Reference backend responsibilities without importing the full Skia filter DAG.
- [`GFX-SPECIAL-IMAGE-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-image-layer) - source [SpecialImage_Graphite.cpp:20](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/SpecialImage_Graphite.cpp:20); Keep intermediate image ownership explicit in refusal diagnostics.
- [`GFX-DST-USAGE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-usage) - source [PaintParams.cpp:51](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:51); Separate filter refusals from destination-read and blend requirements.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class FilterDagRefusal(val nodeKind: String, val reason: String)
```

## Acceptance Criteria

- [ ] Unsupported DAG variants map to stable diagnostics.
- [ ] PM output separates supportable bounded rows from refusals.
- [ ] Missing bounds/intermediate ownership blocks promotion.

## Required Evidence

- Refusal matrix and dashboard/report entries.

## Fallback / Refusal Behavior

Unsupported DAGs refuse; no CPU-rendered filter/layer fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.filter-dag-refusal`
- Expected classification: `RefuseRequired`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Refusal-only matrix implemented in
  `GPUFilterDagRefusalMatrix` and independently reviewed on 2026-06-15 with
  no findings after the non-promotion remediation. Fresh evidence:
  `rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.filters.FilterDagRefusalMatrixTest`
  and `rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:check` passed on
  2026-06-15. The matrix remains non-promotable even when rows are classified
  as supportable-bounded, does not imply simple filter node support from
  KGPU-M5-003, and keeps arbitrary DAGs, unbounded intermediates, picture
  prepass, runtime-effect-without-descriptor, and CPU-rendered filter textures
  refused.

## Linear Labels

- `gpu-renderer`
- `milestone:M5`
- `area:filters`
