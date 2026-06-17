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
| [KGPU-M8-002 - Add vertex index buffer payload and resource plans](KGPU-M8-002-add-vertex-index-buffer-payload-and-resource-plans.md) | `done` | `P1` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `vertices-resources` | `KGPU-M8-001` | - |
| [KGPU-M8-003 - Add vertices batching sort and refusal evidence](KGPU-M8-003-add-vertices-batching-sort-and-refusal-evidence.md) | `done` | `P2` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `batching` | `KGPU-M8-001`, `KGPU-M8-002` | - |

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
- KGPU-M8-002 is `done` with contract-only
  `GPUVerticesBufferPlanPlanner` evidence for deterministic vertex/index
  buffer payloads, upload plans, resource ownership/generation facts,
  material-key exclusion, upload-before-draw dependencies, usage flags,
  budget checks, invalid/stale refusals, and live-handle refusal. It remains
  non-promoted: no product `DrawVertices` support, adapter-backed upload,
  mesh support, batching support, materialized live handles, or CPU-rasterized
  mesh texture fallback is claimed. Independent re-review
  `019ed5dd-8e76-78a0-8e8d-646398e40e90` found no remaining P0/P1/P2 blockers.
  Evidence report:
  `reports/gpu-renderer/2026-06-17-m8-002-vertices-buffer-plans.md`.
- KGPU-M8-003 is `done` with contract-only
  `GPUVerticesBatchingPlanner` evidence for deterministic adjacent batch keys,
  sort-window preimages, per-batch `sortWindow` axes, split reasons, telemetry,
  and refusal rows over accepted M8-001/M8-002 route/buffer evidence. It splits
  on `sortWindowId`, topology, render-step, pipeline/layout, material, blend,
  clip, layer, destination-read, barrier, upload-generation, and unknown-overlap
  boundaries. It remains non-promoted: no product `DrawVertices` support,
  executable batching, cross-layer batching, destination-read batching,
  adapter-backed execution, performance readiness, mesh support, or
  CPU-rasterized mesh texture fallback is claimed. Independent final re-review
  `019ed5ec-2289-7d53-8778-7948635b5e06` found no remaining P0/P1/P2 blockers.
  Evidence report: `reports/gpu-renderer/2026-06-17-m8-003-vertices-batching.md`.
- No `DrawVertices`, mesh, primitive blender, vertex/index upload, executable
  batching, GPU-native product route, or CPU-rasterized mesh texture fallback
  support is implied.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
