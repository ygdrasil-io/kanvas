---
id: KGPU-M1-003
title: "Add controlled first-route product flag"
status: ready
milestone: M1
priority: P0
owner_area: gpu-raster-adapter
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001, KGPU-M1-002]
legacy_gate: "legacy drawRect"
---

# KGPU-M1-003 - Add controlled first-route product flag

## PM Note

Ce ticket ajoute un interrupteur contrôlé pour tester la première route sans
perdre le retour arrière legacy.

## Problem

The first route has shadow and diagnostic evidence, but product code needs an
explicit flag and rollback path before default behavior can change.

## Scope

- Add a scoped first-route product flag for solid `FillRect`.
- Keep legacy route available.
- Emit diagnostics distinguishing shadow, diagnostic, and product-flagged route.

## Non-Goals

- Do not enable the flag by default.
- Do not support unsupported nearby variants.

## Spec Sources

- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-DRAWCONTEXT-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-record) - source [DrawContext.cpp:155](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:155); Use draw admission and barrier decisions to define what the flag actually enables.
- [`GFX-TASKLIST`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tasklist) - source [TaskList.cpp:19](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/TaskList.cpp:19); Reference ordered task replay when validating flag-controlled execution.
- [`GFX-RENDERPASS-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderpass-task) - source [RenderPassTask.cpp:128](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/RenderPassTask.cpp:128); Use render-pass preparation/replay as evidence for adapter-backed promotion boundaries.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class FirstRouteFlagState(val enabled: Boolean, val routeScope: String)
```

## Acceptance Criteria

- [ ] Flag scope is limited to accepted solid `FillRect`.
- [ ] Legacy fallback remains available and visible.
- [ ] Unsupported variants keep stable refusals or legacy policy behavior.

## Required Evidence

- Shadow adapter test output.
- Flag-on/flag-off route diagnostics.
- Rollback dry-run or targeted regression test.

## Fallback / Refusal Behavior

If validation fails, the flag remains off and default legacy behavior is
preserved.

## Dashboard Impact

- Expected row: `gpu-renderer.first-route-product-flag`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until flag policy is accepted.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `ready`: KGPU-M1-001 is accepted and KGPU-M1-002 is done with independent
  review approval. Scope remains limited to adding an explicit controlled flag
  for solid `FillRect`; the flag must stay disabled by default, legacy behavior
  must remain available, and no unsupported variants or broad GPU route support
  may be claimed.

## Linear Labels

- `gpu-renderer`
- `milestone:M1`
- `area:gpu-raster-adapter`
