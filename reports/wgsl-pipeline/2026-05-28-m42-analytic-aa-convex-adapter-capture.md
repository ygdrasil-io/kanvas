# M42 Analytic AA Convex Adapter Capture

Date: 2026-05-28
Linear: GRA-203
Scene: `analytic-aa-convex`
Follow-up: GRA-222

## Result

`analytic-aa-convex` now has adapter-backed WebGPU render evidence, but remains `tracked-gap`.

The GPU render uses the supported convex AA lane:

- Coverage strategy: `webgpu.coverage.path-convex-fan`
- Pipeline key: `coverageKind=pathConvexFan`
- Fallback reason: `none`
- Edge-budget reason: `not coverage.edge-count-exceeded`

This is distinct from the broad Path AA edge-budget expected-unsupported inventory.

## Adapter Evidence

- Backend: WebGPU
- Adapter: Apple M2 Max
- Similarity against current CPU oracle: 90.6%
- Matching pixels: 232 / 256
- Max channel delta: 127
- Support threshold: 99.85%

## Blocker

The current static CPU oracle stores AA edge pixels as non-composited green alpha pixels: `(31,122,76,128)`.

The adapter-backed WebGPU `SrcOver` render produces composited opaque AA edge pixels over the scene background: `(138,180,152,255)`.

The rendered route is present and not refused, but the row cannot claim `pass` until GRA-222 reconciles the oracle/render contract or adjusts the GPU AA compositing semantics with matching CPU evidence.

## Artifacts

- Reference: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/skia.png`
- CPU oracle: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/cpu.png`
- CPU diff: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/cpu-diff.png`
- GPU capture: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu-diff.png`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/stats.json`

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Result: passed; wrote `gpu.png`, `gpu-diff.png`, `route-gpu.json`, and `stats.json` from an adapter-backed run while preserving `tracked-gap` status.
