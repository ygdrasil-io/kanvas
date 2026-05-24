# Implementation shortlist

Snapshot date: 2026-05-24.

Source: `reports/upstream-rebaseline/2026-05-24.tsv`, filtered to
`bucket=implementation`.

Purpose: pick implementation work that is useful while fonts, codecs, and
WGSL/parser delivery are still pending.

## Summary

The rebaseline currently classifies 31 upstream `.cpp` rows as
`implementation`. These are not blocked by the font delivery, codec
delivery, or the WGSL/runtime-effect parser track.

The best immediate work is small API surface with clear tests and low
cross-cutting risk. Avoid broad rendering rewrites until the dependency
deliveries land.

Completed since this snapshot:

- `STUB.SURFACE_SNAPSHOT_SUBSET`: implemented in `SkSurface.makeImageSnapshot(SkIRect)`
  and `SurfaceUnderdrawTest` is enabled.

## Recommended order

| Priority | Track | Impact | Effort | Why now |
|---:|---|---:|---|---|
| 1 | `STUB.IMAGE_MAKE_SCALED` | 1 cpp (`crbug_404394639`, currently `missing-mapping`) | S/M | Isolated image helper; can reuse existing sampling paths. |
| 2 | `STUB.SRC_RECT_CONSTRAINT` | 1 cpp (`bleed`) | S/M | Adds an overload/constraint path around existing `drawImageRect`; bounded blast radius. |
| 3 | `STUB.MAKE_WITH_COLOR_FILTER` | 1 cpp (`colorfilterimagefilter`) | M | Useful shader composition primitive; test surface is small. |
| 4 | `STUB.COLOR4F_BLEND_CF` | 1 cpp (`color4f`) | M | Pure color-filter behavior; likely contained in foundation/effects. |
| 5 | `STUB.POLY_TO_POLY` / `STUB.PERSPECTIVE_ADDPATH` | 2 cpps (`complexclip`, `patharcto`) | M/L | Core matrix/path correctness; useful but more geometry risk. |
| 6 | `STUB.PATH_EFFECT_CTM` | 1 cpp (`patheffects`) | M/L | Needs CTM-aware path-effect behavior; moderate correctness risk. |
| 7 | `STUB.GAUSSIAN_COLOR_FILTER` | 1 cpp (`shadowutils`) | M/L | Private color-filter behavior; useful but less central. |
| 8 | `STUB.IFX.MULTIPLE_FILTERS_SPAN` | 1 cpp (`imagefilters`) | L | Image-filter/layer integration can touch saveLayer behavior. |
| 9 | `STUB.EDGE_AA_IMAGE_SET` / `STUB.EDGE_AA_QUAD` | 3+ cpps (`drawimageset`, `savelayer`, `drawquadset`) | L | Higher impact, but batched image draw semantics are broad. |
| 10 | `STUB.DRAW_VERTICES` | 1 cpp (`vertices`) | L | Shared drawing primitive; likely useful, but broad rendering surface. |
| 11 | `STUB.RSXBLOB` / `STUB.DF_TEXT_RASTER` | 3 cpps (`drawatlas`, `dftext_blob_persp`, `textblobmixedsizes`) | L/XL | Text/glyph transform work; defer if font delivery may change internals. |

## Implementation bucket rows

| Upstream cpp | Tags | Local GM files |
|---|---|---|
| `aaclip` | `STUB.MISSING_API` | `AaclipGM.kt`, `CgimageGM.kt`, `ClipCubicGM.kt` |
| `addarc` | `STUB.MISSING_API` | `AddArcGM.kt`, `AddArcMeasGM.kt`, `FillCircleGM.kt`, `ManyArcsGM.kt`, `StrokeCircleGM.kt`, `TinyAngleArcsGM.kt` |
| `bleed` | `STUB.SRC_RECT_CONSTRAINT` | `BleedDownscaleGM.kt`, `SrcRectConstraintGM.kt` |
| `blurrect` | `STUB.BLURRECT_GALLERY`, `STUB.BLUR_RECTS_FULL`, `STUB.BLUR_RECT_COMPARE` | `BlurMatrixRectGM.kt`, `BlurRectCompareGM.kt`, `BlurRectGM.kt`, `BlurRectGalleryGM.kt` |
| `color4f` | `STUB.COLOR4F_BLEND_CF` | `Color4blendcfGM.kt`, `Color4fGM.kt`, `Color4shaderGM.kt` |
| `colorfilterimagefilter` | `STUB.MAKE_WITH_COLOR_FILTER` | `ColorFilterImageFilterGM.kt`, `ColorFilterImageFilterLayerGM.kt`, `ColorFilterShaderGM.kt` |
| `complexclip` | `STUB.POLY_TO_POLY` | `ClipShaderComplexClipGM.kt`, `ClipShaderPerspGM.kt`, `ComplexClipGM.kt` |
| `composeshader` | `STUB.COMPOSE_SHADER` | `ComposeShaderAlphaGM.kt`, `ComposeShaderBitmap2GM.kt`, `ComposeShaderBitmapGM.kt`, `ComposeShaderGM.kt`, `ComposeShaderGridGM.kt` |
| `dashcubics` | disabled `TrimGM` without `STUB.*` tag | `DashCubicsGM.kt`, `TrimGM.kt` |
| `dftext_blob_persp` | `STUB.DF_TEXT_RASTER` | `DFTextBlobPerspGM.kt` |
| `drawatlas` | `STUB.RSXBLOB` | `BlobRSXformDistortableGM.kt`, `BlobRSXformGM.kt`, `CompareAtlasVerticesGM.kt`, `DrawAtlasGM.kt`, `DrawTextRSXformGM.kt` |
| `drawimageset` | `STUB.EDGE_AA_IMAGE_SET` | `DrawImageSetAlphaOnlyGM.kt`, `DrawImageSetGM.kt`, `DrawImageSetRectToRectGM.kt` |
| `drawquadset` | `STUB.EDGE_AA_QUAD` | `DrawQuadSetGM.kt` |
| `fiddle` | stub file without `STUB.*` tag | `FiddleGM.kt` |
| `gradients` | `STUB.GRADIENT_INTERPOLATION` | gradient interpolation variants |
| `imagefilters` | `STUB.IFX.MULTIPLE_FILTERS_SPAN`, `STUB.BACKDROP_FILTER` | `MultipleFiltersGM.kt`, `SaveLayerWithBackdropGM.kt`, others |
| `imagefiltersbase` | `STUB.TEXT_IMAGE_FILTER` | `ImageFiltersBaseGM.kt`, `ImageFiltersTextBaseGM.kt` |
| `imagemasksubset` | `STUB.ALPHA8_IMAGE_AS_MASK` | `ImageMaskSubsetGM.kt` |
| `lumafilter` | `STUB.COLOR_FILTER_PRIV` | `AlternateLumaGM.kt`, `LumaFilterGM.kt` |
| `mesh` | `STUB.MESH` | `MeshGMs.kt` |
| `patharcto` | `STUB.PERSPECTIVE_ADDPATH` | `ArctoSkbug9272GM.kt`, `PathAppendExtendGM.kt`, `ShallowAnglePathArcToGM.kt` |
| `patheffects` | `STUB.PATH_EFFECT_CTM` | `CTMPathEffectGM.kt`, `PathEffectGM.kt` |
| `pathmeasure` | `STUB.PATH_MEASURE_EXPLOSION` | `PathMeasureExplosionGM.kt` |
| `recordopts` | `STUB.RECORDOPTS.SAVELAYER_COLOR_FILTER_FOLD`, `STUB.XYZ` | `RecordOptsGM.kt` |
| `rrect` | disabled `RRectBlurGM` without `STUB.*` tag | `RRectBlurGM.kt`, `RRectGM.kt` |
| `savelayer` | `STUB.EDGE_AA_IMAGE_SET`, `STUB.F16_COLOR_TYPE`, `STUB.SAVE_BEHIND` | `SaveBehindGM.kt`, `SaveLayerF16GM.kt`, `SaveLayerGM.kt`, `Skbug14554GM.kt` |
| `shadowutils` | `STUB.GAUSSIAN_COLOR_FILTER` | `ShadowUtilsDirectionalGM.kt`, `ShadowUtilsGaussianColorFilterGM.kt` |
| `strokedlines` | `STUB.STROKEDLINE_CAPS` | `StrokedLineCapsGM.kt`, `StrokedLinesGM.kt` |
| `surface` | `STUB.SURFACE_PROPS` | `NewSurfaceGM.kt`, `SnapWithMipsGM.kt`, `SurfacePropsGM.kt` |
| `textblobmixedsizes` | `STUB.DF_TEXT_RASTER` | `TextBlobMixedSizesGM.kt` |
| `vertices` | `STUB.DRAW_VERTICES` | `Skbug13047GM.kt`, `VerticesBatchingGM.kt`, `VerticesCollapsedGM.kt`, `VerticesGM.kt`, `VerticesPerspectiveGM.kt` |

## Notes

- `STUB.MISSING_API` is too vague. The affected rows (`aaclip`, `addarc`)
  need tag cleanup before implementation selection.
- `fiddle`, `dashcubics`, and `rrect` are classified as implementation
  because their disabled/stub reason is actionable but not normalized as
  a `STUB.*` tag.
- Text-related rows should be revisited after the font delivery if their
  implementation would touch glyph storage or shaping assumptions.
- `STUB.COMPOSE_SHADER` may overlap with WGSL/parser work on the GPU side,
  but the raster factory itself can be evaluated independently.
