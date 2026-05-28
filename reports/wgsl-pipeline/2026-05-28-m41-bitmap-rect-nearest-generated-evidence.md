# M41 Bitmap Rect Nearest Generated Evidence

Linear: GRA-198
Date: 2026-05-28
Scene: `bitmap-rect-nearest`

## Result

Converted the `bitmap-rect-nearest` dashboard row from static registry evidence to a generated scene result consumed by `pipelineGeneratedSceneExport`.

The source row keeps its accepted M32 behavior:

- scene status: `pass`;
- CPU lane: `pass`, similarity `100.0`;
- GPU lane: `pass`, similarity `100.0`;
- threshold: `99.95`;
- pixels: `4096`, matching pixels: `4096`, max channel delta: `0`.

## Generated Artifacts

The exporter source artifacts now live under:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/
```

Canonical dashboard paths remain stable after export:

- `artifacts/bitmap-rect-nearest/skia.png`
- `artifacts/bitmap-rect-nearest/cpu.png`
- `artifacts/bitmap-rect-nearest/gpu.png`
- `artifacts/bitmap-rect-nearest/cpu-diff.png`
- `artifacts/bitmap-rect-nearest/gpu-diff.png`
- `artifacts/bitmap-rect-nearest/route-cpu.json`
- `artifacts/bitmap-rect-nearest/route-gpu.json`
- `artifacts/bitmap-rect-nearest/stats.json`

## Source Test And Reports

- Source task: `:gpu-raster:gpuSmokeTest`
- Source test: `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`
- Accepted behavior: `.upstream/specs/wgsl-pipeline/08-bitmap-image-rect-sampling.md`
- Prior scene evidence: `reports/wgsl-pipeline/2026-05-28-m36-bitmap-rect-nearest-scene.md`
- M32 pass evidence: `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md`
- Smoke promotion: `reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion.md`

No similarity floor was changed or lowered.

## Validation

- `rtk git diff --check`
- `rtk ./gradlew --no-daemon pipelineSceneDashboard`
- `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`
