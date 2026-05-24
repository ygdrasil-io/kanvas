# Implementation shortlist

Snapshot date: 2026-05-24.

Source: `reports/upstream-rebaseline/2026-05-24.tsv`, filtered to
`bucket=implementation`.

Purpose: pick implementation work that is useful while fonts, codecs, and
WGSL/parser delivery are still pending.

## Summary

The rebaseline currently classifies 25 upstream `.cpp` rows as
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

## Recommended order

| Priority | Track | Impact | Effort | Why now |
|---:|---|---:|---|---|
| 1 | `STUB.RSXBLOB` / `STUB.DF_TEXT_RASTER` | 3 cpps (`drawatlas`, `dftext_blob_persp`, `textblobmixedsizes`) | L/XL | Text/glyph transform work; defer if font delivery may change internals. |

## Implementation bucket rows

| Upstream cpp | Tags | Local GM files |
|---|---|---|
| `aaclip` | `STUB.MISSING_API` | `AaclipGM.kt`, `CgimageGM.kt`, `ClipCubicGM.kt` |
| `addarc` | `STUB.MISSING_API` | `AddArcGM.kt`, `AddArcMeasGM.kt`, `FillCircleGM.kt`, `ManyArcsGM.kt`, `StrokeCircleGM.kt`, `TinyAngleArcsGM.kt` |
| `blurrect` | `STUB.BLURRECT_GALLERY`, `STUB.BLUR_RECTS_FULL`, `STUB.BLUR_RECT_COMPARE` | `BlurMatrixRectGM.kt`, `BlurRectCompareGM.kt`, `BlurRectGM.kt`, `BlurRectGalleryGM.kt` |
| `color4f` | ported | `Color4blendcfGM.kt`, `Color4fGM.kt`, `Color4shaderGM.kt` |
| `colorfilterimagefilter` | ported | `ColorFilterImageFilterGM.kt`, `ColorFilterImageFilterLayerGM.kt`, `ColorFilterShaderGM.kt` |
| `complexclip` | ported | `ClipShaderComplexClipGM.kt`, `ClipShaderPerspGM.kt`, `ComplexClipGM.kt` |
| `composeshader` | ported | `ComposeShaderAlphaGM.kt`, `ComposeShaderBitmap2GM.kt`, `ComposeShaderBitmapGM.kt`, `ComposeShaderGM.kt`, `ComposeShaderGridGM.kt` |
| `dashcubics` | disabled `TrimGM` without `STUB.*` tag | `DashCubicsGM.kt`, `TrimGM.kt` |
| `dftext_blob_persp` | `STUB.DF_TEXT_RASTER` | `DFTextBlobPerspGM.kt` |
| `drawatlas` | `STUB.RSXBLOB` | `BlobRSXformDistortableGM.kt`, `BlobRSXformGM.kt`, `CompareAtlasVerticesGM.kt`, `DrawAtlasGM.kt`, `DrawTextRSXformGM.kt` |
| `drawimageset` | ported | `DrawImageSetAlphaOnlyGM.kt`, `DrawImageSetGM.kt`, `DrawImageSetRectToRectGM.kt` |
| `drawquadset` | ported | `DrawQuadSetGM.kt` |
| `fiddle` | ported | `FiddleGM.kt` |
| `gradients` | `STUB.GRADIENT_INTERPOLATION` | gradient interpolation variants |
| `imagefilters` | `STUB.BACKDROP_FILTER` | `MultipleFiltersGM.kt`, `SaveLayerWithBackdropGM.kt`, others |
| `imagefiltersbase` | `STUB.TEXT_IMAGE_FILTER` | `ImageFiltersBaseGM.kt`, `ImageFiltersTextBaseGM.kt` |
| `imagemasksubset` | ported | `ImageMaskSubsetGM.kt` |
| `lumafilter` | `STUB.COLOR_FILTER_PRIV` | `AlternateLumaGM.kt`, `LumaFilterGM.kt` |
| `mesh` | `STUB.MESH` | `MeshGMs.kt` |
| `patharcto` | ported | `ArctoSkbug9272GM.kt`, `PathAppendExtendGM.kt`, `ShallowAnglePathArcToGM.kt` |
| `patheffects` | ported | `CTMPathEffectGM.kt`, `PathEffectGM.kt` |
| `pathmeasure` | `STUB.PATH_MEASURE_EXPLOSION` | `PathMeasureExplosionGM.kt` |
| `recordopts` | `STUB.RECORDOPTS.SAVELAYER_COLOR_FILTER_FOLD`, `STUB.XYZ` | `RecordOptsGM.kt` |
| `rrect` | disabled `RRectBlurGM` without `STUB.*` tag | `RRectBlurGM.kt`, `RRectGM.kt` |
| `savelayer` | `STUB.F16_COLOR_TYPE`, `STUB.SAVE_BEHIND`; `Skbug14554GM` ported | `SaveBehindGM.kt`, `SaveLayerF16GM.kt`, `SaveLayerGM.kt`, `Skbug14554GM.kt` |
| `shadowutils` | ported | `ShadowUtilsDirectionalGM.kt`, `ShadowUtilsGaussianColorFilterGM.kt` |
| `strokedlines` | ported | `StrokedLineCapsGM.kt`, `StrokedLinesGM.kt` |
| `surface` | `STUB.SURFACE_PROPS` | `NewSurfaceGM.kt`, `SnapWithMipsGM.kt`, `SurfacePropsGM.kt` |
| `textblobmixedsizes` | `STUB.DF_TEXT_RASTER` | `TextBlobMixedSizesGM.kt` |
| `vertices` | partial: `VerticesBatchingGM` ported; `VerticesGM` still disabled | `Skbug13047GM.kt`, `VerticesBatchingGM.kt`, `VerticesCollapsedGM.kt`, `VerticesGM.kt`, `VerticesPerspectiveGM.kt` |

## Notes

- `STUB.MISSING_API` is too vague. The affected rows (`aaclip`, `addarc`)
  need tag cleanup before implementation selection.
- `dashcubics` and `rrect` are classified as implementation
  because their disabled/stub reason is actionable but not normalized as
  a `STUB.*` tag.
- Text-related rows should be revisited after the font delivery if their
  implementation would touch glyph storage or shaping assumptions.
