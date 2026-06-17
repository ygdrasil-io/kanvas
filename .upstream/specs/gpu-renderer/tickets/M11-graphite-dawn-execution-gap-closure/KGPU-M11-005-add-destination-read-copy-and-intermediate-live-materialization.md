---
id: KGPU-M11-005
title: "Add destination-read copy and intermediate live materialization"
status: proposed
milestone: M11
priority: P1
owner_area: destination-read
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M5-002, KGPU-M11-003, KGPU-M11-004]
legacy_gate: "blend legacy"
---

# KGPU-M11-005 - Add destination-read copy and intermediate live materialization

## PM Note

Ce ticket matérialise les copies destination nécessaires aux blends avancés,
sans supposer framebuffer fetch.

## Problem

KGPU-M5-002 defines copy/intermediate strategy evidence and active-attachment
sampling refusals, but it remains contract-only and `materialized=false`.
Graphite/Dawn comparison still shows no live destination-copy texture,
existing-intermediate binding, pass split, or copy-before-sample execution path.

## Scope

- Materialize `GPUDestinationReadPlan` actions into copy commands, intermediate
  target refs, sampled bindings, and pass boundaries.
- Support bounded `TargetCopySnapshot` and `SampleExistingIntermediate` lanes
  only where strategy, bounds, usage flags, and budget are accepted.
- Keep fixed-function blend routes separate from shader destination reads.
- Link destination-read materialization to payload binding and pass command
  streams.

## Non-Goals

- Do not add framebuffer fetch, input attachments, or active-attachment
  sampling.
- Do not support all blend modes or arbitrary filter/backdrop reads.
- Do not add CPU readback as a product destination-read route.

## Spec Sources

- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`
- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`

## Graphite Algorithm References

- [`GFX-DST-USAGE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-usage) - Reference destination-use classification.
- [`GFX-DST-READ-COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-read-copy) - Study explicit destination copy insertion.
- [`GFX-DRAWCONTEXT-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-record) - Reference barrier selection before destination-dependent draws.
- Boundary: do not accept framebuffer fetch or Graphite backend strategies as
  Kanvas WebGPU support.

## Design Sketch

```kotlin
data class GPUDestinationReadMaterializationPlan(
    val action: String,
    val copyTextureRef: String?,
    val intermediateRef: String?,
    val passBoundaryToken: String,
    val sampledBindingSlot: String?,
)
```

## Acceptance Criteria

- [ ] Bounded destination-copy plans create separate copy-destination and
      texture-binding resources before sampling.
- [ ] Pass split and copy-before-sample ordering are visible in
      `GPUPassCommandStream` dumps.
- [ ] Existing-intermediate usage validates target generation, bounds, format,
      sample count, and texture-binding usage before binding.
- [ ] Active attachment sampling, unbounded reads, missing copy capability,
      budget overflow, and plan/action mismatches refuse stably.
- [ ] Adapter-backed evidence includes copy/intermediate command traces and
      readback or skipped-readback diagnostics without product activation.

## Required Evidence

- Destination-copy materialization dump with bounds and usage flags.
- Pass split/copy-before-sample command stream dump.
- Sampled binding dump for the copied or existing intermediate texture.
- Refusal fixtures for active attachment sampling, missing usage, unbounded
  reads, and budget overflow.

## Fallback / Refusal Behavior

If the destination read cannot be represented by fixed-function blend, a
separate copied texture, or a validated existing intermediate, it refuses. It
must not sample the active attachment or use CPU readback for product rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.destination-read.live-materialization`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until adapter-backed evidence and review are
  linked.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Planning-only continuation of KGPU-M5-002 from strategy gate to
  live resource materialization.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:destination-read`
