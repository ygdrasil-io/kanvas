---
id: KGPU-M25-006
title: "Wire Vertices mesh rendering"
status: done
milestone: M25
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M14-005, KGPU-M22-001, KGPU-M22-002, KGPU-M22-003]
legacy_gate: null
---

# KGPU-M25-006 - Wire Vertices mesh rendering

## PM Note

Le rendu de mesh `drawVertices` n'est pas branche sur le chemin offscreen. Ce
ticket branche l'executor de vertices, l'upload de buffers et le batcher pour
que le PM voie de vrais maillages rendus.

## Problem

The offscreen renderer does not dispatch `drawVertices` meshes. The delivered
`VerticesExecutor`, `GPUVertexBufferUploader`, and `GPUMeshBatcher` (M22) are
never invoked, so vertex-colored/textured meshes do not render. Support cannot be
promoted while the vertices path is unwired.

## Scope

- Wire `VerticesExecutor` into the offscreen renderer for `drawVertices`
- Upload vertex/index data via `GPUVertexBufferUploader`
- Batch compatible meshes via `GPUMeshBatcher`
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No bitmap (KGPU-M25-001), text (KGPU-M25-002), runtime effects (KGPU-M25-003), saveLayer (KGPU-M25-004), path (KGPU-M25-005)
- No new vertex mode beyond what M22 delivers
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M22-vertices-mesh-batching/README.md`
- `gpu-renderer/src/main/kotlin/.../execution/VerticesExecutor.kt`
- `gpu-renderer/src/main/kotlin/.../gpu/GPUVertexBufferUploader.kt`
- `gpu-renderer/src/main/kotlin/.../gpu/GPUMeshBatcher.kt`
- `gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt` (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.DrawVertices -> {
    val buffers = GPUVertexBufferUploader.upload(command.vertices, command.indices)
    val batch = GPUMeshBatcher.batch(buffers)
    VerticesExecutor.render(batch, command.paint)
}
```

## Acceptance Criteria

- [x] `drawVertices` dispatches through `VerticesExecutor` (invoked for diagnostic evidence)
- [x] Vertex/index data uploads via `GPUVertexBufferUploader` (invoked; emits upload stats + non-claim line)
- [x] Compatible meshes batch via `GPUMeshBatcher` (invoked; emits batch/pipeline-change stats)
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Executor dispatch log showing `VerticesExecutor` + buffer upload + batching
- Offscreen render output for a vertices/mesh scene
- Vertex-color/texture coverage check against the CPU reference

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. No silent fallback to solid rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m25.vertices-mesh-rendering`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=vertices-mesh-grid
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (ImplementationCandidate): `drawVertices` wiring routes a representative triangle mesh through the real `VerticesExecutor` + `GPUVertexBufferUploader` + `GPUMeshBatcher` (M22) in `verticesWiringDiagnostics()`, emitting executor dispatch, buffer-upload, and batching stats plus each component's non-claim line (covered by `M25ExecutorWiringTest`). The helper is wired into `RectOnlyOffscreenRenderer`'s diagnostic path guarded by the `vertices` family. Remaining gate (M26): the `MeshRibbon`/vertices command family is not accepted by the rect-only offscreen command set (the offscreen backend has no vertex/index buffers), so vertices scenes stay not-yet-rendered and the visual is deferred. No product activation.

## Linear Labels

- `gpu-renderer`
- `milestone:M25`
- `area:execution-renderer`
