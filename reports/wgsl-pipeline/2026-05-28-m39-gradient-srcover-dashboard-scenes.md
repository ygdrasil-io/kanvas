# M39 Gradient And SrcOver Dashboard Scenes

Date: 2026-05-28
Linear: GRA-185
Milestone: M39 Pipeline Route Convergence

## Scope

GRA-185 adds the first two selected M39 P1 route-convergence rows to the static
scene dashboard:

- `linear-gradient-rect`
- `src-over-stack`

The rows are intentionally distinct from P0 `solid-rect`: they prove shader
lowering/generated WGSL metadata and fixed-function blend-stack metadata rather
than only solid fill coverage.

## `linear-gradient-rect`

Source tests:

- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/LinearGradientRectTest.kt`
- `GeneratedLinearGradientWgslTest`

Dashboard artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/stats.json`

Route evidence:

- CPU route: `cpu.shader.linear-gradient.rect`
- GPU route: `webgpu.generated.linear-gradient.rect`
- Pipeline metadata: `shaderFamily=linearGradient`, `entryPoint=fs_clamp`,
  `generatedPath=true`, `blendMode=kSrcOver`
- Fallback policy: `fallbackReason=none` for the selected kClamp rect route.

## `src-over-stack`

Source tests:

- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/BlendModeTest.kt`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/TranslucentSrcOverTest.kt`

Dashboard artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/stats.json`

Route evidence:

- CPU route: `cpu.blend.src-over-stack`
- GPU route: `webgpu.blend.src-over.fixed-function`
- Pipeline metadata: fixed-function `kSrc` seed draw followed by `kSrcOver`
  analytic rect stack.
- Fallback policy: `fallbackReason=none` for the selected fixed-function blend
  stack. Unsupported blend variants remain separate expected-unsupported rows.

## Dashboard Export

The static dashboard source is `reports/wgsl-pipeline/scenes/data/scenes.json`.
The Gradle export target is `build/reports/wgsl-pipeline-scenes/index.html`.

## Validation

Required validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest'
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BlendModeTest --tests org.skia.gpu.webgpu.TranslucentSrcOverTest
```
