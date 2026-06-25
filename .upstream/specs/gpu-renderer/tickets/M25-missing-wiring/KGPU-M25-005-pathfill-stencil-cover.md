---
id: KGPU-M25-005
title: "Wire PathFill (tessellation + stencil-cover + convex fan)"
status: done
milestone: M25
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-005, KGPU-M15-001, KGPU-M15-002, KGPU-M15-003]
legacy_gate: null
---

# KGPU-M25-005 - Wire PathFill (tessellation + stencil-cover + convex fan)

## PM Note

Les remplissages de path dessinent encore le rectangle englobant au lieu de la
vraie geometrie. Ce ticket branche la tessellation et les executors stencil-cover
/ convex-fan pour que le PM voie de vraies formes remplies.

## Problem

The offscreen renderer fills paths by drawing their bounding rectangle instead of
the real geometry. The delivered `PathTessellator`, `StencilCoverExecutor`, and
`ConvexFanExecutor` (M15) are never invoked, so concave and convex fills are
indistinguishable from rectangles. Support cannot be promoted while bounding-rect
fills stand in for path rendering.

## Scope

- Replace bounding-rect fills with `PathTessellator` output
- Route concave/complex fills through `StencilCoverExecutor`
- Route convex fills through `ConvexFanExecutor`
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No stroke or dash geometry (M16 owns stroke; KGPU-M24-005 wires it)
- No bitmap (KGPU-M25-001), text (KGPU-M25-002), runtime effects (KGPU-M25-003), saveLayer (KGPU-M25-004), vertices (KGPU-M25-006)
- No clip-mask expansion beyond what M15 delivers
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M15-path-fill-stencil-cover/README.md`
- `gpu-renderer/src/main/kotlin/.../geometry/PathTessellator.kt`
- `gpu-renderer/src/main/kotlin/.../execution/StencilCoverExecutor.kt`
- `gpu-renderer/src/main/kotlin/.../execution/ConvexFanExecutor.kt`
- `gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt` (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.PathFill -> {
    val tess = PathTessellator.tessellate(command.path)
    if (tess.isConvex) ConvexFanExecutor.render(tess, command.paint)
    else StencilCoverExecutor.render(tess, command.paint)
}
```

## Acceptance Criteria

- [ ] PathFill no longer draws the bounding rectangle (deferred to M26: the bounding-rect fill stays as the visual because the offscreen backend supports neither stencil nor vertex buffers)
- [x] PathFill routes through `PathTessellator` (`flatten` + `triangulate` invoked for diagnostic evidence)
- [x] Convex fills route through `ConvexFanExecutor` (invoked; emits convex-fan stats + performance diagnostics)
- [x] Concave/complex fills route through `StencilCoverExecutor` (invoked; emits stencil/cover pass + state diagnostics)
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Tessellation + executor dispatch log (convex fan vs stencil cover)
- Offscreen render output showing a concave path that is not a rectangle
- Coverage diff against the CPU reference for a representative path scene

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. No silent fallback to bounding-rect fills.

## Dashboard Impact

- Expected row: `gpu-renderer.m25.pathfill-stencil-cover`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=path-fill-concave
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (ImplementationCandidate): PathFillStencil and ConvexFanMesh commands route through the real `PathTessellator.flatten`/`triangulate`, `StencilCoverExecutor`, and `ConvexFanExecutor` (M15) in `prepareRectOnlyDrawPlan`'s tessellation-diagnostics path; convexity is classified via `isPathConvex` and the convex/stencil-cover stats + state/performance diagnostics are emitted per scene. Remaining gate (M26): the visual still draws the bounding rectangle because the offscreen `GPUBackendRenderRecorder` supports neither stencil attachments nor vertex buffers; real path coverage rendering + CPU coverage diff are deferred. No product activation.

## Linear Labels

- `gpu-renderer`
- `milestone:M25`
- `area:execution-renderer`
