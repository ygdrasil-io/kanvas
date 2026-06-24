# M22 - Vertices + Mesh Batching

## Goal

Deliver DrawVertices execution, vertex buffer materialization, and mesh batching with controlled product activation. Completes Wave 3 draw family coverage.

## Dependencies

Depends on M12 (wgsl4k gate KGPU-M12-010). Wave 3 milestone.

## Exit Criteria

- [ ] DrawVertices renders triangle meshes with vertex colors on GPU
- [ ] Vertex buffers are correctly materialized and uploaded
- [ ] Mesh batching reduces draw call count
- [ ] DrawVertices route is product-activated with rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M22-001 - Add DrawVertices execution: triangle list + vertex colors + primitive blend](KGPU-M22-001-draw-vertices-execution.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `vertices-mesh` | [KGPU-M12-010] | null |
| [KGPU-M22-002 - Add vertex buffer materialization: CPU-packed buffers -> GPU upload -> bind](KGPU-M22-002-vertex-buffer-materialization.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `resources-execution` | [KGPU-M22-001] | null |
| [KGPU-M22-003 - Add mesh batching: sort + merge draw calls by pipeline key](KGPU-M22-003-mesh-batching.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `recording` | [KGPU-M22-001] | null |
| [KGPU-M22-004 - Activate M22 routes: DrawVertices + mesh default ON with rollback](KGPU-M22-004-route-activation.md) | `done` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M22-001, KGPU-M22-002, KGPU-M22-003] | legacy drawVertices |
| [KGPU-M22-005 - Add gpu-renderer-scenes evidence: vertices-color-mesh, mesh-ribbon-depth](KGPU-M22-005-scenes-evidence.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M22-001, KGPU-M22-002, KGPU-M22-003] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DrawVertices*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*VertexBuffer*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*MeshBatch*'
```

## Non-Claims

- No index buffer
- No custom vertex layouts beyond position+color
- No GPU-driven draw generation
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
