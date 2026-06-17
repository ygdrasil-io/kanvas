---
id: KGPU-M11-007
title: "Add bounded stencil-cover adapter-backed lane"
status: proposed
milestone: M11
priority: P1
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M3-002, KGPU-M11-001, KGPU-M11-003]
legacy_gate: "path fill legacy"
---

# KGPU-M11-007 - Add bounded stencil-cover adapter-backed lane

## PM Note

Ce ticket demande une voie stencil-cover native bornée, avec preuves GPU, avant
toute promesse path générale.

## Problem

KGPU-M3-002 records a stencil-cover gate candidate, but current evidence is
contract-only and explicitly non-promoted. The execution gap is a bounded
adapter-backed lane with depth/stencil state, producer/cover ordering,
resources, readback, and stable fallback diagnostics.

## Scope

- Implement a future lane for one bounded path/clip class accepted by
  `GPUStencilCoverPlan`.
- Materialize depth/stencil attachments, stencil producer commands, cover
  commands, pipeline states, and ordering tokens.
- Require CPU/reference diff evidence, GPU readback or skipped-readback reason,
  and stencil/depth state dumps.
- Keep atlas, tessellation, compute path, strokes, inverse fill, and broad clip
  stacks outside this ticket.

## Non-Goals

- Do not claim general path fill, path atlas, compute path, or stroke support.
- Do not replace prepared-path gates or CPU reference evidence with hidden
  product fallback.
- Do not enable product routing by default.

## Spec Sources

- `.upstream/specs/gpu-renderer/24-clip-stencil-mask-pipeline.md`
- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`
- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`

## Graphite Algorithm References

- [`GFX-MSAA-PATH-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-msaa-path-heuristics) - Reference bounded path route selection.
- [`GFX-TESSELLATE-WEDGES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-wedges) - Study wedge patch producer ideas as references only.
- [`GFX-DRAW-ORDER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-order) - Reference painter order and stencil ordering constraints.
- Boundary: do not port Graphite tessellation, atlas, or stencil internals.

## Design Sketch

```kotlin
data class GPUStencilCoverExecutionPlan(
    val stencilProducerPacket: String,
    val coverPacket: String,
    val depthStencilStateKey: String,
    val orderingToken: String,
)
```

## Acceptance Criteria

- [ ] A bounded accepted stencil-cover route emits separate producer and cover
      packets with explicit ordering tokens.
- [ ] Depth/stencil attachment, load/store, clear, compare, write-mask, and
      sample-count facts are materialized and dumpable.
- [ ] Producer-before-cover and clip/path ordering constraints survive
      sorting, batching, and pass-command emission.
- [ ] Unsupported fill types, unbounded paths, unsupported sample counts,
      missing depth/stencil support, and readback-unavailable lanes refuse or
      skip evidence stably.
- [ ] GPU readback/reference diff evidence is linked before any native support
      claim.

## Required Evidence

- Stencil producer and cover command stream dumps.
- Depth/stencil state and attachment materialization dump.
- CPU/reference and GPU readback diff evidence or explicit skipped-readback
  evidence.
- Refusal diagnostics for unsupported bounded-lane cases.

## Fallback / Refusal Behavior

If the bounded stencil-cover lane cannot prove ordering, depth/stencil support,
or readback evidence, it remains refused or evidence-skipped. It must not fall
back to CPU-rendered path textures.

## Dashboard Impact

- Expected row: `gpu-renderer.path.stencil-cover.live`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until adapter-backed lane evidence is accepted.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Planning-only continuation of KGPU-M3-002 from contract gate to a
  bounded adapter-backed lane.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:geometry`
