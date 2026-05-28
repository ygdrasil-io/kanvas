# M41 Linear Gradient Generated Evidence

Linear: GRA-200
Date: 2026-05-28
Scene: `linear-gradient-rect`

## Result

Converted the M39 `linear-gradient-rect` P1 route-convergence row from static registry evidence to generated dashboard evidence consumed by `pipelineGeneratedSceneExport`.

The row preserves the accepted M39 route result:

- scene status: `pass`;
- CPU lane: `pass` via `cpu.shader.linear-gradient.rect`;
- GPU lane: `pass` via `webgpu.generated.linear-gradient.rect`;
- threshold: `99.95`;
- GPU route pipeline key: `code=[entryPoint=fs_clamp,generatedPath=true,shaderFamily=linearGradient] state=[blendMode=kSrcOver]`;
- fallback reason: `none`.

## Generated WGSL Evidence

The GPU route remains parser-backed generated WGSL evidence. The source validation command covers both the rendered scene and the generated WGSL golden/parser/reflection tests:

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest'
```

## Generated Artifacts

The exporter source artifacts now live under:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/
```

The generated row includes:

- `skia.png`
- `cpu.png`
- `gpu.png`
- `cpu-diff.png`
- `gpu-diff.png`
- `route-cpu.json`
- `route-gpu.json`
- `stats.json`
- `cpu-performance.json`
- `gpu-performance.json`

The existing CPU/GPU `performanceTrend` blocks remain estimated informational metrics and keep their raw metric links.

## Source Reports

- Generated WGSL spec: `.upstream/specs/wgsl-pipeline/04-gpu-generated-wgsl-backend.md`
- M39 scene report: `reports/wgsl-pipeline/2026-05-28-m39-gradient-srcover-dashboard-scenes.md`
- M39 closeout: `reports/wgsl-pipeline/2026-05-28-m39-route-convergence-closeout.md`

No similarity floor was changed or lowered.

## Validation

- `rtk git diff --check`
- `rtk ./gradlew --no-daemon pipelineSceneDashboard`
- `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest'`
