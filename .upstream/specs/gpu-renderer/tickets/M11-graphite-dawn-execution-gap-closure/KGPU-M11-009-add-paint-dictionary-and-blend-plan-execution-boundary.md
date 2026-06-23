---
id: KGPU-M11-009
title: "Add paint dictionary and blend-plan execution boundary"
status: done
milestone: M11
priority: P1
owner_area: paint-blend
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M7-003, KGPU-M11-002, KGPU-M11-005]
legacy_gate: "blend legacy"
---

# KGPU-M11-009 - Add paint dictionary and blend-plan execution boundary

## PM Note

Ce ticket relie dictionnaire de paint et plan de blend à l'exécution, sans
promettre tous les modes Skia.

## Problem

Existing material and blend tickets define solid material lowering, registered
runtime-effect descriptors, and a blend allowlist/refusal gate. They do not yet
prove the execution boundary where paint dictionary roots, snippet IDs,
fixed-function blend state, shader blend requirements, destination-read plans,
payload bindings, and pipeline keys produce commands that run.

## Scope

- Define a ticket for the paint dictionary and `GPUBlendPlan` execution
  boundary when a material route is accepted.
- Require fixed-function blend plans to materialize into WGPU pipeline target
  state and optional blend constants.
- Require shader/destination-read blend plans to cite an accepted destination
  read materialization lane or refuse.
- Keep MaterialKey, payload values, concrete handles, and destination-copy
  resources separated.

## Non-Goals

- Do not support all blend modes.
- Do not accept arbitrary runtime effects, SkSL, framebuffer fetch, or input
  attachments.
- Do not duplicate KGPU-M7-003 allowlist/refusal evidence; this ticket owns
  execution materialization only.

## Spec Sources

- `.upstream/specs/gpu-renderer/12-blend-color-target-state.md`
- `.upstream/specs/gpu-renderer/16-material-dictionary-and-snippet-registry.md`
- `.upstream/specs/gpu-renderer/31-material-source-paint-pipeline.md`
- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`

## Graphite Algorithm References

- [`GFX-PAINT-KEY-TREE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paint-key-tree) - Reference paint key root separation.
- [`GFX-PAINTPARAMS-TO-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paintparams-to-key) - Reference destination usage and blend lowering vocabulary.
- [`GFX-BLEND-KEYING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-blend-keying) - Study blend key reduction and allowlist boundaries.
- [`GFX-DST-USAGE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-usage) - Reference destination-read classification.
- Boundary: Graphite references are not acceptance criteria and do not add SkSL
  support.

## Design Sketch

```kotlin
data class GPUPaintBlendExecutionBoundary(
    val materialProgramId: String,
    val blendPlanId: String,
    val destinationReadPlanId: String?,
    val pipelineTargetStateKey: String,
    val payloadBoundary: String,
)
```

## Acceptance Criteria

- [x] Accepted material dictionary roots and snippet IDs map to an executable
      pipeline key without including payload values or concrete resources.
- [x] Fixed-function blend plans materialize target color state and blend
      constants in pass commands.
- [x] Shader or destination-read blend plans require an accepted
      `GPUDestinationReadPlan` materialization or refuse before pipeline
      creation.
- [x] Material payload boundary and render-step payload boundary remain visible
      so multi-step routes can share paint payloads safely.
- [x] Unsupported blend modes, missing destination-read strategy, incompatible
      alpha plans, and active-attachment sampling refuse stably.

## Required Evidence

- Paint dictionary expansion and material program dump.
- Blend plan to pipeline target-state dump.
- Pass-command evidence for fixed-function blend state or refused shader blend.
- Destination-read linkage dump when a blend requires sampled destination.
- Refusal fixtures for unsupported blend and destination-read mismatches.

## Fallback / Refusal Behavior

Unsupported paint/blend combinations refuse. The renderer must not replace
shader blend with CPU-rendered output, framebuffer fetch assumptions, or broad
all-mode support.

## Dashboard Impact

- Expected row: `gpu-renderer.paint-blend.execution-boundary`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no; this ticket closes execution boundary planning
  only.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added a contract-only paint dictionary plus fixed-function blend
  execution boundary for accepted solid material routes. The lane derives
  render/cache keys through `GPUPipelineKeys`, validates target state against
  the accepted blend gate, materializes payload upload, bind group, render
  pipeline operand, and pass commands, and keeps payload values and concrete
  resources out of executable keys.
- Independent review found three P2 gaps around target-state validation,
  destination-read linkage, and refusal coverage. Follow-up fixes landed, and
  re-review found no remaining P0/P1/P2 findings.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:paint-blend`
