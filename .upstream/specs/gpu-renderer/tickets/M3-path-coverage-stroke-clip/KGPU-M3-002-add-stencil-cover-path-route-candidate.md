---
id: KGPU-M3-002
title: "Add stencil-cover path route candidate"
status: proposed
milestone: M3
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M3-001]
legacy_gate: "path fill legacy"
---

# KGPU-M3-002 - Add stencil-cover path route candidate

## PM Note

Ce ticket explore un chemin path natif via stencil-cover, avec preuves et refus.

## Problem

Stencil-cover can be native, but must prove ordering, pass state, coverage, and
failure diagnostics before promotion.

## Scope

- Add `GPUStencilCoverPlan` evidence for one bounded path class.
- Add pass/resource/telemetry dumps and unsupported-case refusals.

## Non-Goals

- No general tessellation or compute path pipeline.
- No stroke support.

## Spec Sources

- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`
- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`

## Design Sketch

```kotlin
data class StencilCoverEvidence(val stencilPlan: String, val coverPass: String)
```

## Acceptance Criteria

- [ ] Stencil and cover ordering is dumpable.
- [ ] Unsupported fill/clip/target cases refuse.
- [ ] GPU evidence or explicit skipped reason is linked.

## Required Evidence

- Stencil-cover route dumps.
- Pass/resource/readback evidence.
- Refusal diagnostics.

## Fallback / Refusal Behavior

If stencil-cover cannot preserve semantics, route refuses or remains prepared.

## Dashboard Impact

- Expected row: `gpu-renderer.path.stencil-cover`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Candidate route only.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:geometry`
