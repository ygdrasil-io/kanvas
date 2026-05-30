# M48-D Bitmap / Gradient Generated Evidence

Date: 2026-05-31
Linear: GRA-283
Parent epic: GRA-279
Selection: `reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md`

## Outcome

GRA-283 adds the three selected M48-D bitmap/gradient scenes as generated
dashboard evidence rows:

- `bitmap-shader-repeat-tile`
- `bitmap-subset-local-matrix-repeat`
- `sweep-gradient-path-clamp`

Each row is scoped to the selected route contract and keeps `fallbackReason=none`.
No row claims broad bitmap, gradient, codec, color-space, perspective, or Path AA
coverage beyond its named owner test.

## Rows

| Row | Owner command | Artifact root | Threshold | Contract |
|---|---|---|---:|---|
| `bitmap-shader-repeat-tile` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderPaintRectTest` | `artifacts/bitmap-shader-repeat-tile` | 99.95 | Nearest bitmap shader repeat tiling on rect. |
| `bitmap-subset-local-matrix-repeat` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.BitmapSubsetShaderCrossBackendTest` | `artifacts/bitmap-subset-local-matrix-repeat` | 99.94 | Bitmap subset shader with rotate/scale local matrix and repeat tiling. |
| `sweep-gradient-path-clamp` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SweepGradientPathTest` | `artifacts/sweep-gradient-path-clamp` | 99.95 | kClamp sweep gradient on AA circle path. |

## Route Summary

| Row | CPU route | GPU route | Fallback |
|---|---|---|---|
| `bitmap-shader-repeat-tile` | `cpu.shader.bitmap.repeat-tile-oracle` | `webgpu.shader.bitmap.repeat-tile` | `none` |
| `bitmap-subset-local-matrix-repeat` | `cpu.shader.bitmap.subset-local-matrix-repeat` | `webgpu.shader.bitmap.subset-local-matrix-repeat` | `none` |
| `sweep-gradient-path-clamp` | `cpu.shader.sweep-gradient.path-aa-oracle` | `webgpu.generated.sweep-gradient.path-aa` | `none` |

## Dashboard Impact

Expected counters after this ticket:

- scene rows: 20
- `pass`: 18
- `expected-unsupported`: 2
- `tracked-gap`: 0
- `fail`: 0
- `maturity.generated-evidence`: 18
- `maturity.static-evidence`: 2

## Validation

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderPaintRectTest --tests org.skia.gpu.webgpu.crossbackend.BitmapSubsetShaderCrossBackendTest --tests org.skia.gpu.webgpu.SweepGradientPathTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
