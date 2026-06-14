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
| [KGPU-M8-001 - Add `DrawVertices` descriptor and route decisions](KGPU-M8-001-add-drawvertices-descriptor-and-route-decisions.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `vertices` | `KGPU-M2-002`, `KGPU-M7-003` | `vertices legacy` |
| [KGPU-M8-002 - Add vertex index buffer payload and resource plans](KGPU-M8-002-add-vertex-index-buffer-payload-and-resource-plans.md) | `proposed` | `P1` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `vertices-resources` | `KGPU-M8-001` | - |
| [KGPU-M8-003 - Add vertices batching sort and refusal evidence](KGPU-M8-003-add-vertices-batching-sort-and-refusal-evidence.md) | `proposed` | `P2` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `batching` | `KGPU-M8-001`, `KGPU-M8-002` | - |

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

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
