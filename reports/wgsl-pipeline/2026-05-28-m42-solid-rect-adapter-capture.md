# M42 Solid Rect Adapter Capture

Date: 2026-05-28
Linear: GRA-202
Scene: `solid-rect`

## Result

`solid-rect` moved from `tracked-gap` to `pass` with adapter-backed WebGPU evidence.

The GPU image was produced by `SkWebGpuDevice.flush()` in `SolidRectSceneCaptureTest`; no CPU artifact was copied or reused as GPU evidence.

## Adapter Evidence

- Backend: WebGPU
- Adapter: Apple M2 Max
- Similarity: 100.0%
- Matching pixels: 64 / 64
- Max channel delta: 0
- Threshold: 99.95%

## Artifacts

- Reference: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/skia.png`
- CPU oracle: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/cpu.png`
- CPU diff: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/cpu-diff.png`
- GPU capture: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/gpu.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/gpu-diff.png`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/solid-rect/stats.json`

## Route

- CPU route remains `cpu.descriptor.coverage-plan.solid-rect` with `fallbackReason=none`.
- GPU route is `webgpu.coverage.analytic-rect`, pipeline key `coverageKind=analyticRect`, with `fallbackReason=none`.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Result: passed; wrote `gpu.png`, `gpu-diff.png`, `route-gpu.json`, and `stats.json` from an adapter-backed run.
