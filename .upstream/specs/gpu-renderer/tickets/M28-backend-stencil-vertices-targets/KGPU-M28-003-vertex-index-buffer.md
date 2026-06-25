---
id: KGPU-M28-003
title: "Add vertex/index buffer to offscreen backend"
status: proposed
milestone: M28
priority: P0
owner_area: execution-backend
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M25-006]
legacy_gate: null
---

# KGPU-M28-003 - Add vertex/index buffer to offscreen backend

## PM Note

Le backend offscreen actuel ne supporte que des quads plein ecran sans geometry
reelle. Sans vertex/index buffers, les meshs de vertices et les fans convexes
restent invisibles. Ce ticket ajoute la creation de buffers et le draw indexe
pour que le PM voie la vraie geometry mesh.

## Problem

The offscreen backend (`WgpuRenderRecorder` / `GPUBackendRenderRecorder`) only
supports fullscreen uniform passes via a single hardcoded quad. The vertices
family (M25-006) wires `VerticesExecutor` and `GPUVertexBufferUploader`, and
the convex-fan path fill path needs indexed triangle draws, but neither can
produce visible output because there is no vertex buffer creation, no index
buffer creation, and no indexed draw support. Real mesh and convex-fan rendering
is blocked.

## Scope

- Add `createVertexBuffer` to `WgpuRenderRecorder` (staging buffer -> GPU vertex buffer)
- Add `createIndexBuffer` to `WgpuRenderRecorder` (staging buffer -> GPU index buffer)
- Add `drawIndexed` to `GPUBackendRenderRecorder` interface and impl (triangle-list topology)
- Support vertex layouts with position + color attributes
- Keep existing fullscreen uniform pass support working

## Non-Goals

- No depth-stencil attachment (KGPU-M28-001)
- No secondary render targets (KGPU-M28-005)
- No vertices wiring (KGPU-M28-004)
- No line-strip or triangle-strip topologies (triangle-list only)
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/26-draw-vertices-mesh-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M22-vertices-mesh-batching/README.md`
- `.upstream/specs/gpu-renderer/tickets/M25-missing-wiring/KGPU-M25-006-vertices-mesh-rendering.md`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/WgpuRenderRecorder.kt`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/GPUBackendRenderRecorder.kt`

## Design Sketch

```kotlin
// WgpuRenderRecorder: add vertex/index buffer creation
fun createVertexBuffer(data: Float32Array, layout: GPUVertexLayout): GPUVertexBufferHandle
fun createIndexBuffer(data: Uint16Array): GPUIndexBufferHandle

// GPUBackendRenderRecorder: add indexed draw interface
fun drawIndexed(
    pipeline: GPURenderPipelineHandle,
    vertexBuffer: GPUVertexBufferHandle,
    indexBuffer: GPUIndexBufferHandle,
    vertexCount: Int,
    instanceCount: Int,
    uniforms: Float32Array,
)

// Supporting types
data class GPUVertexLayout(
    val attributes: List<GPUVertexAttribute>, // e.g. position: float2, color: float4
    val stride: Int,
)
```

## Acceptance Criteria

- [ ] `WgpuRenderRecorder.createVertexBuffer` uploads float data to a GPU vertex buffer via staging
- [ ] `WgpuRenderRecorder.createIndexBuffer` uploads uint16 data to a GPU index buffer via staging
- [ ] `GPUBackendRenderRecorder.drawIndexed` dispatches an indexed draw call with triangle-list topology
- [ ] Vertex layout supports position (float2) and color (float4) attributes
- [ ] Existing fullscreen uniform pass support continues to work
- [ ] Vertex/index buffer creation diagnostics are emitted (byte count, attribute count)

## Required Evidence

- Vertex buffer creation transcript (byte count, attribute layout, stride)
- Index buffer creation transcript (index count, format)
- Indexed draw dispatch confirmation (draw call count, triangle count)
- Existing uniform-pass visual output unchanged (regression check)

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. If vertex/index buffer creation fails, emit a
`buffer-creation-failed` diagnostic. No silent fallback to fullscreen quad
rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m28.vertex-index-buffer`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M28`
- `area:execution-backend`
