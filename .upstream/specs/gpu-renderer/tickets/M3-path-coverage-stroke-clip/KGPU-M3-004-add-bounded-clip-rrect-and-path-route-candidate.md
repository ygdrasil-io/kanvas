---
id: KGPU-M3-004
title: "Add bounded clip rrect and path route candidate"
status: done
milestone: M3
priority: P0
owner_area: clips-atlas
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M3-001]
legacy_gate: "clip legacy"
---

# KGPU-M3-004 - Add bounded clip rrect and path route candidate

## PM Note

Ce ticket rend les clips complexes visibles comme plans ou refus, pas comme
approximations cachées.

## Problem

Clip rrect/path requires ordering, stencil/mask/atlas strategy, and stable
refusals for unsupported stack interactions.

## Scope

- Add bounded rrect/path clip planning and dump evidence.
- Add refusal fixtures for difference, inverse, shader, and over-budget clips.

## Non-Goals

- No arbitrary clip-stack support.
- No CPU-rendered clipped layer fallback.

## Spec Sources

- `.upstream/specs/gpu-renderer/24-clip-stencil-mask-pipeline.md`
- `.upstream/specs/gpu-renderer/19-path-coverage-atlas-strategy.md`

## Graphite Algorithm References

- [`GFX-CLIP-SIMPLIFY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-clip-simplify) - source [ClipStack.cpp:348](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ClipStack.cpp:348); Study rect/rrect/path containment and complex clip preservation.
- [`GFX-RENDERSTEP-SCISSOR`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-scissor) - source [Renderer.cpp:49](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.cpp:49); Reference scissor minimization and inverse-fill handling for bounded clips.
- [`GFX-TESSELLATE-WEDGES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-wedges) - source [TessellateWedgesRenderStep.cpp:82](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/TessellateWedgesRenderStep.cpp:82); Use wedge tessellation as a path clip draw reference when GPU-native support is considered.
- [`GFX-COVERAGE-MASK-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-coverage-mask-step) - source [CoverageMaskRenderStep.cpp:39](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/CoverageMaskRenderStep.cpp:39); Reference coverage-mask sampling for prepared clip artifacts.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class ComplexClipEvidence(val clipPlan: String, val orderingToken: String)
```

## Acceptance Criteria

- [ ] Clip ordering and bounds are dumpable.
- [ ] Stencil/mask/atlas strategy maps to an explicit route kind.
- [ ] Unsupported interactions refuse.

## Required Evidence

- Clip plan, stencil/mask/atlas, and refusal dumps.
- Route diagnostics and budget facts.

## Fallback / Refusal Behavior

Unsupported clips refuse with `unsupported.clip.*` diagnostics.

## Dashboard Impact

- Expected row: `gpu-renderer.clip.rrect-path`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added `GPUBoundedClipPreparedPlanner` contract evidence for one
  bounded rrect+path intersect stack as `CPUPreparedGPU` with a typed
  `CoverageMaskArtifact`, stable `GPUClipOrderingToken`, deterministic artifact
  key `coverage.clip.*` including content-changing element facts, ordered
  element descriptor dumps, `NoAtlas` mask strategy evidence, and stable
  refusals for difference operation, inverse fill, unregistered shader clip,
  mask budget overflow, nondeterministic rrect and path keys, shape/key
  mismatch, and unbounded stack bounds. Independent review
  `019ec7f0-a288-76f0-b573-a31d94fe8ca7` found insufficient artifact key
  content and missing shape/key prefix validation; both were remediated with
  targeted RED/GREEN coverage. Post-remediation review
  `019ec7f6-e111-7a32-9883-06d8e174c429` then found missing `fillRule` in the
  artifact key and lossy key sanitation; both were remediated by hex-encoding
  accepted stack/element content facts and adding RED/GREEN coverage for
  `NonZero` vs `EvenOdd`, separator-preserving shape keys, and stack bounds.
  Final independent review `019ec7fe-3b8a-77b1-bc93-e9f75f6965b7` accepted the
  evidence and confirmed the key remediation, stable non-claims, and
  contract-only scope. The slice does not activate product clipping, atlas
  generation, stencil
  coverage, shader clips, arbitrary clip stacks, adapter-backed execution, or
  CPU-rendered clipped layer fallback. Remaining gate: future clip promotion
  still needs real execution, atlas/stencil/shader route evidence, and
  visual/reference evidence before any broader support claim.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:clips`
