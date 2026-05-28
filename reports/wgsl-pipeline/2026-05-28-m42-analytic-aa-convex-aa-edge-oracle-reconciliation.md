# M42 Analytic AA Convex Edge Oracle Reconciliation

Date: 2026-05-28
Issue: GRA-222

## Outcome

`analytic-aa-convex` is promoted from `tracked-gap` to `pass`.

The mismatch was in the dashboard CPU oracle, not in the WebGPU AA compositing
path. The old static oracle stored edge pixels as non-composited source alpha
`rgba(31, 122, 76, 128)`. The adapter-backed WebGPU path renders the same AA
coverage through `SrcOver` over the opaque dashboard background, producing
opaque composited edge pixels `rgba(138, 180, 152, 255)`.

The scene contract now uses the composited CPU oracle. This preserves the
important Path AA distinction: the route is still
`webgpu.coverage.path-convex-fan`, `fallbackReason=none`, and
`edgeBudgetReason=not coverage.edge-count-exceeded`. Broad Path AA rows remain
separate expected-unsupported inventory under `coverage.edge-count-exceeded`.

## Evidence

Updated artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/stats.json`

Pixel summary from regenerated artifacts:

| Artifact | Result |
|---|---|
| Reference/CPU/GPU edge pixel | `rgba(138, 180, 152, 255)` |
| Reference/CPU/GPU interior pixel | `rgba(31, 122, 76, 255)` |
| CPU diff | empty transparent diff |
| GPU diff | empty transparent diff |
| Matching pixels | 256 / 256 |
| GPU similarity | 100.0% |
| Max channel delta | 0 |
| Threshold | 99.85% |
| Adapter | Apple M2 Max |

## Dashboard Changes

`reports/wgsl-pipeline/scenes/data/scenes.json` now records:

- `status=pass`;
- `gpu.status=pass`;
- `gpu.route.fallbackReason=none`;
- `gpu.route.edgeBudgetReason=not coverage.edge-count-exceeded`;
- no `blockerReason` or `followUp` on the GPU route;
- `risk.none` instead of `risk.tracked-gap` / `risk.oracle-mismatch` tags.

## Validation

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
