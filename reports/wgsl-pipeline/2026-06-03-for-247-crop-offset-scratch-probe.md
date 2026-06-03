# FOR-247 WebGPU Crop(input = Offset) Scratch Probe

Linear: FOR-247

## Decision

KEEP_DIAGNOSTIC.

FOR-247 adds a bounded WebGPU scratch probe for the selected
`crop-image-filter-nonnull-prepass` residual cell:

```text
Crop(kDecal, rect=(40,0,80,40), input=Offset(40,0,input=null))
origin=(340,120), layerExtent=(80,40), offset=(40,0)
target cell: crop == clip == dst
```

The probe proves that the child materialization is not the source of this
cell's remaining strict-fidelity failure:

```text
sourceLocal(5,5)  -> rgba(202,59,19,255)
scratchPixel(45,5) -> rgba(202,59,19,255)
```

`scratchPixel(45,5)` matches `sourceLocal(5,5)` before the final Crop
composite, both observed as `rgba(202,59,19,255)`.

## Probe Method

The default renderer route is unchanged. A test-only system property,
`kanvas.webgpu.for247.cropOffsetScratchProbe`, enables two 1x1 WebGPU `kSrc`
diagnostic copies only when all selected-case guards match:

- `origin=(340,120)`
- `layerExtent=(80,40)`
- `offset=(40,0)`
- `crop=(40,0,80,40)`
- `tileMode=kDecal`

The diagnostic copies run around the existing child materialization pass:

| Output sentinel | Observed texture | Observed coordinate |
|---|---|---:|
| `(0,0)` | `gpuSrc.intermediateView` | `sourceLocal(5,5)` |
| `(1,0)` | `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch` | `scratchPixel(45,5)` |

The normal WebGPU readback observes those two sentinels after the GM finishes.
No CPU fallback, readback fallback, graph compiler, Ganesh/Graphite path, or
SkSL compiler is added.

## Evidence

Stable dump:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/scratch-probe-for247.json
```

Route diagnostic:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json
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

No bounded materialization correction is applied. Since the scratch probe
matches `sourceLocal(5,5)`, the remaining row-2 residual is downstream of the
child pre-pass content and still requires a separate final Crop/composite
diagnostic before any scoped coordinate correction can be justified.

The scene stays below strict promotion and remains a fidelity-gap diagnostic.

## Validation

Passed:

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest'
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
```

Additional expected checks for handoff:

```text
rtk python3 scripts/validate_for247_crop_offset_scratch_probe.py
rtk python3 scripts/validate_for246_webgpu_crop_offset_materialization.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
