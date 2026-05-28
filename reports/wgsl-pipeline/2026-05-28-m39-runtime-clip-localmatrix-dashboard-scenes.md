# M39 Runtime, Clip, And Local-Matrix Dashboard Scenes

Date: 2026-05-28
Linear: GRA-186
Milestone: M39 Pipeline Route Convergence

## Scope

GRA-186 adds the remaining selected M39 P1 route-convergence rows:

- `runtime-effect-simple`
- `clip-rect-difference`
- `bitmap-shader-local-matrix`

The runtime-effect row keeps `SkRuntimeEffect` as a compatibility facade backed
by a registered Kotlin/WGSL descriptor. It does not imply a SkSL compiler port.

## `runtime-effect-simple`

Source tests:

- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/RuntimeEffectDescriptorWebGpuTest.kt`

Dashboard artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/stats.json`

Route evidence:

- CPU route: `cpu.runtime-effect.descriptor.simple_rt`
- GPU route: `webgpu.runtime-effect.descriptor.simple_rt`
- Registered descriptor: `runtime_simple_rt.wgsl`, uniform `gColor` at offset 0
- Fallback policy: `fallbackReason=none` for `SimpleRT`; unregistered runtime
  shaders fail with the stable descriptor diagnostic asserted by the source
  test.

## `clip-rect-difference`

Source tests:

- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ClipDifferenceCrossTest.kt`

Dashboard artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/stats.json`

Route evidence:

- CPU route: `cpu.coverage.clip-rect-difference`
- GPU route: `webgpu.coverage.clip-difference.analytic-rrect-mask`
- Source case: `Skbug9319GM` using `clipRect(kDifference)` and
  `clipRRect(kDifference)`.
- Similarity: GPU `84.44%`, `110672 / 131072` matching pixels, threshold
  `80.0%`.
- Fallback policy: selected rect/rrect difference route has
  `fallbackReason=none`; non-selected AA clip variants remain separate stable
  expected-unsupported rows when needed.

## `bitmap-shader-local-matrix`

Source tests:

- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/BitmapShaderRotatedTest.kt`

Dashboard artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/stats.json`

Route evidence:

- CPU route: `cpu.shader.bitmap.local-matrix`
- GPU route: `webgpu.shader.bitmap.local-matrix`
- Metadata: nearest sampling, kClamp tile mode, affine inverse derived from
  `localMatrix=rotate(90deg, pivot=(2,2))`.
- Fallback policy: `fallbackReason=none` for the selected rotated local-matrix
  rect route.

## Dashboard Export

The static dashboard source is `reports/wgsl-pipeline/scenes/data/scenes.json`.
The Gradle export target is `build/reports/wgsl-pipeline-scenes/index.html`.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest
```
