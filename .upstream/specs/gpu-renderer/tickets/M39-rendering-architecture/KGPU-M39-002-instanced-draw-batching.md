---
id: KGPU-M39-002
title: "Instanced draw batching"
status: proposed
milestone: M39
priority: P0
owner_area: passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M39-002 - Instanced draw batching

## PM Note

Le batching instanced regroupe les draw packets compatibles en un seul draw
call GPU, réduisant les appels API et améliorant le débit de rendu sans
changer le résultat visuel.

## Problem

The GPU renderer currently issues one draw call per packet, even when
multiple consecutive packets share the same render step, pipeline key, and
bind group layout. This produces unnecessary API overhead and limits draw
throughput. Instanced batching must group compatible packets into a single
instanced draw call with per-instance varying data delivered through the
uniform or vertex buffer path.

## Scope

- `GPUInstancedPacketGroup` — groups N compatible packets that share the
  same render step identifier, render pipeline key, and bind group layout
  key.
- `GPUInstancedUniformStrategy` — delivers instance-varying uniforms in a
  single buffer with a configurable byte stride.
- `GPUInstancedVertexStrategy` — delivers per-instance vertex data through
  instanced vertex buffers with `divisor = 1`.
- `GPUInstancedDrawCommand` — issues a single `drawIndexedInstanced` with
  `instanceCount` equal to the group size.
- Grouping rules:
  - `renderStepIdentifier` must match.
  - `renderPipelineKey` must match.
  - `bindingLayoutKey` must match.
  - Varying data must be confined to the packet payload.
  - No ordering barriers, atomic groups, or fences between grouped packets.

## Non-Goals

- Do not reorder packets across render steps.
- Do not batch packets that require inter-draw barriers or atomics.
- Do not claim performance improvement without telemetry evidence.

## Spec Sources

- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- `GFX-DRAWCONTEXT-RECORD` from `../GRAPHITE-ALGORITHM-REFERENCES.md` — study
  draw admission and grouping for algorithm reference; do not port Graphite
  or Ganesh.
- Boundary: references are for algorithm study only; do not port Graphite or
  Ganesh and do not treat them as Kanvas acceptance criteria.

## Design Sketch

```kotlin
data class GPUInstancedPacketGroup(
    val packets: List<GPUDrawPacket>,
    val renderStepIdentifier: GPURenderStepIdentifier,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutKey: GPUBindingLayoutKey,
)

data class GPUInstancedUniformStrategy(
    val buffer: GPUBuffer,
    val strideBytes: Int,
)

data class GPUInstancedVertexStrategy(
    val vertexBuffers: List<GPUInstancedVertexBuffer>,
)

data class GPUInstancedDrawCommand(
    val indexCount: Int,
    val instanceCount: Int,
    val uniformStrategy: GPUInstancedUniformStrategy?,
    val vertexStrategy: GPUInstancedVertexStrategy?,
)
```

## Acceptance Criteria

- [ ] 4 solid-color rect packets with different uniform data are batched
      into one instanced draw call.
- [ ] Per-instance uniform and vertex data is correct in the rendered output.
- [ ] Non-batchable packets (different pipeline key, intervening barriers) are
      not incorrectly grouped.
- [ ] Batching does not change pixel output vs. individual draw calls.
- [ ] Telemetry reports batch count, group size distribution, and
      non-batchable reason.

## Required Evidence

- Instanced draw call trace (WebGPU capture) showing single draw with
  `instanceCount = 4` for 4 compatible packets.
- Pixel diff report: batched vs. unbatched render output (must be zero-diff).
- Non-batchable refusal diagnostic dump for incompatible packet sequences.
- Telemetry artifact: batch statistics from a test frame.

## Fallback / Refusal Behavior

- Incompatible packets → `unsupported.stream.instanced_incompatible_packets`
  diagnostic.
- Individual draw calls remain the fallback path when grouping conditions are
  not met.

## Dashboard Impact

- Expected row: `gpu-renderer.rendering.instanced-batching`
- Expected classification: `TargetNative`
- Claim promotion allowed: only after Required Evidence is linked and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*InstancedBatch*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M39`
- `area:passes`
