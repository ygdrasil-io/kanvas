# FOR-248 WebGPU Final Crop Composite Probe

Linear: FOR-248

## Decision

KEEP_DIAGNOSTIC.

FOR-248 extends the bounded WebGPU probe for the selected
`crop-image-filter-nonnull-prepass` residual cell:

```text
Crop(kDecal, rect=(40,0,80,40), input=Offset(40,0,input=null))
origin=(340,120), layerExtent=(80,40), offset=(40,0)
target cell: crop == clip == dst
```

FOR-247 already proved that `scratchPixel(45,5)` contains the expected source
sample before the final Crop composite:

```text
scratchPixel(45,5) -> rgba(202,59,19,255)
```

FOR-248 proves that a forced final Crop composite sample of the same layer
coordinate reads that scratch pixel and applies the GM paint alpha:

```text
final Crop composite sentinel -> rgba(202,59,19,102)
normal final GM fragment       -> rgba(220,153,145,255)
```

The forced composite sentinel is not clipped or decal-transparentized after the
Crop remap. The normal final GM fragment is also observed explicitly, but this
probe does not by itself prove a bounded renderer correction or justify strict
promotion.

## Probe Method

The default renderer route is unchanged. A test-only system property,
`kanvas.webgpu.for248.finalCropCompositeProbe`, enables two 1x1 WebGPU `kSrc`
diagnostic copies only when all selected-case guards match:

- `origin=(340,120)`
- `layerExtent=(80,40)`
- `offset=(40,0)`
- `crop=(40,0,80,40)`
- `tileMode=kDecal`

The diagnostic copies run after child materialization and before the normal
final layer composite:

| Output sentinel | Observed operation | Observed coordinate |
|---|---|---:|
| `(2,0)` | direct scratch copy | `scratchPixel(45,5)` |
| `(3,0)` | final Crop payload remap | `layerPixel(45,5)` -> `scratchPixel(45,5)` |

The normal WebGPU readback also samples the GM output fragment `(385,125)`.
No CPU fallback, readback fallback, graph compiler, Ganesh/Graphite path, or
SkSL compiler is added.

## Evidence

Stable dump:

```text
reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/final-crop-composite-probe-for248.json
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

No bounded final-composite correction is applied. The probe proves that the
diagnostic Crop composite can read `scratchPixel(45,5)` as
`rgba(202,59,19,102)`, so this ticket narrows the residual without changing
scene thresholds or promoting broader image-filter support.

The scene stays below strict promotion and remains a fidelity-gap diagnostic.

## Validation

Passed:

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
rtk python3 scripts/validate_for248_final_crop_composite_probe.py
rtk python3 scripts/validate_for247_crop_offset_scratch_probe.py
rtk python3 scripts/validate_for246_webgpu_crop_offset_materialization.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
