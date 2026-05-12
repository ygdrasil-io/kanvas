# Plan de portage des GM Skia → kanvas-skia

Statut du portage des Graphics Modules (GM) C++ de Skia vers `kanvas-skia/src/main/kotlin/org/skia/tests/`.

**Source de référence** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm/` (437 fichiers `.cpp`)
**Cible** : `kanvas-skia/src/main/kotlin/org/skia/tests/*GM.kt` (310 GMs Kotlin sur `origin/master`)
**Snapshot** : post-merge **13 phases d'API** (G1-G10 + G9a/b) + **H1 complet** (10 GMs image-asset) + **H3 wave 1** (9 cpps, 11 variants)

## Résumé

| Statut | Fichiers `.cpp` | Pourcentage |
|---|---:|---:|
| ✅ Porté (≥ 1 `*GM.kt` rattaché) | 241 | 55% |
| 🚧 Bloqué (API manquante / GPU-only / asset / PNG ref) | 98 | 22% |
| ❌ Non tenté (potentiellement portable) | 98 | 22% |
| **Total** | **437** | **100%** |

GMs Kotlin sans correspondance dans le tree de référence (probablement issus d'une version Skia plus récente) : **30**.

## Progression

| Étape | `.cpp` portés | Couverture |
|---|---:|---:|
| Snapshot initial (post-D2) | 148 / 437 | 34% |
| Vagues A→F + bulk-mark | 166 / 437 | 38% |
| Phases G1, G3, G4a, G7, G9a, G9b + vagues I-A/I-B | 184 / 437 | 42% |
| Phases G2, G4b, G5 + vagues K/L | 208 / 437 | 47% |
| Phases G4c, G6, G8 + vagues K-bis, L | 215 / 437 | 49% |
| Phases G10 + H1 (assets + 4 ports) | 215 / 437 | 49% |
| H1 complet (10 ports) + H3 wave 1 (9 ports) | 232 / 437 | 53% |
| **H3 wave 2 (9 ports + 1 skip SkM44)** | **241 / 437** | **55%** |

**Gain net** : +93 GMs en 3 jours (de 34% à 55% de couverture).

## Phases d'API — toutes mergées ✅

| Phase | API |
|---|---|
| **G1** — Image loading | `ToolUtils.GetResourceAsImage`/`GetResourceAsBitmap`/`MakeTextureImage`/`draw_checkerboard` |
| **G2** — Bicubic sampling | `SkCubicResampler` + cubic `SkSamplingOptions` ; câblage `SkBitmapShader`/`SkBitmapDevice` |
| **G3** — Picture shader | `SkPicture.makeShader(tileX, tileY, filter, localMatrix?, tile?)` |
| **G4a** — Alpha8 | `kAlpha_8` backing array dans `SkBitmap` |
| **G4b** — BGRA8888 | `kBGRA_8888` accepté par `SkBitmap.allocPixels` |
| **G4c** — ARGB4444 | `SkImageInfo.Make4444` + tests (backing pré-existant C5) |
| **G5** — Edge AA quad | `QuadAAFlags` enum + `SkCanvas.experimental_DrawEdgeAAQuad` |
| **G6** — SaveLayerRec backdrop | `SaveLayerRec` w/ `backdrop` + `SkImageFilters.Blur(tileMode, cropRect)` |
| **G7** — SkBitmapDevice gaps | `compositeFrom` applique `paint.colorFilter` ; `drawPaint` route via saveLayer quand `paint.imageFilter` |
| **G8** — SkRegion + clipRegion | `SkRegion.translate` + `SkCanvas.clipRegion` (coords canvas/global, gère layer origin) |
| **G9a** — SkParsePath | `SkParsePath.FromSVGString` (12 commandes SVG, arc via `arcTo`) |
| **G9b** — OverdrawColorFilter | `SkOverdrawColorFilter.MakeWithSkColors(IntArray(6))` |
| **G10** — Mipmap/Aniso | `SkImage.withDefaultMipmaps`, `SkSamplingOptions.Aniso`, LOD selection raster |
| **H1** — Asset copy + ports | 16 PNG/JPG copiés depuis upstream `resources/images/` + 10 ports (4 initial + 3 H1-A + 5 H1-B, 2 skip) |
| **H3 wave 1** — Ports raster purs | 9 cpps portés (color4f, dash*, degenerate, emboss, blurcircles2, blur_ignore_xform, complexclip2, convexpolyclip), 1 skip (closedcappedhairlines : pas de PNG ref) |

## Bloqueurs restants — catalogue par ROI

Pondération par nombre de `.cpp` impactés.

| Bloqueur | `.cpp` | Type |
|---|---:|---|
| GPU-only (`DEF_SIMPLE_GPU_GM`, pas de PNG de référence raster) | 62 | **n/a** (intransposable) |
| `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` | 6 | API publique |
| Encodeurs image (`SkJpegEncoder`/`SkPngEncoder`/`SkWebpEncoder`) | 5 | API publique |
| `SkImage::makeSubset` | 4 | API publique |
| `SkCanvas::clipShader` (per-pixel clip) | 4 | API publique |
| `SkShaderMaskFilter::Make` | 2 | API publique |
| `SkImageGenerator` / `SkImages::DeferredFromGenerator` | 2 | API publique |
| `SkColorFilters::LinearToSRGBGamma` / `SRGBToLinearGamma` | 2 | API publique |
| `SkCanvas::drawRegion` (distinct de `clipRegion`) | 2 | API publique |
| `SkTableMaskFilter` | 1 | API publique |
| `SkShaders::CoordClamp` | 1 | API publique |
| `SkCanvas::drawImageNine` (9-patch raster) | 1 | API publique |
| YUV multi-plane (`tools/gpu/YUVUtils`) | 1 | API publique |
| GM Bazel-only | 1 | n/a |

**Net** : 62 GMs GPU-only intransposables, **32 GMs** sur 13 APIs publiques à ajouter (cohorte H2 ci-dessous).

## Petits gaps de fidélité révélés par H1

Pas des APIs manquantes — des comportements existants qui dégradent la similarité de ports déjà livrés. À traiter en suivi :

- **`drawImage` sur source A8 ne module pas `paint.color`** — le tinting est absent, le rendu reste gris uniforme. Bloque `alpha_image`, `alpha_image_alpha_tint`.
- **`canvas.clear(c)` dans `saveLayer` court-circuite sur device root** au lieu de la layer. Bloque `colorfilterimagefilter_layer`.
- **`MatrixConvolution` sans paramètre `crop`** — sortie structurellement correcte mais déborde. Bloque `imagefilter_convolve_subset`.
- **Fast-path `Blur` à σ très grand** (≥ 500) drop la Gaussienne et renvoie l'identité. Bloque `BlurBigSigma`.
- **`SkBitmap.setPixel` re-premultiplie par défaut**, défait l'intention pass-through de certains GMs. Bloque `image_out_of_gamut`.

## Plan résiduel

### Phase H2 — 13 APIs publiques unitaires (~32 GMs débloquables)

Chaque sous-tâche est petite (½-1 jour). Toutes parallélisables car touchent des fichiers distincts.

| Sous-tâche | ROI | Fichier(s) impacté(s) |
|---|---:|---|
| H2.1 — `SkShader.makeWithLocalMatrix` + `SkImageFilter.makeWithLocalMatrix` | 6 | `SkShader.kt`, `SkImageFilter.kt` |
| H2.2 — Encodeurs `SkJpegEncoder`/`SkPngEncoder`/`SkWebpEncoder` | 5 | nouveau package `org.skia.encode` |
| H2.3 — `SkImage.makeSubset` | 4 | `SkImage.kt` |
| H2.4 — `SkCanvas.clipShader(shader, op)` | 4 | `SkCanvas.kt`, clip-stack |
| H2.5 — `SkShaderMaskFilter.Make(shader)` | 2 | nouvelle classe |
| H2.6 — `SkImageGenerator` + `SkImages.DeferredFromGenerator` | 2 | nouveau package |
| H2.7 — `SkColorFilters.LinearToSRGBGamma` / `SRGBToLinearGamma` | 2 | `SkColorFilters.kt` |
| H2.8 — `SkCanvas.drawRegion(region, paint)` | 2 | `SkCanvas.kt` |
| H2.9 — `SkTableMaskFilter.Create(ByteArray(256))` | 1 | nouvelle classe |
| H2.10 — `SkShaders.CoordClamp(shader, rect)` | 1 | `SkShaders.kt` |
| H2.11 — `SkCanvas.drawImageNine(image, center, dstRect)` | 1 | `SkCanvas.kt`, 9-patch raster |
| H2.12 — YUV multi-plane support | 1 | beaucoup |
| H2.13 — `SkMaskFilter.MakeBlur(style, sigma, respectCTM)` overload | 3 | `SkMaskFilter.kt` (BlurIgnoreXform → ~99 %) |
| H2.14 — `SkImage.makeColorSpace` + `SkCanvas.makeSurface(info)` + `imageInfo().colorSpace()` | 1 | `SkImage.kt`, `SkCanvas.kt` |
| H2.15 — `SkMipmapBuilder` + `SkImage.attachTo` | 1 | nouvelle classe |
| H2.16 — `SkShaders.Color(c)` (constant-color shader factory) | 1+ | `SkShaders.kt` |
| H2.17 — `SkColorFilters.Lighting(mul, add)` factory direct | 1+ | `SkColorFilters.kt` |
| H2.18 — `SkBitmap` color types `kRGB_565`, `kGray_8` | 1+ | `SkBitmap.kt` |
| H2.19 — `ToolUtils.copy_to` + `create_checkerboard_image` helpers | nombreux | `ToolUtils.kt` |
| H2.20 — `SkM44` (matrice 4×4 perspective) + `SkCanvas` integration | 1+ | nouvelle classe + `SkCanvas.kt` |
| H2.21 — `SkCanvas` rotation/perspective `drawImageRect` (CTM non axis-aligned) | 1+ | `SkBitmapDevice.kt` (H1.5 lié) |
| H2.22 — `androidFramework_setDeviceClipRestriction` + `SkCanvasPriv::ResetClip` | 1 | `SkCanvas.kt` |
| H2.23 — `SkCanvas.drawAtlas` colors + blendMode (Phase I5.3 deferred) | 1+ | `SkBitmapDevice.kt` |
| H2.24 — `SkImageFilters.DisplacementMap(..., cropRect)` 6-arg overload | 1 | `SkImageFilters.kt` |
| H2.25 — `ToolUtils.makeSurface(canvas, info)` colour-space-matched helper | nombreux | `ToolUtils.kt` |

### Phase H1.5 — Suivis fidélité (qualité de ports)

Les 5 petits gaps listés ci-dessus. Chacun ½ journée. Effort cumulé : 2-3 jours, gain en similarité sur ~10+ ports déjà livrés.

### Phase H3 — Vagues de ports continues (~128 GMs non tentés)

Sortie d'agents 2×5 en parallèle, triage par taille de cpp et catégorie. Beaucoup devraient passer maintenant que toutes les APIs principales sont présentes. Certains révéleront de nouveaux blockers à intégrer au catalogue au fil de l'eau.

### Hors scope définitif

- **62 GMs GPU-only** (`DEF_SIMPLE_GPU_GM`, `GrFragmentProcessor`, etc.) — pas de PNG de référence raster upstream, rien à valider. Resteront ❌ marqués GPU-only.
- **1 GM Bazel-only** (`hello_bazel_world`).

## Méthodologie

- ✅ **Porté** : ≥ 1 `*GM.kt` correspond au `.cpp` après normalisation, ou contient un `DEF_SIMPLE_GM`/`DEF_GM` du `.cpp`. La conformité visuelle n'est pas garantie — voir scores dans `kanvas-skia/test-similarity-report.md`.
- 🚧 **Bloqué** : scan automatique a détecté un bloqueur dur (GPU-only, API manquante, asset absent, etc.).
- ❌ **Non tenté** : aucun bloqueur détecté par le scan. À porter en vagues continues.

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
| `all_bitmap_configs.cpp` | ✅ | `AllBitmapConfigsGM.kt` | — (partiel : RGB565/Gray8 absents) |
| `alpha_image.cpp` | ✅ | `AlphaImageGM.kt` | — (partiel : H1.5 tint A8) |
| `alphagradients.cpp` | ✅ | `AlphaGradientsGM.kt` | — |
| `analytic_gradients.cpp` | ✅ | `AnalyticGradientShaderGM.kt` | — |
| `androidblendmodes.cpp` | ✅ | `AndroidBlendModesGM.kt` | — |
| `animated_gif.cpp` | ❌ | — | — |
| `animated_image_orientation.cpp` | ❌ | — | — |
| `animatedimageblurs.cpp` | ❌ | — | — |
| `anisotropic.cpp` | ✅ | `AnisoMipsGM.kt`, `AnisotropicImageScaleAnisoGM.kt`, `AnisotropicImageScaleLinearGM.kt`, `AnisotropicImageScaleMipGM.kt`, `AnisotropicMipGM.kt` | — |
| `annotated_text.cpp` | ✅ | `AnnotatedTextGM.kt` | — |
| `arcofzorro.cpp` | ✅ | `ArcOfZorroGM.kt` | — |
| `arcto.cpp` | ✅ | `ArcToGM.kt`, `Bug593049GM.kt` | — |
| `arithmode.cpp` | ✅ | `ArithmodeGM.kt`, `ArithmodeBlenderGM.kt` | — |
| `asyncrescaleandread.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
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
| `bitmapshader.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `bitmaptiled.cpp` | ✅ | `BitmapTiledGM.kt` | — |
| `bleed.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `blurcircles.cpp` | ✅ | `BlurCirclesGM.kt` | — |
| `blurcircles2.cpp` | ✅ | `BlurCircles2GM.kt` | — |
| `blurignorexform.cpp` | ✅ | `BlurIgnoreXformGM.kt` (3 variants) | — (H2 : `MakeBlur(respectCTM=false)`) |
| `blurimagevmask.cpp` | ✅ | `BlurImageGM.kt`, `BlurImageVMaskGM.kt` | — |
| `blurpositioning.cpp` | ✅ | `BlurPositioningGM.kt` | — |
| `blurquickreject.cpp` | ✅ | `BlurQuickRejectGM.kt` | — |
| `blurrect.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `blurredclippedcircle.cpp` | ✅ | `BlurredClippedCircleGM.kt` | — |
| `blurroundrect.cpp` | ✅ | `SimpleBlurRoundRectGM.kt` | — |
| `blurs.cpp` | ✅ | `Blur2RectsGM.kt`, `Blur2RectsNonNinepatchGM.kt`, `BlurDrawImageGM.kt`, `BlurSmallSigmaGM.kt` | — |
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
| `clipdrawdraw.cpp` | ✅ | `ClipDrawDrawGM.kt`, `ClipRegionGM.kt` | — |
| `clippedbitmapshaders.cpp` | ❌ | — | — |
| `clipshader.cpp` | 🚧 | — | `SkCanvas::clipShader` |
| `clockwise.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `closedcappedhairlines.cpp` | 🚧 | — | Pas de PNG de référence dans `original-888/` |
| `collapsepaths.cpp` | ✅ | `CollapsePathsGM.kt` | — |
| `color4f.cpp` | ✅ | `Color4fGM.kt` | — |
| `coloremoji.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `coloremoji_blendmodes.cpp` | ❌ | — | — |
| `colorfilteralpha8.cpp` | ✅ | `ColorFilterAlpha8GM.kt` | — |
| `colorfilterimagefilter.cpp` | ✅ | `ColorFilterImageFilterGM.kt` | — |
| `colorfilters.cpp` | ✅ | `ColorFiltersGM.kt` (lightingcolorfilter) | — |
| `colormatrix.cpp` | ✅ | `ColorMatrixGM.kt` | — |
| `colorspace.cpp` | 🚧 | — | `SkImage.makeColorSpace` + `SkCanvas.makeSurface(info)` absents |
| `colorwheel.cpp` | ✅ | `ColorWheelNativeGM.kt` | — |
| `colrv1.cpp` | ❌ | — | — |
| `complexclip.cpp` | 🚧 | — | `SkCanvas::clipShader` |
| `complexclip2.cpp` | ✅ | `ComplexClip2GM.kt` (6 variants rect/rrect/path × bw/aa) | — |
| `complexclip3.cpp` | ✅ | `ComplexClip3GM.kt` (simple + complex) | — |
| `complexclip4.cpp` | ✅ | `ComplexClip4GM.kt` (bw + aa, approx) | — (manque `setDeviceClipRestriction` + `ResetClip`) |
| `complexclip_blur_tiled.cpp` | ✅ | `ComplexClipBlurTiledGM.kt` | — |
| `composecolorfilter.cpp` | ✅ | `ComposeColorFilterGM.kt` | — |
| `composeshader.cpp` | 🚧 | — | `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` |
| `compositor_quads.cpp` | 🚧 | — | YUV multi-plane (`tools/gpu/YUVUtils`) |
| `compressed_textures.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `concavepaths.cpp` | ✅ | `ConcavePathsGM.kt` | — |
| `conicpaths.cpp` | ✅ | `ArcCircleGapGM.kt`, `ConicPathsGM.kt`, `LargeCircleGM.kt`, `LargeOvalsGM.kt` | — |
| `constcolorprocessor.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `convex_all_line_paths.cpp` | ✅ | `ConvexLineOnlyPathsGM.kt` | — |
| `convexpaths.cpp` | ✅ | `ConvexPathsGM.kt` | — |
| `convexpolyclip.cpp` | ✅ | `ConvexPolyClipGM.kt` | — |
| `convexpolyeffect.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `coordclampshader.cpp` | 🚧 | — | `SkShaders::CoordClamp` |
| `copy_to_4444.cpp` | ✅ | `CopyTo4444GM.kt` | — |
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
| `crbug_224618.cpp` | 🚧 | — | `SkM44` (4×4 perspective matrix) + perspective `drawImageRect` |
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
| `crop_imagefilter.cpp` | 🚧 | — | `SkImage::makeSubset` |
| `croppedrects.cpp` | ✅ | `CroppedRectsGM.kt` | — |
| `crosscontextimage.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `cubicpaths.cpp` | ✅ | `Bug5099GM.kt`, `Bug6083GM.kt`, `ClippedCubicGM.kt`, `ClippedCubic2GM.kt`, `CubicClosePathGM.kt`, `CubicPathGM.kt`, `CubicPathShaderGM.kt` | — |
| `daa.cpp` | ✅ | `DaaGM.kt` | — |
| `dashcircle.cpp` | ✅ | `DashCircleGM.kt` | — |
| `dashcubics.cpp` | ✅ | `DashCubicsGM.kt` | — |
| `dashing.cpp` | ✅ | `DashingGM.kt`, `LongWavyLineGM.kt`, `PathEffectGM.kt` | — |
| `degeneratesegments.cpp` | ✅ | `DegenerateSegmentsGM.kt` | — |
| `destcolor.cpp` | ✅ | `DestColorGM.kt` | — |
| `dftext.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `dftext_blob_persp.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `discard.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `displacement.cpp` | ✅ | `DisplacementGM.kt` | — |
| `distantclip.cpp` | ✅ | `DistantClipGM.kt` | — |
| `draw_bitmap_rect_skbug4374.cpp` | ✅ | `DrawBitmapRectSkbug4734GM.kt` | — |
| `drawable.cpp` | ✅ | `DrawableGM.kt` | — |
| `drawatlas.cpp` | ✅ | `DrawAtlasGM.kt` | — |
| `drawatlascolor.cpp` | ✅ | `DrawAtlasColorGM.kt` | — (partiel : tint couleur ignoré, Phase I5.3) |
| `drawbitmaprect.cpp` | 🚧 | — | `SkImage::makeSubset` |
| `drawglyphs.cpp` | ✅ | `DrawGlyphsGM.kt` | — |
| `drawimageset.cpp` | 🚧 | — | `SkImage::makeSubset` |
| `drawlines_with_local_matrix.cpp` | ✅ | `DrawlinesWithLocalMatrixGM.kt` | — |
| `drawminibitmaprect.cpp` | ✅ | `DrawMiniBitmapRectGM.kt` (bw + aa) | — |
| `drawquadset.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `drawregion.cpp` | 🚧 | — | `SkCanvas::drawRegion` |
| `drawregionmodes.cpp` | 🚧 | — | `SkCanvas::drawRegion` |
| `dropshadowimagefilter.cpp` | ✅ | `DropShadowImageFilterGM.kt` | — |
| `drrect.cpp` | ✅ | `DRRectGM.kt` | — |
| `drrect_small_inner.cpp` | ✅ | `DRRectSmallInnerGM.kt` | — |
| `dstreadshuffle.cpp` | ✅ | `DstReadShuffleGM.kt` | — |
| `ducky_yuv_blend.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `emboss.cpp` | ✅ | `EmbossGM.kt` | — |
| `emptypath.cpp` | ✅ | `EmptyPathGM.kt` | — |
| `emptyshader.cpp` | ✅ | `EmptyShaderGM.kt` | — |
| `encode.cpp` | ✅ | `EncodeGM.kt` | — |
| `encode_alpha_jpeg.cpp` | 🚧 | — | Encodeurs image (`SkJpegEncoder`/`SkPngEncoder`/`SkWebpEncoder`) |
| `encode_color_types.cpp` | 🚧 | — | Encodeurs image (`SkJpegEncoder`/`SkPngEncoder`/`SkWebpEncoder`) |
| `encode_platform.cpp` | 🚧 | — | Encodeurs image (`SkJpegEncoder`/`SkPngEncoder`/`SkWebpEncoder`) |
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
| `fontations.cpp` | ❌ | — | — |
| `fontations_ft_compare.cpp` | ❌ | — | — |
| `fontcache.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fontmgr.cpp` | ❌ | — | — |
| `fontregen.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fontscaler.cpp` | ✅ | `FontScalerGM.kt` | — |
| `fontscalerdistortable.cpp` | ❌ | — | — |
| `fp_sample_chaining.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fpcoordinateoverride.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fwidth_squircle.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `gammatext.cpp` | ❌ | — | — |
| `getpostextpath.cpp` | ✅ | `GetPosTextPathGM.kt` | — |
| `giantbitmap.cpp` | ❌ | — | — |
| `glyph_pos.cpp` | ❌ | — | — |
| `gm.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `gpu_blur_utils.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `gradient_dirty_laundry.cpp` | ❌ | — | — |
| `gradient_matrix.cpp` | ❌ | — | — |
| `gradients.cpp` | ✅ | `ClampedGradientsGM.kt`, `GradientsGM.kt`, `RgbwSweepGradientGM.kt`, `SmallColorStopGM.kt`, `SweepTilingGM.kt` | — |
| `gradients_2pt_conical.cpp` | ❌ | — | — |
| `gradients_degenerate.cpp` | ❌ | — | — |
| `gradients_no_texture.cpp` | ❌ | — | — |
| `gradtext.cpp` | ✅ | `ChromeGradTextGM.kt`, `GradTextGM.kt` | — |
| `graphite_replay.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `graphitestart.cpp` | 🚧 | — | `SkColorFilters::LinearToSRGBGamma`/`SRGBToLinearGamma` |
| `grayscalejpg.cpp` | ✅ | `GrayscaleJpgGM.kt` | — |
| `hairlines.cpp` | ✅ | `HairlineSubdivGM.kt`, `HairlinesGM.kt`, `SquareHairGM.kt` | — |
| `hairmodes.cpp` | ✅ | `HairModesGM.kt` | — |
| `hardstop_gradients.cpp` | ✅ | `HardstopGradientShaderGM.kt` | — |
| `hardstop_gradients_many.cpp` | ✅ | `HardstopGradientsManyGM.kt` | — |
| `hdr_pip_blur.cpp` | ❌ | — | — |
| `hello_bazel_world.cpp` | 🚧 | — | GM Bazel-only (pas de PNG de référence) |
| `highcontrastfilter.cpp` | ❌ | — | — |
| `hittestpath.cpp` | ❌ | — | — |
| `hsl.cpp` | ✅ | `HSLGM.kt` | — |
| `hugepath.cpp` | ❌ | — | — |
| `image.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `image_pict.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `image_shader.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imageblur.cpp` | ✅ | `ImageBlurGM.kt` | — |
| `imageblur2.cpp` | ❌ | — | — |
| `imageblurclampmode.cpp` | ❌ | — | — |
| `imageblurrepeatmode.cpp` | ❌ | — | — |
| `imageblurtiled.cpp` | ✅ | `ImageBlurTiledGM.kt` | — |
| `imagedither.cpp` | ✅ | `ImageDitherGM.kt` | — |
| `imagefilters.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imagefiltersbase.cpp` | ❌ | — | — |
| `imagefiltersclipped.cpp` | ✅ | `ImageFiltersClippedGM.kt` | — |
| `imagefilterscropexpand.cpp` | ❌ | — | — |
| `imagefilterscropped.cpp` | ❌ | — | — |
| `imagefiltersgraph.cpp` | ❌ | — | — |
| `imagefiltersscaled.cpp` | ❌ | — | — |
| `imagefiltersstroked.cpp` | ❌ | — | — |
| `imagefilterstransformed.cpp` | 🚧 | — | `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` |
| `imagefiltersunpremul.cpp` | ✅ | `ImageFiltersUnpremulGM.kt` | — |
| `imagefromyuvtextures.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imagemagnifier.cpp` | ❌ | — | — |
| `imagemakewithfilter.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imagemasksubset.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
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
| `localmatrixshader.cpp` | 🚧 | — | `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` |
| `lumafilter.cpp` | ✅ | `LumaFilterGM.kt` | — |
| `luminosity.cpp` | ✅ | `LuminosityOverflowGM.kt` | — |
| `mac_aa_explorer.cpp` | ❌ | — | — |
| `make_raster_image.cpp` | ✅ | `MakeRasterImageGM.kt` | — |
| `makecolorspace.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `mandoline.cpp` | ❌ | — | — |
| `manypathatlases.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `manypaths.cpp` | ✅ | `ManyCirclesGM.kt`, `ManyRRectsGM.kt` | — |
| `matrixconvolution.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `matriximagefilter.cpp` | ✅ | `MatrixImageFilterGM.kt` | — |
| `mesh.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
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
| `orientation.cpp` | 🚧 | — | `SkImageGenerator` / `DeferredFromGenerator` |
| `ovals.cpp` | ✅ | `OvalGM.kt` | — |
| `overdrawcanvas.cpp` | ❌ | — | — |
| `overdrawcolorfilter.cpp` | ✅ | `OverdrawColorFilterGM.kt` | — |
| `overstroke.cpp` | ❌ | — | — |
| `p3.cpp` | ❌ | — | — |
| `palette.cpp` | ❌ | — | — |
| `patch.cpp` | ✅ | `PatchAlphaTestGM.kt`, `PatchPrimitiveGM.kt` | — |
| `path_stroke_with_zero_length.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `patharcto.cpp` | ❌ | — | — |
| `pathcontourstart.cpp` | ✅ | `ContourStartGM.kt` | — |
| `patheffects.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `pathfill.cpp` | ✅ | `Bug7792GM.kt`, `RotatedCubicPathGM.kt` | — |
| `pathinterior.cpp` | ✅ | `PathInteriorGM.kt` | — |
| `pathmaskcache.cpp` | ✅ | `PathMaskCacheGM.kt` | — |
| `pathmeasure.cpp` | ❌ | — | — |
| `pathopsblend.cpp` | ✅ | `PathOpsBlendGM.kt` | — |
| `pathopsinverse.cpp` | ✅ | `PathOpsInverseGM.kt` | — |
| `pathreverse.cpp` | ✅ | `PathReverseGM.kt` | — |
| `pdf_never_embed.cpp` | ❌ | — | — |
| `perlinnoise.cpp` | ✅ | `PerlinNoiseGM.kt`, `PerlinNoiseRotatedGM.kt` | — |
| `perspimages.cpp` | 🚧 | — | `SkImage::makeSubset` |
| `perspshaders.cpp` | ✅ | `PerspShadersGM.kt` | — (H1.5 : `drawImage` sous perspective drop) |
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
| `readpixels.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `recordopts.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rect_poly_stroke.cpp` | ✅ | `RectPolyStrokeGM.kt` | — |
| `rectangletexture.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rendertomipmappedyuvimageplanes.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `repeated_bitmap.cpp` | ✅ | `RepeatedBitmapGM.kt` | — |
| `resizeimagefilter.cpp` | ❌ | — | — |
| `rippleshadergm.cpp` | ❌ | — | — |
| `roundrects.cpp` | ✅ | `RoundRectGM.kt` | — |
| `rrect.cpp` | ✅ | `RRectGM.kt` | — |
| `rrectclipdrawpaint.cpp` | ✅ | `RRectClipDrawPaintGM.kt` | — |
| `rrects.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rsxtext.cpp` | 🚧 | — | `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` |
| `runtimecolorfilter.cpp` | ✅ | `RuntimeColorFilterGM.kt` | — |
| `runtimefunctions.cpp` | ✅ | `RuntimeFunctionsGM.kt` | — |
| `runtimeimagefilter.cpp` | ✅ | `RtifDistortGM.kt`, `RtifUnsharpGM.kt` | — |
| `runtimeintrinsics.cpp` | ✅ | `RuntimeIntrinsicsCommonGM.kt`, `RuntimeIntrinsicsExponentialGM.kt`, `RuntimeIntrinsicsGeometricGM.kt`, `RuntimeIntrinsicsMatrixGM.kt`, `RuntimeIntrinsicsRelationalGM.kt`, `RuntimeIntrinsicsTrigGM.kt` | — |
| `runtimeshader.cpp` | 🚧 | — | `SkCanvas::clipShader` |
| `samplerstress.cpp` | ❌ | — | — |
| `savelayer.cpp` | 🚧 | — | `SkShaderMaskFilter` |
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
| `showmiplevels.cpp` | 🚧 | — | `SkMipmapBuilder` + `SkImage.attachTo` absents |
| `simpleaaclip.cpp` | ❌ | — | — |
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
| `srgb.cpp` | 🚧 | — | `SkColorFilters::LinearToSRGBGamma`/`SRGBToLinearGamma` |
| `stlouisarch.cpp` | ✅ | `StLouisArchGM.kt` | — |
| `stringart.cpp` | ✅ | `StringArtGM.kt` | — |
| `stroke_rect_shader.cpp` | ✅ | `StrokeRectShaderGM.kt` | — |
| `strokedlines.cpp` | ❌ | — | — |
| `strokefill.cpp` | ✅ | `Bug339297GM.kt`, `Bug6987GM.kt` | — |
| `strokerect.cpp` | ❌ | — | — |
| `strokerect_anisotropic.cpp` | ✅ | `StrokerectAnisotropicGM.kt` | — |
| `strokerects.cpp` | ✅ | `StrokeRectsGM.kt` | — |
| `strokes.cpp` | ✅ | `CubicStrokeGM.kt`, `InnerJoinGeometryGM.kt`, `QuadCapGM.kt`, `Skbug12244GM.kt`, `StrokesGM.kt`, `Strokes2GM.kt`, `Strokes4GM.kt`, `TeenyStrokesGM.kt`, `ZeroLineStrokeGM.kt` | — |
| `stroketext.cpp` | ❌ | — | — |
| `subsetshader.cpp` | ✅ | `BitmapSubsetShaderGM.kt` | — |
| `surface.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `swizzle.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `tablecolorfilter.cpp` | ❌ | — | — |
| `tablemaskfilter.cpp` | 🚧 | — | `SkTableMaskFilter` |
| `tallstretchedbitmaps.cpp` | ❌ | — | — |
| `testgradient.cpp` | ✅ | `TestGradientGM.kt` | — |
| `texelsubset.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
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
| `tilemodes.cpp` | ❌ | — | — |
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
| `wacky_yuv_formats.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `widebuttcaps.cpp` | ✅ | `WideButtCapsGM.kt` | — |
| `windowrectangles.cpp` | 🚧 | — | `SkCanvas::clipShader` |
| `workingspace.cpp` | ❌ | — | — |
| `xfermodeimagefilter.cpp` | ❌ | — | — |
| `xfermodes.cpp` | ✅ | `XfermodesGM.kt` | — |
| `xfermodes2.cpp` | ❌ | — | — |
| `xfermodes3.cpp` | ❌ | — | — |
| `ycbcrimage.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `yuv420_odd_dim.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
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
