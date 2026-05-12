# Plan de portage des GM Skia → kanvas-skia

Statut du portage des Graphics Modules (GM) C++ de Skia vers `kanvas-skia/src/main/kotlin/org/skia/tests/`.

**Source de référence** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm/` (437 fichiers `.cpp`)
**Cible** : `kanvas-skia/src/main/kotlin/org/skia/tests/*GM.kt` (303 GMs Kotlin sur `origin/master`)
**Snapshot** : post-merge vagues A→R + K-bis + L + phases G1, G2, G3, G4a, G4b, G4c, G5, G6, G7, G8, G9a, G9b mergées (PRs #304–309, #311–320, #321–333, #323, #324, #325, #326, #327)

## Résumé

| Statut | Fichiers `.cpp` | Pourcentage |
|---|---:|---:|
| ✅ Porté (≥ 1 `*GM.kt` rattaché) | 215 | 49% |
| 🚧 Bloqué (API manquante / dépendance externe / GPU-only) | 116 | 26% |
| ❌ Non tenté (potentiellement portable) | 106 | 25% |
| **Total** | **437** | **100%** |

GMs Kotlin sans correspondance dans le tree de référence (probablement issus d'une version Skia plus récente) : **30**.

## Progression depuis le snapshot initial

| Étape | `.cpp` portés | Δ | Couverture |
|---|---:|---:|---:|
| Snapshot initial (post-D2) | 148 / 437 | — | 34% |
| Après vagues A→F + bulk-mark | 166 / 437 | +18 | 38% |
| Après phases G1, G3, G4a, G7, G9a, G9b + vagues I-A/I-B | 184 / 437 | +18 | 42% |
| Après phases G2, G4b, G5 + vagues K/L (post-rebase) | 215 / 437 | +31 | 49% |

**Gain net** : +67 GMs portés en deux jours, couverture passée de 34% à 49%.

## Phases d'API

| Phase | Statut | API |
|---|:---:|---|
| G1 — Image loading | ✅ mergé (#311) | `ToolUtils.GetResourceAsImage`/`GetResourceAsBitmap`/`MakeTextureImage`/`draw_checkerboard` |
| G2 — Bicubic sampling | ✅ mergé (#319) | `SkCubicResampler` + cubic `SkSamplingOptions` ; câblage `SkBitmapShader`/`SkBitmapDevice` |
| G3 — Picture shader | ✅ mergé (#315) | `SkPicture.makeShader(tileX, tileY, filter, localMatrix?, tile?)` |
| G4a — Alpha8 | ✅ mergé (#312) | `kAlpha_8` backing array dans `SkBitmap` |
| G4b — BGRA8888 | ✅ mergé (#316) | `kBGRA_8888` accepté par `SkBitmap.allocPixels` |
| G4c — RGBA4444 | ✅ mergé (#326) | `SkImageInfo.Make4444` + tests (backing déjà présent depuis C5) |
| G5 — Edge AA quad | ✅ mergé (#317) | `QuadAAFlags` enum + `SkCanvas.experimental_DrawEdgeAAQuad` |
| G6 — SaveLayerRec backdrop | ✅ mergé (#324) | `SaveLayerRec` w/ `backdrop` + `SkImageFilters.Blur(tileMode, cropRect)` |
| G7 — SkBitmapDevice gaps | ✅ mergé (#314) | `compositeFrom` applique `paint.colorFilter` ; `drawPaint` route via saveLayer quand `paint.imageFilter` |
| G8 — SkRegion + clipRegion | ✅ mergé (#325) | `SkRegion.translate` + `SkCanvas.clipRegion` (coords canvas/global) |
| G9a — SkParsePath | ✅ déjà sur master | `SkParsePath.FromSVGString` (12 commandes SVG, arc via `arcTo`) |
| G9b — OverdrawColorFilter | ✅ mergé (#313, amélioré post-G4a via PR #322) | `SkOverdrawColorFilter.MakeWithSkColors(IntArray(6))` |
| **G10 — Mipmap/Aniso** | ⏸️ en cours (agent quota épuisé) | `SkImage.withDefaultMipmaps`, `SkSamplingOptions.Aniso`, LOD selection |

## APIs encore manquantes (catalogue par ROI)

Pondération par nombre de `.cpp` impactés.

| Bloqueur | `.cpp` impactés | Type |
|---|---:|---|
| **Chargement d'images** (ressource PNG/JPG non copiée dans `kanvas/src/test/resources/images/`) | 58 | I/O (copie d'assets) |
| GPU-only (pas de PNG de référence raster, hors-scope `:kanvas-skia`) | 39 | n/a |
| `SkImageFilter::makeWithLocalMatrix` / `SkShader::makeWithLocalMatrix` | 3 | API publique |
| `SkParsePath::FromSVGString` (faux positif — déjà sur master) | 3 | (résolu) |
| `SkRegion::setPath` / `getBoundaryPath` (au-delà de G8) | 3 | API publique |
| `SkJpegEncoder` / `SkPngEncoder` / `SkWebpEncoder` | 2 | API publique |
| `SkTableMaskFilter` | 1 | API publique |
| `SkShaderMaskFilter::Make` | 1 | API publique |
| `SkCanvas::drawImageNine` | 1 | API publique |
| `SkCanvas::drawRegion` (rendre via region, distinct de clipRegion) | 1 | API publique |
| `SkCanvas::clipShader` | 1 | API publique |
| `SkImage::makeSubset` | 1 | API publique |
| `SkImageGenerator` / `SkImages::DeferredFromGenerator` | 1 | API publique |
| GM Bazel-only | 1 | n/a |

**Net après filtrage** : 58 GMs débloquables par copie d'assets, 39 GMs GPU-only intransposables, ~19 GMs sur des APIs à ajouter.

## Plan résiduel

### Phase H1 — Copier les assets images manquants (ROI : 58 GMs)

L'unique plus gros débloqueur restant. Scanner les 58 cpps marqués 🚧 « Chargement d'images », extraire les noms de ressources (`images/foo.png`), copier depuis `/Users/chaos/workspace/kanvas-forge/skia-main/resources/images/` vers `kanvas/src/test/resources/images/`. Les helpers `ToolUtils.GetResourceAsImage` existent déjà (G1).

**Effort** : 1-2 heures de scripting + copy. Optionnel : factoriser un script `update-test-resources.sh`.

### Phase H2 — Petites APIs publiques (ROI : ~19 GMs)

Phase fragmentée, chaque sous-tâche unitaire (½-1 jour) :

- `SkImageFilter.makeWithLocalMatrix` / `SkShader.makeWithLocalMatrix` (3 GMs)
- `SkRegion.setPath` / `getBoundaryPath` (3 GMs)
- `SkCanvas.drawImageNine` (1 GM, 9-patch raster)
- `SkCanvas.drawRegion` (1 GM, distinct de clipRegion)
- `SkCanvas.clipShader` (1 GM, per-pixel clip)
- `SkTableMaskFilter` (1 GM)
- `SkShaderMaskFilter.Make` (1 GM)
- `SkImage.makeSubset` (1 GM)
- `SkImageGenerator` + `SkImages.DeferredFromGenerator` (1 GM)
- `SkJpegEncoder` / `SkPngEncoder` / `SkWebpEncoder` (2 GMs, encodage)
- `SkColorFilters.LinearToSRGBGamma` / `SRGBToLinearGamma` (1 GM, `srgb_colorfilter`)

### Phase G10 — Mipmap/Aniso (ROI : 1 GM + variants)

À relancer (agent précédent épuisé sur quota). `SkImage.withDefaultMipmaps`, `SkSamplingOptions.Aniso`, LOD selection dans `SkBitmapShader`/`SkBitmapDevice`.

### Phase H3 — 106 GMs non tentés

Les GMs marqués ❌ n'ont pas été scannés en détail. À traiter en vagues continues d'agents (5 GMs par agent, 2 agents par vague). Certains se révèleront bloqués sur des APIs nouvelles — enrichir le catalogue au fil de l'eau.

## Méthodologie

- ✅ **Porté** : ≥ 1 `*GM.kt` correspond au `.cpp` après normalisation, ou contient un `DEF_SIMPLE_GM`/`DEF_GM` du `.cpp`. La conformité visuelle n'est pas garantie — voir scores dans `kanvas-skia/test-similarity-report.md`.
- 🚧 **Bloqué** : un agent a tenté le portage et abandonné faute d'API, **ou** le scan automatique a détecté un bloqueur dur (image absente, GPU-only, `experimental_DrawEdgeAAQuad`/`SkRegion`/etc.).
- ❌ **Non tenté** : reste à analyser ; à terme rejoindra ✅ ou 🚧.

## Table de portage

| `.cpp` source | Statut | GMs Kotlin | Bloqué par |
|---|:---:|---|---|
| `3d.cpp` | ❌ | — | — |
| `aaa.cpp` | ✅ | `AnalyticAntialiasConvexGM.kt`, `AnalyticAntialiasGeneralGM.kt`, `AnalyticAntialiasInverseGM.kt` | — |
| `aaclip.cpp` | ✅ | `AaclipGM.kt`, `ClipCubicGM.kt` | — |
| `aarecteffect.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `aarectmodes.cpp` | ✅ | `AaRectModesGM.kt` | — |
| `aaxfermodes.cpp` | ✅ | `AAXfermodesGM.kt` | — |
| `addarc.cpp` | ✅ | `AddArcGM.kt`, `FillCircleGM.kt`, `StrokeCircleGM.kt` | — |
| `all_bitmap_configs.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `alpha_image.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `alphagradients.cpp` | ✅ | `AlphaGradientsGM.kt` | — |
| `analytic_gradients.cpp` | ✅ | `AnalyticGradientShaderGM.kt` | — |
| `androidblendmodes.cpp` | ✅ | `AndroidBlendModesGM.kt` | — |
| `animated_gif.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `animated_image_orientation.cpp` | ❌ | — | — |
| `animatedimageblurs.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `anisotropic.cpp` | ✅ | `AnisotropicImageScaleLinearGM.kt`, `AnisotropicMipGM.kt` | — |
| `annotated_text.cpp` | ✅ | `AnnotatedTextGM.kt` | — |
| `arcofzorro.cpp` | ✅ | `ArcOfZorroGM.kt` | — |
| `arcto.cpp` | ✅ | `ArcToGM.kt`, `Bug593049GM.kt` | — |
| `arithmode.cpp` | ✅ | `ArithmodeGM.kt`, `ArithmodeBlenderGM.kt` | — |
| `asyncrescaleandread.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `attributes.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `b_119394958.cpp` | ✅ | `B119394958GM.kt` | — |
| `backdrop.cpp` | ❌ | — | — |
| `backdrop_imagefilter_croprect.cpp` | ❌ | — | — |
| `badpaint.cpp` | ✅ | `BadPaintGM.kt` | — |
| `batchedconvexpaths.cpp` | ✅ | `BatchedConvexPathsGM.kt` | — |
| `bc1_transparency.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `beziereffects.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `beziers.cpp` | ✅ | `BeziersGM.kt` | — |
| `bicubic.cpp` | ✅ | `BicubicGM.kt` | — |
| `bigblurs.cpp` | ✅ | `BigBlursGM.kt` | — |
| `bigmatrix.cpp` | ✅ | `BigMatrixGM.kt` | — |
| `bigrect.cpp` | ✅ | `BigRectGM.kt` | — |
| `bigrrectaaeffect.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `bigtext.cpp` | ✅ | `BigTextGM.kt` | — |
| `bigtileimagefilter.cpp` | ✅ | `BigTileImageFilterGM.kt` | — |
| `bitmapcopy.cpp` | ❌ | — | — |
| `bitmapfilters.cpp` | ❌ | — | — |
| `bitmapimage.cpp` | ✅ | `BitmapImageGM.kt` | — |
| `bitmappremul.cpp` | ✅ | `BitmapPremulGM.kt` | — |
| `bitmaprect.cpp` | ✅ | `BitmapRectRoundingGM.kt`, `DrawBitmapRect3GM.kt` | — |
| `bitmaprecttest.cpp` | ✅ | `BitmapRectTestGM.kt` | — |
| `bitmapshader.cpp` | ❌ | — | — |
| `bitmaptiled.cpp` | ✅ | `BitmapTiledGM.kt` | — |
| `bleed.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `blurcircles.cpp` | ✅ | `BlurCirclesGM.kt` | — |
| `blurcircles2.cpp` | ❌ | — | — |
| `blurignorexform.cpp` | ❌ | — | — |
| `blurimagevmask.cpp` | ✅ | `BlurImageVMaskGM.kt` | — |
| `blurpositioning.cpp` | ✅ | `BlurPositioningGM.kt` | — |
| `blurquickreject.cpp` | ✅ | `BlurQuickRejectGM.kt` | — |
| `blurrect.cpp` | ❌ | — | — |
| `blurredclippedcircle.cpp` | ✅ | `BlurredClippedCircleGM.kt` | — |
| `blurroundrect.cpp` | ✅ | `SimpleBlurRoundRectGM.kt` | — |
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
| `circulararcs.cpp` | ✅ | `Bug406747427GM.kt`, `CircularArcsGM.kt`, `OneBadArcGM.kt` | — |
| `circularclips.cpp` | ✅ | `CircularClipsGM.kt` | — |
| `clear_swizzle.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `clip_error.cpp` | ✅ | `ClipErrorGM.kt` | — |
| `clip_sierpinski_region.cpp` | ✅ | `ClipSierpinskiRegionGM.kt` | — |
| `clip_strokerect.cpp` | ✅ | `ClipStrokeRectGM.kt` | — |
| `clipdrawdraw.cpp` | ✅ | `ClipDrawDrawGM.kt` | — |
| `clippedbitmapshaders.cpp` | ❌ | — | — |
| `clipshader.cpp` | 🚧 | — | `SkCanvas::clipShader` |
| `clockwise.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `closedcappedhairlines.cpp` | ❌ | — | — |
| `collapsepaths.cpp` | ✅ | `CollapsePathsGM.kt` | — |
| `color4f.cpp` | ❌ | — | — |
| `coloremoji.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `coloremoji_blendmodes.cpp` | ❌ | — | — |
| `colorfilteralpha8.cpp` | ✅ | `ColorFilterAlpha8GM.kt` | — |
| `colorfilterimagefilter.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `colorfilters.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `colormatrix.cpp` | ✅ | `ColorMatrixGM.kt` | — |
| `colorspace.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `colorwheel.cpp` | ✅ | `ColorWheelNativeGM.kt` | — |
| `colrv1.cpp` | ❌ | — | — |
| `complexclip.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `complexclip2.cpp` | ❌ | — | — |
| `complexclip3.cpp` | ❌ | — | — |
| `complexclip4.cpp` | ❌ | — | — |
| `complexclip_blur_tiled.cpp` | ❌ | — | — |
| `composecolorfilter.cpp` | ✅ | `ComposeColorFilterGM.kt` | — |
| `composeshader.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `compositor_quads.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `compressed_textures.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `concavepaths.cpp` | ✅ | `ConcavePathsGM.kt` | — |
| `conicpaths.cpp` | ✅ | `ArcCircleGapGM.kt`, `ConicPathsGM.kt`, `LargeCircleGM.kt`, `LargeOvalsGM.kt` | — |
| `constcolorprocessor.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `convex_all_line_paths.cpp` | ✅ | `ConvexLineOnlyPathsGM.kt` | — |
| `convexpaths.cpp` | ✅ | `ConvexPathsGM.kt` | — |
| `convexpolyclip.cpp` | ❌ | — | — |
| `convexpolyeffect.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `coordclampshader.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `copy_to_4444.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `crbug_1041204.cpp` | ✅ | `Crbug10141204GM.kt` | — |
| `crbug_1073670.cpp` | ✅ | `Crbug1073670GM.kt` | — |
| `crbug_1086705.cpp` | ✅ | `Crbug1086705GM.kt` | — |
| `crbug_1113794.cpp` | ✅ | `Crbug1113794GM.kt` | — |
| `crbug_1139750.cpp` | ✅ | `Crbug1139750GM.kt` | — |
| `crbug_1156804.cpp` | ✅ | `Crbug1156804GM.kt` | — |
| `crbug_1162942.cpp` | ✅ | `Crbug1162942GM.kt` | — |
| `crbug_1167277.cpp` | ✅ | `Crbug1167277GM.kt` | — |
| `crbug_1174186.cpp` | ✅ | `Crbug1174186GM.kt` | — |
| `crbug_1174354.cpp` | ✅ | `Crbug1174354GM.kt` | — |
| `crbug_1177833.cpp` | ✅ | `Crbug1177833GM.kt` | — |
| `crbug_1257515.cpp` | ✅ | `Crbug1257515GM.kt` | — |
| `crbug_1313579.cpp` | ✅ | `Crbug1313579GM.kt` | — |
| `crbug_224618.cpp` | ❌ | — | — |
| `crbug_478659067.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
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
| `crop_imagefilter.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `croppedrects.cpp` | ✅ | `CroppedRectsGM.kt` | — |
| `crosscontextimage.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `cubicpaths.cpp` | ✅ | `Bug5099GM.kt`, `Bug6083GM.kt`, `ClippedCubicGM.kt`, `ClippedCubic2GM.kt`, `CubicClosePathGM.kt`, `CubicPathGM.kt`, `CubicPathShaderGM.kt` | — |
| `daa.cpp` | ❌ | — | — |
| `dashcircle.cpp` | ❌ | — | — |
| `dashcubics.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `dashing.cpp` | ✅ | `DashingGM.kt`, `LongWavyLineGM.kt`, `PathEffectGM.kt` | — |
| `degeneratesegments.cpp` | ❌ | — | — |
| `destcolor.cpp` | ✅ | `DestColorGM.kt` | — |
| `dftext.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `dftext_blob_persp.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `discard.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `displacement.cpp` | ❌ | — | — |
| `distantclip.cpp` | ✅ | `DistantClipGM.kt` | — |
| `draw_bitmap_rect_skbug4374.cpp` | ✅ | `DrawBitmapRectSkbug4734GM.kt` | — |
| `drawable.cpp` | ✅ | `DrawableGM.kt` | — |
| `drawatlas.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `drawatlascolor.cpp` | ❌ | — | — |
| `drawbitmaprect.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `drawglyphs.cpp` | ✅ | `DrawGlyphsGM.kt` | — |
| `drawimageset.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `drawlines_with_local_matrix.cpp` | ✅ | `DrawlinesWithLocalMatrixGM.kt` | — |
| `drawminibitmaprect.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `drawquadset.cpp` | 🚧 | — | `SkCanvas::experimental_DrawEdgeAAQuad` |
| `drawregion.cpp` | 🚧 | — | `SkRegion::*` API |
| `drawregionmodes.cpp` | 🚧 | — | `SkRegion::*` API |
| `dropshadowimagefilter.cpp` | ✅ | `DropShadowImageFilterGM.kt` | — |
| `drrect.cpp` | ✅ | `DRRectGM.kt` | — |
| `drrect_small_inner.cpp` | ✅ | `DRRectSmallInnerGM.kt` | — |
| `dstreadshuffle.cpp` | ❌ | — | — |
| `ducky_yuv_blend.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `emboss.cpp` | ❌ | — | — |
| `emptypath.cpp` | ✅ | `EmptyPathGM.kt` | — |
| `emptyshader.cpp` | ✅ | `EmptyShaderGM.kt` | — |
| `encode.cpp` | ✅ | `EncodeGM.kt` | — |
| `encode_alpha_jpeg.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `encode_color_types.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `encode_platform.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `encode_srgb.cpp` | 🚧 | — | Encodeurs image (`SkJpegEncoder`/`SkPngEncoder`/`SkWebpEncoder`) |
| `exoticformats.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fadefilter.cpp` | ✅ | `FadeFilterGM.kt` | — |
| `fatpathfill.cpp` | ✅ | `FatPathFillGM.kt` | — |
| `fiddle.cpp` | ✅ | `FiddleGM.kt` | — |
| `fillrect_gradient.cpp` | ✅ | `FillrectGradientGM.kt` | — |
| `filltypes.cpp` | ✅ | `FillTypeGM.kt`, `FillTypesGM.kt` | — |
| `filltypespersp.cpp` | ✅ | `FillTypePerspGM.kt` | — |
| `filterbug.cpp` | ❌ | — | — |
| `filterfastbounds.cpp` | ❌ | — | — |
| `filterindiabox.cpp` | ✅ | `FilterIndiaBoxGM.kt` | — |
| `flippity.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fontations.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `fontations_ft_compare.cpp` | ❌ | — | — |
| `fontcache.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fontmgr.cpp` | ❌ | — | — |
| `fontregen.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fontscaler.cpp` | ✅ | `FontScalerGM.kt` | — |
| `fontscalerdistortable.cpp` | ❌ | — | — |
| `fp_sample_chaining.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fpcoordinateoverride.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `fwidth_squircle.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `gammatext.cpp` | ❌ | — | — |
| `getpostextpath.cpp` | ✅ | `GetPosTextPathGM.kt` | — |
| `giantbitmap.cpp` | ❌ | — | — |
| `glyph_pos.cpp` | ❌ | — | — |
| `gm.cpp` | ❌ | — | — |
| `gpu_blur_utils.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `gradient_dirty_laundry.cpp` | ❌ | — | — |
| `gradient_matrix.cpp` | ❌ | — | — |
| `gradients.cpp` | ✅ | `ClampedGradientsGM.kt`, `GradientsGM.kt`, `RgbwSweepGradientGM.kt`, `SmallColorStopGM.kt`, `SweepTilingGM.kt` | — |
| `gradients_2pt_conical.cpp` | ❌ | — | — |
| `gradients_degenerate.cpp` | ❌ | — | — |
| `gradients_no_texture.cpp` | ❌ | — | — |
| `gradtext.cpp` | ✅ | `ChromeGradTextGM.kt`, `GradTextGM.kt` | — |
| `graphite_replay.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `graphitestart.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `grayscalejpg.cpp` | ✅ | `GrayscaleJpgGM.kt` | — |
| `hairlines.cpp` | ✅ | `HairlineSubdivGM.kt`, `HairlinesGM.kt`, `SquareHairGM.kt` | — |
| `hairmodes.cpp` | ✅ | `HairModesGM.kt` | — |
| `hardstop_gradients.cpp` | ✅ | `HardstopGradientShaderGM.kt` | — |
| `hardstop_gradients_many.cpp` | ✅ | `HardstopGradientsManyGM.kt` | — |
| `hdr_pip_blur.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `hello_bazel_world.cpp` | 🚧 | — | GM Bazel-only (pas de PNG de référence) |
| `highcontrastfilter.cpp` | ❌ | — | — |
| `hittestpath.cpp` | ❌ | — | — |
| `hsl.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `hugepath.cpp` | ❌ | — | — |
| `image.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `image_pict.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `image_shader.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `imageblur.cpp` | ✅ | `ImageBlurGM.kt` | — |
| `imageblur2.cpp` | ❌ | — | — |
| `imageblurclampmode.cpp` | ❌ | — | — |
| `imageblurrepeatmode.cpp` | ❌ | — | — |
| `imageblurtiled.cpp` | ✅ | `ImageBlurTiledGM.kt` | — |
| `imagedither.cpp` | ✅ | `ImageDitherGM.kt` | — |
| `imagefilters.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `imagefiltersbase.cpp` | ❌ | — | — |
| `imagefiltersclipped.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `imagefilterscropexpand.cpp` | ❌ | — | — |
| `imagefilterscropped.cpp` | ❌ | — | — |
| `imagefiltersgraph.cpp` | ❌ | — | — |
| `imagefiltersscaled.cpp` | ❌ | — | — |
| `imagefiltersstroked.cpp` | ❌ | — | — |
| `imagefilterstransformed.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `imagefiltersunpremul.cpp` | ✅ | `ImageFiltersUnpremulGM.kt` | — |
| `imagefromyuvtextures.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `imagemagnifier.cpp` | ❌ | — | — |
| `imagemakewithfilter.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `imagemasksubset.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `imageresizetiled.cpp` | ❌ | — | — |
| `imagesource.cpp` | ✅ | `ImageSourceGM.kt` | — |
| `imagesource2.cpp` | ❌ | — | — |
| `internal_links.cpp` | ❌ | — | — |
| `inverseclip.cpp` | ✅ | `InverseClipGM.kt` | — |
| `inversepaths.cpp` | ✅ | `InversePathsGM.kt`, `InverseWindingmodeFiltersGM.kt` | — |
| `jpg_color_cube.cpp` | 🚧 | — | Encodeurs image (`SkJpegEncoder`/`SkPngEncoder`/`SkWebpEncoder`) |
| `kawase_blur_rt.cpp` | ✅ | `KawaseBlurRtGM.kt` | — |
| `labyrinth.cpp` | ✅ | `LabyrinthGM.kt` | — |
| `largeclippedpath.cpp` | ✅ | `LargeClippedPathGM.kt` | — |
| `largeglyphblur.cpp` | ✅ | `LargeGlyphBlurGM.kt` | — |
| `lattice.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `lazytiling.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `lcdblendmodes.cpp` | ❌ | — | — |
| `lcdoverlap.cpp` | ✅ | `LcdOverlapGM.kt` | — |
| `lcdtext.cpp` | ❌ | — | — |
| `lighting.cpp` | ✅ | `LightingGM.kt` | — |
| `linepaths.cpp` | ✅ | `LinePathGM.kt` | — |
| `localmatriximagefilter.cpp` | 🚧 | — | `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` |
| `localmatriximageshader.cpp` | ✅ | `LocalMatrixImageShaderGM.kt` | — |
| `localmatrixshader.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `lumafilter.cpp` | ✅ | `LumaFilterGM.kt` | — |
| `luminosity.cpp` | ✅ | `LuminosityOverflowGM.kt` | — |
| `mac_aa_explorer.cpp` | ❌ | — | — |
| `make_raster_image.cpp` | ✅ | `MakeRasterImageGM.kt` | — |
| `makecolorspace.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `mandoline.cpp` | ❌ | — | — |
| `manypathatlases.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `manypaths.cpp` | ✅ | `ManyCirclesGM.kt`, `ManyRRectsGM.kt` | — |
| `matrixconvolution.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `matriximagefilter.cpp` | ✅ | `MatrixImageFilterGM.kt` | — |
| `mesh.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `mipmap.cpp` | ❌ | — | — |
| `mirrortile.cpp` | ✅ | `MirrorTileGM.kt` | — |
| `mixedtextblobs.cpp` | ❌ | — | — |
| `mixercolorfilter.cpp` | ❌ | — | — |
| `modecolorfilters.cpp` | ❌ | — | — |
| `morphology.cpp` | ✅ | `MorphologyGM.kt` | — |
| `nearesthalfpixelimage.cpp` | ❌ | — | — |
| `nested.cpp` | ✅ | `NestedGM.kt` | — |
| `ninepatchstretch.cpp` | 🚧 | — | `SkCanvas::drawImageNine` |
| `nonclosedpaths.cpp` | ✅ | `NonClosedPathsGM.kt` | — |
| `offsetimagefilter.cpp` | ❌ | — | — |
| `orientation.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `ovals.cpp` | ✅ | `OvalGM.kt` | — |
| `overdrawcanvas.cpp` | 🚧 | — | `SkOverdrawColorFilter` |
| `overdrawcolorfilter.cpp` | ✅ | `OverdrawColorFilterGM.kt` | — |
| `overstroke.cpp` | ❌ | — | — |
| `p3.cpp` | ❌ | — | — |
| `palette.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `patch.cpp` | ✅ | `PatchAlphaTestGM.kt`, `PatchPrimitiveGM.kt` | — |
| `path_stroke_with_zero_length.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `patharcto.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `pathcontourstart.cpp` | ✅ | `ContourStartGM.kt` | — |
| `patheffects.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `pathfill.cpp` | ✅ | `Bug7792GM.kt`, `RotatedCubicPathGM.kt` | — |
| `pathinterior.cpp` | ✅ | `PathInteriorGM.kt` | — |
| `pathmaskcache.cpp` | ✅ | `PathMaskCacheGM.kt` | — |
| `pathmeasure.cpp` | ❌ | — | — |
| `pathopsblend.cpp` | ✅ | `PathOpsBlendGM.kt` | — |
| `pathopsinverse.cpp` | ✅ | `PathOpsInverseGM.kt` | — |
| `pathreverse.cpp` | ✅ | `PathReverseGM.kt` | — |
| `pdf_never_embed.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `perlinnoise.cpp` | ✅ | `PerlinNoiseGM.kt`, `PerlinNoiseRotatedGM.kt` | — |
| `perspimages.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `perspshaders.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `persptext.cpp` | ❌ | — | — |
| `picture.cpp` | ✅ | `PictureGM.kt`, `PictureCullRectGM.kt` | — |
| `pictureimagefilter.cpp` | ❌ | — | — |
| `pictureimagegenerator.cpp` | 🚧 | — | `SkImageGenerator` / `DeferredFromGenerator` |
| `pictureshader.cpp` | 🚧 | — | `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` |
| `pictureshadercache.cpp` | ✅ | `PictureShaderCacheGM.kt` | — |
| `pictureshadertile.cpp` | ❌ | — | — |
| `plus.cpp` | ✅ | `PlusMergesAaGM.kt` | — |
| `points.cpp` | ✅ | `PointsGM.kt` | — |
| `poly2poly.cpp` | ❌ | — | — |
| `polygonoffset.cpp` | ❌ | — | — |
| `polygons.cpp` | ✅ | `ConjoinedPolygonsGM.kt`, `PolygonsGM.kt` | — |
| `postercircle.cpp` | ❌ | — | — |
| `preservefillrule.cpp` | ✅ | `PreserveFillRuleGM.kt` | — |
| `quadpaths.cpp` | ✅ | `QuadClosePathGM.kt`, `QuadPathGM.kt` | — |
| `radial_gradient_precision.cpp` | ✅ | `RadialGradientPrecisionGM.kt` | — |
| `rasterhandleallocator.cpp` | ❌ | — | — |
| `readpixels.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `recordopts.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rect_poly_stroke.cpp` | ✅ | `RectPolyStrokeGM.kt` | — |
| `rectangletexture.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rendertomipmappedyuvimageplanes.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `repeated_bitmap.cpp` | ✅ | `RepeatedBitmapGM.kt` | — |
| `resizeimagefilter.cpp` | ❌ | — | — |
| `rippleshadergm.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `roundrects.cpp` | ✅ | `RoundRectGM.kt` | — |
| `rrect.cpp` | ✅ | `RRectGM.kt` | — |
| `rrectclipdrawpaint.cpp` | ✅ | `RRectClipDrawPaintGM.kt` | — |
| `rrects.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rsxtext.cpp` | 🚧 | — | `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` |
| `runtimecolorfilter.cpp` | ✅ | `RuntimeColorFilterGM.kt` | — |
| `runtimefunctions.cpp` | ✅ | `RuntimeFunctionsGM.kt` | — |
| `runtimeimagefilter.cpp` | ✅ | `RtifDistortGM.kt`, `RtifUnsharpGM.kt` | — |
| `runtimeintrinsics.cpp` | ✅ | `RuntimeIntrinsicsCommonGM.kt`, `RuntimeIntrinsicsExponentialGM.kt`, `RuntimeIntrinsicsGeometricGM.kt`, `RuntimeIntrinsicsMatrixGM.kt`, `RuntimeIntrinsicsRelationalGM.kt`, `RuntimeIntrinsicsTrigGM.kt` | — |
| `runtimeshader.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `samplerstress.cpp` | ❌ | — | — |
| `savelayer.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `scaledemoji.cpp` | ❌ | — | — |
| `scaledemoji_rendering.cpp` | ❌ | — | — |
| `scaledrects.cpp` | ✅ | `ClipLargeRectGM.kt`, `ScaledRectsGM.kt` | — |
| `scaledstrokes.cpp` | ✅ | `ScaledStrokesGM.kt` | — |
| `shadermaskfilter.cpp` | 🚧 | — | `SkShaderMaskFilter` |
| `shaderpath.cpp` | ✅ | `ShaderPathGM.kt` | — |
| `shadertext3.cpp` | ❌ | — | — |
| `shadowutils.cpp` | ❌ | — | — |
| `shallowgradient.cpp` | ❌ | — | — |
| `shapes.cpp` | ✅ | `InnerShapesGM.kt`, `SimpleShapesGM.kt` | — |
| `sharedcorners.cpp` | ❌ | — | — |
| `showmiplevels.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `simpleaaclip.cpp` | 🚧 | — | `SkRegion::*` API |
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
| `slug.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `smallarc.cpp` | ✅ | `SmallArcGM.kt` | — |
| `smallcircles.cpp` | ✅ | `SmallCirclesGM.kt` | — |
| `smallpaths.cpp` | ✅ | `SmallPathsGM.kt` | — |
| `spritebitmap.cpp` | ✅ | `SpriteBitmapGM.kt` | — |
| `srcmode.cpp` | ❌ | — | — |
| `srgb.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `stlouisarch.cpp` | ✅ | `StLouisArchGM.kt` | — |
| `stringart.cpp` | ✅ | `StringArtGM.kt` | — |
| `stroke_rect_shader.cpp` | ✅ | `StrokeRectShaderGM.kt` | — |
| `strokedlines.cpp` | ❌ | — | — |
| `strokefill.cpp` | ✅ | `Bug339297GM.kt`, `Bug6987GM.kt` | — |
| `strokerect.cpp` | ❌ | — | — |
| `strokerect_anisotropic.cpp` | ✅ | `StrokerectAnisotropicGM.kt` | — |
| `strokerects.cpp` | ✅ | `StrokeRectsGM.kt` | — |
| `strokes.cpp` | ✅ | `CubicStrokeGM.kt`, `InnerJoinGeometryGM.kt`, `QuadCapGM.kt`, `Skbug12244GM.kt`, `StrokesGM.kt`, `Strokes2GM.kt`, `Strokes4GM.kt`, `TeenyStrokesGM.kt`, `ZeroLineStrokeGM.kt` | — |
| `stroketext.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `subsetshader.cpp` | ✅ | `BitmapSubsetShaderGM.kt` | — |
| `surface.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `swizzle.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `tablecolorfilter.cpp` | ❌ | — | — |
| `tablemaskfilter.cpp` | 🚧 | — | `SkTableMaskFilter` |
| `tallstretchedbitmaps.cpp` | ❌ | — | — |
| `testgradient.cpp` | ✅ | `TestGradientGM.kt` | — |
| `texelsubset.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `text_scale_skew.cpp` | ✅ | `TextScaleSkewGM.kt` | — |
| `textblob.cpp` | ✅ | `TextBlobGM.kt` | — |
| `textblobblockreordering.cpp` | ✅ | `TextBlobBlockReorderingGM.kt` | — |
| `textblobcolortrans.cpp` | ✅ | `TextBlobColorTransGM.kt` | — |
| `textblobgeometrychange.cpp` | ✅ | `TextBlobGeometryChangeGM.kt` | — |
| `textblobmixedsizes.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `textblobrandomfont.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `textblobshader.cpp` | ✅ | `TextBlobShaderGM.kt` | — |
| `textblobtransforms.cpp` | ❌ | — | — |
| `textblobuseaftergpufree.cpp` | ✅ | `TextBlobUseAfterGpuFreeGM.kt` | — |
| `texteffects.cpp` | ❌ | — | — |
| `thinconcavepaths.cpp` | ✅ | `ThinConcavePathsGM.kt` | — |
| `thinrects.cpp` | ✅ | `ThinRectsGM.kt` | — |
| `thinstrokedrects.cpp` | ✅ | `ThinStrokedRectsGM.kt` | — |
| `tiledscaledbitmap.cpp` | ✅ | `TiledScaledBitmapGM.kt` | — |
| `tileimagefilter.cpp` | ❌ | — | — |
| `tilemodes.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `tilemodes_alpha.cpp` | ✅ | `TilemodesAlphaGM.kt` | — |
| `tilemodes_scaled.cpp` | ❌ | — | — |
| `tinybitmap.cpp` | ✅ | `TinyBitmapGM.kt` | — |
| `transparency.cpp` | ✅ | `TransparencyCheckGM.kt` | — |
| `trickycubicstrokes.cpp` | ✅ | `TrickyCubicStrokesGM.kt` | — |
| `typeface.cpp` | ❌ | — | — |
| `unpremul.cpp` | ❌ | — | — |
| `userfont.cpp` | ❌ | — | — |
| `variedtext.cpp` | ❌ | — | — |
| `vertices.cpp` | ✅ | `VerticesCollapsedGM.kt`, `VerticesPerspectiveGM.kt` | — |
| `verylargebitmap.cpp` | ❌ | — | — |
| `video_decoder.cpp` | ❌ | — | — |
| `wacky_yuv_formats.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `widebuttcaps.cpp` | ✅ | `WideButtCapsGM.kt` | — |
| `windowrectangles.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `workingspace.cpp` | ❌ | — | — |
| `xfermodeimagefilter.cpp` | ❌ | — | — |
| `xfermodes.cpp` | ✅ | `XfermodesGM.kt` | — |
| `xfermodes2.cpp` | ❌ | — | — |
| `xfermodes3.cpp` | ❌ | — | — |
| `ycbcrimage.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `yuv420_odd_dim.cpp` | 🚧 | — | Chargement d'images (ressource non en `resources/images/`) |
| `yuvtorgbsubset.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |

## GMs Kotlin sans `.cpp` correspondant

Ces 30 fichiers `*GM.kt` n'ont pas pu être rattachés à un `.cpp` du tree de référence (probablement issus d'une version Skia plus récente, ou correspondant à des bugs/crbug pas encore présents en upstream local) :

- `B340982297GM.kt`
- `BlurLargeRRectsGM.kt`
- `ConicalGradients2ptInsideGM.kt`
- `ConicalGradients2ptOutsideGM.kt`
- `CornerDiscretePathEffectGM.kt`
- `Crbug1472747GM.kt`
- `Crbug640176GM.kt`
- `Crbug888453GM.kt`
- `ImageFilterBlurDropShadowGM.kt`
- `ImageFilterOffsetGM.kt`
- `PathArcToSkbug9077GM.kt`
- `PathCapsFillsGridGM.kt`
- `PathHugeCrbug800804GM.kt`
- `PathInvFillGM.kt`
- `PathOpsSkbug10155GM.kt`
- `PathSkbug11859GM.kt`
- `PathSkbug11886GM.kt`
- `PointsMaskFilterGM.kt`
- `ShallowGradientConicalGM.kt`
- `ShallowGradientLinearGM.kt`
- `ShallowGradientLinearNoditherGM.kt`
- `ShallowGradientRadialGM.kt`
- `ShallowGradientRadialNoditherGM.kt`
- `ShallowGradientSweepGM.kt`
- `Skbug13047GM.kt`
- `StrokeRectsRotatedGM.kt`
- `StrokerectAnisotropic5408GM.kt`
- `ThinRoundRectsGM.kt`
- `TrickyCubicStrokesLargeRadiusGM.kt`
- `ZeroControlStrokeGM.kt`
