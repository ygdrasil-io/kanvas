# M42 Adapter-Backed P0 Capture Closeout

Date: 2026-05-28
Linear: GRA-205
Epic: GRA-192

## Summary

M42 attempted adapter-backed GPU captures for the two P0 tracked-gap rows left by M41.

| Scene | Before M42 | After M42 | Evidence |
|---|---|---|---|
| `solid-rect` | `tracked-gap` | `pass` | Adapter-backed WebGPU `gpu.png`, `gpu-diff.png`, `route-gpu.json`, and `stats.json`; 100.0%, 64/64 matching pixels, max channel delta 0. |
| `analytic-aa-convex` | `tracked-gap` | `tracked-gap` | Adapter-backed WebGPU artifacts exist; route is `webgpu.coverage.path-convex-fan`, but current CPU oracle edge-alpha contract does not match WebGPU `SrcOver` AA output. |

## Before / After Counts

M42 start from M41 closeout:

| Status | Count |
|---|---:|
| `pass` | 7 |
| `tracked-gap` | 2 |
| `expected-unsupported` | 2 |
| `fail` | 0 |

M42 static registry after PRs #1221-#1223:

| Status | Count |
|---|---:|
| `pass` | 5 static rows |
| `tracked-gap` | 1 static row |
| `expected-unsupported` | 2 static rows |
| `fail` | 0 |

The static count excludes generated rows merged into the dashboard export by `pipelineGeneratedSceneExport`. The generated M41 rows remain available through the export path.

## PRs

- GRA-202 / PR #1221: `solid-rect` adapter-backed capture, merged as `77e993bc24fc33acc6fd50a4b88222819ff3dbf1`.
- GRA-203 / PR #1222: `analytic-aa-convex` adapter-backed capture with tracked blocker, merged as `6bbe025e954368226960073defc35d79611dcf36`.
- GRA-204 / PR #1223: dashboard tracked-gap blocker/follow-up policy, merged as `00264dabda76ef070d8af26cbfa9e68ed5112b7a`.

## Artifact Paths

### solid-rect

- Report: `reports/wgsl-pipeline/2026-05-28-m42-solid-rect-adapter-capture.md`
- GPU capture: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/gpu.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/gpu-diff.png`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/stats.json`

### analytic-aa-convex

- Report: `reports/wgsl-pipeline/2026-05-28-m42-analytic-aa-convex-adapter-capture.md`
- GPU capture: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu-diff.png`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/stats.json`
- Follow-up: `GRA-222`

## Residual Risk

`analytic-aa-convex` remains tracked because the current static CPU oracle stores AA edge pixels as non-composited alpha values while WebGPU renders the AA edge through `SrcOver` over the scene background. This is not an edge-budget refusal: `edgeBudgetReason=not coverage.edge-count-exceeded`.

## M43 / M44 Readiness

- M43 benchmark harness work can use `solid-rect` as an adapter-backed pass scene.
- M43 should keep performance fields non-gating until measured host/JDK/backend/adapter metadata replaces estimated values.
- M44 Path AA promotion should not treat `analytic-aa-convex` as a broad edge-budget failure; it is a narrow oracle/render-contract follow-up now owned by `GRA-222`.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest
```
