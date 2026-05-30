# M48-C Paint / Blend / Transform Generated Evidence

Date: 2026-05-31
Linear: GRA-282
Parent epic: GRA-279
Selection: `reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md`

## Outcome

GRA-282 adds the four M48-C selected paint/blend/transform scenes as generated
dashboard evidence rows:

- `draw-paint-full-clip`
- `draw-paint-clipped-rect`
- `scaled-rects-transform-stack`
- `gradient-color-filter-linear-kplus`

Each row has reference, CPU, GPU, diff, route, stats, tags, and raw evidence
links. All four rows are scoped scene contracts; none claims broad paint, blend,
transform, color-filter, clip, or color-space support.

## Rows

| Row | Owner command | Artifact root | Threshold | Non-claim |
|---|---|---|---:|---|
| `draw-paint-full-clip` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawPaintTest` | `artifacts/draw-paint-full-clip` | 99.95 | Opaque `drawPaint` full-clip only. |
| `draw-paint-clipped-rect` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawPaintTest` | `artifacts/draw-paint-clipped-rect` | 99.95 | Rect clip only; not arbitrary clip stacks. |
| `scaled-rects-transform-stack` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ScaledRectsWebGpuTest` | `artifacts/scaled-rects-transform-stack` | 99.99 | 2D scaled/rotated rect stack only; not perspective or 3D. |
| `gradient-color-filter-linear-kplus` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GradientColorFilterTest` | `artifacts/gradient-color-filter-linear-kplus` | 99.95 | Linear gradient + Blend(red,kPlus) color filter only. |

## Route Summary

| Row | CPU route | GPU route | Fallback |
|---|---|---|---|
| `draw-paint-full-clip` | `cpu.paint.draw-paint.full-clip-oracle` | `webgpu.paint.draw-paint.full-clip` | `none` |
| `draw-paint-clipped-rect` | `cpu.paint.draw-paint.clip-rect-oracle` | `webgpu.paint.draw-paint.clip-rect` | `none` |
| `scaled-rects-transform-stack` | `cpu.gm.scaled-rects.reference-oracle` | `webgpu.transform.scaled-rects.convex-polygon` | `none` |
| `gradient-color-filter-linear-kplus` | `cpu.shader.linear-gradient.color-filter.blend-kplus-oracle` | `webgpu.generated.linear-gradient.color-filter.blend-kplus` | `none` |

## Dashboard Impact

Expected counters after this ticket:

- scene rows: 17
- `pass`: 15
- `expected-unsupported`: 2
- `tracked-gap`: 0
- `fail`: 0
- `maturity.generated-evidence`: 15
- `maturity.static-evidence`: 2

## Validation

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawPaintTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ScaledRectsWebGpuTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GradientColorFilterTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
