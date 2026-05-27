# M36 Scene Evidence Dashboard Closeout

Date: 2026-05-28
Linear: GRA-172
Milestone: M36 -- Scene Evidence Dashboard

## Outcome

M36 delivered a static, data-driven scene dashboard under
`reports/wgsl-pipeline/scenes/` with deterministic Gradle export to
`build/reports/wgsl-pipeline-scenes/index.html`.

The dashboard now contains all P0 rows required by the M36 backlog:

| Scene | Status | CPU evidence | GPU evidence | Key limitation |
|---|---|---|---|---|
| `solid-rect` | `tracked-gap` | CPU descriptor analytic rect oracle and route JSON. | Route diagnostic only, no attached adapter-backed render artifact. | GPU render capture not produced in the local M36-C row. |
| `bitmap-rect-nearest` | `pass` | M32 accepted raster artifact and stats. | M32 accepted GPU artifact and `DrawBitmapRectSkbug4734WebGpuTest` smoke evidence. | Broader image-rect inventory remains outside this P0 row. |
| `analytic-aa-convex` | `tracked-gap` | Static CPU AA oracle and route JSON. | Route diagnostic plus promoted smoke test evidence. | GPU render capture not produced in the local M36-E row. |

## How To Read The Dashboard

- Reference: Skia/upstream image when available, otherwise CPU oracle.
- CPU render: CPU output image, diff, route JSON, and stats for the scene.
- GPU render: GPU image/diff/stats when an adapter-backed artifact is attached.
- Tracked gap: the scene remains visible, but a backend render capture is not attached yet.
- Route diagnostics: selected route, coverage strategy, pipeline key, fallback route, and fallback reason.
- Unsupported reason: must be a stable diagnostic string; blank GPU sections are not allowed.

## Raw Evidence Inventory

Solid rect:

- `reports/wgsl-pipeline/2026-05-28-m36-solid-rect-scene.md`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/stats.json`

Bitmap rect nearest:

- `reports/wgsl-pipeline/2026-05-28-m36-bitmap-rect-nearest-scene.md`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/stats.json`

Analytic AA convex:

- `reports/wgsl-pipeline/2026-05-28-m36-analytic-aa-convex-scene.md`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/stats.json`

## Validation

Local validation passed:

```text
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Additional targeted evidence was collected in the scene tickets:

- `GRA-169`: `SkBitmapDescriptorCoverageOracleTest` and `WebGpuCoveragePlanSelectorTest`.
- `GRA-170`: `DrawBitmapRectSkbug4734WebGpuTest` through `:gpu-raster:gpuSmokeTest`.
- `GRA-171`: `AnalyticAntialiasConvexWebGpuTest` through `:gpu-raster:gpuSmokeTest`.

## Deferred P1 Scene Set

No new Linear tickets were created in this closeout because the current backlog
already contains M39 route-convergence tickets that cover the next P1 dashboard
rows:

- `linear-gradient-rect` and `src-over-stack`: covered by `GRA-175` / `GRA-185`.
- `runtime-effect-simple`, clip, and local-matrix rows: covered by `GRA-175` / `GRA-186`.
- `crop-image-filter-nonnull`: covered by M38 tickets `GRA-174` through `GRA-184`.
- `path-aa-edge-budget`: covered by M37 tickets `GRA-173` through `GRA-180`.

## Known Limitations

- `solid-rect` and `analytic-aa-convex` keep GPU capture gaps visible as tracked gaps rather than claiming fabricated renders.
- The dashboard is static; native/live capture remains future work.
- Text/glyph/codec-dependent scenes remain dependency-gated and are not part of M36.
