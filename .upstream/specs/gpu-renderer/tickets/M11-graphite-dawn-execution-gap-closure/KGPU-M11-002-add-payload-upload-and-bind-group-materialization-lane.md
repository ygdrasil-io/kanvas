---
id: KGPU-M11-002
title: "Add payload upload and bind group materialization lane"
status: done
milestone: M11
priority: P0
owner_area: payloads-bind-groups
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M11-001, KGPU-M11-003]
legacy_gate: null
---

# KGPU-M11-002 - Add payload upload and bind group materialization lane

## PM Note

Ce ticket relie les payloads déjà packés à de vrais buffers et bind groups
avant de parler de rendu supporté.

## Problem

Solid payloads and resource binding blocks can be packed and dumped, but they
are not uploaded, placed in buffers, or materialized into bind groups consumed
by `GPUPassCommandStream`. Graphite/Dawn-style execution needs a clear bridge
from payload slots to WGPU buffers and bind groups.

## Scope

- Define an implementation ticket for upload buffers, uniform/storage payload
  materialization, dynamic-offset policy, and bind group creation.
- Require payload slot IDs to remain pass-local while provider-owned handles
  stay out of durable keys and public dumps.
- Connect payload upload plans to `GPUResourceProvider` and the execution cache
  from `KGPU-M11-001`.
- Cover resource-binding payloads for uniform-only and sampled-texture layouts
  without widening image, text, or runtime-effect support.

## Non-Goals

- Do not change `MaterialKey` or pipeline keys to include payload values.
- Do not introduce hidden CPU fallback when upload or binding fails.
- Do not implement glyph/text upload; M6 remains the text handoff owner.

## Spec Sources

- `.upstream/specs/gpu-renderer/11-wgsl-layout-binding-abi.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`
- `.upstream/specs/gpu-renderer/04-pipeline-key-cache-resources.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`

## Graphite Algorithm References

- [`GFX-DRAWLIST-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-record) - Study uniform and texture data capture per render step.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - Reference sampled texture and payload validation before command encoding.
- [`GFX-DRAW-WRITER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-writer) - Reference state setup and draw emission without copying implementation.
- Boundary: references are for algorithm study only; Kanvas keeps WGSL and
  WGPU facade contracts authoritative.

## Design Sketch

```kotlin
data class GPUPayloadMaterializationPlan(
    val payloadSlot: String,
    val uploadBufferRef: String,
    val bindGroupLayoutKey: String,
    val bindGroupRef: String,
    val dynamicOffsets: List<Int>,
)
```

## Acceptance Criteria

- [x] Uniform payload bytes are uploaded through an accepted upload/staging path
      with zeroed padding, layout hash, and byte-count evidence.
- [x] `GPUResourceBindingBlock` values materialize into bind groups matching
      the reflected `WGSLBindingLayout`.
- [x] Bind group creation refuses on missing usage, stale resource generation,
      incompatible layout, exceeded dynamic-offset limits, or missing upload
      capability.
- [x] `GPUPassCommandStream` can reference materialized bind group IDs without
      exposing raw backend handles in dumps.
- [x] Payload upload and bind group telemetry records real create/reuse/failure
      facts and remains non-promotional.

## Required Evidence

- Payload upload dump with byte size, alignment, generation, and upload scope.
- Bind group materialization dump with layout key and resource binding facts.
- Refusal fixtures for layout mismatch, stale generation, and upload budget
  exhaustion.
- Adapter-backed smoke evidence for a uniform-only route before any support
  claim.

## Fallback / Refusal Behavior

If payload upload or bind group materialization fails, the route refuses with a
stable diagnostic. It must not bind stale handles, substitute different payload
values, or CPU-render the draw.

## Dashboard Impact

- Expected row: `gpu-renderer.payload.bind-group-materialization`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no; this ticket only enables the execution boundary.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest --tests org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketCommandStreamTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Implemented provider-owned payload upload and bind-group
  materialization contracts, pass-command bridge evidence, adapter-backed
  uniform-only payload smoke, scoped non-promotional telemetry, and refusal
  fixtures for layout, usage, generation, upload range, budget, dynamic
  offsets, upload capability, and incomplete sampled/storage binding facts.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:payloads`
