---
id: KGPU-M7-003
title: "Add blend mode allowlist and destination-read refusals"
status: done
milestone: M7
priority: P0
owner_area: blend-destination-read
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M5-002]
legacy_gate: "blend legacy"
---

# KGPU-M7-003 - Add blend mode allowlist and destination-read refusals

## PM Note

Ce ticket borne les modes de blend au lieu de promettre toute la matrice Skia.

## Problem

Blend modes need fixed-function, shader, or destination-read strategies with
stable refusals for unsupported modes.

## Scope

- Add blend allowlist and key/state evidence.
- Add destination-read refusal evidence for modes without accepted strategy.

## Non-Goals

- Do not support all blend modes.
- Do not assume framebuffer fetch.

## Spec Sources

- `.upstream/specs/gpu-renderer/12-blend-color-target-state.md`
- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`

## Graphite Algorithm References

- [`GFX-BLEND-KEYING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-blend-keying) - source [KeyHelpers.cpp:2593](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:2593); Study blend mode grouping into coefficient, HSL, and fixed-mode key blocks.
- [`GFX-DST-USAGE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-usage) - source [PaintParams.cpp:51](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:51); Separate blend support from destination-read and renderer-only destination usage.
- [`GFX-DST-READ-COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-read-copy) - source [DrawContext.cpp:270](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:270); Reference explicit copy strategy for blend modes that require destination reads.
- [`GFX-PAINTPARAMS-TO-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paintparams-to-key) - source [PaintParams.cpp:222](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:222); Use final blend lowering as allowlist evidence vocabulary.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUBlendAllowlistRequest(val mode: GPUBlendMode, val destinationReadPlan: GPUDestinationReadStrategyGatePlan?)
data class GPUBlendAllowlistGatePlan(val plan: GPUBlendPlan, val diagnostics: List<GPUBlendDiagnostic>)
```

## Acceptance Criteria

- [x] Supported modes have deterministic state/key dumps.
- [x] Unsupported modes refuse stably.
- [x] Destination-read modes cite accepted strategy or refuse.

## Required Evidence

- Blend plan, pipeline key, destination-read, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported blend modes refuse or remain legacy-policy gated before activation.

## Dashboard Impact

- Expected row: `gpu-renderer.blend-allowlist`
- Expected classification: `TargetNative`
- Claim promotion allowed: no; this remains contract/refusal evidence only.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: KGPU-M5-002 is `done`, and this ticket adds
  `GPUBlendAllowlistPlanner` evidence for the `gpu-renderer.blend-allowlist`
  row. `Src`, `SrcOver`, and `DstOver` produce deterministic fixed-function
  blend state, alpha-plan, and pipeline-key dumps. `Multiply`, `Screen`,
  `Custom`, active-attachment sampling, unaccepted alpha plans,
  destination-read-without-strategy, destination-read plan mismatches, and
  destination-read shader routes without validated product strategy refuse
  stably without exposing executable destination-read actions. No
  framebuffer-fetch, input-attachment, destination-read texture,
  all-blend-mode support, product activation, adapter-backed execution, or
  CPU-rendered blend fallback is implied. Independent review
  `019ed58b-6d88-7af2-a38f-56ec7b547ee0` found no remaining P0/P1/P2 issues
  after the alpha-plan, refused-action, mode-coverage, plan-mismatch, and
  existing-intermediate target-format checks were added.

## Linear Labels

- `gpu-renderer`
- `milestone:M7`
- `area:blend`
