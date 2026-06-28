---
id: KGPU-M40-003
title: "Hi-Z occlusion culling"
status: proposed
milestone: M40
priority: P1
owner_area: analysis
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001, KGPU-M40-001]
legacy_gate: null
---

# KGPU-M40-003 - Hi-Z occlusion culling

## PM Note

Le Hi-Z culling utilise une pyramide de profondeur GPU pour éliminer les draws occlus et réduire la charge de rasterisation.

## Problem

Without occlusion culling, every opaque draw in view is submitted to the GPU regardless of visibility, wasting fragment shader invocations and bandwidth on fully occluded geometry. A Hi-Z depth pyramid provides conservative per-draw occlusion tests at minimal cost.

## Scope

- `GPUHiZPyramid` — mip chain of depth buffers where each level stores the maximum depth of the corresponding 2x2 block from the level below.
- `GPUHiZPyramidBuildPlan` — compute pass that builds the pyramid from Z-prepass depth or previous-frame depth.
- `GPUHiZOcclusionTest` — projects draw bounds, samples the highest pyramid level where bounds <= 4 texels, compares draw min depth against pyramid max depth.
- `GPUHiZOcclusionResult` — `Visible`, `Occluded`, or `Uncertain` outcome.
- Source depth options: Z-prepass (most accurate), Previous frame (faster, re-projected), Hybrid.
- Depth format policy: `depth32float` preferred, `depth24plus` accepted, `depth16unorm` refused with diagnostic.
- Integration: extends `GPUOcclusionTracker`, culls only opaque draws, zero false-positive tolerance.
- Tile interaction: per-tile pyramid built from tile depth region (from KGPU-M40-001).

## Non-Goals

- Do not promote support without accepted evidence.
- Do not activate product routing unless this ticket explicitly owns that decision and validation.
- Do not add hidden CPU-rendered texture compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/40-hi-z-occlusion-culling.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- `GFX-HIZ-OCCLUSION-CULLER` from `../GRAPHITE-ALGORITHM-REFERENCES.md` — study pyramid construction, conservative depth test, and Z-prepass integration in Graphite.
- Boundary: references are for algorithm study only; do not port Graphite or Ganesh and do not treat them as Kanvas acceptance criteria.

## Design Sketch

```kotlin
data class GPUHiZPyramid(
    val levels: List<GPUDepthBuffer>,
    val topLevelWidth: Int,
    val topLevelHeight: Int,
    val sourceDepthFormat: GPUDepthFormat,
)

data class GPUHiZPyramidBuildPlan(
    val sourceDepth: GPUHiZDepthSource,
    val computePass: GPUComputePass,
    val maxLevels: Int,
)

enum class GPUHiZDepthSource {
    ZPREPASS,
    PREVIOUS_FRAME,
    HYBRID,
}

data class GPUHiZOcclusionTest(
    val pyramid: GPUHiZPyramid,
    val drawBounds: GPURect,
    val drawMinDepth: Float,
    val selectedLevel: Int,
)

sealed class GPUHiZOcclusionResult {
    object Visible : GPUHiZOcclusionResult()
    object Occluded : GPUHiZOcclusionResult()
    object Uncertain : GPUHiZOcclusionResult()
}

enum class GPUDepthFormat {
    DEPTH32FLOAT,
    DEPTH24PLUS,
    DEPTH16UNORM,
}
```

## Acceptance Criteria

- [ ] 50+ opaque rects with 50% occlusion: Hi-Z reduces rendered draw count by >= 40%.
- [ ] Z-prepass depth produces correct pyramid levels verified against CPU oracle.
- [ ] Zero false positives (no visible draw incorrectly culled).
- [ ] False negatives measured and reported as efficiency percentage.
- [ ] Memory budget for pyramid storage enforced.
- [ ] Depth16unorm format refused with stable diagnostic.
- [ ] Depth-not-readable target refused with stable diagnostic.

## Required Evidence

- Occlusion reduction percentage report (>= 40%) with draw-count diff.
- CPU oracle pyramid level comparison for Z-prepass depth source.
- Zero-false-positive assertion with visible-region verification.
- False-negative efficiency report.
- Depth format refusal diagnostic for depth16unorm.
- Depth-not-readable refusal diagnostic.
- Telemetry dump: `reports/gpu-renderer/telemetry/hi-z-occlusion/`.

## Fallback / Refusal Behavior

- Unsupported depth format: `unsupported.occlusion.depth_format_unsupported` diagnostic, Hi-Z disabled for target.
- Depth not readable: `unsupported.occlusion.depth_not_readable` diagnostic, Hi-Z disabled for target.
- Silent fallback to CPU-rendered complete draw/layer/filter/text texture compatibility is not allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.architecture.hi-z-occlusion`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HiZ*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M40`
- `area:analysis`
