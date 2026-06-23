---
id: KGPU-M11-003
title: "Add resource materialization handles and provider bridge"
status: done
milestone: M11
priority: P0
owner_area: resources-execution
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M0-005]
legacy_gate: null
---

# KGPU-M11-003 - Add resource materialization handles and provider bridge

## PM Note

Ce ticket crée le pont entre les plans de ressources et des handles GPU vivants
sans les faire fuiter dans les clés.

## Problem

`GPUResourceProvider` currently guards planning/refusal contracts, but accepted
packets still need materialized references for pipelines, buffers, textures,
views, samplers, targets, and readback resources before encoding. The missing
bridge is a renderer-owned handle layer that command streams can consume
without exposing raw facade objects in planning packages.

## Scope

- Define materialized resource reference types for pipelines, buffers, bind
  groups, textures, views, samplers, targets, destination copies, and readback
  resources.
- Require provider-owned validation for usage flags, device generation, target
  generation, resource state, and lifetime scope.
- Connect `GPUDrawPacketStream` references to materialized `GPUPassCommandStream`
  operands through `GPUResourceProvider`.
- Keep raw WGPU facade handles behind `resources` and `execution`.

## Non-Goals

- Do not make resource handles durable cache keys or public APIs.
- Do not activate product routing or root PM support.
- Do not add CPU-rendered texture compatibility for unsupported resources.

## Spec Sources

- `.upstream/specs/gpu-renderer/04-pipeline-key-cache-resources.md`
- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/34-analysis-materialization-recording.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`

## Graphite Algorithm References

- [`GFX-RESOURCE-KEYED-CACHE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-keyed-cache) - Reference descriptor-keyed resource creation and lookup.
- [`GFX-RESOURCE-CACHE-MRU`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-cache-mru) - Study residency and purge evidence without adopting implementation.
- [`GFX-TASKLIST`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tasklist) - Reference prepare/addCommands traversal and failure separation.
- Boundary: references are for algorithm study only; concrete Kanvas handles
  remain provider-owned and WGPU-scoped.

## Design Sketch

```kotlin
data class GPUMaterializedResourceSet(
    val deviceGeneration: String,
    val pipelineRefs: List<String>,
    val bufferRefs: List<String>,
    val textureRefs: List<String>,
    val diagnostics: List<String>,
)
```

## Acceptance Criteria

- [x] Provider materialization returns scoped references for every command-stream
      operand needed by the accepted route.
- [x] Materialized references carry device generation, owner scope, usage facts,
      and invalidation policy in dumps.
- [x] Planning packages consume only logical refs or materialization records,
      never raw WGPU handles.
- [x] Late failures separate allocation, validation, encoding, submission, and
      readback diagnostics.
- [x] Stale generation, missing usage, evicted resource, and active attachment
      sampling refuse before queue submission.

## Required Evidence

- Resource materialization decision dump for an accepted first-route packet.
- Provider bridge dump from packet references to pass-command operands.
- Refusal dumps for stale generation, missing usage, and active attachment
  sampling.
- Readback or explicitly skipped-readback evidence when adapter evidence is
  requested.

## Fallback / Refusal Behavior

Materialization may rebuild only when the same logical descriptor, route, and
device generation remain valid. Otherwise it refuses. It must not silently
change route kind or CPU-render unsupported output.

## Dashboard Impact

- Expected row: `gpu-renderer.resource.materialization-provider`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no without adapter-backed execution and review.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProviderTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionContextTest --tests org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketCommandStreamTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added provider-owned materialized command operand refs and bridge
  evidence, wired `GPUDrawPacketStream` lowering to provider materialization,
  required bridged operands for first-route submit handoff, rejected handle-like
  operand dump fields, and validated stale generation, missing usage, evicted
  resource, active attachment sampling, and skipped-readback evidence. Evidence:
  `reports/gpu-renderer/2026-06-17-m11-003-resource-provider-bridge.md`.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:resources`
