# M47 Bitmap Shader Local Matrix Generated Evidence

Date: 2026-05-31
Issue: GRA-276

## Outcome

`bitmap-shader-local-matrix` was converted from static dashboard evidence to
generated evidence through `pipelineGeneratedSceneExport` while preserving the
bitmap shader local-matrix route and M43 measured performance payload links.

The static row was removed from:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

The generated row was added to:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

The scene id remains `bitmap-shader-local-matrix`, so the merged dashboard keeps
the same public row identity without duplicate scene ids.

## Preserved Support Semantics

| Field | Value |
|---|---|
| Status | `pass` |
| Priority | `P1` |
| Reference kind | `test-oracle` |
| Draw kind | `BitmapShaderRotatedTest.rotatedLocalMatrixRect` |
| CPU route | `cpu.shader.bitmap.local-matrix` |
| CPU coverage plan | `AnalyticRect(10.0,10.0,14.0,14.0,aa=false)` |
| GPU route | `webgpu.shader.bitmap.local-matrix` |
| GPU coverage strategy | `webgpu.coverage.analytic-rect` |
| GPU pipeline key | `shaderFamily=bitmapShader sampling=nearest tile=kClamp localMatrix=affineInverse state=[blendMode=kSrcOver]` |
| CPU fallback reason | `none` |
| GPU fallback reason | `none` |
| Threshold | `99.95` |
| CPU/GPU similarity | `100.0%` |
| Matching pixels | `1024 / 1024` |
| Max channel delta | `0` |

Tags changed from `source.static` / `maturity.static-evidence` to
`source.generated` / `maturity.generated-evidence`. Existing image, shader,
route, reference, and risk tags were preserved.

## Local-Matrix Route Diagnostics

The generated row keeps explicit bitmap local-matrix evidence:

- Sampling: `nearest`.
- Tile mode: `kClamp`.
- Local matrix: `rotate(90deg, pivot=(2,2))`.
- GPU remap state: `devToImageRow0` and `devToImageRow1` derived from
  `inverse(localMatrix)`.
- Pipeline key includes `localMatrix=affineInverse`.
- Image sampling payload is handed through the bitmap shader route rather than
  the P0 bitmap-rect nearest row.

## Preserved Measured Performance Payloads

| Lane | Status | Command | Baseline | Gate | Raw metrics |
|---|---|---|---|---|---|
| CPU | `measured` | `rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance` | `m43-cpu-measured-local` / `698a8c8e7554fad0d33271765f0442f038e2cbae` | `reporting-only` | `artifacts/bitmap-shader-local-matrix/cpu-performance.json` |
| GPU | `measured` | `rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance` | `m43-gpu-cache-measured-local` / `698a8c8e7554fad0d33271765f0442f038e2cbae` | `reporting-only` | `artifacts/bitmap-shader-local-matrix/gpu-performance.json` |

This conversion does not introduce a required CI performance gate. The M43
measured CPU/GPU payloads remain reporting-only.

## Artifacts

Canonical artifacts remain under:

```text
reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/
```

Key files:

- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/stats.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu-performance.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu-performance.json`

## Generation Command

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest
```

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
