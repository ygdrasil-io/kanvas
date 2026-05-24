# Implementation shortlist

Snapshot date: 2026-05-24.

Source: `reports/upstream-rebaseline/2026-05-24.tsv`, filtered to
`bucket=implementation`.

Purpose: pick implementation work that is useful while fonts, codecs, and
WGSL/parser delivery are still pending.

## Summary

The rebaseline currently classifies 11 upstream `.cpp` rows as
`implementation`. These are not blocked by the font delivery, codec
delivery, or the WGSL/runtime-effect parser track.

The best immediate work is small API surface with clear tests and low
cross-cutting risk. Avoid broad rendering rewrites until the dependency
deliveries land.

Completed since this snapshot:

- `STUB.SURFACE_SNAPSHOT_SUBSET`: implemented in `SkSurface.makeImageSnapshot(SkIRect)`
  and `SurfaceUnderdrawTest` is enabled.
- `STUB.IMAGE_MAKE_SCALED`: implemented in `SkImage.makeScaled`
  and `Crbug404394639Test` is enabled.
- `STUB.SRC_RECT_CONSTRAINT`: implemented for raster `drawImageRect`
  and `SrcRectConstraintTest` is enabled.
- `STUB.MAKE_WITH_COLOR_FILTER`: implemented in shader/color-filter composition
  and `ColorFilterShaderGM` is enabled.
- `STUB.COLOR4F_BLEND_CF`: implemented for `SkColor4f` color-filter blending
  and `Color4blendcfGM` is enabled.
- `STUB.PERSPECTIVE_ADDPATH`: implemented in `SkPathBuilder.addPath`
  and `PathAppendExtendGM` is enabled.
- `STUB.GAUSSIAN_COLOR_FILTER`: implemented for private gaussian color filters
  and `ShadowUtilsGaussianColorFilterGM` is enabled.
- `STUB.PATH_EFFECT_CTM`: implemented in `CTMPathEffectGM` with CTM-aware
  line inflation and ratchet coverage.
- `STUB.POLY_TO_POLY`: `ClipShaderPerspGM` is ported and enabled; the
  matrix factory was already present, and the remaining visual delta is
  tracked by the ratchet.
- `STUB.IFX.MULTIPLE_FILTERS_SPAN`: raster `saveLayerWithMultipleFilters`
  is implemented for `MultipleFiltersGM` and the test is enabled.
- `STUB.DRAW_VERTICES`: `VerticesBatchingGM` is ported through the existing
  `drawVertices` raster path and the test is enabled. The broader
  `VerticesGM` remains a separate follow-up.
- `STUB.EDGE_AA_IMAGE_SET`: raster fallback implemented in
  `SkCanvas.experimental_DrawEdgeAAImageSet`; `DrawImageSetGM`,
  `DrawImageSetRectToRectGM`, `DrawImageSetAlphaOnlyGM`, and `Skbug14554GM`
  are enabled with ratchet coverage.
- `STUB.COMPOSE_SHADER`: `ComposeShaderAlphaGM` is ported and enabled;
  the compose-shader family is now covered by ratcheted tests.
- `STUB.ALPHA8_IMAGE_AS_MASK`: `ImageMaskSubsetGM` is enabled against the
  existing alpha-mask tint path in `SkBitmapDevice.drawImageRect`.
- `STUB.EDGE_AA_QUAD`: `DrawQuadSetGM` is enabled against the existing
  `experimental_DrawEdgeAAQuad` raster fallback; the GPU-gradient residual is
  covered by the similarity floor.
- `STUB.STROKEDLINE_CAPS`: `StrokedLineCapsGM` is enabled with ratchet
  coverage; `StrokedLinesGM` was already enabled.
- `fiddle`: upstream's intentionally empty GM is already ported and covered
  by `FiddleTest`; the implementation bucket entry was stale.
- `STUB.SURFACE_PROPS`: `SurfacePropsGM` is ported for the raster path via
  `SkSurface.MakeRaster(..., SkSurfaceProps)` and enabled with ratchet
  coverage. The WebGPU and crossbackend wrappers are also enabled with
  95% floors.
- `RRectBlurGM`: `SkCanvas.readPixels` / `writePixels` raster overloads are
  implemented, the diff GM is ported, and `RRectBlurTest` is enabled.
- `STUB.BACKDROP_FILTER`: `SaveLayerWithBackdropGM` is ported and enabled on
  raster, WebGPU, and crossbackend wrappers with ratchet coverage; the
  remaining `imagefilters` bucket tag was stale tracking.
- `STUB.COLOR_FILTER_PRIV`: `SkColorFilterPriv.withWorkingFormat` delegates to
  the existing working-colour-space wrapper; `AlternateLumaGM` is enabled with a
  low ratchet because the mandrill source asset is still synthetic.
- Tracker hygiene: `gm-status.sh` now ignores upstream `#if 0` GM
  registrations, uses a per-run Kotlin index to avoid parallel collisions, and
  treats documented intentionally-empty ports such as `FiddleGM` as ported. The
  rebaseline bucket logic now lets already-`PORTED` rows override stale
  historical `STUB.*` tags.
- `STUB.BLURRECT_GALLERY` and `STUB.BLUR_RECTS_FULL`: `SkBlurMask.BlurRect`
  now delegates to the existing separable blur mask filter, and
  `BlurRectGalleryGM` / `BlurRectGM` are enabled with raster, WebGPU, and
  crossbackend ratchets. The remaining `blurrect` implementation item is the
  analytic-vs-brute-force `BlurRectCompareGM` harness.

## Recommended order

| Priority | Track | Impact | Effort | Why now |
|---:|---|---:|---|---|
| 1 | `blurrect_compare` | 1 cpp | M | Remaining blurrect work is the analytic-vs-brute-force comparison harness. |
| 2 | `gradients` | 1 cpp | M/L | Isolated interpolation variants; useful after the tracker noise reduction. |
| 3 | `savelayer` | 1 cpp | M/L | Raster-facing, but touches F16/save-behind semantics. |
| 4 | `STUB.RSXBLOB` / `STUB.DF_TEXT_RASTER` | 2 cpps (`drawatlas`, `dftext_blob_persp`) | L/XL | Text/glyph transform work; defer if font delivery may change internals. |

## Implementation bucket rows

| Upstream cpp | Tags | Local GM files |
|---|---|---|
| `addarc` | `STUB.MISSING_API` | `AddArcGM.kt`, `AddArcMeasGM.kt`, `FillCircleGM.kt`, `ManyArcsGM.kt`, `StrokeCircleGM.kt`, `TinyAngleArcsGM.kt` |
| `blurrect` | `STUB.BLUR_RECT_COMPARE` | `BlurMatrixRectGM.kt`, `BlurRectCompareGM.kt`, `BlurRectGM.kt`, `BlurRectGalleryGM.kt` |
| `dftext_blob_persp` | `STUB.DF_TEXT_RASTER` | `DFTextBlobPerspGM.kt` |
| `drawatlas` | `STUB.RSXBLOB` | `BlobRSXformDistortableGM.kt`, `BlobRSXformGM.kt`, `CompareAtlasVerticesGM.kt`, `DrawAtlasGM.kt`, `DrawTextRSXformGM.kt` |
| `gradients` | `STUB.GRADIENT_INTERPOLATION` | gradient interpolation variants |
| `imagefiltersbase` | `STUB.TEXT_IMAGE_FILTER` | `ImageFiltersBaseGM.kt`, `ImageFiltersTextBaseGM.kt` |
| `mesh` | `STUB.MESH` | `MeshGMs.kt` |
| `recordopts` | `STUB.RECORDOPTS.SAVELAYER_COLOR_FILTER_FOLD`, `STUB.XYZ` | `RecordOptsGM.kt` |
| `savelayer` | `STUB.SAVE_BEHIND`; `SaveLayerF16GM` and `Skbug14554GM` ported | `SaveBehindGM.kt`, `SaveLayerF16GM.kt`, `SaveLayerGM.kt`, `Skbug14554GM.kt` |
| `vertices` | partial: `VerticesBatchingGM` ported; `VerticesGM` still disabled | `Skbug13047GM.kt`, `VerticesBatchingGM.kt`, `VerticesCollapsedGM.kt`, `VerticesGM.kt`, `VerticesPerspectiveGM.kt` |

## Notes

- `STUB.MISSING_API` is too vague. The affected rows (`aaclip`, `addarc`)
  need tag cleanup before implementation selection.
- `dashcubics` is now ported: `SkTrimPathEffect` is implemented and
  `TrimGM` is reactivated with a 90% ratchet (`92.29%` in validation).
- Text-related rows should be revisited after the font delivery if their
  implementation would touch glyph storage or shaping assumptions.
