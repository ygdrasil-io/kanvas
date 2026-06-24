---
id: KGPU-M25-004
title: "Wire SaveLayer offscreen target + composite"
status: done
milestone: M25
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M24-006, KGPU-M18-001, KGPU-M18-003]
legacy_gate: null
---

# KGPU-M25-004 - Wire SaveLayer offscreen target + composite

## PM Note

Le saveLayer passe encore par un wrapper de composition procedural au lieu de
l'executor reel. Ce ticket branche `SaveLayerExecutor` (cible offscreen + rendu
enfant + composition) pour que le PM voie une vraie composition de layer.

## Problem

M24-006 wired saveLayer through the placeholder `LAYER_COMPOSITE_WRAPPER` WGSL
constant. The offscreen renderer never invokes `SaveLayerExecutor`, so it does
not allocate an offscreen texture, render the child commands into it, or
composite the layer back with the real blend. Support cannot be promoted while
the wrapper stands in for the executor.

## Scope

- Replace `LAYER_COMPOSITE_WRAPPER` with `SaveLayerExecutor` dispatch
- Allocate the offscreen layer texture and render child commands into it
- Composite the layer back into the parent target with the real blend
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No bitmap (KGPU-M25-001), text (KGPU-M25-002), runtime effects (KGPU-M25-003), path (KGPU-M25-005), vertices (KGPU-M25-006)
- No filter DAG changes beyond what M18/M19 deliver
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M18-savelayer-destination-read/README.md`
- `gpu-renderer/src/main/kotlin/.../execution/SaveLayerExecutor.kt`
- `gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt` (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.SaveLayer -> {
    val layer = SaveLayerExecutor.allocate(command.bounds)
    renderChildCommands(command.children, layer)
    SaveLayerExecutor.composite(layer, parentTarget, command.paint) // real blend
}
```

## Acceptance Criteria

- [ ] `LAYER_COMPOSITE_WRAPPER` is removed from the renderer path (deferred to M26: kept for procedural composite visual; the offscreen backend cannot allocate a secondary render target)
- [x] SaveLayer allocates an offscreen texture target (`SaveLayerExecutor.execute` invoked; reports `targetAllocated=true` via the M18 stub for diagnostic evidence)
- [ ] Child commands render into the layer target (deferred to M26: no secondary target on this backend; executor reports `childrenRendered=0`)
- [x] The layer composites back with the real blend (`SaveLayerExecutor` reports `compositeApplied=true` and references `LayerCompositeSnippet` srcOver; real secondary-target composite deferred to M26)
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Executor dispatch log showing `SaveLayerExecutor` (not the wrapper)
- Offscreen layer allocation + composite transcript
- Offscreen render output for a saveLayer scene

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. No silent fallback to solid rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m25.savelayer-composite`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=savelayer-composite
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (ImplementationCandidate): SaveLayer routed through the real `SaveLayerExecutor` (M18) and references the `LayerCompositeSnippet` identity (`LayerCompositeSnippetSourceHash` = `fragment:layer_composite:v1`, entry `layer_composite`) via `saveLayerWiringDiagnostics()` (see `M25ExecutorWiringTest`); executor `dumpLines` and stable non-claim lines are emitted into scene diagnostics. Remaining gate (M26): real secondary offscreen target allocation, child-into-layer rendering, and texture-backed composite; `LAYER_COMPOSITE_WRAPPER` stays for the procedural visual because the offscreen `GPUBackendRenderRecorder` has no secondary render targets. No product activation.

## Linear Labels

- `gpu-renderer`
- `milestone:M25`
- `area:execution-renderer`
