# M8 - Vertices, Mesh, And Batching

## Goal

Add `DrawVertices` and future mesh-like route contracts with explicit buffer,
topology, primitive color, batching, and sorting boundaries.

## Dependencies

Depends on M2 material/pipeline foundations and M7 blend/color behavior for
primitive color interactions.

## Exit Criteria

- [ ] Vertex/index layouts and upload plans are deterministic and dumpable.
- [ ] Topology, primitive color, texcoords, and index policies have accepted or
      refused route evidence.
- [ ] Batching preserves material, clip, layer, destination-read, and ordering
      constraints.
- [ ] No CPU-rasterized mesh texture fallback is accepted.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M8-001 - Add `DrawVertices` descriptor and route decisions](KGPU-M8-001-add-drawvertices-descriptor-and-route-decisions.md) | `done` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `vertices` | `KGPU-M2-002`, `KGPU-M7-003` | `vertices legacy` |
| [KGPU-M8-002 - Add vertex index buffer payload and resource plans](KGPU-M8-002-add-vertex-index-buffer-payload-and-resource-plans.md) | `blocked` | `P1` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `vertices-resources` | `KGPU-M8-001` | - |
| [KGPU-M8-003 - Add vertices batching sort and refusal evidence](KGPU-M8-003-add-vertices-batching-sort-and-refusal-evidence.md) | `blocked` | `P2` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `batching` | `KGPU-M8-001`, `KGPU-M8-002` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Vertices*'
```

## Non-Claims

- No 3D engine, general mesh renderer, or CPU-rasterized vertex texture
  fallback.
- Descriptor support does not imply product support.

## Current Evidence

- KGPU-M8-001 is `done` with contract-only `GPUVerticesRouteDecisionPlanner`
  evidence for typed `GPUVertexMode` descriptors, deterministic
  descriptor/layout/key/route dumps, and stable refusals for unsupported
  topology, triangle fan, nondeterministic sources, non-finite positions,
  vertex/index budgets, attribute/color format, texcoord/local-coordinate,
  primitive blender, primitive-blend destination-read, and missing WGSL/layout
  evidence cases. It remains non-promoted: no `DrawVertices` support,
  vertex/index upload, primitive blender support, texcoord material support,
  mesh support, batching support, product activation, or CPU-rasterized mesh
  texture fallback is claimed. Independent review
  `019ed5c8-898d-7923-83b6-f8c82775d12e` found no P0/P1/P2 blockers. Evidence report:
  `reports/gpu-renderer/2026-06-17-m8-001-vertices-route-decisions.md`.
- KGPU-M8-002 is `blocked` on KGPU-M8-001 plus adapter-backed vertex/index
  buffer ownership, upload-before-draw ordering, resource-generation, budget,
  and invalid/stale buffer refusal evidence.
- KGPU-M8-003 is `blocked` on KGPU-M8-001 and KGPU-M8-002. Batching evidence
  must not be produced before route and buffer facts exist, because sort/split
  decisions need material, clip, layer, destination-read, barrier, and
  upload-generation boundaries.
- No `DrawVertices`, mesh, primitive blender, vertex/index upload, batching,
  GPU-native route, or CPU-rasterized mesh texture fallback support is implied.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
