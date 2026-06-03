# FOR-245 Image-Filter Source Capture / Output Clip Contract

Linear: FOR-245

## Decision

KEEP_DIAGNOSTIC.

FOR-245 adds a bounded `SkCanvas.drawRect(imageFilter)` contract fix: the
implicit image-filter layer can now capture source pixels from the owning
device bounds while the final restore/composite still uses the active output
clip. Public `saveLayer(bounds, paint)` behavior remains clipped to the current
clip as before.

The fix is necessary for simple source-capture semantics and is covered by
unit tests, but the selected `crop-image-filter-nonnull-prepass` scene does not
move. The remaining SimpleOffset residual therefore stays in the WebGPU
Crop/Offset materialisation path or in a narrower GM-specific coordinate
contract, not in the generic canvas layer-opening contract alone.

| Signal | Before FOR-245 | After FOR-245 |
|---|---:|---:|
| CPU/reference similarity | 84.88% | 84.88% |
| GPU/reference similarity | 98.44% | 98.44% |
| Strict promotion target | 99.95% | 99.95% |
| GPU matching pixels | 126000/128000 | 126000/128000 |
| CPU matching pixels | 108644/128000 | 108644/128000 |

No threshold is lowered, no strict support promotion is claimed, and the scene
remains classified as `risk.fidelity-gap`.

## Scoped Change

`SkCanvas.drawRect` still routes image-filtered draws through an implicit
offscreen layer, but the internal layer-opening path now differs from public
`saveLayer`:

- public `saveLayer(bounds, paint)` intersects layer allocation and child clip
  with the current clip;
- internal `drawRect(imageFilter)` source capture clamps to the device bounds
  instead of the current output clip;
- `restore()` still composites through the parent state's `top.clip`, so output
  clipping is preserved.

This avoids exposing a new public API and keeps the change limited to the
implicit direct-draw image-filter path.

## Unit Evidence

Two `kanvas-skia` tests cover the contract directly:

- `drawRect imageFilter captures source outside output clip`
- `drawRect crop offset imageFilter captures source before output clip`

The first covers a plain `Offset(input = null)` filter. The second covers the
FOR-245-relevant `Crop(kDecal, input = Offset(input = null))` shape with crop
equal to the output clip.

## Scene Evidence

The targeted SimpleOffset scene was regenerated after the patch:

```text
CPU/reference: 84.88%
GPU/reference: 98.44%
GPU matching pixels: 126000/128000
CPU matching pixels: 108644/128000
```

Route diagnostics stay fallback-free for the selected WebGPU scene:

```text
coverageStrategy: webgpu.image-filter.crop-nonnull-offset-prepass
selectedRoute: webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite
fallbackReason: none
```

The out-of-scope refusal remains stable:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Preserved Boundaries

- No arbitrary image-filter DAG compiler is added.
- No `PictureImageFilter` pre-pass or general image-filter pre-pass is added.
- No CPU/readback fallback is introduced.
- No Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM work is added.
- The strict target remains `99.95%`.

## Residual Risk

The `SkCanvas` contract is now better specified and tested, but the GM score
proves it is not sufficient for SimpleOffset promotion. The next implementation
slice should inspect the WebGPU `Crop(input = Offset)` materialisation payload
and shader-side sample coordinates with the expanded layer origin, especially
the two cells already isolated by FOR-244:

- row 1 `clip == dst`;
- row 2 `crop == clip == dst`.

## Validation

Passed:

```text
rtk ./gradlew --no-daemon --rerun-tasks :kanvas-skia:test --tests 'org.skia.core.SkCanvasInternalsTest.drawRect imageFilter captures source outside output clip'
rtk ./gradlew --no-daemon --rerun-tasks :kanvas-skia:test --tests 'org.skia.core.SkCanvasInternalsTest.drawRect crop offset imageFilter captures source before output clip'
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest' --tests '*SimpleOffsetImageFilterCrossBackendTest'
rtk python3 scripts/validate_for245_image_filter_source_capture_output_clip.py
rtk python3 scripts/validate_for244_crop_prepass_color_source.py
rtk git diff --check
```
