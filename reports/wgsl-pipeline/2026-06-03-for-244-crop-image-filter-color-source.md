# FOR-244 Crop Image-Filter Color/Source Semantics

Linear: FOR-244

## Decision

KEEP_DIAGNOSTIC.

FOR-244 isolates the post-FOR-243 residual for
`crop-image-filter-nonnull-prepass`. The remaining high-delta pixels do not
look like a plain channel, premul-alpha, scratch-clear, or final `Crop(kDecal)`
remap issue. The evidence points instead to source pixels that Skia later
offsets into the output clip but that current Kanvas CPU/WebGPU paths do not
materialise when the source rect starts outside the active clip.

The selected route therefore stays below strict promotion:

| Signal | Value |
|---|---:|
| CPU/reference similarity | 84.88% |
| GPU/reference similarity | 98.44% |
| Strict promotion target | 99.95% |
| Pixels | 128000 |
| GPU matching pixels | 126000 |
| CPU matching pixels | 108644 |

No threshold is lowered, no support promotion is claimed, and the row remains
classified as `risk.fidelity-gap`.

## Residual Pixels

Using exact image comparisons with tolerance greater than one byte, the
remaining visible WebGPU/reference residual is concentrated in two blocks:

| Cell | Global bbox | Local bbox | Pixels | Skia dominant color | GPU dominant color | Interpretation |
|---|---|---|---:|---|---|---|
| row 1 `clip == dst` | `(580,80)-(599,99)` | `(40,40)-(59,59)` | 400 | `(221,153,145,255)` | `(255,255,255,255)` | Skia shows the red offset source blended over the existing scene; GPU/CPU leave the clipped output area mostly white. |
| row 2 `crop == clip == dst` | `(380,120)-(419,159)` | `(40,0)-(79,39)` | 1600 | `(221,153,145,255)` | `(255,255,255,255)` | Skia keeps the source rect available for the offset+crop result even though the output clip is the destination rect; GPU/CPU do not materialise that source for the final crop. |

The lower-delta cells are mostly one-byte premul/rounding residue and are not
the promotion blocker.

## Hypothesis Check

| Hypothesis | Result |
|---|---|
| Source color | Primary signal: Skia has red-over-existing-scene pixels while GPU/CPU are white or diagnostic-stroke-only. The source is missing, not merely recolored. |
| Composition order | Likely root: the active clip is constraining capture/materialisation of the filter input before the `Offset`/`Crop` result is produced. Skia semantics constrain final output by the clip while still allowing the filter source outside that output clip. |
| Premul/blend alpha | Not primary: alpha is fully opaque in the residual and the large deltas are white-vs-red, not small alpha rounding. |
| Scratch clear | Not primary: the scratch/white symptom is localized to cases where source is outside output clip; broader scratch-clear corruption is not visible. |
| Final `Crop(kDecal)` mapping | Not primary for this slice: FOR-242 removed the double `originX/originY` compensation and the selected route now places the crop window correctly enough to reach 98.44%. The remaining blocks need source availability before final crop/composite. |

## Scoped Correction Attempt

A bounded experiment separated the implicit `drawRect(imageFilter)` layer's
source capture clip from its output clip in `SkCanvas`. The patch compiled and
the targeted SimpleOffset tests still passed, but the regenerated artifacts did
not move any score or residual pixel:

```text
CPU/reference: 84.88%
GPU/reference: 98.44%
GPU matching pixels: 126000/128000
```

Because that change touched every `drawRect` with an `imageFilter` and had no
measured benefit, it was not retained. A defensible fix now needs a narrower
backend/source-coordinate contract for image-filter materialisation, not a
generic color or threshold adjustment.

## Preserved Boundaries

- `image-filter.crop-input-nonnull-prepass-required` remains the stable refusal
  for out-of-scope non-null crop graphs.
- The selected WebGPU route remains fallback-free for the bounded scene.
- No arbitrary image-filter DAG compiler is added.
- No `PictureImageFilter` pre-pass, CPU/readback fallback, Ganesh, Graphite,
  SkSL compiler, SkSL IR, or SkSL VM work is added.
- No strict promotion is claimed without CPU/GPU/reference evidence.

## Validation

Passed:

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
rtk python3 scripts/validate_for244_crop_prepass_color_source.py
rtk python3 scripts/validate_for243_crop_prepass_bounds.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
