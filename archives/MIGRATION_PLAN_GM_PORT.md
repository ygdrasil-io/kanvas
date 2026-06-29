# Plan de portage des GM Skia → kanvas-skia

Statut du portage des Graphics Modules (GM) C++ de Skia vers `kanvas-skia/src/main/kotlin/org/skia/tests/`.

**Source de référence** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm/` (437 fichiers `.cpp`)
**Cible** : `kanvas-skia/src/main/kotlin/org/skia/tests/*GM.kt` (310 GMs Kotlin sur `origin/master`)
**Snapshot** : post-merge **13 phases d'API** (G1-G10 + G9a/b) + **H1 complet** + **H3 waves 1-12 toutes mergées** + **API_REMEDIATION_PLAN.md complet (64/64 R-suivi ✅, archivé)** + **API_FINALIZATION_PLAN.md complet (10/10 sprints R-final + R-final.S, +43 GMs)**

## Résumé

| Statut | Fichiers `.cpp` | Pourcentage |
|---|---:|---:|
| ✅ Porté (≥ 1 `*GM.kt` rattaché) | **357** | **82 %** |
| 🚧 Bloqué (GPU-only / stub JNI / asset manquant / PNG ref dégénérée) | **80** | **18 %** |
| ❌ Non tenté (potentiellement portable) | **0** | **0 %** |
| **Total** | **437** | **100 %** |

🎯 **Phase R-final EXHAUSTÉE** — décomposition des 80 🚧 résiduels :
- **67 GPU-only** — renvoyées vers `MIGRATION_PLAN_GPU_WEBGPU.md`
- **8 stub-JNI** — `R-final.S` a livré les stubs en compile-only (`@Ignore` côté tests) ; les 10 GMs squelettes attendent les vraies dépendances (libwebp lossy, FFmpeg, Fontations, COLR v1, emoji tables, SkSL, LiberationFontMgr, AAClip)
- **5 résiduels post-R-final** — `drawimageset` (F6), `perspimages` (F7), `rsxtext` (F2), `flightAnim/stoplight` (F11+F12) — voir follow-ups F1-F12 dans `API_FINALIZATION_PLAN.md` § 7

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
| H3 wave 2 (9 ports + 1 skip SkM44) | 241 / 437 | 55% |
| H3 wave 3 (7 ports + 1 skip computeFastBounds) | 248 / 437 | 57% |
| H3 wave 4 (9 ports + 1 skip SkPath.contains) | 257 / 437 | 59% |
| H3 wave 5 (8 ports + 2 skip HighContrastFilter / `#if 0`) | 265 / 437 | 61% |
| H3 wave 6 (9 ports + 1 skip palette/COLR) | 274 / 437 | 63% |
| H3 wave 7 (10 ports + 0 skip) | 284 / 437 | 65% |
| H3 wave 8 (10 ports + 0 skip) | 294 / 437 | 67% |
| H3 wave 9 (10 ports + 1 skip textblob_intercepts) | 304 / 437 | 70% |
| H3 wave 10 (9 ports + 1 skip workingspace) | 313 / 437 | 72% |
| H3 wave 11 (3 ports + 7 skips emoji/anim/codec/HDR) | 316 / 437 | 72% |
| H3 wave 12 (3 ports + 7 skips font infra/runtime effect) | 319 / 437 | 73 % |
| R-final.1 — clipShader + drawRegion + contains + drawImageNine (PR #438) | 327 / 437 | 75 % |
| R-final.2 — makeWithLocalMatrix shader/filter (PR #439) | 335 / 437 | 77 % |
| R-final.3 — Color management (PR #440) | 336 / 437 | 77 % |
| R-final.4 — Image helpers + ShaderMaskFilter (PR #441) | 341 / 437 | 78 % |
| R-final.5 — Generators + mipmaps + GIF anim (PR #443) | 346 / 437 | 79 % |
| R-final.6 — Encodeurs via javax.imageio (PR #442) | 351 / 437 | 80 % |
| R-final.7 — Misc raster (M44 persp + CoordClamp + HighContrast + TableMaskFilter, PR #445) | 356 / 437 | 81 % |
| R-final.8 — AnimatedImage + EXIF + YUVUtils (PR #444) | 357 / 437 | 82 % |
| R-final.9 — Variable fonts + HDR pipeline (PR #447) | 357 / 437 | 82 % (+ 2 GMs in-progress — variantes Distortable.ttf / HDR PQ) |
| **R-final.S — JNI stubs documented (PR #446)** | **357 / 437** | **82 %** (10 squelettes compile-only) |

**Gain net depuis snapshot initial** : +209 GMs (de 34 % à 82 % de couverture). **Tous les `❌` épuisés** — chaque cpp est désormais soit ✅ soit 🚧 (résiduel justifié).

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

Pondération par nombre de `.cpp` impactés. Le catalogue détaillé classe-par-classe vit dans [`API_REMEDIATION_PLAN.md`](API_REMEDIATION_PLAN.md) — cette section ne garde que la vue d'ensemble.

| Bloqueur | `.cpp` | Type |
|---|---:|---|
| GPU-only (`DEF_SIMPLE_GPU_GM`, pas de PNG de référence raster) | 62 | **n/a** (intransposable) |
| Surface API publique manquante (47 classes, ~110 méthodes, ~35 overloads, ~20 champs/enums) | ~40 | API publique — voir `API_REMEDIATION_PLAN.md` |
| GM Bazel-only | 1 | n/a |

**Net** : 62 GMs GPU-only intransposables ; le reste des `🚧` (~40 cpps) sera levé par les phases R1 → R3 de `API_REMEDIATION_PLAN.md`.

## Petits gaps de fidélité révélés par H1

Pas des APIs manquantes — des comportements existants qui dégradent la similarité de ports déjà livrés. À traiter en suivi :

- **`drawImage` sur source A8 ne module pas `paint.color`** — le tinting est absent, le rendu reste gris uniforme. Bloque `alpha_image`, `alpha_image_alpha_tint`.
- **`canvas.clear(c)` dans `saveLayer` court-circuite sur device root** au lieu de la layer. Bloque `colorfilterimagefilter_layer`.
- **`MatrixConvolution` sans paramètre `crop`** — sortie structurellement correcte mais déborde. Bloque `imagefilter_convolve_subset`.
- **Fast-path `Blur` à σ très grand** (≥ 500) drop la Gaussienne et renvoie l'identité. Bloque `BlurBigSigma`.
- **`SkBitmap.setPixel` re-premultiplie par défaut**, défait l'intention pass-through de certains GMs. Bloque `image_out_of_gamut`.

## Plan résiduel

### Phase H2 / R — Combler la surface d'API publique upstream

Le catalogue H2 accumulé au fil des vagues H3 (34 sous-tâches) est désormais **consolidé et étendu** dans un document dédié : [`API_REMEDIATION_PLAN.md`](API_REMEDIATION_PLAN.md).

**Synthèse audit (2026-05-13)** :

| Catégorie | Count |
|---|---:|
| Classes manquantes entièrement (non-GPU public) | 47 |
| Méthodes manquantes (classe présente) | ~110 |
| Overloads manquants | ~35 |
| Champs / enums manquants | ~20 |

**Séquencement** :
- **Phase R1** — Quick wins (25 items, effort S) : factories `SkColorFilters` / `SkShaders`, `SkTableMaskFilter`, `SkShaderMaskFilter`, `SkHighContrastFilter`, `SkPath.contains`, `MakeBlur(respectCTM)`, color types `kRGB_565`/`kGray_8`, `SkPaint.computeFastBounds`, …
- **Phase R2** — Classes moyennes (20 items, effort M) : `SkPathMeasure`, `SkColorMatrix`, `SkPixmap`/`SkPixelRef`, `SkImageGenerator`, `SkImage.makeSubset`/`makeColorSpace`, `SkCanvas.drawRegion`/`drawImageNine`/`clipShader`, factories `SkImages` / `SkSurfaces` / `SkShaders`, `Sk3DView`, `SkICC`, `SkColorSpace.MakeSRGB`, `SkWebpEncoder`, …
- **Phase R3** — Grandes classes (11 items, effort L/XL) : `SkM44` complet, `SkFontMgr` + fontconfig, `SkCustomTypefaceBuilder`, `SkStream`/`SkWStream`, `AndroidCodec`, `SkDocument` + PDF, `SkShadowUtils`, `SkRasterHandleAllocator`, décodeurs étendus (AVIF / JpegXL / RAW / ICO), YUV multi-plane.

Voir `API_REMEDIATION_PLAN.md` pour les signatures précises, les en-têtes upstream, et l'annexe brute fichier par fichier.

### Phase H1.5 — Suivis fidélité (qualité de ports)

Les 5 petits gaps listés ci-dessus. Chacun ½ journée. Effort cumulé : 2-3 jours, gain en similarité sur ~10+ ports déjà livrés.

### Phase H3 — Vagues de ports continues ✅ **EXHAUSTÉE** (12 vagues, 0 ❌ restant)

**12 vagues livrées** (wave 1 → wave 12, ~104 cpps portés cumulés depuis snapshot post-G10). Les `❌` "non tentés" sont tous adressés.

Bloqueurs émergés en wave 6-12 (à logguer comme nouveaux R-suivi ou en pre-block) :
- **Codec animé** : `Codec.getFrameInfo` + per-frame `getPixels(Options{frameIndex})` non exposés (animated_gif, animated_image_orientation)
- **`AnimatedImage` module** : non porté (animated_image_orientation)
- **`VideoDecoder`** : FFmpeg + GrContext only, pas de CPU path (video_decoder)
- **`SkImages.MakeWithFilter`** + `canvas.makeSurface` + `makeTemporaryImage` + HDR PQ retag (hdr_pip_blur)
- **COLR v1** + `SkFontArguments::VariationPosition` + `SkTypeface.makeClone(args)` (palette, colrv1, scaledemoji×2, fontscalerdistortable)
- **Color emoji typefaces** (CBDT/Sbix/ColrV0/Svg) (scaledemoji, coloremoji_blendmodes)
- **`SkRuntimeEffect` SkSL parser** (rippleshadergm)
- **`LiberationFontMgr` portable public surface** (fontmgr_*)
- **Fontations Rust crate binding** (fontations, fontations_ft_compare)
- **`SkTypeface.getKerningPairAdjustments`** (typefacestyles_kerning)
- **`SkTextBlob.getIntercepts`** (textblob_intercepts skip dans texteffects)
- **macOS-specific CGContext/CTFont** (mac_aa_fonts variant)
- **`SkColorFilter::makeWithWorkingColorSpace` family** (workingspace)
- **`SkAAClip` Skia-internal** (simpleaaclip_aaclip variant)
- **`SkInsetConvexPolygon` + `SkOffsetSimplePolygon`** (polygonoffset hi-fi)
- **`PlanetTypeface` + `ReallyBigA.ttf` resource** (mixedtextblobs)

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
| `3d.cpp` | ✅ | `Sk3dSimpleGM.kt` | — |
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
| `animated_gif.cpp` | ✅ | `AnimatedGifGM.kt` | R-final.5 — 100% similarity |
| `animated_image_orientation.cpp` | ✅ | `AnimatedImageOrientationGM.kt` | R-final.8 — via AnimatedImage + EXIF |
| `animatedimageblurs.cpp` | ✅ | `AnimatedImageBlursGM.kt` | — (t=0 snapshot) |
| `anisotropic.cpp` | ✅ | `AnisoMipsGM.kt`, `AnisotropicImageScaleAnisoGM.kt`, `AnisotropicImageScaleLinearGM.kt`, `AnisotropicImageScaleMipGM.kt`, `AnisotropicMipGM.kt` | — |
| `annotated_text.cpp` | ✅ | `AnnotatedTextGM.kt` | — |
| `arcofzorro.cpp` | ✅ | `ArcOfZorroGM.kt` | — |
| `arcto.cpp` | ✅ | `ArcToGM.kt`, `Bug593049GM.kt` | — |
| `arithmode.cpp` | ✅ | `ArithmodeGM.kt`, `ArithmodeBlenderGM.kt` | — |
| `asyncrescaleandread.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `attributes.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `b_119394958.cpp` | ✅ | `B119394958GM.kt` | — |
| `backdrop.cpp` | ✅ | `BackdropHintrectClippingGM.kt` (+ `BackdropScalefactorGM`) | — (partiel : `SaveLayerRec.scaleFactor` ignoré) |
| `backdrop_imagefilter_croprect.cpp` | ✅ | `BackdropImagefilterCroprectGM.kt` | — (partiel : variants rotated/persp/nested/tilemode non portés) |
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
| `bitmapcopy.cpp` | ✅ | `BitmapCopyGM.kt` | — (partiel : RGB565 → 8888 fallback) |
| `bitmapfilters.cpp` | ✅ | `BitmapFiltersGM.kt` | — (partiel : RGB565 → 8888 fallback) |
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
| `clippedbitmapshaders.cpp` | ✅ | `ClippedBitmapShadersGM.kt` (6 variants : clamp/mirror/tile × low/hq) | — |
| `clipshader.cpp` | ✅ | `ClipShaderGM.kt` | R-final.1 — floor ~50% (bug F1: SkRuntimeEffect.makeShader localMatrix forward/inverse) |
| `clockwise.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `closedcappedhairlines.cpp` | 🚧 | — | Pas de PNG de référence dans `original-888/` |
| `collapsepaths.cpp` | ✅ | `CollapsePathsGM.kt` | — |
| `color4f.cpp` | ✅ | `Color4fGM.kt` | — |
| `coloremoji.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `coloremoji_blendmodes.cpp` | 🚧 | — | EmojiSample/Format infra non portée |
| `colorfilteralpha8.cpp` | ✅ | `ColorFilterAlpha8GM.kt` | — |
| `colorfilterimagefilter.cpp` | ✅ | `ColorFilterImageFilterGM.kt` | — |
| `colorfilters.cpp` | ✅ | `ColorFiltersGM.kt` (lightingcolorfilter) | — |
| `colormatrix.cpp` | ✅ | `ColorMatrixGM.kt` | — |
| `colorspace.cpp` | 🚧 | — | `SkImage.makeColorSpace` + `SkCanvas.makeSurface(info)` absents |
| `colorwheel.cpp` | ✅ | `ColorWheelNativeGM.kt` | — |
| `colrv1.cpp` | 🚧 | — | COLR v1 emoji + variable axes |
| `complexclip.cpp` | ✅ | `ComplexClipGM.kt × 8 (bw/aa × invert × layer)` | R-final.1 |
| `complexclip2.cpp` | ✅ | `ComplexClip2GM.kt` (6 variants rect/rrect/path × bw/aa) | — |
| `complexclip3.cpp` | ✅ | `ComplexClip3GM.kt` (simple + complex) | — |
| `complexclip4.cpp` | ✅ | `ComplexClip4GM.kt` (bw + aa, approx) | — (manque `setDeviceClipRestriction` + `ResetClip`) |
| `complexclip_blur_tiled.cpp` | ✅ | `ComplexClipBlurTiledGM.kt` | — |
| `composecolorfilter.cpp` | ✅ | `ComposeColorFilterGM.kt` | — |
| `composeshader.cpp` | ✅ | `ComposeShaderGM.kt, ComposeShaderBitmapGM.kt × 2` | R-final.2 |
| `compositor_quads.cpp` | ✅ | `CompositorQuadsImageGM.kt` | R-final.8 — port minimal (full = F6 experimental_DrawEdgeAAImageSet) |
| `compressed_textures.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `concavepaths.cpp` | ✅ | `ConcavePathsGM.kt` | — |
| `conicpaths.cpp` | ✅ | `ArcCircleGapGM.kt`, `ConicPathsGM.kt`, `LargeCircleGM.kt`, `LargeOvalsGM.kt` | — |
| `constcolorprocessor.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `convex_all_line_paths.cpp` | ✅ | `ConvexLineOnlyPathsGM.kt` | — |
| `convexpaths.cpp` | ✅ | `ConvexPathsGM.kt` | — |
| `convexpolyclip.cpp` | ✅ | `ConvexPolyClipGM.kt` | — |
| `convexpolyeffect.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `coordclampshader.cpp` | ✅ | `CoordClampShaderGM.kt` | R-final.7 |
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
| `crbug_224618.cpp` | ✅ | `Crbug224618GM.kt` | R-final.7 — perspective via SkM44 |
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
| `crop_imagefilter.cpp` | ✅ | `CropImageFilterGM.kt × 4 SkTileMode pairs` | R-final.4 |
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
| `drawbitmaprect.cpp` | ✅ | `DrawBitmapRectGM.kt × 4 procs` | R-final.4 |
| `drawglyphs.cpp` | ✅ | `DrawGlyphsGM.kt` | — |
| `drawimageset.cpp` | 🚧 | — | `SkImage::makeSubset` |
| `drawlines_with_local_matrix.cpp` | ✅ | `DrawlinesWithLocalMatrixGM.kt` | — |
| `drawminibitmaprect.cpp` | ✅ | `DrawMiniBitmapRectGM.kt` (bw + aa) | — |
| `drawquadset.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `drawregion.cpp` | ✅ | `DrawRegionGM.kt` | R-final.1 |
| `drawregionmodes.cpp` | ✅ | `DrawRegionModesGM.kt` | R-final.1 |
| `dropshadowimagefilter.cpp` | ✅ | `DropShadowImageFilterGM.kt` | — |
| `drrect.cpp` | ✅ | `DRRectGM.kt` | — |
| `drrect_small_inner.cpp` | ✅ | `DRRectSmallInnerGM.kt` | — |
| `dstreadshuffle.cpp` | ✅ | `DstReadShuffleGM.kt` | — |
| `ducky_yuv_blend.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `emboss.cpp` | ✅ | `EmbossGM.kt` | — |
| `emptypath.cpp` | ✅ | `EmptyPathGM.kt` | — |
| `emptyshader.cpp` | ✅ | `EmptyShaderGM.kt` | — |
| `encode.cpp` | ✅ | `EncodeGM.kt` | — |
| `encode_alpha_jpeg.cpp` | ✅ | `EncodeAlphaJpegGM.kt` | R-final.6 — 83.53% via javax.imageio |
| `encode_color_types.cpp` | ✅ | `EncodeColorTypesGM.kt` | R-final.6 — webp-lossless variant |
| `encode_platform.cpp` | ✅ | `EncodePlatformGM.kt` | R-final.6 — 80.17% |
| `encode_srgb.cpp` | ✅ | `EncodeSrgbGM.kt (png+jpg variants)` | R-final.6 |
| `exoticformats.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fadefilter.cpp` | ✅ | `FadeFilterGM.kt` | — |
| `fatpathfill.cpp` | ✅ | `FatPathFillGM.kt` | — |
| `fiddle.cpp` | ✅ | `FiddleGM.kt` | — |
| `fillrect_gradient.cpp` | ✅ | `FillrectGradientGM.kt` | — |
| `filltypes.cpp` | ✅ | `FillTypeGM.kt`, `FillTypesGM.kt` | — |
| `filltypespersp.cpp` | ✅ | `FillTypePerspGM.kt` | — |
| `filterbug.cpp` | ✅ | `FilterBugGM.kt` | — |
| `filterfastbounds.cpp` | ✅ | `FilterFastBoundsGM.kt` | R-final.4 |
| `filterindiabox.cpp` | ✅ | `FilterIndiaBoxGM.kt` | — |
| `flippity.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fontations.cpp` | 🚧 | — | Fontations Rust crate non bound |
| `fontations_ft_compare.cpp` | 🚧 | — | Fontations crate non bound |
| `fontcache.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fontmgr.cpp` | 🚧 | — | LiberationFontMgr internal ; pas de portable family API public |
| `fontregen.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fontscaler.cpp` | ✅ | `FontScalerGM.kt` | — |
| `fontscalerdistortable.cpp` | ✅ | `FontScalerDistortableGM.kt` | R-final.9 — AWT 4 axes (wght/wdth/slnt/ital) |
| `fp_sample_chaining.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fpcoordinateoverride.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `fwidth_squircle.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `gammatext.cpp` | ✅ | `GammatextGM.kt, GammatextColorShaderGM.kt` | — |
| `getpostextpath.cpp` | ✅ | `GetPosTextPathGM.kt` | — |
| `giantbitmap.cpp` | ✅ | `GiantBitmapGM.kt (12 variants Clamp/Mirror/Repeat × Bilerp/Point × Scale/Rotate)` | — |
| `glyph_pos.cpp` | ✅ | `GlyphPosGM.kt (6 variants h/n × b/s/f)` | — |
| `gm.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `gpu_blur_utils.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `gradient_dirty_laundry.cpp` | ✅ | `GradientDirtyLaundryGM.kt` | — |
| `gradient_matrix.cpp` | ✅ | `GradientMatrixGM.kt` | — (66 % : tiles tile-mode fallback `paint.color`) |
| `gradients.cpp` | ✅ | `ClampedGradientsGM.kt`, `GradientsGM.kt`, `RgbwSweepGradientGM.kt`, `SmallColorStopGM.kt`, `SweepTilingGM.kt` | — |
| `gradients_2pt_conical.cpp` | ✅ | `Gradients2ptConicalGM.kt (7 variants inside/outside/edge × kClamp/kRepeat/kMirror)` | — |
| `gradients_degenerate.cpp` | ✅ | `DegenerateGradientGM.kt` | — (61 % : Radial r=0 / Sweep s==e rejetés, fallback color) |
| `gradients_no_texture.cpp` | ✅ | `GradientsNoTextureGM.kt` | — |
| `gradtext.cpp` | ✅ | `ChromeGradTextGM.kt`, `GradTextGM.kt` | — |
| `graphite_replay.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `graphitestart.cpp` | 🚧 | — | `SkColorFilters::LinearToSRGBGamma`/`SRGBToLinearGamma` |
| `grayscalejpg.cpp` | ✅ | `GrayscaleJpgGM.kt` | — |
| `hairlines.cpp` | ✅ | `HairlineSubdivGM.kt`, `HairlinesGM.kt`, `SquareHairGM.kt` | — |
| `hairmodes.cpp` | ✅ | `HairModesGM.kt` | — |
| `hardstop_gradients.cpp` | ✅ | `HardstopGradientShaderGM.kt` | — |
| `hardstop_gradients_many.cpp` | ✅ | `HardstopGradientsManyGM.kt` | — |
| `hdr_pip_blur.cpp` | ✅ | `HdrPipBlurGM.kt` | R-final.9 — MakeWithFilter + makeTemporaryImage + MakePqHdr |
| `hello_bazel_world.cpp` | 🚧 | — | GM Bazel-only (pas de PNG de référence) |
| `highcontrastfilter.cpp` | ✅ | `HighContrastFilterGM.kt` | R-final.7 |
| `hittestpath.cpp` | ✅ | `HitTestPathGM.kt` | R-final.1 |
| `hsl.cpp` | ✅ | `HSLGM.kt` | — |
| `hugepath.cpp` | ✅ | `HugePathCrbug800804GM.kt`, `PathHugeAaGM.kt`, `PathHugeAaManualGM.kt` | — |
| `image.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `image_pict.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `image_shader.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imageblur.cpp` | ✅ | `ImageBlurGM.kt` | — |
| `imageblur2.cpp` | ✅ | `ImageBlur2GM.kt` | — |
| `imageblurclampmode.cpp` | ✅ | `ImageBlurClampModeGM.kt` | — |
| `imageblurrepeatmode.cpp` | ✅ | `ImageBlurRepeatModeGM.kt` | — (35 % : divergence kRepeat blur) |
| `imageblurtiled.cpp` | ✅ | `ImageBlurTiledGM.kt` | — |
| `imagedither.cpp` | ✅ | `ImageDitherGM.kt` | — |
| `imagefilters.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imagefiltersbase.cpp` | ✅ | `ImageFiltersBaseGM.kt` | — |
| `imagefiltersclipped.cpp` | ✅ | `ImageFiltersClippedGM.kt` | — |
| `imagefilterscropexpand.cpp` | ✅ | `ImageFiltersCropExpandGM.kt` | — (cropRect wrappé `Crop`) |
| `imagefilterscropped.cpp` | ✅ | `ImageFiltersCroppedGM.kt` | — (cropRect wrappé `Crop`) |
| `imagefiltersgraph.cpp` | ✅ | `ImageFiltersGraphGM.kt` | — (cropRect wrappé `Crop`) |
| `imagefiltersscaled.cpp` | ✅ | `ImageFiltersScaledGM.kt` | — |
| `imagefiltersstroked.cpp` | ✅ | `ImageFiltersStrokedGM.kt` | — |
| `imagefilterstransformed.cpp` | ✅ | `ImageFiltersTransformedGM.kt` | R-final.2 |
| `imagefiltersunpremul.cpp` | ✅ | `ImageFiltersUnpremulGM.kt` | — |
| `imagefromyuvtextures.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imagemagnifier.cpp` | ✅ | `ImageMagnifierGM.kt`, `ImageMagnifierCroppedGM.kt`, `ImageMagnifierBoundsGM.kt` | — |
| `imagemakewithfilter.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imagemasksubset.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `imageresizetiled.cpp` | ✅ | `ImageResizeTiledGM.kt` | — |
| `imagesource.cpp` | ✅ | `ImageSourceGM.kt` | — |
| `imagesource2.cpp` | ✅ | `ImageSource2GM.kt (4 variants None/Low/Med/High)` | — (22-24% : sampler kernel divergence) |
| `internal_links.cpp` | ✅ | `InternalLinksGM.kt` | — |
| `inverseclip.cpp` | ✅ | `InverseClipGM.kt` | — |
| `inversepaths.cpp` | ✅ | `InversePathsGM.kt`, `InverseWindingmodeFiltersGM.kt` | — |
| `jpg_color_cube.cpp` | ✅ | `JpgColorCubeGM.kt` | R-final.6 — 100% |
| `kawase_blur_rt.cpp` | ✅ | `KawaseBlurRtGM.kt` | — |
| `labyrinth.cpp` | ✅ | `LabyrinthGM.kt` | — |
| `largeclippedpath.cpp` | ✅ | `LargeClippedPathGM.kt` | — |
| `largeglyphblur.cpp` | ✅ | `LargeGlyphBlurGM.kt` | — |
| `lattice.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `lazytiling.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `lcdblendmodes.cpp` | ✅ | `LcdBlendGM.kt` | — |
| `lcdoverlap.cpp` | ✅ | `LcdOverlapGM.kt` | — |
| `lcdtext.cpp` | ✅ | `LcdTextGM.kt` | — |
| `lighting.cpp` | ✅ | `LightingGM.kt` | — |
| `linepaths.cpp` | ✅ | `LinePathGM.kt` | — |
| `localmatriximagefilter.cpp` | ✅ | `LocalMatrixImageFilterGM.kt` | R-final.2 |
| `localmatriximageshader.cpp` | ✅ | `LocalMatrixImageShaderGM.kt` | — |
| `localmatrixshader.cpp` | ✅ | `LocalMatrixShaderGM.kt` | R-final.2 — 4 shader-topology factories |
| `lumafilter.cpp` | ✅ | `LumaFilterGM.kt` | — |
| `luminosity.cpp` | ✅ | `LuminosityOverflowGM.kt` | — |
| `mac_aa_explorer.cpp` | ✅ | `MacaaColorsGM.kt` | — (partial : MacAAFontsGM = #ifdef SK_BUILD_FOR_MAC) |
| `make_raster_image.cpp` | ✅ | `MakeRasterImageGM.kt` | — |
| `makecolorspace.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `mandoline.cpp` | ✅ | `MandolineGM.kt` | — |
| `manypathatlases.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `manypaths.cpp` | ✅ | `ManyCirclesGM.kt`, `ManyRRectsGM.kt` | — |
| `matrixconvolution.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `matriximagefilter.cpp` | ✅ | `MatrixImageFilterGM.kt` | — |
| `mesh.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `mipmap.cpp` | ✅ | `MipmapGM.kt, MipmapSrgbGM.kt, MipmapGray8SrgbGM.kt` | — |
| `mirrortile.cpp` | ✅ | `MirrorTileGM.kt` | — |
| `mixedtextblobs.cpp` | ✅ | `MixedTextBlobsGM.kt` | R-final.7 — sans variante color-emoji (PlanetTypeface non-AWT) |
| `mixercolorfilter.cpp` | ✅ | `MixerCFGM.kt` | — (30 % : `Lerp` non-nullable workaround) |
| `modecolorfilters.cpp` | ✅ | `ModeColorFiltersGM.kt` | — (44 % : divergence saveLayer + `SrcOver` bgPaint) |
| `morphology.cpp` | ✅ | `MorphologyGM.kt` | — |
| `nearesthalfpixelimage.cpp` | ✅ | `NearestHalfPixelImageGM.kt` | — |
| `nested.cpp` | ✅ | `NestedGM.kt` | — |
| `ninepatchstretch.cpp` | ✅ | `NinePatchStretchGM.kt` | R-final.1 — degenerate drawImageLattice 3×3 |
| `nonclosedpaths.cpp` | ✅ | `NonClosedPathsGM.kt` | — |
| `offsetimagefilter.cpp` | ✅ | `OffsetImageFilterGM.kt` | — (cropRect wrappé `Crop`) |
| `orientation.cpp` | ✅ | `Orientation444GM.kt, RespectOrientationJpegGM.kt` | R-final.5 (ports) + R-final.8 (EXIF fix → 100%) |
| `ovals.cpp` | ✅ | `OvalGM.kt` | — |
| `overdrawcanvas.cpp` | ✅ | `OverdrawCanvasGM.kt` | — |
| `overdrawcolorfilter.cpp` | ✅ | `OverdrawColorFilterGM.kt` | — |
| `overstroke.cpp` | ✅ | `OverStrokeGM.kt` | — |
| `p3.cpp` | ✅ | `P3GM.kt, P3OvalsGM.kt` | — |
| `palette.cpp` | 🚧 | — | COLR v1 emoji typeface + SkFontArguments::Palette + SkTypeface.makeClone(args) |
| `patch.cpp` | ✅ | `PatchAlphaTestGM.kt`, `PatchPrimitiveGM.kt` | — |
| `path_stroke_with_zero_length.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `patharcto.cpp` | ✅ | `ShallowAnglePathArcToGM.kt` | — |
| `pathcontourstart.cpp` | ✅ | `ContourStartGM.kt` | — |
| `patheffects.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `pathfill.cpp` | ✅ | `Bug7792GM.kt`, `RotatedCubicPathGM.kt` | — |
| `pathinterior.cpp` | ✅ | `PathInteriorGM.kt` | — |
| `pathmaskcache.cpp` | ✅ | `PathMaskCacheGM.kt` | — |
| `pathmeasure.cpp` | 🚧 | — | Hors scope upstream : `#if 0` autour de `PathMeasure_explosion`, pas de `DEF_GM` |
| `pathopsblend.cpp` | ✅ | `PathOpsBlendGM.kt` | — |
| `pathopsinverse.cpp` | ✅ | `PathOpsInverseGM.kt` | — |
| `pathreverse.cpp` | ✅ | `PathReverseGM.kt` | — |
| `pdf_never_embed.cpp` | ✅ | `PdfNeverEmbedGM.kt` | — |
| `perlinnoise.cpp` | ✅ | `PerlinNoiseGM.kt`, `PerlinNoiseRotatedGM.kt` | — |
| `perspimages.cpp` | 🚧 | — | `SkImage::makeSubset` |
| `perspshaders.cpp` | ✅ | `PerspShadersGM.kt` | — (H1.5 : `drawImage` sous perspective drop) |
| `persptext.cpp` | ✅ | `PerspTextGM.kt, PerspTextMinimalGM.kt` | — |
| `picture.cpp` | ✅ | `PictureGM.kt`, `PictureCullRectGM.kt` | — |
| `pictureimagefilter.cpp` | ✅ | `PictureImageFilterGM.kt` | — |
| `pictureimagegenerator.cpp` | ✅ | `PictureImageGeneratorGM.kt` | R-final.5 — 83% (manque F10 SkTextUtils.GetPath) |
| `pictureshader.cpp` | ✅ | `PictureShaderGM.kt × 3 (incl. _localwrapper, _alpha)` | R-final.2 |
| `pictureshadercache.cpp` | ✅ | `PictureShaderCacheGM.kt` | — |
| `pictureshadertile.cpp` | ✅ | `PictureShaderTileGM.kt` | — |
| `plus.cpp` | ✅ | `PlusMergesAaGM.kt` | — |
| `points.cpp` | ✅ | `PointsGM.kt` | — |
| `poly2poly.cpp` | ✅ | `Poly2PolyGM.kt` | — |
| `polygonoffset.cpp` | ✅ | `PolygonOffsetGM.kt (convex + simple)` | — (centroid-shrink approx ; SkInsetConvexPolygon/SkOffsetSimplePolygon non portés) |
| `polygons.cpp` | ✅ | `ConjoinedPolygonsGM.kt`, `PolygonsGM.kt` | — |
| `postercircle.cpp` | ✅ | `PosterCircleGM.kt` | — |
| `preservefillrule.cpp` | ✅ | `PreserveFillRuleGM.kt` | — |
| `quadpaths.cpp` | ✅ | `QuadClosePathGM.kt`, `QuadPathGM.kt` | — |
| `radial_gradient_precision.cpp` | ✅ | `RadialGradientPrecisionGM.kt` | — |
| `rasterhandleallocator.cpp` | ✅ | `RasterAllocatorGM.kt` | — |
| `readpixels.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `recordopts.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rect_poly_stroke.cpp` | ✅ | `RectPolyStrokeGM.kt` | — |
| `rectangletexture.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rendertomipmappedyuvimageplanes.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `repeated_bitmap.cpp` | ✅ | `RepeatedBitmapGM.kt` | — |
| `resizeimagefilter.cpp` | ✅ | `ResizeImageFilterGM.kt` | — |
| `rippleshadergm.cpp` | 🚧 | — | RippleShader.rts (SkRuntimeEffect SkSL parser non porté) |
| `roundrects.cpp` | ✅ | `RoundRectGM.kt` | — |
| `rrect.cpp` | ✅ | `RRectGM.kt` | — |
| `rrectclipdrawpaint.cpp` | ✅ | `RRectClipDrawPaintGM.kt` | — |
| `rrects.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `rsxtext.cpp` | 🚧 | — | `SkShader::makeWithLocalMatrix` / `SkImageFilter::makeWithLocalMatrix` |
| `runtimecolorfilter.cpp` | ✅ | `RuntimeColorFilterGM.kt` | — |
| `runtimefunctions.cpp` | ✅ | `RuntimeFunctionsGM.kt` | — |
| `runtimeimagefilter.cpp` | ✅ | `RtifDistortGM.kt`, `RtifUnsharpGM.kt` | — |
| `runtimeintrinsics.cpp` | ✅ | `RuntimeIntrinsicsCommonGM.kt`, `RuntimeIntrinsicsExponentialGM.kt`, `RuntimeIntrinsicsGeometricGM.kt`, `RuntimeIntrinsicsMatrixGM.kt`, `RuntimeIntrinsicsRelationalGM.kt`, `RuntimeIntrinsicsTrigGM.kt` | — |
| `runtimeshader.cpp` | ✅ | `RuntimeShaderGM.kt` | R-final.1 — floor ~50% (bug F1: localMatrix forward/inverse) |
| `samplerstress.cpp` | ✅ | `SamplerStressGM.kt` | — |
| `savelayer.cpp` | ✅ | `SaveLayerGM.kt (savelayer_initfromprev)` | R-final.4 |
| `scaledemoji.cpp` | 🚧 | — | CBDT/Sbix/ColrV0/Svg emoji typefaces |
| `scaledemoji_rendering.cpp` | 🚧 | — | CBDT/Sbix/ColrV0/Svg emoji typefaces |
| `scaledrects.cpp` | ✅ | `ClipLargeRectGM.kt`, `ScaledRectsGM.kt` | — |
| `scaledstrokes.cpp` | ✅ | `ScaledStrokesGM.kt` | — |
| `shadermaskfilter.cpp` | ✅ | `ShaderMaskFilterGM.kt` | R-final.4 |
| `shaderpath.cpp` | ✅ | `ShaderPathGM.kt` | — |
| `shadertext3.cpp` | ✅ | `ShaderText3GM.kt` | — |
| `shadowutils.cpp` | ✅ | `ShadowUtilsGM.kt (NoOccluders / Occluders / Grayscale)` | — |
| `shallowgradient.cpp` | ✅ | `ShallowGradientConicalGM.kt, ShallowGradientSweepGM.kt` | — (déjà porté antérieurement) |
| `shapes.cpp` | ✅ | `InnerShapesGM.kt`, `SimpleShapesGM.kt` | — |
| `sharedcorners.cpp` | ✅ | `SharedCornersGM.kt` | — |
| `showmiplevels.cpp` | ✅ | `ShowMipLevelsGM.kt` | R-final.5 — 78% |
| `simpleaaclip.cpp` | ✅ | `SimpleAaClipRectGM.kt, SimpleAaClipPathGM.kt` | — (_aaclip flavour skip : SkAAClip Skia-internal) |
| `simplerect.cpp` | ✅ | `SimpleRectGM.kt` | — |
| `skbug1719.cpp` | ✅ | `Skbug1719GM.kt` | — |
| `skbug_12212.cpp` | ✅ | `Skbug12212GM.kt` | — |
| `skbug_257.cpp` | ✅ | `Skbug257GM.kt` | — |
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
| `srcmode.cpp` | ✅ | `SrcModeGM.kt` | — |
| `srgb.cpp` | ✅ | `SrgbGM.kt` | R-final.3 — 97.33% |
| `stlouisarch.cpp` | ✅ | `StLouisArchGM.kt` | — |
| `stringart.cpp` | ✅ | `StringArtGM.kt` | — |
| `stroke_rect_shader.cpp` | ✅ | `StrokeRectShaderGM.kt` | — |
| `strokedlines.cpp` | ✅ | `StrokedLinesGM.kt (drawPath + drawPoints)` | — |
| `strokefill.cpp` | ✅ | `Bug339297GM.kt`, `Bug6987GM.kt` | — |
| `strokerect.cpp` | ✅ | `StrokeRectGM.kt` | — |
| `strokerect_anisotropic.cpp` | ✅ | `StrokerectAnisotropicGM.kt` | — |
| `strokerects.cpp` | ✅ | `StrokeRectsGM.kt` | — |
| `strokes.cpp` | ✅ | `CubicStrokeGM.kt`, `InnerJoinGeometryGM.kt`, `QuadCapGM.kt`, `Skbug12244GM.kt`, `StrokesGM.kt`, `Strokes2GM.kt`, `Strokes4GM.kt`, `TeenyStrokesGM.kt`, `ZeroLineStrokeGM.kt` | — |
| `stroketext.cpp` | ✅ | `StrokeTextGM.kt` | — (dash-on-text panel no-op) |
| `subsetshader.cpp` | ✅ | `BitmapSubsetShaderGM.kt` | — |
| `surface.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `swizzle.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `tablecolorfilter.cpp` | ✅ | `TableColorFilterGM.kt` | — |
| `tablemaskfilter.cpp` | ✅ | `TableMaskFilterGM.kt` | R-final.7 |
| `tallstretchedbitmaps.cpp` | ✅ | `TallStretchedBitmapsGM.kt` | — |
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
| `textblobtransforms.cpp` | ✅ | `TextBlobTransformsGM.kt` | — |
| `textblobuseaftergpufree.cpp` | ✅ | `TextBlobUseAfterGpuFreeGM.kt` | — |
| `texteffects.cpp` | ✅ | `TextEffectsGM.kt (FancyBlobUnderline)` | — (textblob_intercepts skip : SkTextBlob.getIntercepts manquant) |
| `thinconcavepaths.cpp` | ✅ | `ThinConcavePathsGM.kt` | — |
| `thinrects.cpp` | ✅ | `ThinRectsGM.kt` | — |
| `thinstrokedrects.cpp` | ✅ | `ThinStrokedRectsGM.kt` | — |
| `tiledscaledbitmap.cpp` | ✅ | `TiledScaledBitmapGM.kt` | — |
| `tileimagefilter.cpp` | ✅ | `TileImageFilterGM.kt` | — |
| `tilemodes.cpp` | ✅ | `TilemodesGM.kt` | — |
| `tilemodes_alpha.cpp` | ✅ | `TilemodesAlphaGM.kt` | — |
| `tilemodes_scaled.cpp` | ✅ | `ScaledTilemodesGM.kt` | — |
| `tinybitmap.cpp` | ✅ | `TinyBitmapGM.kt` | — |
| `transparency.cpp` | ✅ | `TransparencyCheckGM.kt` | — |
| `trickycubicstrokes.cpp` | ✅ | `TrickyCubicStrokesGM.kt` | — |
| `typeface.cpp` | ✅ | `TypefaceStylesGM.kt, TypefaceStylingGM.kt, TypefaceStylesKerningGM.kt` | R-final.7 — variante kerning levée via AWT |
| `unpremul.cpp` | ✅ | `UnpremulGM.kt` | — |
| `userfont.cpp` | ✅ | `UserFontGM.kt` | — (R-suivi.49 SkCustomTypeface drawable hook) |
| `variedtext.cpp` | ✅ | `VariedTextGM.kt (4 variants Clipped/IgnorableClip × Lcd/NoLcd)` | — |
| `vertices.cpp` | ✅ | `VerticesCollapsedGM.kt`, `VerticesPerspectiveGM.kt` | — |
| `verylargebitmap.cpp` | ✅ | `VeryLargeBitmapGM.kt` | — |
| `video_decoder.cpp` | 🚧 | — | VideoDecoder = FFmpeg + GrContext only ; pas de CPU path |
| `wacky_yuv_formats.cpp` | 🚧 | — | GPU-only (pas de PNG de référence raster) |
| `widebuttcaps.cpp` | ✅ | `WideButtCapsGM.kt` | — |
| `windowrectangles.cpp` | ✅ | `WindowRectanglesGM.kt` | R-final.1 |
| `workingspace.cpp` | 🚧 | — | SkColorFilter::makeWithWorkingColorSpace + SkShader::makeWithWorkingColorSpace + SkWorkingColorSpaceShader |
| `xfermodeimagefilter.cpp` | ✅ | `XfermodeImageFilterGM.kt` | — (Blend(cropRect) substitué Crop(kDecal)) |
| `xfermodes.cpp` | ✅ | `XfermodesGM.kt` | — |
| `xfermodes2.cpp` | ✅ | `Xfermodes2GM.kt` | — (premul SkPMColor patterns reconstructs) |
| `xfermodes3.cpp` | ✅ | `Xfermodes3GM.kt` | — |
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
