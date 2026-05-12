# Plan de portage des GM Skia → kanvas-skia

Statut du portage des Graphics Modules (GM) C++ de Skia vers `kanvas-skia/src/main/kotlin/org/skia/tests/`.

**Source de référence** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm/` (437 fichiers `.cpp`)
**Cible** : `kanvas-skia/src/main/kotlin/org/skia/tests/*GM.kt` (295 GMs Kotlin sur `origin/master`)
**Snapshot** : post-merge vagues A→R (PRs #304-309, #321-322, #328-333) + phases G1, G2, G3, G4a, G4b, G5, G7, G9b mergées

## Résumé

| Statut | Fichiers `.cpp` | Pourcentage |
|---|---:|---:|
| ✅ Porté (≥ 1 `*GM.kt` rattaché) | 208 | 47% |
| 🚧 Bloqué par API manquante / dépendance externe | 118 | 27% |
| ❌ Non tenté (potentiellement portable) | 111 | 26% |
| **Total** | **437** | **100%** |

GMs Kotlin sans correspondance dans le tree de référence (probablement issus d'une version Skia plus récente) : **20**.

## Progression depuis le snapshot initial

| Étape | Date | `.cpp` portés | Δ |
|---|---|---:|---:|
| Snapshot initial (post-D2) | 2026-05-12 (matin) | 148 / 437 (34%) | — |
| Après vagues A→F + bulk-mark | 2026-05-12 (midi) | 166 / 437 (38%) | +18 |
| Après phases G1-G9b + vagues I-R | 2026-05-12 (soir) | 208 / 437 (48%) | +42 |

**Vagues livrées** (mon stream + workstreams parallèles) :
- Vagues A→F (mes batch initiaux) : 15 ports
- Vagues I-A, I-B (parallèle) : 8 ports (image-loading utilisateurs)
- Vagues K (#321/#323), L (#322/#327) : ~7 nouveaux ports + upgrades
- Vagues M, N, O, P, Q, R (mon stream) : **34 GMs** sur 35 tentés (1 skippé : `unpremul`)

## APIs ajoutées (phases G mergées)

| Phase | API | PR | Impact |
|---|---|---|---|
| **G1** | `ToolUtils::GetResourceAsImage/AsBitmap` + ressources `kanvas/src/test/resources/images/` | merged | débloque ~65 GMs image-dépendants |
| **G2** | `SkCubicResampler` + cubic `SkSamplingOptions` | #319 | débloque `bicubic`, sampling cubique |
| **G3** | `SkPicture::makeShader` | #315 | débloque `bug6643`, `pictureshadercache`, etc. |
| **G4a** | `kAlpha_8` `SkBitmap` accessors | #311 | débloque `colorfilteralpha8`, `skbug_12212`, `overdrawcolorfilter` |
| **G4b** | `kBGRA_8888` `SkBitmap` accessors | #316 | débloque `skbug_9819` |
| **G5** | `SkCanvas::experimental_DrawEdgeAAQuad` + `QuadAAFlags` | #317 | débloque `crbug_1167277`, `crbug_1174186`, `crbug_1162942`, `crbug_1177833` |
| **G7** | `SkBitmapDevice` routing de `paint.colorFilter` (saveLayer) + `paint.imageFilter` (drawPaint) | #314 | débloque `crbug_918512`, `imagefiltersunpremul` |
| **G9b** | `SkOverdrawColorFilter` | #313 | débloque `overdrawcolorfilter` |

## APIs en PR ouverte (à merger)

| Phase | API | PR | Impact |
|---|---|---|---|
| G4c | `kRGBA_4444` `SkBitmap` accessors | #326 | débloque `copy_to_4444` |
| G6 | `SkCanvas::SaveLayerRec` avec `fBackdrop` + `SkImageFilters::Blur(σ, σ, tileMode, ..., cropRect)` | #324 | débloque `crbug_1313579`, `crbug_1174354`, `complexclip_blur_tiled` |
| G8 | `SkRegion` ops + `SkCanvas::clipRegion` | #325 | débloque `clip_sierpinski_region`, `drawregion`, `drawregionmodes` |

## APIs encore à ajouter (catalogue cumulatif des manques)

### API publiques upstream à ajouter

| API Skia upstream | Header upstream | GM(s) bloqué(s) | Suggestion `:kanvas-skia` |
|---|---|---|---|
| `SkTableMaskFilter::Create(uint8_t[256])` | `include/effects/SkTableMaskFilter.h` | `tablemaskfilter` | factory `ByteArray(256) → SkMaskFilter` |
| `SkShaderMaskFilter::Make(sk_sp<SkShader>)` | `include/effects/SkShaderMaskFilter.h` | `shadermaskfilter` | impl. `SkMaskFilter` qui échantillonne son shader aux coords mask |
| `SkCanvas::clipShader(sk_sp<SkShader>)` | `include/core/SkCanvas.h` | `clipshader` | `SkCanvas.clipShader(shader, op = kIntersect)` |
| `SkM44` + `SkCanvas::concat(SkM44)` + `SkM44::{Perspective, LookAt, Rotate, Translate}` | `include/core/SkM44.h` | `sk3d_simple`, `crbug_224618` | `data class SkM44(16 floats)` + ops + `SkCanvas.concat(SkM44)` |
| `SkPath::contains(x, y): Boolean` | `include/core/SkPath.h` | `hittestpath` | winding-number ray-cast sur le verb stream |
| `SkCanvas::readPixels(dst, x, y): Boolean` | `include/core/SkCanvas.h` | `unpremul`, `skbug_9819`, `encode_platform` | blit + conversion couleur depuis le device |
| `SkShaders::CoordClamp` + `SkImage::makeSubset` + `SkImage::withDefaultMipmaps` | `include/core/SkImage.h` | `coordclampshader` | 3 gaps indépendants |
| `SkCanvas::drawImageNine(image, center, dst, filter, paint?)` | `include/core/SkCanvas.h` | `ninepatchstretch` | lattice-stretch des 9 cellules |
| `SkImageFilter::makeWithLocalMatrix(matrix)` | `include/core/SkImageFilter.h` | `localmatriximagefilter` | wraps `this` en pré-composant la matrice |
| `SkCanvas::discard()` | `include/core/SkCanvas.h` | `discard` | raster fallback no-op ou `bitmap.eraseColor(0)` |
| `SkCanvas::drawGlyphs` / `drawGlyphsRSXform` | `include/core/SkCanvas.h` | `drawglyphs` (déjà porté avec workaround) | API native plus directe |
| `SkSamplingOptions::Aniso(maxAniso: Int)` + mip LOD chaîne `SkImage` | `include/core/SkSamplingOptions.h` | `anisotropic_image_scale_*` variants, `filterindiabox`, `bigtileimagefilter` | extension `SkSamplingOptions.aniso` + pré-compute mip pyramid |
| Image encoders (`SkPngEncoder`, `SkJpegEncoder`, `SkImage::encodeToData`) | `include/encode/*.h` | `encode_alpha_jpeg`, `encode_platform` | besoin de PR dédié |
| `SkParsePath::FromSVGString` | `include/utils/SkParsePath.h` | (déjà porté `crbug_691386` avec workaround) | parser SVG path |

### Lacunes d'intégration (API existe, intégration incomplète)

- **Gradient lerp linear-space** — `SkLinearGradient.lookupStop` / `lerpPremul` interpole en sRGB 8 bits ; cause perte de qualité sur `transparency_check` (13%). Besoin : overload `SkColor4f` ou décodage/réencodage transfer-function-aware autour du lerp.
- **`SkBitmapDevice.drawImageRect` sous CTM non-axis-aligned** — court-circuit qui drop le draw quand la matrice n'est pas alignée sur les axes (cf TODO `SkCanvas.kt:798`). Impacte `filterindiabox` (cellule rotation 30°).
- **Variable-font scaler consumption** — `SkFontVariation` stocké mais non consommé par le AWT scaler ; bloque `fontscalerdistortable`.

## Catégories des GMs encore bloqués

| Bloqueur | Nb `.cpp` |
|---|---:|
| GM GPU-only (Ganesh-specific) | 18 |
| (résolu G3) — revoir port | 18 |
| (résolu G2) — revoir port | 12 |
| (résolu G4a) — revoir port | 9 |
| `SkPicture` recording / culling (variable) | 7 |
| `SkImageFilters::Blur(σ, σ, tileMode, ...)` (Phase G6) | 7 |
| Variable-font scaler consumption | 7 |
| `SkRegion::*` (Phase G8 en PR #325) | 6 |
| (résolu G4b) — revoir port | 5 |
| `SkM44` + `SkCanvas::concat(SkM44)` | 4 |
| `kRGBA_4444` (Phase G4c en PR #326) | 4 |
| `SkParsePath::FromSVGString` | 3 |
| `SkShaderMaskFilter::Make` | 3 |
| (résolu G5) — revoir port | 2 |
| `SkPath::contains(x, y)` | 2 |
| `SkCanvas::readPixels` | 2 |
| `SkCanvas::SaveLayerRec` avec `fBackdrop` (Phase G6 en PR #324) | 1 |
| `SkCanvas::discard` | 1 |
| GM Bazel-only (pas de PNG de référence) | 1 |
| GM PDF-only (annotations) | 1 |
| `SkImageFilter::makeWithLocalMatrix` | 1 |
| `SkCanvas::drawImageNine` / `drawImageLattice` | 1 |
| (résolu G9b) — revoir port | 1 |
| `SkTableMaskFilter` | 1 |
| Video decoder (non-applicable) | 1 |

## Table de portage complète

| `.cpp` source | Statut | GMs Kotlin | Note |
|---|:---:|---|---|
| `3d.cpp` | 🚧 | — | `SkM44` + `SkCanvas::concat(SkM44)` |
| `aaa.cpp` | ✅ | `AnalyticAntialiasConvexGM.kt`, `AnalyticAntialiasGeneralGM.kt`, `AnalyticAntialiasInverseGM.kt` | — |
| `aaclip.cpp` | ✅ | `AaclipGM.kt`, `ClipCubicGM.kt` | — |
| `aarecteffect.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `aarectmodes.cpp` | ✅ | `AaRectModesGM.kt` | — |
| `aaxfermodes.cpp` | ✅ | `AAXfermodesGM.kt` | — |
| `addarc.cpp` | ✅ | `AddArcGM.kt`, `FillCircleGM.kt`, `StrokeCircleGM.kt` | — |
| `all_bitmap_configs.cpp` | 🚧 | — | (résolu G4a) — revoir port |
| `alpha_image.cpp` | ❌ | — | — |
| `alphagradients.cpp` | ✅ | `AlphaGradientsGM.kt` | — |
| `analytic_gradients.cpp` | ✅ | `AnalyticGradientShaderGM.kt` | — |
| `androidblendmodes.cpp` | ✅ | `AndroidBlendModesGM.kt` | — |
| `animated_gif.cpp` | ❌ | — | — |
| `animated_image_orientation.cpp` | 🚧 | — | `SkPicture` recording / culling (variable) |
| `animatedimageblurs.cpp` | 🚧 | — | `SkImageFilters::Blur(σ, σ, tileMode, ...)` (Phase G6) |
| `anisotropic.cpp` | ❌ | — | — |
| `annotated_text.cpp` | ✅ | `AnnotatedTextGM.kt` | — |
| `arcofzorro.cpp` | ✅ | `ArcOfZorroGM.kt` | — |
| `arcto.cpp` | ✅ | `ArcToGM.kt`, `Bug593049GM.kt` | — |
| `arithmode.cpp` | ✅ | `ArithmodeGM.kt`, `ArithmodeBlenderGM.kt` | — |
| `asyncrescaleandread.cpp` | 🚧 | — | (résolu G4b) — revoir port |
| `attributes.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `b_119394958.cpp` | ✅ | `B119394958GM.kt` | — |
| `backdrop.cpp` | 🚧 | — | `SkImageFilters::Blur(σ, σ, tileMode, ...)` (Phase G6) |
| `backdrop_imagefilter_croprect.cpp` | ❌ | — | — |
| `badpaint.cpp` | ✅ | `BadPaintGM.kt` | — |
| `batchedconvexpaths.cpp` | ✅ | `BatchedConvexPathsGM.kt` | — |
| `bc1_transparency.cpp` | ❌ | — | — |
| `beziereffects.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `beziers.cpp` | ✅ | `BeziersGM.kt` | — |
| `bicubic.cpp` | ✅ | `BicubicGM.kt` | — |
| `bigblurs.cpp` | ✅ | `BigBlursGM.kt` | — |
| `bigmatrix.cpp` | ✅ | `BigMatrixGM.kt` | — |
| `bigrect.cpp` | ✅ | `BigRectGM.kt` | — |
| `bigrrectaaeffect.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `bigtext.cpp` | ✅ | `BigTextGM.kt` | — |
| `bigtileimagefilter.cpp` | ✅ | `BigTileImageFilterGM.kt` | — |
| `bitmapcopy.cpp` | 🚧 | — | (résolu G4a) — revoir port |
| `bitmapfilters.cpp` | 🚧 | — | `kRGBA_4444` (Phase G4c en PR #326) |
| `bitmapimage.cpp` | ❌ | — | — |
| `bitmappremul.cpp` | 🚧 | — | `kRGBA_4444` (Phase G4c en PR #326) |
| `bitmaprect.cpp` | ✅ | `BitmapRectRoundingGM.kt`, `DrawBitmapRect3GM.kt` | — |
| `bitmaprecttest.cpp` | ✅ | `BitmapRectTestGM.kt` | — |
| `bitmapshader.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `bitmaptiled.cpp` | ✅ | `BitmapTiledGM.kt` | — |
| `bleed.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `blurcircles.cpp` | ✅ | `BlurCirclesGM.kt` | — |
| `blurcircles2.cpp` | ❌ | — | — |
| `blurignorexform.cpp` | ❌ | — | — |
| `blurimagevmask.cpp` | ❌ | — | — |
| `blurpositioning.cpp` | ✅ | `BlurPositioningGM.kt` | — |
| `blurquickreject.cpp` | ✅ | `BlurQuickRejectGM.kt` | — |
| `blurrect.cpp` | ❌ | — | — |
| `blurredclippedcircle.cpp` | ✅ | `BlurredClippedCircleGM.kt` | — |
| `blurroundrect.cpp` | ✅ | `BlurLargeRRectsGM.kt`, `SimpleBlurRoundRectGM.kt` | — |
| `blurs.cpp` | ✅ | `Blur2RectsGM.kt`, `Blur2RectsNonNinepatchGM.kt` | — |
| `blurtextsmallradii.cpp` | ✅ | `BlurTextSmallRadiiGM.kt` | — |
| `bmpfilterqualityrepeat.cpp` | ✅ | `BmpFilterQualityRepeatGM.kt` | — |
| `bug12866.cpp` | ✅ | `Bug12866GM.kt`, `Bug40810065GM.kt` | — |
| `bug5252.cpp` | ✅ | `Bug5252GM.kt` | — |
| `bug530095.cpp` | ✅ | `Bug530095GM.kt`, `Bug591993GM.kt` | — |
| `bug615686.cpp` | ✅ | `Bug615686GM.kt` | — |
| `bug6643.cpp` | ✅ | `Bug6643GM.kt` | — |
| `bug6783.cpp` | ✅ | `Bug6783GM.kt` | — |
| `bug9331.cpp` | ✅ | `Bug9331GM.kt` | — |
| `circle_sizes.cpp` | ✅ | `CircleSizesGM.kt` | — |
| `circulararcs.cpp` | ✅ | `Bug406747427GM.kt`, `CircularArcsGM.kt`, `Crbug1472747GM.kt`, `Crbug888453GM.kt`, `OneBadArcGM.kt` | — |
| `circularclips.cpp` | ✅ | `CircularClipsGM.kt` | — |
| `clear_swizzle.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `clip_error.cpp` | ✅ | `ClipErrorGM.kt` | — |
| `clip_sierpinski_region.cpp` | 🚧 | — | `SkRegion::*` (Phase G8 en PR #325) |
| `clip_strokerect.cpp` | ✅ | `ClipStrokeRectGM.kt` | — |
| `clipdrawdraw.cpp` | ✅ | `ClipDrawDrawGM.kt` | — |
| `clippedbitmapshaders.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `clipshader.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `clockwise.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `closedcappedhairlines.cpp` | ❌ | — | — |
| `collapsepaths.cpp` | ✅ | `CollapsePathsGM.kt` | — |
| `color4f.cpp` | ❌ | — | — |
| `coloremoji.cpp` | ❌ | — | — |
| `coloremoji_blendmodes.cpp` | ❌ | — | — |
| `colorfilteralpha8.cpp` | ✅ | `ColorFilterAlpha8GM.kt` | — |
| `colorfilterimagefilter.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `colorfilters.cpp` | ❌ | — | — |
| `colormatrix.cpp` | ✅ | `ColorMatrixGM.kt` | — |
| `colorspace.cpp` | ❌ | — | — |
| `colorwheel.cpp` | ✅ | `ColorWheelNativeGM.kt` | — |
| `colrv1.cpp` | 🚧 | — | Variable-font scaler consumption |
| `complexclip.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `complexclip2.cpp` | ❌ | — | — |
| `complexclip3.cpp` | ❌ | — | — |
| `complexclip4.cpp` | ❌ | — | — |
| `complexclip_blur_tiled.cpp` | ❌ | — | — |
| `composecolorfilter.cpp` | ✅ | `ComposeColorFilterGM.kt` | — |
| `composeshader.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `compositor_quads.cpp` | 🚧 | — | (résolu G5) — revoir port |
| `compressed_textures.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `concavepaths.cpp` | ✅ | `ConcavePathsGM.kt` | — |
| `conicpaths.cpp` | ✅ | `ArcCircleGapGM.kt`, `ConicPathsGM.kt`, `Crbug640176GM.kt`, `LargeCircleGM.kt`, `LargeOvalsGM.kt` | — |
| `constcolorprocessor.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `convex_all_line_paths.cpp` | ✅ | `ConvexLineOnlyPathsGM.kt` | — |
| `convexpaths.cpp` | ✅ | `ConvexPathsGM.kt` | — |
| `convexpolyclip.cpp` | ❌ | — | — |
| `convexpolyeffect.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `coordclampshader.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `copy_to_4444.cpp` | 🚧 | — | `kRGBA_4444` (Phase G4c en PR #326) |
| `crbug_1041204.cpp` | ✅ | `Crbug10141204GM.kt` | — |
| `crbug_1073670.cpp` | ✅ | `Crbug1073670GM.kt` | — |
| `crbug_1086705.cpp` | ✅ | `Crbug1086705GM.kt` | — |
| `crbug_1113794.cpp` | ✅ | `Crbug1113794GM.kt` | — |
| `crbug_1139750.cpp` | ✅ | `Crbug1139750GM.kt` | — |
| `crbug_1156804.cpp` | ✅ | `Crbug1156804GM.kt` | — |
| `crbug_1162942.cpp` | ✅ | `Crbug1162942GM.kt` | — |
| `crbug_1167277.cpp` | ✅ | `Crbug1167277GM.kt` | — |
| `crbug_1174186.cpp` | ✅ | `Crbug1174186GM.kt` | — |
| `crbug_1174354.cpp` | 🚧 | — | `SkCanvas::SaveLayerRec` avec `fBackdrop` (Phase G6 en PR #324) |
| `crbug_1177833.cpp` | ✅ | `Crbug1177833GM.kt` | — |
| `crbug_1257515.cpp` | ✅ | `Crbug1257515GM.kt` | — |
| `crbug_1313579.cpp` | 🚧 | — | `SkImageFilters::Blur(σ, σ, tileMode, ...)` (Phase G6) |
| `crbug_224618.cpp` | 🚧 | — | `SkM44` + `SkCanvas::concat(SkM44)` |
| `crbug_478659067.cpp` | ❌ | — | — |
| `crbug_691386.cpp` | ✅ | `Crbug691386GM.kt` | — |
| `crbug_788500.cpp` | ✅ | `Crbug788500GM.kt` | — |
| `crbug_847759.cpp` | ✅ | `Crbug847759GM.kt` | — |
| `crbug_884166.cpp` | ✅ | `Crbug884166GM.kt` | — |
| `crbug_887103.cpp` | ✅ | `Crbug887103GM.kt` | — |
| `crbug_892988.cpp` | ✅ | `Crbug892988GM.kt` | — |
| `crbug_899512.cpp` | ✅ | `Crbug899512GM.kt` | — |
| `crbug_905548.cpp` | ✅ | `Crbug905548GM.kt` | — |
| `crbug_908646.cpp` | ✅ | `Crbug908646GM.kt` | — |
| `crbug_913349.cpp` | ✅ | `Crbug913349GM.kt` | — |
| `crbug_918512.cpp` | ✅ | `Crbug918512GM.kt` | — |
| `crbug_938592.cpp` | ✅ | `Crbug938592GM.kt` | — |
| `crbug_946965.cpp` | ✅ | `Crbug946965GM.kt` | — |
| `crbug_947055.cpp` | ✅ | `Crbug947055GM.kt` | — |
| `crbug_996140.cpp` | ✅ | `Crbug996140GM.kt` | — |
| `crop_imagefilter.cpp` | 🚧 | — | `SkPath::contains(x, y)` |
| `croppedrects.cpp` | ✅ | `CroppedRectsGM.kt` | — |
| `crosscontextimage.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `cubicpaths.cpp` | ✅ | `Bug5099GM.kt`, `Bug6083GM.kt`, `ClippedCubicGM.kt`, `ClippedCubic2GM.kt`, `CubicClosePathGM.kt`, `CubicPathGM.kt`, `CubicPathShaderGM.kt` | — |
| `daa.cpp` | ❌ | — | — |
| `dashcircle.cpp` | ❌ | — | — |
| `dashcubics.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `dashing.cpp` | ✅ | `DashingGM.kt`, `LongWavyLineGM.kt` | — |
| `degeneratesegments.cpp` | ❌ | — | — |
| `destcolor.cpp` | ✅ | `DestColorGM.kt` | — |
| `dftext.cpp` | ❌ | — | — |
| `dftext_blob_persp.cpp` | ❌ | — | — |
| `discard.cpp` | 🚧 | — | `SkCanvas::discard` |
| `displacement.cpp` | ❌ | — | — |
| `distantclip.cpp` | ✅ | `DistantClipGM.kt` | — |
| `draw_bitmap_rect_skbug4374.cpp` | ✅ | `DrawBitmapRectSkbug4734GM.kt` | — |
| `drawable.cpp` | ✅ | `DrawableGM.kt` | — |
| `drawatlas.cpp` | 🚧 | — | Variable-font scaler consumption |
| `drawatlascolor.cpp` | ❌ | — | — |
| `drawbitmaprect.cpp` | ❌ | — | — |
| `drawglyphs.cpp` | ✅ | `DrawGlyphsGM.kt` | — |
| `drawimageset.cpp` | 🚧 | — | (résolu G4a) — revoir port |
| `drawlines_with_local_matrix.cpp` | ✅ | `DrawlinesWithLocalMatrixGM.kt` | — |
| `drawminibitmaprect.cpp` | ❌ | — | — |
| `drawquadset.cpp` | 🚧 | — | (résolu G5) — revoir port |
| `drawregion.cpp` | 🚧 | — | `SkRegion::*` (Phase G8 en PR #325) |
| `drawregionmodes.cpp` | 🚧 | — | `SkRegion::*` (Phase G8 en PR #325) |
| `dropshadowimagefilter.cpp` | ✅ | `DropShadowImageFilterGM.kt` | — |
| `drrect.cpp` | ✅ | `DRRectGM.kt` | — |
| `drrect_small_inner.cpp` | ✅ | `DRRectSmallInnerGM.kt` | — |
| `dstreadshuffle.cpp` | 🚧 | — | (résolu G4b) — revoir port |
| `ducky_yuv_blend.cpp` | ❌ | — | — |
| `emboss.cpp` | ❌ | — | — |
| `emptypath.cpp` | ✅ | `EmptyPathGM.kt` | — |
| `emptyshader.cpp` | ✅ | `EmptyShaderGM.kt` | — |
| `encode.cpp` | ✅ | `EncodeGM.kt` | — |
| `encode_alpha_jpeg.cpp` | 🚧 | — | `SkCanvas::readPixels` |
| `encode_color_types.cpp` | 🚧 | — | `kRGBA_4444` (Phase G4c en PR #326) |
| `encode_platform.cpp` | ❌ | — | — |
| `encode_srgb.cpp` | ❌ | — | — |
| `exoticformats.cpp` | ❌ | — | — |
| `fadefilter.cpp` | ✅ | `FadeFilterGM.kt` | — |
| `fatpathfill.cpp` | ✅ | `FatPathFillGM.kt` | — |
| `fiddle.cpp` | ✅ | `FiddleGM.kt` | — |
| `fillrect_gradient.cpp` | ✅ | `FillrectGradientGM.kt` | — |
| `filltypes.cpp` | ✅ | `FillTypeGM.kt`, `FillTypesGM.kt` | — |
| `filltypespersp.cpp` | ✅ | `FillTypePerspGM.kt` | — |
| `filterbug.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `filterfastbounds.cpp` | 🚧 | — | `SkPicture` recording / culling (variable) |
| `filterindiabox.cpp` | ✅ | `FilterIndiaBoxGM.kt` | — |
| `flippity.cpp` | ❌ | — | — |
| `fontations.cpp` | 🚧 | — | Variable-font scaler consumption |
| `fontations_ft_compare.cpp` | 🚧 | — | Variable-font scaler consumption |
| `fontcache.cpp` | ❌ | — | — |
| `fontmgr.cpp` | 🚧 | — | Variable-font scaler consumption |
| `fontregen.cpp` | ❌ | — | — |
| `fontscaler.cpp` | ✅ | `FontScalerGM.kt` | — |
| `fontscalerdistortable.cpp` | 🚧 | — | Variable-font scaler consumption |
| `fp_sample_chaining.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `fpcoordinateoverride.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `fwidth_squircle.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `gammatext.cpp` | ❌ | — | — |
| `getpostextpath.cpp` | ✅ | `GetPosTextPathGM.kt` | — |
| `giantbitmap.cpp` | ❌ | — | — |
| `glyph_pos.cpp` | ❌ | — | — |
| `gm.cpp` | ❌ | — | — |
| `gpu_blur_utils.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `gradient_dirty_laundry.cpp` | ❌ | — | — |
| `gradient_matrix.cpp` | ❌ | — | — |
| `gradients.cpp` | ✅ | `ClampedGradientsGM.kt`, `GradientsGM.kt`, `RgbwSweepGradientGM.kt`, `SmallColorStopGM.kt`, `SweepTilingGM.kt` | — |
| `gradients_2pt_conical.cpp` | ❌ | — | — |
| `gradients_degenerate.cpp` | ❌ | — | — |
| `gradients_no_texture.cpp` | ❌ | — | — |
| `gradtext.cpp` | ✅ | `GradTextGM.kt` | — |
| `graphite_replay.cpp` | ❌ | — | — |
| `graphitestart.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `grayscalejpg.cpp` | ✅ | `GrayscaleJpgGM.kt` | — |
| `hairlines.cpp` | ✅ | `HairlineSubdivGM.kt`, `HairlinesGM.kt`, `SquareHairGM.kt` | — |
| `hairmodes.cpp` | ✅ | `HairModesGM.kt` | — |
| `hardstop_gradients.cpp` | ✅ | `HardstopGradientShaderGM.kt` | — |
| `hardstop_gradients_many.cpp` | ✅ | `HardstopGradientsManyGM.kt` | — |
| `hdr_pip_blur.cpp` | 🚧 | — | `SkImageFilters::Blur(σ, σ, tileMode, ...)` (Phase G6) |
| `hello_bazel_world.cpp` | 🚧 | — | GM Bazel-only (pas de PNG de référence) |
| `highcontrastfilter.cpp` | ❌ | — | — |
| `hittestpath.cpp` | 🚧 | — | `SkPath::contains(x, y)` |
| `hsl.cpp` | ❌ | — | — |
| `hugepath.cpp` | ✅ | `PathHugeCrbug800804GM.kt` | — |
| `image.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `image_pict.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `image_shader.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `imageblur.cpp` | ✅ | `ImageBlurGM.kt` | — |
| `imageblur2.cpp` | ❌ | — | — |
| `imageblurclampmode.cpp` | 🚧 | — | `SkImageFilters::Blur(σ, σ, tileMode, ...)` (Phase G6) |
| `imageblurrepeatmode.cpp` | 🚧 | — | `SkImageFilters::Blur(σ, σ, tileMode, ...)` (Phase G6) |
| `imageblurtiled.cpp` | ✅ | `ImageBlurTiledGM.kt` | — |
| `imagedither.cpp` | ✅ | `ImageDitherGM.kt` | — |
| `imagefilters.cpp` | 🚧 | — | `SkShaderMaskFilter::Make` |
| `imagefiltersbase.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `imagefiltersclipped.cpp` | 🚧 | — | `SkImageFilters::Blur(σ, σ, tileMode, ...)` (Phase G6) |
| `imagefilterscropexpand.cpp` | ❌ | — | — |
| `imagefilterscropped.cpp` | ❌ | — | — |
| `imagefiltersgraph.cpp` | ❌ | — | — |
| `imagefiltersscaled.cpp` | ❌ | — | — |
| `imagefiltersstroked.cpp` | ❌ | — | — |
| `imagefilterstransformed.cpp` | 🚧 | — | `SkM44` + `SkCanvas::concat(SkM44)` |
| `imagefiltersunpremul.cpp` | ✅ | `ImageFiltersUnpremulGM.kt` | — |
| `imagefromyuvtextures.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `imagemagnifier.cpp` | ❌ | — | — |
| `imagemakewithfilter.cpp` | 🚧 | — | `SkRegion::*` (Phase G8 en PR #325) |
| `imagemasksubset.cpp` | 🚧 | — | (résolu G4a) — revoir port |
| `imageresizetiled.cpp` | ❌ | — | — |
| `imagesource.cpp` | ❌ | — | — |
| `imagesource2.cpp` | ❌ | — | — |
| `internal_links.cpp` | 🚧 | — | GM PDF-only (annotations) |
| `inverseclip.cpp` | ✅ | `InverseClipGM.kt` | — |
| `inversepaths.cpp` | ✅ | `InversePathsGM.kt`, `InverseWindingmodeFiltersGM.kt` | — |
| `jpg_color_cube.cpp` | ❌ | — | — |
| `kawase_blur_rt.cpp` | ✅ | `KawaseBlurRtGM.kt` | — |
| `labyrinth.cpp` | ✅ | `LabyrinthGM.kt` | — |
| `largeclippedpath.cpp` | ✅ | `LargeClippedPathGM.kt` | — |
| `largeglyphblur.cpp` | ✅ | `LargeGlyphBlurGM.kt` | — |
| `lattice.cpp` | 🚧 | — | (résolu G4b) — revoir port |
| `lazytiling.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `lcdblendmodes.cpp` | ❌ | — | — |
| `lcdoverlap.cpp` | ✅ | `LcdOverlapGM.kt` | — |
| `lcdtext.cpp` | ❌ | — | — |
| `lighting.cpp` | ✅ | `LightingGM.kt` | — |
| `linepaths.cpp` | ✅ | `LinePathGM.kt` | — |
| `localmatriximagefilter.cpp` | 🚧 | — | `SkImageFilter::makeWithLocalMatrix` |
| `localmatriximageshader.cpp` | ✅ | `LocalMatrixImageShaderGM.kt` | — |
| `localmatrixshader.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `lumafilter.cpp` | ✅ | `LumaFilterGM.kt` | — |
| `luminosity.cpp` | ✅ | `LuminosityOverflowGM.kt` | — |
| `mac_aa_explorer.cpp` | 🚧 | — | (résolu G4a) — revoir port |
| `make_raster_image.cpp` | ✅ | `MakeRasterImageGM.kt` | — |
| `makecolorspace.cpp` | ❌ | — | — |
| `mandoline.cpp` | ❌ | — | — |
| `manypathatlases.cpp` | ❌ | — | — |
| `manypaths.cpp` | ✅ | `ManyCirclesGM.kt`, `ManyRRectsGM.kt` | — |
| `matrixconvolution.cpp` | ❌ | — | — |
| `matriximagefilter.cpp` | ✅ | `MatrixImageFilterGM.kt` | — |
| `mesh.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `mipmap.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `mirrortile.cpp` | ✅ | `MirrorTileGM.kt` | — |
| `mixedtextblobs.cpp` | ❌ | — | — |
| `mixercolorfilter.cpp` | ❌ | — | — |
| `modecolorfilters.cpp` | ❌ | — | — |
| `morphology.cpp` | ✅ | `MorphologyGM.kt` | — |
| `nearesthalfpixelimage.cpp` | 🚧 | — | (résolu G4a) — revoir port |
| `nested.cpp` | ✅ | `NestedGM.kt` | — |
| `ninepatchstretch.cpp` | 🚧 | — | `SkCanvas::drawImageNine` / `drawImageLattice` |
| `nonclosedpaths.cpp` | ✅ | `NonClosedPathsGM.kt` | — |
| `offsetimagefilter.cpp` | ❌ | — | — |
| `orientation.cpp` | ❌ | — | — |
| `ovals.cpp` | ✅ | `OvalGM.kt` | — |
| `overdrawcanvas.cpp` | 🚧 | — | (résolu G9b) — revoir port |
| `overdrawcolorfilter.cpp` | ✅ | `OverdrawColorFilterGM.kt` | — |
| `overstroke.cpp` | ❌ | — | — |
| `p3.cpp` | 🚧 | — | `SkCanvas::readPixels` |
| `palette.cpp` | ❌ | — | — |
| `patch.cpp` | ✅ | `PatchAlphaTestGM.kt`, `PatchPrimitiveGM.kt` | — |
| `path_stroke_with_zero_length.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `patharcto.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `pathcontourstart.cpp` | ✅ | `ContourStartGM.kt` | — |
| `patheffects.cpp` | ✅ | `PathEffectGM.kt` | — |
| `pathfill.cpp` | ✅ | `Bug7792GM.kt`, `PathArcToSkbug9077GM.kt`, `PathSkbug11859GM.kt`, `PathSkbug11886GM.kt`, `RotatedCubicPathGM.kt` | — |
| `pathinterior.cpp` | ✅ | `PathInteriorGM.kt` | — |
| `pathmaskcache.cpp` | ✅ | `PathMaskCacheGM.kt` | — |
| `pathmeasure.cpp` | ❌ | — | — |
| `pathopsblend.cpp` | ✅ | `PathOpsBlendGM.kt` | — |
| `pathopsinverse.cpp` | ✅ | `PathOpsInverseGM.kt`, `PathOpsSkbug10155GM.kt` | — |
| `pathreverse.cpp` | ✅ | `PathReverseGM.kt` | — |
| `pdf_never_embed.cpp` | ❌ | — | — |
| `perlinnoise.cpp` | ✅ | `PerlinNoiseGM.kt`, `PerlinNoiseRotatedGM.kt` | — |
| `perspimages.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `perspshaders.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `persptext.cpp` | ❌ | — | — |
| `picture.cpp` | ✅ | `PictureGM.kt`, `PictureCullRectGM.kt` | — |
| `pictureimagefilter.cpp` | 🚧 | — | `SkPicture` recording / culling (variable) |
| `pictureimagegenerator.cpp` | 🚧 | — | `SkPicture` recording / culling (variable) |
| `pictureshader.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `pictureshadercache.cpp` | ✅ | `PictureShaderCacheGM.kt` | — |
| `pictureshadertile.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `plus.cpp` | ✅ | `PlusMergesAaGM.kt` | — |
| `points.cpp` | ✅ | `PointsGM.kt`, `PointsMaskFilterGM.kt` | — |
| `poly2poly.cpp` | ❌ | — | — |
| `polygonoffset.cpp` | ❌ | — | — |
| `polygons.cpp` | ✅ | `ConjoinedPolygonsGM.kt`, `PolygonsGM.kt` | — |
| `postercircle.cpp` | 🚧 | — | `SkM44` + `SkCanvas::concat(SkM44)` |
| `preservefillrule.cpp` | ✅ | `PreserveFillRuleGM.kt` | — |
| `quadpaths.cpp` | ✅ | `QuadClosePathGM.kt`, `QuadPathGM.kt` | — |
| `radial_gradient_precision.cpp` | ✅ | `RadialGradientPrecisionGM.kt` | — |
| `rasterhandleallocator.cpp` | ❌ | — | — |
| `readpixels.cpp` | 🚧 | — | (résolu G4b) — revoir port |
| `recordopts.cpp` | 🚧 | — | `SkPicture` recording / culling (variable) |
| `rect_poly_stroke.cpp` | ✅ | `RectPolyStrokeGM.kt` | — |
| `rectangletexture.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `rendertomipmappedyuvimageplanes.cpp` | 🚧 | — | (résolu G4a) — revoir port |
| `repeated_bitmap.cpp` | ✅ | `RepeatedBitmapGM.kt` | — |
| `resizeimagefilter.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `rippleshadergm.cpp` | ❌ | — | — |
| `roundrects.cpp` | ✅ | `RoundRectGM.kt` | — |
| `rrect.cpp` | ✅ | `RRectGM.kt` | — |
| `rrectclipdrawpaint.cpp` | ✅ | `RRectClipDrawPaintGM.kt` | — |
| `rrects.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `rsxtext.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `runtimecolorfilter.cpp` | ✅ | `RuntimeColorFilterGM.kt` | — |
| `runtimefunctions.cpp` | ✅ | `RuntimeFunctionsGM.kt` | — |
| `runtimeimagefilter.cpp` | ❌ | — | — |
| `runtimeintrinsics.cpp` | ✅ | `RuntimeIntrinsicsCommonGM.kt`, `RuntimeIntrinsicsExponentialGM.kt`, `RuntimeIntrinsicsGeometricGM.kt`, `RuntimeIntrinsicsMatrixGM.kt`, `RuntimeIntrinsicsRelationalGM.kt`, `RuntimeIntrinsicsTrigGM.kt` | — |
| `runtimeshader.cpp` | 🚧 | — | (résolu G4a) — revoir port |
| `samplerstress.cpp` | ❌ | — | — |
| `savelayer.cpp` | 🚧 | — | `SkShaderMaskFilter::Make` |
| `scaledemoji.cpp` | ❌ | — | — |
| `scaledemoji_rendering.cpp` | ❌ | — | — |
| `scaledrects.cpp` | ✅ | `ClipLargeRectGM.kt`, `ScaledRectsGM.kt` | — |
| `scaledstrokes.cpp` | ✅ | `ScaledStrokesGM.kt` | — |
| `shadermaskfilter.cpp` | 🚧 | — | `SkShaderMaskFilter::Make` |
| `shaderpath.cpp` | ✅ | `ShaderPathGM.kt` | — |
| `shadertext3.cpp` | ❌ | — | — |
| `shadowutils.cpp` | ❌ | — | — |
| `shallowgradient.cpp` | ❌ | — | — |
| `shapes.cpp` | ❌ | — | — |
| `sharedcorners.cpp` | ❌ | — | — |
| `showmiplevels.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `simpleaaclip.cpp` | 🚧 | — | `SkRegion::*` (Phase G8 en PR #325) |
| `simplerect.cpp` | ✅ | `SimpleRectGM.kt` | — |
| `skbug1719.cpp` | ✅ | `Skbug1719GM.kt` | — |
| `skbug_12212.cpp` | ✅ | `Skbug12212GM.kt` | — |
| `skbug_257.cpp` | ❌ | — | — |
| `skbug_4868.cpp` | ✅ | `Skbug4868GM.kt` | — |
| `skbug_5321.cpp` | ✅ | `Skbug5321GM.kt` | — |
| `skbug_8664.cpp` | ✅ | `Skbug8664GM.kt` | — |
| `skbug_8955.cpp` | ✅ | `Skbug8955GM.kt` | — |
| `skbug_9319.cpp` | ✅ | `Skbug9319GM.kt` | — |
| `skbug_9819.cpp` | ✅ | `Skbug9819GM.kt` | — |
| `slug.cpp` | ❌ | — | — |
| `smallarc.cpp` | ✅ | `SmallArcGM.kt` | — |
| `smallcircles.cpp` | ✅ | `SmallCirclesGM.kt` | — |
| `smallpaths.cpp` | ✅ | `SmallPathsGM.kt` | — |
| `spritebitmap.cpp` | ✅ | `SpriteBitmapGM.kt` | — |
| `srcmode.cpp` | ❌ | — | — |
| `srgb.cpp` | ❌ | — | — |
| `stlouisarch.cpp` | ✅ | `StLouisArchGM.kt` | — |
| `stringart.cpp` | ✅ | `StringArtGM.kt` | — |
| `stroke_rect_shader.cpp` | ✅ | `StrokeRectShaderGM.kt` | — |
| `strokedlines.cpp` | ❌ | — | — |
| `strokefill.cpp` | ✅ | `Bug339297GM.kt`, `Bug6987GM.kt` | — |
| `strokerect.cpp` | ✅ | `StrokerectAnisotropic5408GM.kt` | — |
| `strokerect_anisotropic.cpp` | ✅ | `StrokerectAnisotropicGM.kt` | — |
| `strokerects.cpp` | ✅ | `StrokeRectsGM.kt` | — |
| `strokes.cpp` | ✅ | `B340982297GM.kt`, `CubicStrokeGM.kt`, `InnerJoinGeometryGM.kt`, `QuadCapGM.kt`, `Skbug12244GM.kt`, `StrokesGM.kt`, `Strokes2GM.kt`, `Strokes4GM.kt`, `TeenyStrokesGM.kt`, `ZeroLineStrokeGM.kt` | — |
| `stroketext.cpp` | 🚧 | — | Variable-font scaler consumption |
| `subsetshader.cpp` | ✅ | `BitmapSubsetShaderGM.kt` | — |
| `surface.cpp` | ❌ | — | — |
| `swizzle.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `tablecolorfilter.cpp` | ❌ | — | — |
| `tablemaskfilter.cpp` | 🚧 | — | `SkTableMaskFilter` |
| `tallstretchedbitmaps.cpp` | ❌ | — | — |
| `testgradient.cpp` | ✅ | `TestGradientGM.kt` | — |
| `texelsubset.cpp` | 🚧 | — | GM GPU-only (Ganesh-specific) |
| `text_scale_skew.cpp` | ✅ | `TextScaleSkewGM.kt` | — |
| `textblob.cpp` | ✅ | `TextBlobGM.kt` | — |
| `textblobblockreordering.cpp` | ✅ | `TextBlobBlockReorderingGM.kt` | — |
| `textblobcolortrans.cpp` | ✅ | `TextBlobColorTransGM.kt` | — |
| `textblobgeometrychange.cpp` | ✅ | `TextBlobGeometryChangeGM.kt` | — |
| `textblobmixedsizes.cpp` | ❌ | — | — |
| `textblobrandomfont.cpp` | ❌ | — | — |
| `textblobshader.cpp` | ✅ | `TextBlobShaderGM.kt` | — |
| `textblobtransforms.cpp` | ❌ | — | — |
| `textblobuseaftergpufree.cpp` | ✅ | `TextBlobUseAfterGpuFreeGM.kt` | — |
| `texteffects.cpp` | ❌ | — | — |
| `thinconcavepaths.cpp` | ✅ | `ThinConcavePathsGM.kt` | — |
| `thinrects.cpp` | ✅ | `ThinRectsGM.kt` | — |
| `thinstrokedrects.cpp` | ✅ | `ThinStrokedRectsGM.kt` | — |
| `tiledscaledbitmap.cpp` | ✅ | `TiledScaledBitmapGM.kt` | — |
| `tileimagefilter.cpp` | ❌ | — | — |
| `tilemodes.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `tilemodes_alpha.cpp` | ✅ | `TilemodesAlphaGM.kt` | — |
| `tilemodes_scaled.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `tinybitmap.cpp` | ✅ | `TinyBitmapGM.kt` | — |
| `transparency.cpp` | ✅ | `TransparencyCheckGM.kt` | — |
| `trickycubicstrokes.cpp` | ✅ | `TrickyCubicStrokesGM.kt`, `TrickyCubicStrokesLargeRadiusGM.kt` | — |
| `typeface.cpp` | ❌ | — | — |
| `unpremul.cpp` | 🚧 | — | (résolu G4b) — revoir port |
| `userfont.cpp` | 🚧 | — | `SkPicture` recording / culling (variable) |
| `variedtext.cpp` | ❌ | — | — |
| `vertices.cpp` | ✅ | `Skbug13047GM.kt`, `VerticesCollapsedGM.kt`, `VerticesPerspectiveGM.kt` | — |
| `verylargebitmap.cpp` | 🚧 | — | `SkPicture` recording / culling (variable) |
| `video_decoder.cpp` | 🚧 | — | Video decoder (non-applicable) |
| `wacky_yuv_formats.cpp` | 🚧 | — | (résolu G2) — revoir port |
| `widebuttcaps.cpp` | ✅ | `WideButtCapsGM.kt` | — |
| `windowrectangles.cpp` | 🚧 | — | `SkRegion::*` (Phase G8 en PR #325) |
| `workingspace.cpp` | ❌ | — | — |
| `xfermodeimagefilter.cpp` | ❌ | — | — |
| `xfermodes.cpp` | ✅ | `XfermodesGM.kt` | — |
| `xfermodes2.cpp` | ❌ | — | — |
| `xfermodes3.cpp` | ❌ | — | — |
| `ycbcrimage.cpp` | ❌ | — | — |
| `yuv420_odd_dim.cpp` | 🚧 | — | (résolu G3) — revoir port |
| `yuvtorgbsubset.cpp` | 🚧 | — | (résolu G4a) — revoir port |

## GMs Kotlin sans `.cpp` correspondant (20)

Ces fichiers `*GM.kt` n'ont pas pu être rattachés à un `.cpp` du tree de référence (probablement issus d'une version Skia plus récente, ou variantes nommées différemment de l'enregistrement upstream) :

- `AnisotropicMipGM.kt`
- `ChromeGradTextGM.kt`
- `ConicalGradients2ptInsideGM.kt`
- `ConicalGradients2ptOutsideGM.kt`
- `CornerDiscretePathEffectGM.kt`
- `ImageFilterBlurDropShadowGM.kt`
- `ImageFilterOffsetGM.kt`
- `InnerShapesGM.kt`
- `PathCapsFillsGridGM.kt`
- `PathInvFillGM.kt`
- `ShallowGradientConicalGM.kt`
- `ShallowGradientLinearGM.kt`
- `ShallowGradientLinearNoditherGM.kt`
- `ShallowGradientRadialGM.kt`
- `ShallowGradientRadialNoditherGM.kt`
- `ShallowGradientSweepGM.kt`
- `SimpleShapesGM.kt`
- `StrokeRectsRotatedGM.kt`
- `ThinRoundRectsGM.kt`
- `ZeroControlStrokeGM.kt`
