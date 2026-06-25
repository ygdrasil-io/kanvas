---
id: KGPU-M28-004
title: "Wire vertices real GPU rendering"
status: done
milestone: M28
priority: P0
owner_area: execution-backend
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M28-003]
legacy_gate: null
---

# KGPU-M28-004 - Wire vertices real GPU rendering

## PM Note

Les scenes de vertices tournent actuellement sans rendu visible parce que le
backend n'a pas de vertex/index buffers. Avec les buffers disponibles (M28-003),
ce ticket branche le rendu reel des meshs pour que le PM voie les triangles
colores et les ribbons de profondeur.

## Problem

KGPU-M25-006 wired `VerticesExecutor`, `GPUVertexBufferUploader`, and
`GPUMeshBatcher` into the offscreen renderer, but the vertices and mesh output
is never submitted to the GPU because the offscreen backend lacked vertex/index
buffer support. Now that M28-003 delivers buffer creation and indexed draw
capabilities, the vertices path must be upgraded to produce real mesh rendering
output.

## Scope

- Wire `VerticesExecutor` output into real vertex buffers via `createVertexBuffer`
- Wire index buffer output via `createIndexBuffer`
- Wire mesh scene rendering through `drawIndexed` on the GPU backend
- Regenerate vertices-color-mesh scene PNG with real triangle mesh output
- Regenerate mesh-ribbon-depth scene PNG with real ribbon mesh output
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No new vertex attribute types beyond position + color (texcoords deferred)
- No line-strip topology (triangle-list only)
- No mesh batching optimization changes
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/26-draw-vertices-mesh-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M22-vertices-mesh-batching/README.md`
- `.upstream/specs/gpu-renderer/tickets/M25-missing-wiring/KGPU-M25-006-vertices-mesh-rendering.md`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/GpuNativeOffscreenRenderer.kt`

## Design Sketch

```kotlin
is SceneCommand.Vertices -> {
    val executor = VerticesExecutor.prepare(command.vertices)
    val vertexBuffer = createVertexBuffer(executor.vertexData, executor.vertexLayout)
    val indexBuffer = createIndexBuffer(executor.indexData)
    drawIndexed(
        pipeline = resolveVerticesPipeline(executor.vertexLayout),
        vertexBuffer = vertexBuffer,
        indexBuffer = indexBuffer,
        vertexCount = executor.vertexCount,
        instanceCount = 1,
        uniforms = gatherVerticesUniforms(command),
    )
}
```

## Acceptance Criteria

- [ ] VerticesExecutor vertex data is uploaded to GPU vertex buffers
- [ ] VerticesExecutor index data is uploaded to GPU index buffers
- [ ] Mesh scenes dispatch indexed draw calls with correct pipeline + buffers
- [ ] `vertices-color-mesh` scene PNG shows real colored triangle mesh output
- [ ] `mesh-ribbon-depth` scene PNG shows real ribbon mesh output
- [ ] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Vertex buffer upload log for vertices-color-mesh scene (vertex count, byte count)
- Indexed draw dispatch log for mesh-ribbon-depth scene (triangle count, instance count)
- Offscreen render output showing colored triangle mesh (not bounding rectangles)
- Offscreen render output showing ribbon mesh with depth-ordered triangles
- Vertices diagnostics: vertex layout, index format, draw call count per scene

## Fallback / Refusal Behavior

If vertex/index buffer creation failed (M28-003), the renderer emits a
`buffer-unavailable` diagnostic and scenes remain not-yet-rendered. If the GPU
is unavailable, emit `gpu-unavailable`. No silent fallback to fullscreen quad
rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m28.vertices-mesh-rendering`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=vertices-color-mesh
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=mesh-ribbon-depth
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M28`
- `area:execution-backend`
