# FOR-249 Reference/GPU Residual Window Probe

Linear: FOR-249

## Decision

KEEP_DIAGNOSTIC.

FOR-249 compares the normal WebGPU output against the Skia reference PNG around
the selected `crop-image-filter-nonnull-prepass` residual cell:

```text
Crop(kDecal, rect=(40,0,80,40), input=Offset(40,0,input=null))
origin=(340,120), layerExtent=(80,40), offset=(40,0)
target cell: crop == clip == dst
target fragment: (385,125)
```

FOR-247 already proved that `scratchPixel(45,5)` contains the expected source
sample before the final Crop composite. FOR-248 then proved that a forced final
Crop composite sample reads that scratch pixel as `rgba(202,59,19,102)`.

FOR-249 adds the reference/GPU local comparison that was still missing:

```text
Skia reference fragment (385,125) -> rgba(221,153,145,255)
normal WebGPU fragment (385,125) -> rgba(220,153,145,255)
local 5x5 maxChannelDelta        -> 1
```

The target fragment and its immediate 5x5 neighborhood are already inside the
byte-level tolerance used for this diagnostic. The remaining scene-level
similarity gap is therefore not explained by scratch materialization, final
Crop UV remap, or this local Crop composite target fragment.

## Probe Method

The default renderer route is unchanged. No test-only renderer property is
enabled for FOR-249.

The test renders `SimpleOffsetImageFilterGM` through the normal WebGPU path,
loads `original-888/simple-offsetimagefilter.png`, and writes a stable local
window dump when `kanvas.sceneEvidence.write=true`:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/reference-gpu-residual-window-for249.json
```

The dump records:

- target fragment `(385,125)`;
- a 5x5 reference/GPU/diff window centered on that fragment;
- named cell samples for the `crop == clip == dst` case;
- local and observed maximum channel deltas;
- unchanged route and support decision.

No CPU fallback, readback fallback in the render path, general image-filter DAG
compiler, Ganesh/Graphite path, or SkSL compiler is added.

## Evidence

Stable dump:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/reference-gpu-residual-window-for249.json
reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/reference-gpu-residual-window-for249.json
```

Route diagnostic:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json
reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json
```

The route remains:

```text
webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite
```

The fallback reason for out-of-scope Crop(input = nonNull) graphs remains:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Boundary

No bounded renderer correction is applied by FOR-249. The local target window is
already reference-aligned within `maxChannelDelta=1`, and the current evidence
does not isolate a blend, alpha, scissor, or coordinate error at `(385,125)`.

The scene stays below strict promotion and remains a fidelity-gap diagnostic.

## Validation

Passed:

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
rtk python3 scripts/validate_for249_reference_gpu_residual_probe.py
rtk python3 scripts/validate_for248_final_crop_composite_probe.py
rtk python3 scripts/validate_for247_crop_offset_scratch_probe.py
rtk python3 scripts/validate_for246_webgpu_crop_offset_materialization.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
