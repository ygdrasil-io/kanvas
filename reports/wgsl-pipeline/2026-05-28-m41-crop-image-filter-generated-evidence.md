# M41 Crop Image-Filter Generated Evidence

Linear: GRA-199
Date: 2026-05-28
Scene: `crop-image-filter-nonnull-prepass`

## Result

Converted the selected M38 `Crop(kDecal, input = Offset(null))` image-filter child pre-pass dashboard row from static registry evidence to generated scene evidence consumed by `pipelineGeneratedSceneExport`.

The row preserves the accepted M38 result:

- scene status: `pass`;
- CPU lane: `pass`, similarity `84.88`;
- GPU lane: `pass`, similarity `98.13`;
- threshold: `50.0`;
- pixels: `128000`;
- pre-pass diagnostic: `LayerCompositeDraw.materializeToIntermediate` into `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch`.

## Generated Artifacts

The exporter source artifacts now live under:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/
```

The generated row includes:

- `skia.png`
- `cpu.png`
- `gpu.png`
- `cpu-diff.png`
- `gpu-diff.png`
- `route-cpu.json`
- `route-gpu.json`
- `route-prepass.json`
- `stats.json`

## Scope Boundary

This remains the bounded M38 SimpleOffset image-filter case only. It does not promote a general image-filter DAG compiler.

Out-of-scope `Crop(input = nonNull)` graph shapes must keep stable unsupported diagnostics, including `image-filter.crop-input-nonnull-prepass-required`, until a separate bounded implementation and dashboard row exist.

## Source Test And Reports

- Source task: `:gpu-raster:gpuSmokeTest`
- Source test: `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest`
- Accepted behavior: `.upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md`
- M38 implementation: `reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-implementation.md`
- M38 policy update: `reports/wgsl-pipeline/2026-05-28-m38-image-filter-policy-update.md`
- M38 closeout: `reports/wgsl-pipeline/2026-05-28-m38-image-filter-closeout.md`

No similarity floor was changed or lowered.

## Validation

- `rtk git diff --check`
- `rtk ./gradlew --no-daemon pipelineSceneDashboard`
- `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest`
