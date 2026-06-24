---
id: KGPU-M22-002
title: "Add vertex buffer materialization: CPU-packed buffers -> GPU upload -> bind"
status: proposed
milestone: M22
priority: P0
owner_area: resources-execution
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M22-001]
legacy_gate: null
---

# KGPU-M22-002 - Add vertex buffer materialization: CPU-packed buffers -> GPU upload -> bind

## PM Note

La matérialisation des buffers de sommets est le pont entre les données CPU et le rendu GPU. Sans buffer management fiable, les DrawVertices restent théoriques.

## Problem

Vertex data must be materialized from CPU-packed buffers to GPU vertex buffers before DrawVertices can execute. Buffer management (allocation, upload, lifetime) is needed for reliable mesh rendering.

## Scope

- Add vertex buffer allocation and management
- Add CPU-packed buffer -> GPU vertex buffer upload
- Add vertex buffer binding for draw calls
- Produce vertex buffer materialization trace

## Non-Goals

- No GPU-only buffer generation
- No buffer sharing across draw calls

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_RESOURCE_KEYED_CACHE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-keyed-cache) - source src/gpu/graphite/BufferManager.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class VertexBufferMaterializer { fun materialize(vertices: List<VertexData>): GpuVertexBuffer; fun bind(buffer: GpuVertexBuffer, pipeline: Pipeline) }
```

## Acceptance Criteria

- [ ] Vertex buffers are correctly allocated with appropriate size
- [ ] CPU data is correctly uploaded to GPU vertex buffer
- [ ] Vertex buffer is correctly bound for draw call execution

## Required Evidence

- Vertex buffer allocation and upload trace
- Vertex data readback validation (upload->readback comparison)
- Buffer binding validation transcript

## Fallback / Refusal Behavior

Buffer materialization failure emits stable diagnostic; draw call skipped.

## Dashboard Impact

- Expected row: `gpu-renderer.m22.vertex-buffer-materialization`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*VertexBuffer*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M22`
- `area:resources-execution`
