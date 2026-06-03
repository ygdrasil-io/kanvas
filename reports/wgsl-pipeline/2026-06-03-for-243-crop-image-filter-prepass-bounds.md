# FOR-243 Crop Image-Filter Pre-Pass Bounds

Linear: FOR-243

## Decision

KEEP_DIAGNOSTIC.

FOR-243 makes the selected bounds contract more explicit, but the
`crop-image-filter-nonnull-prepass` row does not move toward strict Skia
fidelity after regeneration:

| Signal | Value |
|---|---:|
| CPU/reference similarity | 84.88% |
| GPU/reference similarity | 98.44% |
| Strict promotion target | 99.95% |
| Pixels | 128000 |
| GPU matching pixels | 126000 |
| CPU matching pixels | 108644 |

The route remains a bounded WebGPU `pass` with fallback `none`, but it stays
classified as `risk.fidelity-gap`. No threshold is lowered and no strict
support claim is made.

## Bounds Work

The implementation now keeps the source and filtered output bounds distinct
for implicit `drawRect` image-filter layers:

- `SkCanvas.drawRect` opens the implicit layer on the union of the unfiltered
  source bounds and `paint.computeFastBounds(rect)`.
- `SkCropImageFilter.computeFastBounds` reports the crop rectangle as the
  filter output bounds.
- The selected WebGPU crop pre-pass remains on the existing bounded route; the
  regenerated evidence shows that source/output layer-bounds cleanup alone does
  not move the SimpleOffset score.

This remains scoped to the selected `Crop(kDecal, input=Offset(input=null))`
shape. It does not add arbitrary image-filter DAG support, PictureImageFilter
pre-pass support, CPU readback fallback, Ganesh, Graphite, SkSL compiler, SkSL
IR, or SkSL VM work.

## Residual Cells

Exact byte diffs after FOR-243 remain concentrated in the same SimpleOffset
cells. The high-delta cells are:

| Cell | Local diff bbox | Max RGBA delta | Interpretation |
|---|---|---:|---|
| row 1 `clip == dst` | `(40,40)-(79,79)` | `[38,107,110,0]` | output region is present but blended/source color differs from Skia reference |
| row 2 `crop == clip == dst` | `(40,0)-(79,39)` | `[34,102,110,0]` | output region is present but blended/source color differs from Skia reference |

Lower-delta cells are mostly one-byte or near-one-byte premul/rounding
residue. The remaining issue is therefore no longer a defensible
source-output extent promotion for this ticket; it needs a separate
color/source semantics slice if we want to chase strict fidelity.

## Preserved Boundaries

- `image-filter.crop-input-nonnull-prepass-required` remains the stable refusal
  for out-of-scope non-null crop graphs.
- The selected WebGPU route remains fallback-free.
- The row stays below strict fidelity and keeps `risk.fidelity-gap`.
- No broader image-filter graph compiler is claimed.

## Validation

Passed:

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
rtk ./gradlew --no-daemon --rerun-tasks :kanvas-skia:test --tests 'org.skia.foundation.SkImageFilterComputeFastBoundsTest'
rtk python3 scripts/validate_for243_crop_prepass_bounds.py
rtk python3 scripts/validate_for242_crop_prepass_fidelity.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
rtk git diff --check
```

The GPU test command was rerun in isolation after a concurrent Gradle
compilation produced transient unresolved dependency references. The isolated
rerun completed successfully and all selected tests passed.
