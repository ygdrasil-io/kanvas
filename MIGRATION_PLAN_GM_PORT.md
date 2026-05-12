# Plan de portage des GM Skia → kanvas-skia

Statut du portage des Graphics Modules (GM) C++ de Skia vers `kanvas-skia/src/main/kotlin/org/skia/tests/`.

**Source de référence** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm/` (437 fichiers `.cpp`)
**Cible** : `kanvas-skia/src/main/kotlin/org/skia/tests/*GM.kt` (250 GMs Kotlin sur `origin/master`)
**Dernière mise à jour** : après merge des PRs #304/305/306/307/308/309 (vagues 1-3 de portage) + bulk-mark des bloqueurs connus

## Résumé

| Statut | Fichiers `.cpp` | Pourcentage |
|---|---:|---:|
| ✅ Porté (≥ 1 GM mappé) | 166 | 37% |
| 🚧 Bloqué par API manquante / lacune impl. / ressource indisponible | 119 | 27% |
| ❌ Non tenté | 152 | 36% |
| **Total** | **437** | **100%** |

GMs Kotlin sans correspondance dans le tree de référence : **30** (probablement issus d'une version Skia plus récente).

## Mini-plan d'ajout des API manquantes

Priorité par ROI (nombre de `.cpp` débloqués par chaque API), puis par complexité d'intégration. Les phases peuvent être conduites en parallèle entre elles, mais chaque phase a ses propres dépendances internes.

### Pondération par bloqueur

| Bloqueur | `.cpp` impactés |
|---|---:|
| Chargement d'images (`ToolUtils::GetResourceAsImage` / `GetResourceAsBitmap` / `MakeTextureImage` / `draw_checkerboard`) | 65 |
| `SkCubicResampler` + cubic `SkSamplingOptions` | 9 |
| `SkPicture::makeShader` | 8 |
| Alpha8 `SkBitmap`/`SkImage` | 6 |
| `kBGRA_8888` `SkBitmap` | 5 |
| `SkCanvas::experimental_DrawEdgeAAQuad` + `QuadAAFlags` | 5 |
| `SkRegion::*` (`translate`, `op`, `SkCanvas::clipRegion`) | 4 |
| `SkParsePath::FromSVGString` | 3 |
| `SkCanvas::SaveLayerRec` avec `fBackdrop` | 2 |
| `SkImageFilters::Blur(tileMode, cropRect)` | 2 |
| `kRGBA_4444` colortype | 2 |
| `SkOverdrawColorFilter` | 2 |
| `SkShaderMaskFilter::Make` | 1 |
| `SkCanvas::clipShader` | 1 |
| `SkTableMaskFilter` | 1 |
| Lacunes `SkBitmapDevice` (`drawPaint` ignore `paint.imageFilter`, `compositeFrom` ignore `paint.colorFilter`) | 2 |
| `SkCanvas::readPixels` + `MarkGMGood`/`MarkGMBad` | 1 |
| Mip-LOD raster + `SkSamplingOptions::Aniso` + `SkImage::withDefaultMipmaps` | 1 |

### Phase G1 — Chargement d'images (ROI : 65 `.cpp`)

L'unique plus gros débloqueur. Les PNG/JPG sources d'upstream Skia se trouvent dans `kanvas/src/test/resources/images/` (à confirmer) — il suffit de lire les bytes via `ClassLoader.getResourceAsStream` et de passer par `SkCodec.MakeFromData`.

**Tâches** :
1. `ToolUtils.GetResourceAsImage(path: String): SkImage?` — `SkCodec.MakeFromData(bytes).getImage()`
2. `ToolUtils.GetResourceAsBitmap(path: String, dst: SkBitmap): Boolean`
3. `ToolUtils.MakeTextureImage(canvas, image): SkImage` — no-op en backend raster (renvoyer `image`)
4. `ToolUtils.draw_checkerboard(canvas, c1: SkColor, c2: SkColor, size: Int)` — boucle de `drawRect` simple
5. (optionnel) `ToolUtils.GetResourceAsData(path)` pour les usages qui veulent les bytes bruts

**Effort** : ~½ journée. Aucune nouvelle infrastructure de rendu — c'est de la plomberie I/O.
**Débloque** : tous les GMs marqués "Chargement d'images (...)".

### Phase G2 — Bicubic sampling (ROI : 9 `.cpp`)

**Tâches** :
1. `data class SkCubicResampler(val B: Float, val C: Float)` + factories `Mitchell()`, `CatmullRom()`
2. Étendre `SkSamplingOptions` avec un champ `cubic: SkCubicResampler?` ; `useCubic = cubic != null`
3. Câbler le bicubic dans le sampler de `SkBitmapDevice` (chemin `drawImageRect` + `drawImage`) et `SkImage.makeShader`/`SkBitmapShader`
4. Tests de non-régression sur GMs déjà passants pour s'assurer que le chemin nearest/linear reste inchangé

**Effort** : 1-2 jours. Nouveau noyau de filtrage 4×4 pondéré par les coefficients (B, C).

### Phase G3 — `SkPicture::makeShader` (ROI : 8 `.cpp`)

**Tâches** :
1. Confirmer que `SkPicture` et `SkPictureRecorder` existent en `:kanvas-skia` (`grep -r SkPicture kanvas-skia/src/main`)
2. Implémenter `SkPicture.makeShader(tileX, tileY, filter, localMatrix? = null, tile? = null): SkShader`
3. Stratégie : replay la picture dans un `SkBitmap` à la dimension de `tile` (ou `cullRect` par défaut) puis ré-utiliser `SkBitmapShader`
4. Gestion du cache (réutiliser la même `SkBitmap` si la picture n'a pas changé)

**Effort** : 1 jour. Réutilise l'infra image-shader existante.

### Phase G4 — Colortypes alternatifs (ROI : 6 + 5 + 2 = 13 `.cpp`)

**Tâches** :
1. **`kAlpha_8`** (6 GMs) : ajouter un backing-array `ByteArray` dans `SkBitmap`, faire en sorte que `getPixel`/`setPixel`/`eraseColor` lisent/écrivent l'alpha-seul (RGB forcé à 0). Adapter `SkImage.Make` pour propager l'alpha-only.
2. **`kBGRA_8888`** (5 GMs) : ajouter un backing-array `IntArray` ou conversion à la volée. Pour le raster CPU, BGRA est juste un swap d'octets — implémentable comme overlay sur l'array RGBA existant.
3. **`kRGBA_4444`** (2 GMs) : plus bas ROI, peut attendre. Pack 4-bit/canal.

**Effort** : 1-2 jours pour Alpha8 + BGRA. 4444 = ½ journée supplémentaire.

### Phase G5 — Edge AA quad (ROI : 5 `.cpp`)

**Tâches** :
1. Enum `QuadAAFlags { kLeft, kTop, kRight, kBottom, kNone, kAll }` (bitflags)
2. `SkCanvas.experimental_DrawEdgeAAQuad(rect, clip: Array<SkPoint>?, aaFlags, color, mode)`
3. Géométrie : pour chaque arête, désactiver l'AA si le flag correspondant est absent (mise à 1.0 du coverage)
4. Si `clip != null`, convertir le quad en path et rasteriser avec les arêtes AA sélectives

**Effort** : 1-2 jours. Nécessite un chemin de rasterisation distinct du standard `drawRect`/`drawPath`.

### Phase G6 — `SaveLayerRec` avec backdrop (ROI : 2 `.cpp`, mais débloque le pattern usuel des effets de backdrop blur)

**Tâches** :
1. `data class SaveLayerRec(bounds: SkRect?, paint: SkPaint?, backdrop: SkImageFilter?, flags: SaveLayerFlags = 0)`
2. `SkCanvas.saveLayer(rec: SaveLayerRec): Int`
3. Sémantique du backdrop : avant de rediriger les draws vers la nouvelle layer, passer les pixels du parent device à travers `backdrop` et écrire le résultat comme état initial de la layer
4. Étendre `SkImageFilters.Blur(σx, σy, tileMode = kDecal, cropRect: SkIRect? = null, input: SkImageFilter? = null)`

**Effort** : 1 jour. Le plus délicat est la sémantique "copy + filter" lors du saveLayer.

### Phase G7 — Lacunes `SkBitmapDevice` (ROI : 2-3 `.cpp` + qualité accrue de plusieurs ports)

Pas une nouvelle API — câblage manquant dans le device existant.

**Tâches** :
1. `SkBitmapDevice.compositeFrom` (`kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt:440`) : appliquer `paint?.colorFilter` entre `applyAlpha` et `dispatchBlend`, en miroir du chemin per-draw (l. 214)
2. `SkBitmapDevice.drawPaint` : router vers `saveLayer(paint) + drawPaint(plain) + restore` quand `paint.imageFilter != null`

**Effort** : ½ journée. Pure plomberie. Améliore aussi la similarité des ports `Crbug1156804`, `Crbug905548` déjà mergés.

### Phase G8 — `SkRegion` + clipping (ROI : 4 `.cpp`)

**Tâches** :
1. `SkRegion.translate(dx: Int, dy: Int)` — décalage in-place de toutes les bandes
2. `SkRegion.op(rect: SkIRect, op: SkRegion.Op)` — union/intersect/diff entre region et rect
3. `SkCanvas.clipRegion(region: SkRegion, op: SkClipOp = kIntersect)` — clip à partir d'une region (chemin clip-stack existant peut passer par un masque alpha rasterisé)

**Effort** : 1-2 jours. La rasterisation de `SkRegion` en clip est la partie subtile (pas d'AA, bandes Y-monotones).

### Phase G9 — Petits bloqueurs unitaires (ROI : 1-3 `.cpp` chacun)

Chacun ½ à 1 journée :
- **`SkParsePath::FromSVGString`** (3 GMs) : parser SVG path string ; existe en Skia comme utilitaire texte
- **`SkCanvas::clipShader(shader, op)`** (1 GM) : clip par-pixel via shader — plus complexe, peut-être à différer
- **`SkShaderMaskFilter::Make`** (1 GM) : mask filter qui sample le shader interne aux coords du mask
- **`SkTableMaskFilter`** (1 GM) : factory `ByteArray(256) → SkMaskFilter`, lookup byte-à-byte
- **`SkOverdrawColorFilter::MakeWithSkColors`** (2 GMs) : `IntArray(6) → SkColorFilter` indexé par alpha (1..6)
- **`SkCanvas::readPixels(dst, x, y)`** (1 GM) : blit device → bitmap avec conversion couleur
- **`MarkGMGood`/`MarkGMBad`** (1 GM) : helpers dans `org.skia.tests.GM`, dessin de check vert/rouge

### Phase G10 — Anisotropic / mipmap (ROI : 1 `.cpp` mais variantes multiples bloquées)

**Tâches** :
1. Pré-calculer les niveaux de mip dans `SkImage.makeShader` quand `SkSamplingOptions.mipmap != kNone`
2. `SkSamplingOptions.Aniso(maxAniso: Int)` + sampler à empreinte elliptique
3. `SkImage.withDefaultMipmaps(): SkImage` — pré-build de la pyramide

**Effort** : 2-3 jours. Le plus loin du raster pur. À traiter en dernier si le ROI ne justifie pas l'avancement.

### Chemin recommandé

- **Tier S** (à faire dans la foulée) : **G1** (image loading, 65 unlocks)
- **Tier A** (gros gain, complexité modérée) : **G2** (bicubic, 9 unlocks), **G3** (picture shader, 8 unlocks), **G4** (Alpha8 + BGRA, 11 unlocks)
- **Tier B** (gain moyen) : **G5** (edge AA quad, 5 unlocks), **G6** (saveLayerRec, 2-3 unlocks), **G8** (SkRegion, 4 unlocks)
- **Tier C** (gain mineur, pratiquable en parallèle) : **G7** (lacunes device), **G9** (petits bloqueurs)
- **Tier D** (différable) : **G10** (mip/aniso)

Après G1+G2+G3+G4 → **~89 GMs supplémentaires débloqués** soit ~20% de couverture en plus (167 → ~256, soit ~58% de couverture totale).

## APIs manquantes — Catalogue détaillé

### API publiques upstream à ajouter

| API Skia upstream | Header upstream | Suggestion `:kanvas-skia` |
|---|---|---|
| `SkRegion::translate(int, int)` + `SkCanvas::clipRegion` | `include/core/SkRegion.h`, `include/core/SkCanvas.h` | `SkRegion.translate(dx, dy)` + `SkCanvas.clipRegion(region, op = kIntersect)` |
| `SkPicture::makeShader(...)` | `include/core/SkPicture.h` | `SkPicture.makeShader(tileX, tileY, filter, localMatrix? = null, tile? = null): SkShader` |
| `SkCanvas::experimental_DrawEdgeAAQuad(...)` + `QuadAAFlags` | `include/core/SkCanvas.h` | enum `QuadAAFlags` + méthode `experimental_DrawEdgeAAQuad(rect, clip?, aaFlags, color, mode)` |
| `SkTableMaskFilter::Create(uint8_t[256])` | `include/effects/SkTableMaskFilter.h` | `SkTableMaskFilter(ByteArray(256)): SkMaskFilter` |
| `SkCanvas::SaveLayerRec` avec `fBackdrop` | `include/core/SkCanvas.h` | `data class SaveLayerRec(bounds?, paint?, backdrop?, flags)` + `SkCanvas.saveLayer(SaveLayerRec)` |
| `SkImageFilters::Blur(σx, σy, tileMode, input, cropRect)` | `include/effects/SkImageFilters.h` | étendre `Blur` avec `tileMode = kDecal, cropRect: SkIRect? = null` |
| `SkOverdrawColorFilter::MakeWithSkColors` | `include/effects/SkOverdrawColorFilter.h` | `SkOverdrawColorFilter.MakeWithSkColors(IntArray(6)): SkColorFilter` |
| `SkCanvas::clipShader(sk_sp<SkShader>)` | `include/core/SkCanvas.h` | `SkCanvas.clipShader(shader, op = kIntersect)` |
| `SkShaderMaskFilter::Make(sk_sp<SkShader>)` | `include/effects/SkShaderMaskFilter.h` | `SkShaderMaskFilter` impl. `SkMaskFilter` qui échantillonne son shader aux coords du mask |
| `SkCubicResampler` + cubic `SkSamplingOptions` | `include/core/SkSamplingOptions.h` | étendre `SkSamplingOptions` avec `cubic: SkCubicResampler?` ; câbler `SkBitmapDevice`/`SkImage.makeShader` |
| `SkParsePath::FromSVGString` | `include/utils/SkParsePath.h` | parser de string SVG path → `SkPath?` |
| `SkCanvas::readPixels(dst, x, y)` | `include/core/SkCanvas.h` | `SkCanvas.readPixels(dst: SkBitmap, x, y): Boolean` |
| `ToolUtils::GetResourceAsImage` / `GetResourceAsBitmap` / `MakeTextureImage` / `draw_checkerboard` | `tools/Resources.h`, `tools/ToolUtils.h` | helpers de chargement de PNG/JPG depuis `kanvas/src/test/resources/images/` |

### Lacunes d'implémentation (API existe, intégration incomplète)

| Symptôme | Fichier `:kanvas-skia` |
|---|---|
| `SkBitmapDevice.compositeFrom` ignore `paint.colorFilter` (layer-paint) | `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt:440` |
| `SkBitmapDevice.drawPaint` ignore `paint.imageFilter` | `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt` |
| `kAlpha_8` `SkBitmap.getPixel/setPixel/eraseColor` lèvent unsupported | `SkBitmap` |
| `kBGRA_8888` `SkBitmap` non supporté côté backing-array | `SkBitmap` |
| Mipmap LOD non sélectionné dans `SkBitmapShader`/`drawImageRect` quand `SkSamplingOptions.mipmap != kNone` | `SkBitmapShader`, `SkBitmapDevice` |
| `SkSamplingOptions::Aniso(maxAniso: Int)` absent | `SkSamplingOptions` |
| `SkImage::withDefaultMipmaps()` absent | `SkImage` |

## Méthodologie

- ✅ **Porté** : ≥ 1 `*GM.kt` correspond au `.cpp` après normalisation (lowercase, sans `_`/`-`), ou contient un `DEF_SIMPLE_GM`/`DEF_GM` du `.cpp` à l'intérieur du fichier. La conformité visuelle n'est pas garantie — voir `MIGRATION_PLAN.md` pour le suivi de qualité.
- 🚧 **Bloqué** : un agent a tenté le portage et abandonné faute d'API `:kanvas-skia`, **ou** le scan automatique a détecté un bloqueur dur (dépendance image, `experimental_DrawEdgeAAQuad`, `SkRegion`, `SkParsePath::FromSVGString`, etc.).
- ❌ **Non tenté** : reste à analyser ; à terme rejoindra ✅ ou 🚧.

## Table de portage

| `.cpp` source | Statut | GMs Kotlin | Bloqué par |
|---|:---:|---|---|
| `3d.cpp` | ❌ | — | — |
| `aaa.cpp` | ✅ | `AnalyticAntialiasConvexGM.kt`, `AnalyticAntialiasGeneralGM.kt`, `AnalyticAntialiasInverseGM.kt` | — |
| `aaclip.cpp` | ✅ | `AaclipGM.kt`, `ClipCubicGM.kt` | — |
| `aarecteffect.cpp` | ❌ | — | — |
| `aarectmodes.cpp` | ✅ | `AaRectModesGM.kt` | — |
| `aaxfermodes.cpp` | ✅ | `AAXfermodesGM.kt` | — |
| `addarc.cpp` | ✅ | `AddArcGM.kt`, `FillCircleGM.kt`, `StrokeCircleGM.kt` | — |
| `all_bitmap_configs.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `alpha_image.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `alphagradients.cpp` | ✅ | `AlphaGradientsGM.kt` | — |
| `analytic_gradients.cpp` | ✅ | `AnalyticGradientShaderGM.kt` | — |
| `androidblendmodes.cpp` | ✅ | `AndroidBlendModesGM.kt` | — |
| `animated_gif.cpp` | ❌ | — | — |
| `animated_image_orientation.cpp` | ❌ | — | — |
| `animatedimageblurs.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `anisotropic.cpp` | ✅ | `AnisotropicMipGM.kt` | — |
| `annotated_text.cpp` | ✅ | `AnnotatedTextGM.kt` | — |
| `arcofzorro.cpp` | ✅ | `ArcOfZorroGM.kt` | — |
| `arcto.cpp` | ✅ | `ArcToGM.kt`, `Bug593049GM.kt` | — |
| `arithmode.cpp` | ✅ | `ArithmodeGM.kt`, `ArithmodeBlenderGM.kt` | — |
| `asyncrescaleandread.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `attributes.cpp` | ❌ | — | — |
| `b_119394958.cpp` | ✅ | `B119394958GM.kt` | — |
| `backdrop.cpp` | ❌ | — | — |
| `backdrop_imagefilter_croprect.cpp` | ❌ | — | — |
| `badpaint.cpp` | ✅ | `BadPaintGM.kt` | — |
| `batchedconvexpaths.cpp` | ✅ | `BatchedConvexPathsGM.kt` | — |
| `bc1_transparency.cpp` | ❌ | — | — |
| `beziereffects.cpp` | ❌ | — | — |
| `beziers.cpp` | ✅ | `BeziersGM.kt` | — |
| `bicubic.cpp` | 🚧 | — | `SkCubicResampler`, `SkImage::makeShader` |
| `bigblurs.cpp` | ✅ | `BigBlursGM.kt` | — |
| `bigmatrix.cpp` | ✅ | `BigMatrixGM.kt` | — |
| `bigrect.cpp` | ✅ | `BigRectGM.kt` | — |
| `bigrrectaaeffect.cpp` | ❌ | — | — |
| `bigtext.cpp` | ✅ | `BigTextGM.kt` | — |
| `bigtileimagefilter.cpp` | ❌ | — | — |
| `bitmapcopy.cpp` | 🚧 | — | Alpha8 `SkBitmap`/`SkImage` |
| `bitmapfilters.cpp` | 🚧 | — | colortype `kRGBA_4444` |
| `bitmapimage.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `bitmappremul.cpp` | 🚧 | — | colortype `kRGBA_4444` |
| `bitmaprect.cpp` | ✅ | `BitmapRectRoundingGM.kt`, `DrawBitmapRect3GM.kt` | — |
| `bitmaprecttest.cpp` | ✅ | `BitmapRectTestGM.kt` | — |
| `bitmapshader.cpp` | 🚧 | — | `SkPicture::makeShader` |
| `bitmaptiled.cpp` | ❌ | — | — |
| `bleed.cpp` | 🚧 | — | `SkCubicResampler` |
| `blurcircles.cpp` | ✅ | `BlurCirclesGM.kt` | — |
| `blurcircles2.cpp` | ❌ | — | — |
| `blurignorexform.cpp` | ❌ | — | — |
| `blurimagevmask.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `blurpositioning.cpp` | ✅ | `BlurPositioningGM.kt` | — |
| `blurquickreject.cpp` | ✅ | `BlurQuickRejectGM.kt` | — |
| `blurrect.cpp` | ❌ | — | — |
| `blurredclippedcircle.cpp` | ✅ | `BlurredClippedCircleGM.kt` | — |
| `blurroundrect.cpp` | ✅ | `SimpleBlurRoundRectGM.kt` | — |
| `blurs.cpp` | ✅ | `Blur2RectsGM.kt`, `Blur2RectsNonNinepatchGM.kt` | — |
| `blurtextsmallradii.cpp` | ✅ | `BlurTextSmallRadiiGM.kt` | — |
| `bmpfilterqualityrepeat.cpp` | 🚧 | — | `SkCubicResampler` |
| `bug12866.cpp` | ✅ | `Bug12866GM.kt`, `Bug40810065GM.kt` | — |
| `bug5252.cpp` | ✅ | `Bug5252GM.kt` | — |
| `bug530095.cpp` | ✅ | `Bug530095GM.kt`, `Bug591993GM.kt` | — |
| `bug615686.cpp` | ✅ | `Bug615686GM.kt` | — |
| `bug6643.cpp` | 🚧 | — | `SkPicture::makeShader` |
| `bug6783.cpp` | ✅ | `Bug6783GM.kt` | — |
| `bug9331.cpp` | ✅ | `Bug9331GM.kt` | — |
| `circle_sizes.cpp` | ✅ | `CircleSizesGM.kt` | — |
| `circulararcs.cpp` | ✅ | `Bug406747427GM.kt`, `CircularArcsGM.kt`, `OneBadArcGM.kt` | — |
| `circularclips.cpp` | ✅ | `CircularClipsGM.kt` | — |
| `clear_swizzle.cpp` | ❌ | — | — |
| `clip_error.cpp` | ❌ | — | — |
| `clip_sierpinski_region.cpp` | 🚧 | — | `SkRegion::translate`, `SkCanvas::clipRegion` |
| `clip_strokerect.cpp` | ✅ | `ClipStrokeRectGM.kt` | — |
| `clipdrawdraw.cpp` | ✅ | `ClipDrawDrawGM.kt` | — |
| `clippedbitmapshaders.cpp` | 🚧 | — | `SkCubicResampler` |
| `clipshader.cpp` | 🚧 | — | `SkCanvas::clipShader` |
| `clockwise.cpp` | ❌ | — | — |
| `closedcappedhairlines.cpp` | ❌ | — | — |
| `collapsepaths.cpp` | ✅ | `CollapsePathsGM.kt` | — |
| `color4f.cpp` | ❌ | — | — |
| `coloremoji.cpp` | ❌ | — | — |
| `coloremoji_blendmodes.cpp` | ❌ | — | — |
| `colorfilteralpha8.cpp` | 🚧 | — | Alpha8 `SkBitmap`/`SkImage` |
| `colorfilterimagefilter.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `colorfilters.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `colormatrix.cpp` | ✅ | `ColorMatrixGM.kt` | — |
| `colorspace.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `colorwheel.cpp` | ✅ | `ColorWheelNativeGM.kt` | — |
| `colrv1.cpp` | ❌ | — | — |
| `complexclip.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `complexclip2.cpp` | ❌ | — | — |
| `complexclip3.cpp` | ❌ | — | — |
| `complexclip4.cpp` | ❌ | — | — |
| `complexclip_blur_tiled.cpp` | ❌ | — | — |
| `composecolorfilter.cpp` | ✅ | `ComposeColorFilterGM.kt` | — |
| `composeshader.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `compositor_quads.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `compressed_textures.cpp` | 🚧 | — | `SkCubicResampler` |
| `concavepaths.cpp` | ✅ | `ConcavePathsGM.kt` | — |
| `conicpaths.cpp` | ✅ | `ArcCircleGapGM.kt`, `ConicPathsGM.kt`, `LargeCircleGM.kt`, `LargeOvalsGM.kt` | — |
| `constcolorprocessor.cpp` | ❌ | — | — |
| `convex_all_line_paths.cpp` | ✅ | `ConvexLineOnlyPathsGM.kt` | — |
| `convexpaths.cpp` | ✅ | `ConvexPathsGM.kt` | — |
| `convexpolyclip.cpp` | ❌ | — | — |
| `convexpolyeffect.cpp` | ❌ | — | — |
| `coordclampshader.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `copy_to_4444.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `crbug_1041204.cpp` | ✅ | `Crbug10141204GM.kt` | — |
| `crbug_1073670.cpp` | ✅ | `Crbug1073670GM.kt` | — |
| `crbug_1086705.cpp` | ✅ | `Crbug1086705GM.kt` | — |
| `crbug_1113794.cpp` | ✅ | `Crbug1113794GM.kt` | — |
| `crbug_1139750.cpp` | ✅ | `Crbug1139750GM.kt` | — |
| `crbug_1156804.cpp` | ✅ | `Crbug1156804GM.kt` | — |
| `crbug_1162942.cpp` | 🚧 | — | `SkCanvas::experimental_DrawEdgeAAQuad` |
| `crbug_1167277.cpp` | 🚧 | — | `SkCanvas::experimental_DrawEdgeAAQuad` + `QuadAAFlags` |
| `crbug_1174186.cpp` | 🚧 | — | `SkCanvas::experimental_DrawEdgeAAQuad` |
| `crbug_1174354.cpp` | 🚧 | — | `SkCanvas::SaveLayerRec` avec `fBackdrop` |
| `crbug_1177833.cpp` | 🚧 | — | `SkCanvas::experimental_DrawEdgeAAQuad` |
| `crbug_1257515.cpp` | ✅ | `Crbug1257515GM.kt` | — |
| `crbug_1313579.cpp` | 🚧 | — | `SkCanvas::SaveLayerRec` avec `fBackdrop`, `SkImageFilters::Blur(tileMode, cropRect)` |
| `crbug_224618.cpp` | ❌ | — | — |
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
| `crbug_918512.cpp` | 🚧 | — | `SkBitmapDevice.compositeFrom` ne lit pas `paint.colorFilter` (gap impl.) |
| `crbug_938592.cpp` | ✅ | `Crbug938592GM.kt` | — |
| `crbug_946965.cpp` | ✅ | `Crbug946965GM.kt` | — |
| `crbug_947055.cpp` | ✅ | `Crbug947055GM.kt` | — |
| `crbug_996140.cpp` | ✅ | `Crbug996140GM.kt` | — |
| `crop_imagefilter.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `croppedrects.cpp` | ✅ | `CroppedRectsGM.kt` | — |
| `crosscontextimage.cpp` | ❌ | — | — |
| `cubicpaths.cpp` | ✅ | `Bug5099GM.kt`, `Bug6083GM.kt`, `ClippedCubicGM.kt`, `ClippedCubic2GM.kt`, `CubicClosePathGM.kt`, `CubicPathGM.kt`, `CubicPathShaderGM.kt` | — |
| `daa.cpp` | ❌ | — | — |
| `dashcircle.cpp` | ❌ | — | — |
| `dashcubics.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `dashing.cpp` | ✅ | `DashingGM.kt`, `LongWavyLineGM.kt`, `PathEffectGM.kt` | — |
| `degeneratesegments.cpp` | ❌ | — | — |
| `destcolor.cpp` | ✅ | `DestColorGM.kt` | — |
| `dftext.cpp` | ❌ | — | — |
| `dftext_blob_persp.cpp` | ❌ | — | — |
| `discard.cpp` | ❌ | — | — |
| `displacement.cpp` | ❌ | — | — |
| `distantclip.cpp` | ✅ | `DistantClipGM.kt` | — |
| `draw_bitmap_rect_skbug4374.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `drawable.cpp` | ✅ | `DrawableGM.kt` | — |
| `drawatlas.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `drawatlascolor.cpp` | ❌ | — | — |
| `drawbitmaprect.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `drawglyphs.cpp` | ❌ | — | — |
| `drawimageset.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `drawlines_with_local_matrix.cpp` | ✅ | `DrawlinesWithLocalMatrixGM.kt` | — |
| `drawminibitmaprect.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `drawquadset.cpp` | 🚧 | — | `SkCanvas::experimental_DrawEdgeAAQuad` |
| `drawregion.cpp` | 🚧 | — | `SkRegion::*` API |
| `drawregionmodes.cpp` | 🚧 | — | `SkRegion::*` API |
| `dropshadowimagefilter.cpp` | ✅ | `DropShadowImageFilterGM.kt` | — |
| `drrect.cpp` | ✅ | `DRRectGM.kt` | — |
| `drrect_small_inner.cpp` | ✅ | `DRRectSmallInnerGM.kt` | — |
| `dstreadshuffle.cpp` | 🚧 | — | colortype `kBGRA_8888` |
| `ducky_yuv_blend.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `emboss.cpp` | ❌ | — | — |
| `emptypath.cpp` | ✅ | `EmptyPathGM.kt` | — |
| `emptyshader.cpp` | ✅ | `EmptyShaderGM.kt` | — |
| `encode.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `encode_alpha_jpeg.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `encode_color_types.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `encode_platform.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `encode_srgb.cpp` | ❌ | — | — |
| `exoticformats.cpp` | ❌ | — | — |
| `fadefilter.cpp` | ✅ | `FadeFilterGM.kt` | — |
| `fatpathfill.cpp` | ✅ | `FatPathFillGM.kt` | — |
| `fiddle.cpp` | ✅ | `FiddleGM.kt` | — |
| `fillrect_gradient.cpp` | ✅ | `FillrectGradientGM.kt` | — |
| `filltypes.cpp` | ✅ | `FillTypeGM.kt`, `FillTypesGM.kt` | — |
| `filltypespersp.cpp` | ✅ | `FillTypePerspGM.kt` | — |
| `filterbug.cpp` | 🚧 | — | `SkPicture::makeShader` |
| `filterfastbounds.cpp` | ❌ | — | — |
| `filterindiabox.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `flippity.cpp` | ❌ | — | — |
| `fontations.cpp` | ❌ | — | — |
| `fontations_ft_compare.cpp` | ❌ | — | — |
| `fontcache.cpp` | ❌ | — | — |
| `fontmgr.cpp` | ❌ | — | — |
| `fontregen.cpp` | ❌ | — | — |
| `fontscaler.cpp` | ❌ | — | — |
| `fontscalerdistortable.cpp` | ❌ | — | — |
| `fp_sample_chaining.cpp` | ❌ | — | — |
| `fpcoordinateoverride.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `fwidth_squircle.cpp` | ❌ | — | — |
| `gammatext.cpp` | ❌ | — | — |
| `getpostextpath.cpp` | ✅ | `GetPosTextPathGM.kt` | — |
| `giantbitmap.cpp` | ❌ | — | — |
| `glyph_pos.cpp` | ❌ | — | — |
| `gm.cpp` | ❌ | — | — |
| `gpu_blur_utils.cpp` | ❌ | — | — |
| `gradient_dirty_laundry.cpp` | ❌ | — | — |
| `gradient_matrix.cpp` | ❌ | — | — |
| `gradients.cpp` | ✅ | `ClampedGradientsGM.kt`, `GradientsGM.kt`, `RgbwSweepGradientGM.kt`, `SmallColorStopGM.kt`, `SweepTilingGM.kt` | — |
| `gradients_2pt_conical.cpp` | ❌ | — | — |
| `gradients_degenerate.cpp` | ❌ | — | — |
| `gradients_no_texture.cpp` | ❌ | — | — |
| `gradtext.cpp` | ✅ | `ChromeGradTextGM.kt`, `GradTextGM.kt` | — |
| `graphite_replay.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `graphitestart.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `grayscalejpg.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `hairlines.cpp` | ✅ | `HairlineSubdivGM.kt`, `HairlinesGM.kt`, `SquareHairGM.kt` | — |
| `hairmodes.cpp` | ✅ | `HairModesGM.kt` | — |
| `hardstop_gradients.cpp` | ✅ | `HardstopGradientShaderGM.kt` | — |
| `hardstop_gradients_many.cpp` | ✅ | `HardstopGradientsManyGM.kt` | — |
| `hdr_pip_blur.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `hello_bazel_world.cpp` | 🚧 | — | GM Bazel-only (pas de PNG de référence) |
| `highcontrastfilter.cpp` | ❌ | — | — |
| `hittestpath.cpp` | ❌ | — | — |
| `hsl.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `hugepath.cpp` | ❌ | — | — |
| `image.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `image_pict.cpp` | ❌ | — | — |
| `image_shader.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `imageblur.cpp` | ❌ | — | — |
| `imageblur2.cpp` | ❌ | — | — |
| `imageblurclampmode.cpp` | ❌ | — | — |
| `imageblurrepeatmode.cpp` | ❌ | — | — |
| `imageblurtiled.cpp` | ❌ | — | — |
| `imagedither.cpp` | ✅ | `ImageDitherGM.kt` | — |
| `imagefilters.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `imagefiltersbase.cpp` | 🚧 | — | `SkCubicResampler` |
| `imagefiltersclipped.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `imagefilterscropexpand.cpp` | ❌ | — | — |
| `imagefilterscropped.cpp` | ❌ | — | — |
| `imagefiltersgraph.cpp` | ❌ | — | — |
| `imagefiltersscaled.cpp` | ❌ | — | — |
| `imagefiltersstroked.cpp` | ❌ | — | — |
| `imagefilterstransformed.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `imagefiltersunpremul.cpp` | 🚧 | — | `SkBitmapDevice.drawPaint` ignore `paint.imageFilter` (gap impl.) |
| `imagefromyuvtextures.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `imagemagnifier.cpp` | ❌ | — | — |
| `imagemakewithfilter.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `imagemasksubset.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `imageresizetiled.cpp` | ❌ | — | — |
| `imagesource.cpp` | ❌ | — | — |
| `imagesource2.cpp` | ❌ | — | — |
| `internal_links.cpp` | ❌ | — | — |
| `inverseclip.cpp` | ✅ | `InverseClipGM.kt` | — |
| `inversepaths.cpp` | ✅ | `InversePathsGM.kt`, `InverseWindingmodeFiltersGM.kt` | — |
| `jpg_color_cube.cpp` | ❌ | — | — |
| `kawase_blur_rt.cpp` | ✅ | `KawaseBlurRtGM.kt` | — |
| `labyrinth.cpp` | ❌ | — | — |
| `largeclippedpath.cpp` | ✅ | `LargeClippedPathGM.kt` | — |
| `largeglyphblur.cpp` | ✅ | `LargeGlyphBlurGM.kt` | — |
| `lattice.cpp` | 🚧 | — | colortype `kBGRA_8888` |
| `lazytiling.cpp` | ❌ | — | — |
| `lcdblendmodes.cpp` | ❌ | — | — |
| `lcdoverlap.cpp` | ❌ | — | — |
| `lcdtext.cpp` | ❌ | — | — |
| `lighting.cpp` | ✅ | `LightingGM.kt` | — |
| `linepaths.cpp` | ✅ | `LinePathGM.kt` | — |
| `localmatriximagefilter.cpp` | ❌ | — | — |
| `localmatriximageshader.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `localmatrixshader.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `lumafilter.cpp` | ✅ | `LumaFilterGM.kt` | — |
| `luminosity.cpp` | ✅ | `LuminosityOverflowGM.kt` | — |
| `mac_aa_explorer.cpp` | 🚧 | — | Alpha8 `SkBitmap`/`SkImage` |
| `make_raster_image.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `makecolorspace.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `mandoline.cpp` | ❌ | — | — |
| `manypathatlases.cpp` | ❌ | — | — |
| `manypaths.cpp` | ✅ | `ManyCirclesGM.kt`, `ManyRRectsGM.kt` | — |
| `matrixconvolution.cpp` | ❌ | — | — |
| `matriximagefilter.cpp` | ✅ | `MatrixImageFilterGM.kt` | — |
| `mesh.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `mipmap.cpp` | 🚧 | — | `SkCubicResampler` |
| `mirrortile.cpp` | ✅ | `MirrorTileGM.kt` | — |
| `mixedtextblobs.cpp` | ❌ | — | — |
| `mixercolorfilter.cpp` | ❌ | — | — |
| `modecolorfilters.cpp` | ❌ | — | — |
| `morphology.cpp` | ✅ | `MorphologyGM.kt` | — |
| `nearesthalfpixelimage.cpp` | 🚧 | — | Alpha8 `SkBitmap`/`SkImage` |
| `nested.cpp` | ✅ | `NestedGM.kt` | — |
| `ninepatchstretch.cpp` | ❌ | — | — |
| `nonclosedpaths.cpp` | ✅ | `NonClosedPathsGM.kt` | — |
| `offsetimagefilter.cpp` | ❌ | — | — |
| `orientation.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `ovals.cpp` | ✅ | `OvalGM.kt` | — |
| `overdrawcanvas.cpp` | 🚧 | — | `SkOverdrawColorFilter` |
| `overdrawcolorfilter.cpp` | 🚧 | — | `SkOverdrawColorFilter`, Alpha8 `SkImage` |
| `overstroke.cpp` | ❌ | — | — |
| `p3.cpp` | ❌ | — | — |
| `palette.cpp` | ❌ | — | — |
| `patch.cpp` | ✅ | `PatchAlphaTestGM.kt`, `PatchPrimitiveGM.kt` | — |
| `path_stroke_with_zero_length.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `patharcto.cpp` | 🚧 | — | `SkParsePath::FromSVGString` |
| `pathcontourstart.cpp` | ❌ | — | — |
| `patheffects.cpp` | ❌ | — | — |
| `pathfill.cpp` | ✅ | `Bug7792GM.kt`, `RotatedCubicPathGM.kt` | — |
| `pathinterior.cpp` | ✅ | `PathInteriorGM.kt` | — |
| `pathmaskcache.cpp` | ✅ | `PathMaskCacheGM.kt` | — |
| `pathmeasure.cpp` | 🚧 | — | Upstream désactivé (`#if 0`) — pas de PNG de référence |
| `pathopsblend.cpp` | ✅ | `PathOpsBlendGM.kt` | — |
| `pathopsinverse.cpp` | ✅ | `PathOpsInverseGM.kt` | — |
| `pathreverse.cpp` | ❌ | — | — |
| `pdf_never_embed.cpp` | ❌ | — | — |
| `perlinnoise.cpp` | ✅ | `PerlinNoiseGM.kt`, `PerlinNoiseRotatedGM.kt` | — |
| `perspimages.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `perspshaders.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `persptext.cpp` | ❌ | — | — |
| `picture.cpp` | ✅ | `PictureGM.kt`, `PictureCullRectGM.kt` | — |
| `pictureimagefilter.cpp` | ❌ | — | — |
| `pictureimagegenerator.cpp` | ❌ | — | — |
| `pictureshader.cpp` | 🚧 | — | `SkPicture::makeShader` |
| `pictureshadercache.cpp` | 🚧 | — | `SkPicture::makeShader` |
| `pictureshadertile.cpp` | 🚧 | — | `SkPicture::makeShader` |
| `plus.cpp` | ✅ | `PlusMergesAaGM.kt` | — |
| `points.cpp` | ❌ | — | — |
| `poly2poly.cpp` | ❌ | — | — |
| `polygonoffset.cpp` | ❌ | — | — |
| `polygons.cpp` | ✅ | `ConjoinedPolygonsGM.kt`, `PolygonsGM.kt` | — |
| `postercircle.cpp` | ❌ | — | — |
| `preservefillrule.cpp` | ❌ | — | — |
| `quadpaths.cpp` | ✅ | `QuadClosePathGM.kt`, `QuadPathGM.kt` | — |
| `radial_gradient_precision.cpp` | ✅ | `RadialGradientPrecisionGM.kt` | — |
| `rasterhandleallocator.cpp` | ❌ | — | — |
| `readpixels.cpp` | 🚧 | — | colortype `kBGRA_8888` |
| `recordopts.cpp` | ❌ | — | — |
| `rect_poly_stroke.cpp` | ✅ | `RectPolyStrokeGM.kt` | — |
| `rectangletexture.cpp` | 🚧 | — | `SkPicture::makeShader` |
| `rendertomipmappedyuvimageplanes.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `repeated_bitmap.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `resizeimagefilter.cpp` | 🚧 | — | `SkCubicResampler` |
| `rippleshadergm.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `roundrects.cpp` | ✅ | `RoundRectGM.kt` | — |
| `rrect.cpp` | ✅ | `RRectGM.kt` | — |
| `rrectclipdrawpaint.cpp` | ✅ | `RRectClipDrawPaintGM.kt` | — |
| `rrects.cpp` | ❌ | — | — |
| `rsxtext.cpp` | 🚧 | — | `SkPicture::makeShader` |
| `runtimecolorfilter.cpp` | ✅ | `RuntimeColorFilterGM.kt` | — |
| `runtimefunctions.cpp` | ✅ | `RuntimeFunctionsGM.kt` | — |
| `runtimeimagefilter.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `runtimeintrinsics.cpp` | ✅ | `RuntimeIntrinsicsCommonGM.kt`, `RuntimeIntrinsicsExponentialGM.kt`, `RuntimeIntrinsicsGeometricGM.kt`, `RuntimeIntrinsicsMatrixGM.kt`, `RuntimeIntrinsicsRelationalGM.kt`, `RuntimeIntrinsicsTrigGM.kt` | — |
| `runtimeshader.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `samplerstress.cpp` | ❌ | — | — |
| `savelayer.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `scaledemoji.cpp` | ❌ | — | — |
| `scaledemoji_rendering.cpp` | ❌ | — | — |
| `scaledrects.cpp` | ✅ | `ClipLargeRectGM.kt`, `ScaledRectsGM.kt` | — |
| `scaledstrokes.cpp` | ✅ | `ScaledStrokesGM.kt` | — |
| `shadermaskfilter.cpp` | 🚧 | — | `SkShaderMaskFilter::Make` |
| `shaderpath.cpp` | ✅ | `ShaderPathGM.kt` | — |
| `shadertext3.cpp` | ❌ | — | — |
| `shadowutils.cpp` | ❌ | — | — |
| `shallowgradient.cpp` | ❌ | — | — |
| `shapes.cpp` | ✅ | `InnerShapesGM.kt`, `SimpleShapesGM.kt` | — |
| `sharedcorners.cpp` | ❌ | — | — |
| `showmiplevels.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `simpleaaclip.cpp` | 🚧 | — | `SkRegion::*` API |
| `simplerect.cpp` | ✅ | `SimpleRectGM.kt` | — |
| `skbug1719.cpp` | ✅ | `Skbug1719GM.kt` | — |
| `skbug_12212.cpp` | 🚧 | — | `SkBitmap` accessors pour `kAlpha_8` (gap impl.) |
| `skbug_257.cpp` | ❌ | — | — |
| `skbug_4868.cpp` | ✅ | `Skbug4868GM.kt` | — |
| `skbug_5321.cpp` | ✅ | `Skbug5321GM.kt` | — |
| `skbug_8664.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `skbug_8955.cpp` | ✅ | `Skbug8955GM.kt` | — |
| `skbug_9319.cpp` | ✅ | `Skbug9319GM.kt` | — |
| `skbug_9819.cpp` | 🚧 | — | `kBGRA_8888` `SkBitmap`, `SkCanvas::readPixels` |
| `slug.cpp` | ❌ | — | — |
| `smallarc.cpp` | ✅ | `SmallArcGM.kt` | — |
| `smallcircles.cpp` | ✅ | `SmallCirclesGM.kt` | — |
| `smallpaths.cpp` | ✅ | `SmallPathsGM.kt` | — |
| `spritebitmap.cpp` | ✅ | `SpriteBitmapGM.kt` | — |
| `srcmode.cpp` | ❌ | — | — |
| `srgb.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
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
| `subsetshader.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `surface.cpp` | ❌ | — | — |
| `swizzle.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `tablecolorfilter.cpp` | ❌ | — | — |
| `tablemaskfilter.cpp` | 🚧 | — | `SkTableMaskFilter` |
| `tallstretchedbitmaps.cpp` | ❌ | — | — |
| `testgradient.cpp` | ✅ | `TestGradientGM.kt` | — |
| `texelsubset.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `text_scale_skew.cpp` | ✅ | `TextScaleSkewGM.kt` | — |
| `textblob.cpp` | ✅ | `TextBlobGM.kt` | — |
| `textblobblockreordering.cpp` | ✅ | `TextBlobBlockReorderingGM.kt` | — |
| `textblobcolortrans.cpp` | ✅ | `TextBlobColorTransGM.kt` | — |
| `textblobgeometrychange.cpp` | ❌ | — | — |
| `textblobmixedsizes.cpp` | ❌ | — | — |
| `textblobrandomfont.cpp` | ❌ | — | — |
| `textblobshader.cpp` | ✅ | `TextBlobShaderGM.kt` | — |
| `textblobtransforms.cpp` | ❌ | — | — |
| `textblobuseaftergpufree.cpp` | ❌ | — | — |
| `texteffects.cpp` | ❌ | — | — |
| `thinconcavepaths.cpp` | ✅ | `ThinConcavePathsGM.kt` | — |
| `thinrects.cpp` | ✅ | `ThinRectsGM.kt` | — |
| `thinstrokedrects.cpp` | ✅ | `ThinStrokedRectsGM.kt` | — |
| `tiledscaledbitmap.cpp` | 🚧 | — | `SkCubicResampler` |
| `tileimagefilter.cpp` | ❌ | — | — |
| `tilemodes.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `tilemodes_alpha.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `tilemodes_scaled.cpp` | 🚧 | — | `SkCubicResampler` |
| `tinybitmap.cpp` | ✅ | `TinyBitmapGM.kt` | — |
| `transparency.cpp` | ❌ | — | — |
| `trickycubicstrokes.cpp` | ✅ | `TrickyCubicStrokesGM.kt` | — |
| `typeface.cpp` | ❌ | — | — |
| `unpremul.cpp` | 🚧 | — | colortype `kBGRA_8888` |
| `userfont.cpp` | ❌ | — | — |
| `variedtext.cpp` | ❌ | — | — |
| `vertices.cpp` | ✅ | `VerticesPerspectiveGM.kt` | — |
| `verylargebitmap.cpp` | ❌ | — | — |
| `video_decoder.cpp` | ❌ | — | — |
| `wacky_yuv_formats.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `widebuttcaps.cpp` | ✅ | `WideButtCapsGM.kt` | — |
| `windowrectangles.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `workingspace.cpp` | ❌ | — | — |
| `xfermodeimagefilter.cpp` | ❌ | — | — |
| `xfermodes.cpp` | ✅ | `XfermodesGM.kt` | — |
| `xfermodes2.cpp` | ❌ | — | — |
| `xfermodes3.cpp` | ❌ | — | — |
| `ycbcrimage.cpp` | ❌ | — | — |
| `yuv420_odd_dim.cpp` | 🚧 | — | Chargement d'images (`ToolUtils::GetResource*` indisponible) |
| `yuvtorgbsubset.cpp` | 🚧 | — | Alpha8 `SkBitmap`/`SkImage` |

## GMs Kotlin sans `.cpp` correspondant

Ces 30 fichiers `*GM.kt` n'ont pas pu être rattachés à un `.cpp` du tree de référence (probablement issus d'une version Skia plus récente, ou correspondant à des bugs/crbug pas encore présents en upstream local) :

- `B340982297GM.kt`
- `BlurLargeRRectsGM.kt`
- `ConicalGradients2ptInsideGM.kt`
- `ConicalGradients2ptOutsideGM.kt`
- `CornerDiscretePathEffectGM.kt`
- `Crbug10141204GM.kt`
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
- `PlusMergesAaGM.kt`
- `ShallowGradientConicalGM.kt`
- `ShallowGradientLinearNoditherGM.kt`
- `ShallowGradientRadialNoditherGM.kt`
- `ShallowGradientSweepGM.kt`
- `Skbug13047GM.kt`
- `StrokeRectsRotatedGM.kt`
- `StrokerectAnisotropic5408GM.kt`
- `ThinRoundRectsGM.kt`
- `TrickyCubicStrokesLargeRadiusGM.kt`
- `VerticesCollapsedGM.kt`
- `ZeroControlStrokeGM.kt`
