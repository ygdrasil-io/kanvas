# M39 Route Convergence Closeout

Date: 2026-05-28
Linear: GRA-187
Milestone: M39 Pipeline Route Convergence

## Summary

M39 closes with the static scene dashboard covering 11 rows across P0, M37,
M38, and the selected M39 P1 scene set. Every row has route diagnostics for CPU
and GPU, raw artifacts, stats, and a stable fallback reason when the selected
GPU path is not rendered.

Dashboard source: `reports/wgsl-pipeline/scenes/data/scenes.json`
Dashboard export: `build/reports/wgsl-pipeline-scenes/index.html`

## Scene Route Matrix

| Scene | Priority | Status | CPU route | GPU route | GPU fallback reason |
|---|---|---|---|---|---|
| `solid-rect` | P0 | tracked-gap | `cpu.descriptor.coverage-plan.solid-rect` | `webgpu.coverage.analytic-rect` | `gpu.adapter-backed-solid-rect-capture-not-produced-in-m36-c-local-run` |
| `bitmap-rect-nearest` | P0 | pass | `cpu.image-rect.strict-nearest` | `webgpu.image-rect.strict-nearest` | `none` |
| `analytic-aa-convex` | P0 | tracked-gap | `cpu.path-coverage.analytic-aa-convex-oracle` | `webgpu.coverage.path-convex-fan` | `gpu.adapter-backed-analytic-aa-convex-capture-not-produced-in-m36-e-local-run` |
| `linear-gradient-rect` | P1 | pass | `cpu.shader.linear-gradient.rect` | `webgpu.generated.linear-gradient.rect` | `none` |
| `src-over-stack` | P1 | pass | `cpu.blend.src-over-stack` | `webgpu.blend.src-over.fixed-function` | `none` |
| `runtime-effect-simple` | P1 | pass | `cpu.runtime-effect.descriptor.simple_rt` | `webgpu.runtime-effect.descriptor.simple_rt` | `none` |
| `clip-rect-difference` | P1 | pass | `cpu.coverage.clip-rect-difference` | `webgpu.coverage.clip-difference.analytic-rrect-mask` | `none` |
| `bitmap-shader-local-matrix` | P1 | pass | `cpu.shader.bitmap.local-matrix` | `webgpu.shader.bitmap.local-matrix` | `none` |
| `crop-image-filter-nonnull-prepass` | P1 | pass | `cpu.image-filter.crop-nonnull.offset-oracle` | `webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite` | `none` |
| `path-aa-stroke-outline-fallback` | P1 | expected-unsupported | `cpu.path-coverage.stroke-outline-oracle` | `webgpu.coverage.refuse` | `coverage.stroke-outline-edge-count-exceeded` |
| `path-aa-edge-budget-boundary` | P1 | expected-unsupported | `cpu.path-coverage.raster-oracle` | `webgpu.coverage.refuse` | `coverage.edge-count-exceeded` |

## Status Counts

| Status | Count | Notes |
|---|---:|---|
| `pass` | 7 | Includes all five selected M39 P1 route-convergence scenes plus bitmap nearest and M38 image-filter pre-pass. |
| `tracked-gap` | 2 | P0 rows remain visible with stable adapter-capture fallback reasons. |
| `expected-unsupported` | 2 | M37 Path AA breadth rows remain explicit refusals with stable coverage fallback reasons. |
| `fail` | 0 | No M39 P1 failure was introduced. |

## Route Coverage

| Route family | Covered scenes |
|---|---|
| Geometry / coverage | `solid-rect`, `analytic-aa-convex`, `clip-rect-difference`, `path-aa-stroke-outline-fallback`, `path-aa-edge-budget-boundary` |
| Image sampling | `bitmap-rect-nearest`, `bitmap-shader-local-matrix` |
| Shader lowering / generated WGSL | `linear-gradient-rect` |
| Blend / destination load-store semantics | `src-over-stack` |
| Runtime effects | `runtime-effect-simple` |
| Image filters | `crop-image-filter-nonnull-prepass` |
| Path AA stable refusal | `path-aa-stroke-outline-fallback`, `path-aa-edge-budget-boundary` |

## Unsupported And Tracked Gap Groups

| Group | Scenes | Policy |
|---|---|---|
| Adapter-backed capture absent | `solid-rect`, `analytic-aa-convex` | Kept as `tracked-gap`; route selectors are visible but old M36 local captures were not adapter-backed. |
| `coverage.stroke-outline-edge-count-exceeded` | `path-aa-stroke-outline-fallback` | Expected unsupported; stroke outline remains above the selected Path AA route boundary. |
| `coverage.edge-count-exceeded` | `path-aa-edge-budget-boundary` | Expected unsupported; 256-edge budget remains stable until a profiling ADR justifies a change. |

## M39 Ticket Evidence

| Ticket | Result | Evidence |
|---|---|---|
| GRA-175 | Selected five implementation-ready P1 scenes. | `reports/wgsl-pipeline/2026-05-28-m39-p1-scene-selection.md`, PR #1207 |
| GRA-185 | Added gradient and SrcOver dashboard rows. | `reports/wgsl-pipeline/2026-05-28-m39-gradient-srcover-dashboard-scenes.md`, PR #1208 |
| GRA-186 | Added runtime-effect, clip, and local-matrix dashboard rows. | `reports/wgsl-pipeline/2026-05-28-m39-runtime-clip-localmatrix-dashboard-scenes.md`, PR #1209 |
| GRA-187 | Closed M39 route convergence evidence. | This report, PR #1210 |

## Residual Risks And Next Backlog

- P0 `solid-rect` and `analytic-aa-convex` still carry old adapter-capture
  tracked gaps. They are not new M39 failures.
- Path AA stroke-outline and edge-budget scenes remain expected unsupported by
  stable coverage fallback reasons.
- Text/glyph scenes remain dependency-gated until real font infrastructure
  lands.
- M40 should add trendable performance and cache evidence for promoted routes,
  not broaden unsupported feature scope.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
