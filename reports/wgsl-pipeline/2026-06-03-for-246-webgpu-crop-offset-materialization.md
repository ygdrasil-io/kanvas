# FOR-246 WebGPU Crop(input = Offset) Materialization Diagnostic

Linear: FOR-246

## Decision

KEEP_DIAGNOSTIC.

FOR-246 traces the WebGPU materialization path behind the
`crop-image-filter-nonnull-prepass` scene without changing thresholds or
promoting support. The selected route remains:

```text
webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite
```

The current implementation has the expected two-stage shape for the bounded
`Crop(kDecal, input = Offset(input = null))` route:

1. `resolveCropNonNullOffsetPrePassPlan` accepts only
   `Crop(kDecal, input = Offset(input = null))`, rejects other non-null child
   shapes by falling back to the stable refusal path, and records integer
   `offsetDx/offsetDy`.
2. The child pre-pass creates
   `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch` with the same extent
   as the implicit image-filter layer.
3. The materialize draw samples the layer source with
   `dstOriginX = offsetDx` / `dstOriginY = offsetDy`, so shader-local
   `scratchPixel(p)` reads `childSource(p - offset)`.
4. The final composite samples that scratch with `dstOrigin = layer origin`
   and applies the existing `Crop(kDecal)` UV remap in
   `layer_composite.wgsl`.

This is the intended bounded route, but the generated scene still leaves the
two FOR-244 residual cells below the strict target.

| Signal | Before FOR-246 | After FOR-246 |
|---|---:|---:|
| CPU/reference similarity | 84.88% | 84.88% |
| GPU/reference similarity | 98.44% | 98.44% |
| Strict promotion target | 99.95% | 99.95% |
| GPU matching pixels | 126000/128000 | 126000/128000 |
| CPU matching pixels | 108644/128000 | 108644/128000 |

## Targeted Cells

The two blocking cells from FOR-244 are now tied to concrete GM coordinates
and WebGPU coordinate formulas:

| Cell | GM origin | Local residual bbox | Filter shape | WebGPU mapping | Expected reference | GPU/CPU result |
|---|---:|---:|---|---|---|---|
| row 1 `clip == dst` | `(540,40)` | `(40,40)-(59,59)` | `Offset(20,20,input=null)` | top-level offset branch shifts `dstOrigin` to `(origin + offset)`; representative output `(45,45)` samples source-local `(25,25)` | red over existing scene `(221,153,145,255)` | mostly white `(255,255,255,255)` |
| row 2 `crop == clip == dst` | `(340,120)` | `(40,0)-(79,39)` | `Crop(kDecal, rect=(40,0,80,40), input=Offset(40,0,input=null))` | pre-pass scratch pixel `(40..79,0..39)` samples source-local `(0..39,0..39)`, then final `Crop` samples the same scratch window | red over existing scene `(221,153,145,255)` | mostly white `(255,255,255,255)` |

The second row is the direct FOR-246 `Crop(input = Offset)` case. The first row
is a related pure `Offset(input = null)` cell in the same GM and explains why
FOR-245's generic source-capture fix did not move the scene by itself.

## Boundary

No bounded sampling/origin correction is applied in this slice. The code path
already routes the selected shape through the intended two-pass materialization,
and the remaining error is not safely attributable to a single `originX`,
`originY`, or crop-payload subtraction without exposing the scratch content or
splitting the GM into smaller backend diagnostics.

The next exact frontier is a scratch-content probe for the selected
`Crop(kDecal, input = Offset(input = null))` cell:

```text
origin=(340,120), layerExtent=(80,40), offset=(40,0), crop=(40,0,80,40)
scratchPixel(45,5) should contain sourceLocal(5,5) before final Crop.
```

That probe should prove whether the failure is in source capture into the
implicit layer, the child materialize pass, or the final scratch sampling pass.

## Preserved Boundaries

- `image-filter.crop-input-nonnull-prepass-required` remains the stable refusal
  for out-of-scope non-null crop graphs.
- No arbitrary image-filter DAG compiler is added.
- No `PictureImageFilter` or general pre-pass is added.
- No CPU/readback fallback is introduced.
- No Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM work is added.
- The strict target remains `99.95%`.

## Validation

Passed:

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk python3 scripts/validate_for246_webgpu_crop_offset_materialization.py
rtk python3 scripts/validate_for245_image_filter_source_capture_output_clip.py
rtk python3 scripts/validate_for244_crop_prepass_color_source.py
rtk git diff --check
```
