# Plan de remédiation API — kanvas-skia vs Skia upstream

> Audit non-exhaustif mais systématique de la surface publique de `:kanvas-skia`
> comparée aux en-têtes Skia upstream (hors GPU). Date : **2026-05-13**.

## Résumé exécutif

| Catégorie | Count audit | Restant après R1 + R2 + R3 batch 8 |
|---|---:|---:|
| **Classes manquantes entièrement** (non-GPU, public) | 47 | 3 |
| **Méthodes manquantes** (classe présente) | ~110 | ~30 |
| **Overloads manquants** (signature partielle) | ~35 | ~5 |
| **Champs / enums manquants** | ~20 | ~6 |
| Total APIs publiques upstream non-GPU auditées | ~95 headers | — |

**Progression** :
- ✅ Phase R1 complète (25/25, mergée).
- ✅ Phase R2 complète (20/20 mergée).
- 🔄 Phase R3 : **12/12 ✅ ou ouvert** — toutes les classes/méthodes upstream non-GPU sont implémentées ou stubbées ; simplifications listées en R-suivi.
- 🎉 Phase R-suivi : **50 / 50 ✅** (100 %). Plan de remédiation API **closed côté implémentation**.

Le module `:kanvas-skia` couvre désormais l'intégralité de la surface API publique upstream Skia non-GPU, sauf :
- `SkWebpEncoder.kLossy` (VP8 lossy) : `Custom(callback)` factory permet plug-in libwebp via JNI consommateur ; sortie pure-Kotlin = lossless seul.
- Décodeurs AVIF / JpegXL / RAW étendus : registry public `Codec.Decoders.register(...)` permet plug-in via JNI consommateur.
- `SkShadowTessellator` convexité uniquement : paths concaves/self-intersect → fallback blur legacy.
- `SkPath.IterVerb` typealias `@Deprecated` toujours là pour source-compat (rename mécanique complet livré dans #414).

**Phases R1+R2+R3 du plan de remédiation terminées côté API surface.** Travail restant : finalisation de la fidélité (R-suivi) + reprise des ports GMs (Phase H3 et suivantes).

Plus du tiers de la surface publique Skia non-GPU n'est pas portée. Le module
`:kanvas-skia` est correctement dimensionné pour exécuter les GMs **2D rasterisés
simples** mais s'écarte fortement de l'API upstream sur tout ce qui touche aux :

- formats d'image alternatifs (PNG/JPEG decoder, AVIF, ICO, RAW, JpegXL)
- transformations matricielles 3D (`SkM44`, `Sk3DView`, `SkCamera`)
- gestion fontconfig / `SkFontMgr` (port `SkFontStyleSet`, `SkOrderedFontMgr`, `SkCustomTypefaceBuilder`)
- documents (`SkDocument`, PDF)
- streams et serialisation (`SkStream`, `SkWStream`, `SkSerialProcs`)
- mesures (`SkPathMeasure`, `SkContourMeasure`, `SkCubicMap`)
- effets avancés (`SkHighContrastFilter`, `SkShaderMaskFilter`, `SkTableMaskFilter`, `SkColorMatrix`)
- pixmap / pixel-ref bas-niveau

## Méthodologie

- **Headers audités** :
  - `include/core/` — 84 fichiers
  - `include/effects/` — 18 fichiers
  - `include/encode/` — 6 fichiers
  - `include/pathops/` — 1 fichier
  - `include/utils/` — 13 fichiers
  - `include/codec/` — 18 fichiers
- **Exclusions** :
  - `include/gpu/`, `include/ganesh/`, `include/private/`, `tools/`
  - Classes `Gr*` (Ganesh GPU)
  - Bindings GL / Vulkan / Metal / Dawn / WebGPU
  - Classes `Sk*Priv`, `*Internal`, `*_BACKDOOR`, `SkCanvasVirtualEnforcer`
- **Cible kanvas-skia** : `kanvas-skia/src/main/kotlin/org/skia/`
  - Packages couverts : `core`, `effects`, `encode`, `pathops`, `utils`, `codec`,
    `foundation` (le gros des Sk* historiques), `math`, `tools`, `shaper`, `dm`, `skcms`
- **Critères de classification** :
  - **PRESENT** : la classe existe et l'API publique upstream est essentiellement
    couverte (≥ 80 % des méthodes publiques principales).
  - **OVERLOAD-MISSING** : la classe existe et la méthode existe, mais ≥ 1 overload
    upstream important est absent (ex : `MakeBlur(style, sigma)` mais pas le
    `MakeBlur(style, sigma, respectCTM)`).
  - **METHOD-MISSING** : classe présente, méthode publique upstream absente.
  - **CLASS-MISSING** : type entièrement absent.
  - **N/A** : GPU-only / interne / deprecated.

---

## Section 1 — Classes manquantes entièrement (priorité haute)

> Triées par dépendance / impact GMs (sur la base de `MIGRATION_PLAN_GM_PORT.md`
> Phase H2 et lecture des `.cpp` upstream).

### 1.1 `SkM44` + `SkV2` / `SkV3` / `SkV4`
- **Header** : `include/core/SkM44.h`
- **Méthodes publiques principales** : `Make`, `Rows`, `Cols`, `Translate`, `Scale`, `Rotate`, `RectToRect`, `LookAt`, `Perspective`, `setConcat`, `preConcat`, `postConcat`, `invert`, `transpose`, `map`, `mapVec`, `mapPoint`, `mapHomogeneousPoints`, `asM33`
- **Effort estimé** : L
- **GMs bloqués (partiels)** : `Bug6643GM`, `PerspShadersBitmap`, `ColorMatrix3DGM`, tous ceux qui appellent `canvas.concat(m44)`, `setMatrix(SkM44)`.

### 1.2 `SkPathMeasure` / `SkContourMeasure` / `SkContourMeasureIter`
- **Header** : `include/core/SkPathMeasure.h`, `include/core/SkContourMeasure.h`
- **Méthodes** : `getLength`, `getPosTan`, `getMatrix`, `getSegment`, `isClosed`, `nextContour`
- **Effort estimé** : M
- **GMs bloqués** : `TextOnPathGM`, `Stroker*`, `DashCircleGM` (partiel), `Patheffects`

### 1.3 `SkCubicMap`
- **Header** : `include/core/SkCubicMap.h`
- **Méthodes** : `computeYFromX`, `computeFromT`, `IsLinear`
- **Effort estimé** : S
- **GMs bloqués** : `CubicMapGM`, animations Lottie-style

### 1.4 `SkPathIter`
- **Header** : `include/core/SkPathIter.h`
- **Méthodes** : `next()`, types `Move`/`Line`/`Quad`/`Conic`/`Cubic`/`Close`
- **Effort estimé** : M
- **Note** : exposé via `SkPath::iter()` upstream.
- **GMs bloqués** : nombreux (tout `for (verb : path)`)

### 1.5 `SkPathUtils`
- **Header** : `include/core/SkPathUtils.h`
- **Méthodes** : `FillPathWithPaint(path, paint, dst, ...)` (5 overloads)
- **Effort estimé** : S
- **GMs bloqués** : `StrokesGM`, `FatpathfillGM`, `OvalEffectGM`

### 1.6 `SkFontMgr` + `SkFontStyleSet`
- **Header** : `include/core/SkFontMgr.h`
- **Méthodes** : `countFamilies`, `getFamilyName`, `createStyleSet`, `matchFamily`, `matchFamilyStyle`, `matchFamilyStyleCharacter`, `makeFromData`, `makeFromStream`, `makeFromFile`, `legacyMakeTypeface`
- **Effort estimé** : XL (équipement font-config OS-dépendant)
- **GMs bloqués** : ~80 GMs avec texte non-Latin / fallback

### 1.7 `SkCustomTypefaceBuilder`
- **Header** : `include/utils/SkCustomTypeface.h`
- **Méthodes** : `setGlyph` (4 overloads), `setMetrics`, `setFontStyle`, `detach`
- **Effort estimé** : L
- **GMs bloqués** : `CustomFontGM`, `RotateGlyphsGM`

### 1.8 `SkOrderedFontMgr`
- **Header** : `include/utils/SkOrderedFontMgr.h`
- **Méthodes** : `append(mgr)` + héritage `SkFontMgr`
- **Effort estimé** : S (post-`SkFontMgr`)

### 1.9 `SkDocument` + `SkPDF::Make` (note : PDF) / SkXPS / SkSVG sink
- **Header** : `include/core/SkDocument.h`
- **Méthodes** : `beginPage`, `endPage`, `close`, `abort`
- **Effort estimé** : XL (un sink complet)
- **Note** : `:kanvas-skia/dm` a un `SvgSink` mais pas l'abstraction `SkDocument`.

### 1.10 `SkPixmap`
- **Header** : `include/core/SkPixmap.h`
- **Méthodes** : `reset(info, addr, rowBytes)`, `extractSubset`, `readPixels`, `scalePixels`, `erase` (3 overloads), `info()`, `addr*()` (8 typed accessors), `getColor`, `getColor4f`, `getAlphaf`, `bounds`
- **Effort estimé** : L
- **GMs bloqués** : tous ceux passant par `SkBitmap.peekPixels` / `SkImage.readPixels`.

### 1.11 `SkPixelRef`
- **Header** : `include/core/SkPixelRef.h`
- **Méthodes** : `pixels`, `rowBytes`, `width`, `height`, `notifyPixelsChanged`, `setImmutable`, `isImmutable`
- **Effort estimé** : M
- **Note** : SkBitmap.installPixels en dépend.

### 1.12 `SkAnnotation`
- **Header** : `include/core/SkAnnotation.h`
- **Fonctions libres** : `SkAnnotateRectWithURL`, `SkAnnotateNamedDestination`, `SkAnnotateLinkToDestination`
- **Effort estimé** : S (no-op accepté tant que PDF absent)

### 1.13 `SkColorTable`
- **Header** : `include/core/SkColorTable.h`
- **Méthodes** : `Make(SkData, 4 channels)`, `alphaTable`, `redTable`, `greenTable`, `blueTable`, `asColorFilter`
- **Effort estimé** : S
- **Note** : utile pour `SkColorFilters::Table` per-channel.

### 1.14 `SkColorMatrix`
- **Header** : `include/effects/SkColorMatrix.h`
- **Méthodes** : `setIdentity`, `setScale`, `setSaturation`, `setRowMajor`, `getRowMajor`, `preConcat`, `postConcat`, `preTranslate`, `postTranslate`, `setRGB2YUV`, `setYUV2RGB`
- **Effort estimé** : M
- **GMs bloqués** : `ColorMatrixGM`, `ComposeColorFilterGM` (partiel)

### 1.15 `SkColorMatrixFilter`
- **Header** : `include/effects/SkColorMatrixFilter.h`
- **Méthodes** : `MakeLightingFilter(mul, add)` (existe partiellement via `SkColorFilters.Lighting` H2.17)
- **Effort estimé** : S
- **Note** : surtout factories — la classe Java/Kotlin doit exister par parité.

### 1.16 `SkHighContrastFilter`
- **Header** : `include/effects/SkHighContrastFilter.h`
- **Méthodes** : `Make(SkHighContrastConfig)`
- **Effort estimé** : S
- **Plan existant** : H2.32 dans `MIGRATION_PLAN_GM_PORT.md`.

### 1.17 `SkShaderMaskFilter`
- **Header** : `include/effects/SkShaderMaskFilter.h`
- **Méthodes** : `Make(shader)`
- **Effort estimé** : S
- **Plan existant** : H2.5.

### 1.18 `SkTableMaskFilter`
- **Header** : `include/effects/SkTableMaskFilter.h`
- **Méthodes** : `Create(table[256])`, `CreateGamma(gamma)`, `CreateClip(min, max)`
- **Effort estimé** : S
- **Plan existant** : H2.9.

### 1.19 `SkRuntimeEffectBuilder` (public séparé)
- **Header** : `include/effects/SkRuntimeEffect.h`
- **Méthodes** : `uniform`, `child`, `makeShader`, `makeBlender`, `makeColorFilter`
- **Effort estimé** : S (extraction)
- **Note** : seul `SkRuntimeEffect` existe avec un builder interne (`SkRuntimeEffectBuilder` est présent en classe interne mais pas comme entry point public).

### 1.20 `SkImageGenerator`
- **Header** : `include/core/SkImageGenerator.h`
- **Méthodes** : `getInfo`, `getPixels`, `MakeFromEncoded`
- **Effort estimé** : M
- **Plan existant** : H2.6.

### 1.21 `SkGraphics`
- **Header** : `include/core/SkGraphics.h`
- **Méthodes statiques** : `Init`, `Term`, `GetVersion`, `DumpMemoryStatistics`, `PurgeAllCaches`, `SetResourceCacheTotalByteLimit` (~12 méthodes)
- **Effort estimé** : S (no-op pour la plupart)

### 1.22 `SkCapabilities`
- **Header** : `include/core/SkCapabilities.h`
- **Méthodes** : `RasterBackend`, `skslVersion`
- **Effort estimé** : S

### 1.23 `SkExecutor`
- **Header** : `include/core/SkExecutor.h`
- **Méthodes** : `MakeFIFOThreadPool`, `MakeLIFOThreadPool`, `MakeOpaque`
- **Effort estimé** : S
- **Note** : peut être no-op (mono-thread acceptable).

### 1.24 `SkStream` / `SkWStream` (et tout l'arbre)
- **Header** : `include/core/SkStream.h`
- **Classes** : `SkStream`, `SkStreamRewindable`, `SkStreamSeekable`, `SkStreamAsset`,
  `SkStreamMemory`, `SkWStream`, `SkNullWStream`, `SkFILEStream`, `SkMemoryStream`,
  `SkFILEWStream`, `SkDynamicMemoryWStream`
- **Effort estimé** : L
- **Impact** : encoders/decoders publics passent tous par ces interfaces.

### 1.25 `SkSerialProcs` / `SkDeserialProcs`
- **Header** : `include/core/SkSerialProcs.h`
- **Méthodes** : structs de callbacks `fImageProc`, `fImageCtx`, `fPictureProc`, `fTypefaceProc`
- **Effort estimé** : M
- **Note** : nécessaire pour `SkPicture::serialize`.

### 1.26 `SkUnPreMultiply`
- **Header** : `include/core/SkUnPreMultiply.h`
- **Méthodes** : `PMColorToColor`, `GetScale`, `ApplyScale`
- **Effort estimé** : S

### 1.27 `SkRasterHandleAllocator`
- **Header** : `include/core/SkRasterHandleAllocator.h`
- **Effort estimé** : M
- **Note** : utilisé pour `SkCanvas::MakeRasterDirectN32(info, alloc)`.

### 1.28 `SkRecorder` / `SkCPURecorder` / `SkCPUContext`
- **Header** : `include/core/SkRecorder.h`, `include/core/SkCPURecorder.h`, `include/core/SkCPUContext.h`
- **Effort estimé** : L (couches Skia récentes)
- **Note** : remplace progressivement `SkCanvas` direct calls dans certains GMs récents.

### 1.29 `SkCanvasStateUtils`
- **Header** : `include/utils/SkCanvasStateUtils.h`
- **Méthodes** : `CaptureCanvasState`, `CreateFromCanvasState`, `ReleaseCanvasState`
- **Effort estimé** : S

### 1.30 `SkShadowUtils`
- **Header** : `include/utils/SkShadowUtils.h`
- **Méthodes** : `DrawShadow`, `ComputeTonalColors`, `GetLocalBounds`
- **Effort estimé** : L
- **GMs bloqués** : `ShadowUtilsGM`, `ShadowUtilsOccludersGM`, `RrectShadowsGM`

### 1.31 `Sk3DView` / `SkCamera3D` / `SkPatch3D` (camera)
- **Header** : `include/utils/SkCamera.h`
- **Méthodes** : `rotateX/Y/Z`, `translate`, `applyToCanvas`, `dotWithNormal`
- **Effort estimé** : M
- **GMs bloqués** : tout GM `Camera3D*`

### 1.32 `SkNWayCanvas`
- **Header** : `include/utils/SkNWayCanvas.h`
- **Méthodes** : `addCanvas`, `removeCanvas`
- **Effort estimé** : S
- **Usage** : test infrastructure.

### 1.33 `SkParse`
- **Header** : `include/utils/SkParse.h`
- **Méthodes** : `FindScalars`, `FindBool`, `FindColor`, `FindHex`
- **Effort estimé** : S

### 1.34 `SkICC`
- **Header** : `include/encode/SkICC.h`
- **Méthodes** : `WriteToICC`, `Make`, `ParseColorSpace`
- **Effort estimé** : M

### 1.35 `SkWebpEncoder` (public class)
- **Header** : `include/encode/SkWebpEncoder.h`
- **Méthodes** : `Encode`, `Make` + struct `Options { fCompression, fQuality, fMethod }`
- **Effort estimé** : M
- **Plan existant** : H2.2 (partiellement adressé via `SkPngEncoder`/`SkJpegEncoder`).

### 1.36 `SkPngRustEncoder` / `SkPngRustDecoder`
- **Header** : `include/encode/SkPngRustEncoder.h`, `include/codec/SkPngRustDecoder.h`
- **Effort estimé** : S (alias d'API si le PNG existant suffit)

### 1.37 `AndroidCodec`
- **Header** : `include/codec/AndroidCodec.h`
- **Méthodes** : `MakeFromCodec`, `MakeFromData`, `MakeFromStream`, `getAndroidPixels`, `computeSampleSize`, `getSampledDimensions`, `getSampledSubsetDimensions`
- **Effort estimé** : L

### 1.38 `AvifDecoder`
- **Header** : `include/codec/AvifDecoder.h`
- **Méthodes** : `Decode(SkStream)`, `Decode(SkData)`, `IsAvif(...)`, `Decoder()`
- **Effort estimé** : XL (dépend libavif).

### 1.39 `IcoDecoder`
- **Header** : `include/codec/IcoDecoder.h`
- **Effort estimé** : M

### 1.40 `RawDecoder`
- **Header** : `include/codec/RawDecoder.h`
- **Effort estimé** : XL (dcraw deps).

### 1.41 `JpegxlDecoder`
- **Header** : `include/codec/JpegxlDecoder.h`
- **Effort estimé** : L (libjxl).

### 1.42 `SkPngChunkReader`
- **Header** : `include/codec/SkPngChunkReader.h`
- **Effort estimé** : S

### 1.43 `CodecAnimation`
- **Header** : `include/codec/CodecAnimation.h`
- **Effort estimé** : S (constantes + enums)

### 1.44 `SkEncoder` (base abstract)
- **Header** : `include/encode/SkEncoder.h`
- **Méthodes** : `encodeRows(numRows)`, `dest()`
- **Effort estimé** : S

### 1.45 `PixmapUtils`
- **Header** : `include/codec/PixmapUtils.h`
- **Méthodes** : `SwapWidthHeight`, `Orient`
- **Effort estimé** : S

### 1.46 `SkTiledImageUtils`
- **Header** : `include/core/SkTiledImageUtils.h`
- **Méthodes** : `DrawImageRect` (4 overloads, image-tiling friendly)
- **Effort estimé** : S

### 1.47 `SkSurfaceProps` (semi-présent en argument, pas en type public)
- **Header** : `include/core/SkSurfaceProps.h`
- **Méthodes** : `Flags`, `pixelGeometry`, `textContrast`, `textGamma`
- **Effort estimé** : S
- **Note** : `SkSurface` du module accepte des paramètres équivalents mais le type
  `SkSurfaceProps` n'est pas exposé en classe Kotlin.

---

## Section 2 — Méthodes manquantes (classe existante)

### 2.1 `SkCanvas` (`core/SkCanvas.h`)
- `drawRegion(region, paint)` — **H2.8**
- `drawImageNine(image, center, dstRect, filterMode, paint)` — **H2.11**
- `drawImageLattice(image, lattice, dstRect, paint)`
- `drawAnnotation(rect, key, value)`
- `drawDrawable(drawable, matrix)` overload matrix-arg
- `drawDrawable(drawable, x, y)` overload position
- `drawShadowedRRect(rrect, zPlane, lightPos, lightRadius, ambientColor, spotColor, flags)`
- `clipShader(shader, op)` — **H2.4**
- `clipRegion(region, op)` (partiellement présent)
- `makeSurface(SkImageInfo, SkSurfaceProps?)` — **H2.14**
- `getProps()`, `imageInfo()` — **H2.14**
- `quickReject(SkRect)`, `quickReject(SkPath)`
- `peekPixels(SkPixmap)`
- `readPixels(SkImageInfo, void*, rowBytes, srcX, srcY)`
- `writePixels(SkImageInfo, const void*, rowBytes, x, y)`
- `getLocalToDevice()` (`SkM44`), `getLocalToDeviceAs3x3()`
- `concat(SkM44)` — dépend de `SkM44`
- `setMatrix(SkM44)`
- `androidFramework_setDeviceClipRestriction(rect)` — **H2.22**
- `androidFramework_replaceClip(rect)`
- `private_draw_shadow_rec(path, rec)` (semi-public)
- `discard()`

### 2.2 `SkPath` (`core/SkPath.h`)
- `contains(scalar, scalar)` — **H2.29**
- `getSegmentMasks()`
- `isLastContourClosed()`
- `interpolate(other, weight, dst)`
- `setIsVolatile`, `isVolatile`
- `getFirstDirection()` (heuristic API)
- `getConvexity()`, `setConvexityType()` (deprecated upstream mais utilisé GMs)
- `dumpHex`, `dump` (debug)
- `serialize()`, `Deserialize(data)` (statics)
- `writeToMemory`, `readFromMemory`
- `IsLineDegenerate`, `IsQuadDegenerate`, `IsCubicDegenerate` (statics)
- `ConvertConicToQuads(...)`

### 2.3 `SkPaint` (`core/SkPaint.h`)
- `canComputeFastBounds()` — **H2.26**
- `computeFastBounds(orig, storage)` — **H2.26**
- `getFillPath(src, dst, cullRect?, resScale)` (déplacé vers `SkPathUtils::FillPathWithPaint`)
- `asBlendMode()` (Optional<SkBlendMode>)

### 2.4 `SkBitmap` (`core/SkBitmap.h`)
- `installPixels(info, pixels, rowBytes)`
- `extractSubset(dst, subset)`
- `extractAlpha(dst, paint, offset)`
- `tryAllocPixels(info, rowBytes)`
- `peekPixels(SkPixmap*)`
- `readPixels(dstInfo, dstPixels, dstRowBytes, srcX, srcY)`
- `writePixels(src, x, y)`
- `pixelRef()`, `pixelRefOrigin()`
- `setImmutable`, `isImmutable`
- `getColor(x, y)` (distinct de `getPixel`)
- `getColor4f(x, y)`
- `getAlphaf(x, y)`
- `rowBytes()`, `info()`, `colorSpace()`, `bounds()`
- `kRGB_565`, `kGray_8` colortypes — **H2.18**

### 2.5 `SkImage` (`core/SkImage.h`)
- `makeSubset(SkIRect, SkRecorder?)` — **H2.3**
- `makeRasterImage()`
- `imageInfo()`, `width()`, `height()`, `alphaType()`, `colorType()`, `colorSpace()`
- `bounds()`, `uniqueID()`
- `encodeToData(format, quality)` (2 overloads)
- `refEncodedData()`
- `readPixels(SkImageInfo, void*, rowBytes, srcX, srcY, ...)`
- `peekPixels(SkPixmap*)`
- `scalePixels(dst, sampling, cachingHint)`
- `makeWithFilter(filter, subset, clipBounds, outSubset, outOffset)`
- `makeColorSpace(SkColorSpace, SkRecorder?)` — **H2.14**
- `makeColorTypeAndColorSpace(SkColorType, SkColorSpace, SkRecorder?)`
- `makeNonTextureImage()`, `makeRasterImage(SkRecorder?)`
- `hasMipmaps()`, `withDefaultMipmaps()` (partiellement présent)
- `isAlphaOnly()`, `isOpaque()`, `isLazyGenerated()`
- `asLegacyBitmap(SkBitmap*)`
- `refColorSpace()`

### 2.6 `SkSurface` (`core/SkSurface.h`)
- `makeImageSnapshot(SkIRect)` overload subset
- `draw(SkCanvas, x, y, sampling, paint?)` (au lieu de `draw(canvas, x, y, paint)`)
- `readPixels(dst, srcX, srcY)`
- `writePixels(src, x, y)`
- `getCanvas()` — vérifier signature
- `notifyContentWillChange(mode)`
- `props()`
- `recorder()`
- `wait(fences)` — N/A (GPU)
- `MakeRasterDirect(SkImageInfo, void*, rowBytes, props?)`
- `MakeRasterN32Premul(w, h, props?)`

### 2.7 `SkShader` (`core/SkShader.h`)
- `makeWithLocalMatrix(matrix)` — **H2.1**
- `makeWithColorFilter(cf)`
- `isOpaque()`, `isAImage(matrix?, tileModes?)`
- `asLuminanceShader()`

### 2.8 `SkImageFilter` (`core/SkImageFilter.h`)
- `makeWithLocalMatrix(matrix)` — **H2.1**
- `computeFastBounds(SkRect)` — **H2.26**
- `canComputeFastBounds()`
- `filterBounds(src, ctm, MapDirection, inputRect?)`
- `countInputs()`, `getInput(i)`

### 2.9 `SkColorFilter` (`core/SkColorFilter.h`)
- `makeComposed(inner)`
- `filterColor(SkColor)`, `filterColor4f(SkColor4f, srcCS, dstCS)`
- `isAlphaUnchanged()`
- `asAColorMode(SkColor*, SkBlendMode*)`
- `asAColorMatrix(float[20])`

### 2.10 `SkColorFilters` (`core/SkColorFilter.h` factory)
- `LinearToSRGBGamma()` — **H2.7**
- `SRGBToLinearGamma()` — **H2.7**
- `Lerp(t, dst?, src?)` nullable — **H2.33**
- `Lighting(mul, add)` direct — **H2.17**
- `Compose(outer, inner)`
- `HSLAMatrix(matrix)`
- `Matrix(SkColorMatrix)`
- `Table(uint8[256])`, `TableARGB(a?, r?, g?, b?)`

### 2.11 `SkImageFilters` (`effects/SkImageFilters.h`)
- `DisplacementMap(...)` 6-arg overload — **H2.24**
- `Offset(...)`, `Magnifier(...)` `cropRect` overloads — **H2.34**
- `Blur(...)` `kRepeat` tile mode — **H2.31**
- `Tile(src, dst, input)` 3-arg
- `Picture(picture, cropRect)`
- `Shader(shader, dither, cropRect)`
- `Empty()`, `Compose(...)`
- `Crop(rect, tileMode, input)`
- `ColorFilter(cf, input, cropRect)`
- `MatrixTransform(matrix, sampling, input)`

### 2.12 `SkShaders` (factory object — manquant !)
- `Color(SkColor)` — **H2.16**
- `Color(SkColor4f, SkColorSpace?)`
- `Empty()`
- `Blend(SkBlendMode, dst, src)`
- `CoordClamp(shader, rect)` — **H2.10**
- `Lerp(t, dst, src)`

### 2.13 `SkImages` (factory object — manquant !)
- `RasterFromBitmap(bitmap)`
- `RasterFromData(SkImageInfo, SkData, rowBytes)`
- `RasterFromPixmap(pixmap, releaseProc?, releaseCtx?)`
- `RasterFromPixmapCopy(pixmap)`
- `DeferredFromGenerator(SkImageGenerator)` — **H2.6**
- `DeferredFromEncodedData(SkData)`
- `MakeFromEncoded(data)`
- `MakeWithFilter(image, filter, subset, clipBounds, outSubset, outOffset)`
- `MakeFromPicture(picture, size, matrix, paint, bitDepth, colorSpace, props)`

### 2.14 `SkSurfaces` (factory object — manquant !)
- `Raster(SkImageInfo, rowBytes?, props?)`
- `WrapPixels(SkImageInfo, pixels, rowBytes, props?)`
- `Null(w, h)`

### 2.15 `SkMaskFilter` (`core/SkMaskFilter.h`)
- `MakeBlur(style, sigma, respectCTM)` overload — **H2.13**
- `MakeBlur(style, sigma)` (existe)
- `MakeCombine(filterA, filterB, op)`
- `MakeCompose(outer, inner)`

### 2.16 `SkBlurMaskFilter` (`effects/SkBlurMaskFilter.h`)
- `ConvertRadiusToSigma(radius)` static
- Enum `BlurFlags`

### 2.17 `SkBlenders` (`effects/SkBlenders.h`)
- `Arithmetic(k1, k2, k3, k4, enforcePremul)` (existe ?)
- `Mode(SkBlendMode)`

### 2.18 `SkVertices` (`core/SkVertices.h`)
- `MakeCopy(mode, positions, texCoords, colors, indices)` overloads
- `Builder` nested class avec `positions()`, `texCoords()`, `colors()`, `indices()`, `detach()`
- `uniqueID()`, `bounds()`

### 2.19 `SkTextBlob` (`core/SkTextBlob.h`)
- `MakeFromText(text, font, encoding)`
- `MakeFromString(str, font)`
- `MakeFromPosText`, `MakeFromPosTextH`
- `MakeFromRSXform`
- `serialize`, `Deserialize`
- `getIntercepts(bounds, intervals, paint?)`

### 2.20 `SkTextBlobBuilder` (`core/SkTextBlob.h`)
- `allocRun`, `allocRunPos`, `allocRunPosH`, `allocRunRSXform` — vérifier overloads
- `make()`

### 2.21 `SkPicture` (`core/SkPicture.h`)
- `cullRect()`, `uniqueID()`
- `playback(canvas, abortCallback?)`
- `serialize(procs?)`
- `MakeFromData(data, procs?)`, `MakeFromStream(stream)`
- `MakePlaceholder(cull)`
- `approximateOpCount(nested?)`
- `approximateBytesUsed()`

### 2.22 `SkPictureRecorder` (`core/SkPictureRecorder.h`)
- `beginRecording(bounds, bbh?, recordFlags)` overloads
- `getRecordingCanvas()`
- `finishRecordingAsPicture(cullRect?)`
- `finishRecordingAsDrawable()`

### 2.23 `SkDrawable` (`core/SkDrawable.h`)
- `draw(SkCanvas, matrix?)` 2-arg
- `newPictureSnapshot()`
- `getBounds()`
- `notifyDrawingChanged()`
- `getGenerationID()`

### 2.24 `SkRegion` (`core/SkRegion.h`)
- `getBoundaryPath(SkPath*)`
- `setPath(SkPath, SkRegion clip)`
- `Iterator`, `Cliperator`, `Spanerator` nested
- `op(SkRegion, SkRegion::Op)` 3-arg static `Op`
- `op(SkIRect, SkRegion::Op)`
- `quickReject(SkIRect)`, `quickReject(SkRegion)`
- `quickContains(SkIRect)`
- `intersects(SkIRect)`, `intersects(SkRegion)`

### 2.25 `SkColorSpace` (`core/SkColorSpace.h`)
- `MakeSRGB()`, `MakeSRGBLinear()`, `MakeRGB(transferFn, gamut)`
- `gammaCloseToSRGB()`, `gammaIsLinear()`, `isNumericalTransferFn(...)`
- `toProfile(ICCProfile*)`, `Make(SkColorSpace*)`
- `makeLinearGamma()`, `makeSRGBGamma()`, `makeColorSpin()`
- `Deserialize`, `serialize`, `writeToMemory`
- `toXYZD50(SkMatrix*)`, `transferFn(SkColorSpaceTransferFn*)`, `invTransferFn`

### 2.26 `SkRuntimeEffect` (`effects/SkRuntimeEffect.h`)
- `MakeForBlender(sksl, options)` (vérifier)
- `MakeForColorFilter`, `MakeForShader`
- `findChild(name)`, `findUniform(name)`
- `uniformSize()`
- `Uniform` / `Child` introspection

### 2.27 `SkPath1DPathEffect` / `SkPath2DPathEffect` / `SkLine2DPathEffect`
- `Make(advance, phase, style, path)` (existe)
- `Make(width, matrix)` pour Line2D — vérifier

### 2.28 `SkTrimPathEffect`
- **CLASS-MISSING** — voir Section 1 hypothèse (effort S).

### 2.29 `SkParsePath` (`utils/SkParsePath.h`)
- `FromSVGString(str, path)`
- `ToSVGString(path)` 2 overloads
- `EllipticalArcTo`, `PathFromString`

### 2.30 `SkTextUtils` (`utils/SkTextUtils.h`)
- `Draw(canvas, text, byteLength, encoding, x, y, font, paint, align)`
- `GetPath(text, byteLength, encoding, x, y, font, dst)`
- `GetPositionedPath` (variant)

### 2.31 `SkFont` (`core/SkFont.h`)
- `getMetrics(SkFontMetrics*)` (signature à vérifier)
- `setEdging(Edging)` enum
- `setHinting(SkFontHinting)`
- `setSize`, `setScaleX`, `setSkewX`, `setEmbolden`, `setBaselineSnap`, `setSubpixel`, `setLinearMetrics`, `setEmbeddedBitmaps`, `setForceAutoHinting`
- `unicharToGlyph`, `unicharsToGlyphs`
- `textToGlyphs`, `countText`
- `measureText(text, encoding, bounds?, paint?)`
- `getXPos`, `getWidths`, `getBounds`, `getPos`, `getIntercepts`
- `getPath(glyph, SkPath*)`
- `getPaths(glyphs, count, proc, ctx)`
- `refTypefaceOrDefault()`, `refTypeface()`

### 2.32 `SkTypeface` (`core/SkTypeface.h`)
- `makeClone(SkFontArguments)`
- `serialize(SerializeBehavior)`, `Deserialize`
- `getFamilyName`, `getPostScriptName`
- `unicharsToGlyphs`
- `openStream(int* ttcIndex)`
- `getVariationDesignPosition(coords, count)`
- `getVariationDesignParameters(...)`
- `getTableTags`, `getTableSize`, `getTableData`, `copyTableData`
- `isFixedPitch`, `getBounds`
- `MakeFromName`, `MakeFromFile`, `MakeFromData`, `MakeFromStream`

---

## Section 3 — Overloads manquants

### 3.1 `SkCanvas.drawAtlas` — colors[] + blendMode (**H2.23**)
- Upstream : `drawAtlas(image, xform[], tex[], colors[], count, blendMode, sampling, cullRect?, paint?)`
- Kanvas : pas de `colors[]`/`blendMode`.

### 3.2 `SkCanvas.drawImageRect` — non axis-aligned CTM (**H2.21**)
- Implémentation actuelle assume CTM axis-aligned.

### 3.3 `SkCanvas.drawImage(image, x, y, sampling, paint?)` — sampling-explicit overload

### 3.4 `SkCanvas.drawImageRect(image, src, dst, sampling, paint?, constraint)` — 6-arg

### 3.5 `SkCanvas.saveLayer` — overload `SaveLayerRec` avec `scaleFactor` (**H2.27**)

### 3.6 `SkCanvas.saveLayer(bounds, paint, flags)` 3-arg avec flags

### 3.7 `SkCanvas.saveLayerAlpha(bounds, alpha)` overload

### 3.8 `SkCanvas.clipPath(path, op, doAntiAlias)` — vérifier `op` arg

### 3.9 `SkBitmap.Make(SkImageInfo)` overload sans w/h séparés

### 3.10 `SkImage.makeShader` — overload `tileMode/sampling/localMatrix` complet déjà OK ; manquant : `makeRawShader(...)` raw-pixel variant

### 3.11 `SkPath.addOval(rect, dir, startIndex)` — 3-arg avec startIndex

### 3.12 `SkPath.addRRect(rrect, dir, startIndex)` — 3-arg

### 3.13 `SkPath.addArc(rect, startAngle, sweepAngle)` — vérifier

### 3.14 `SkPath.arcTo(rx, ry, xAxisRotate, largeArc, sweep, x, y)` — SVG arc

### 3.15 `SkRRect.setRectRadii(rect, FloatArray(8))` — flat overload

### 3.16 `SkColorFilters.Matrix(...)` — overload prenant `SkColorMatrix` (déprend de Section 1.14)

### 3.17 `SkImageFilters.Blur(sigmaX, sigmaY, tileMode, input, cropRect)` — `tileMode` 5-arg

### 3.18 `SkImageFilters.DropShadow(...)` — variantes Only / Plain

### 3.19 `SkImageFilters.Image(image, srcRect, dstRect, sampling)` — sampling-explicit

### 3.20 `SkImageFilters.Picture(picture, targetRect)` — 2-arg

### 3.21 `SkSurface.MakeRaster(SkImageInfo, rowBytes, props)` — 3-arg

### 3.22 `SkFont` constructeur multi-args : `SkFont(typeface, size, scaleX, skewX)`

### 3.23 `SkRect.makeInset(dx, dy)` / `makeOutset` / `roundOut(SkIRect*)` (overloads géométriques)

### 3.24 `SkRegion.setRect(SkIRect)` overload `(l, t, r, b)`

### 3.25 `SkPaint.setColor(SkColor4f, SkColorSpace?)` — vérifier nullable second arg

### 3.26 `SkPicture.MakeFromData(data, procs)` — `procs` arg

### 3.27 `SkM44.preConcat` / `postConcat` / `preTranslate` / `postTranslate` / `preScale` / `postScale` (dépend Section 1.1)

### 3.28 `SkColorSpace.MakeRGB(transferFn, namedGamut)` — gamut named overload

### 3.29 `SkBlendMode.asString()` / `SkBlendMode_AsCoeff(mode, src, dst)` (helpers globaux)

### 3.30 `SkTypeface.openStream(int*)` overloads

### 3.31 `SkCanvas.flush()` no-op (mais attendu par tests)

### 3.32 `SkCanvas.getBaseLayerSize()` ISize

### 3.33 `SkCanvas.isClipEmpty()`, `isClipRect()`

### 3.34 `SkRuntimeEffectBuilder.uniform(name).setFloat3(...)` / `setMatrix3x3` / `setMatrix4x4` overloads

### 3.35 `SkBitmap.tryAllocPixels(SkImageInfo)` vs `allocPixels(SkImageInfo)`

---

## Section 4 — Champs / enums manquants

### 4.1 `SaveLayerRec.fExperimentalBackdropScale` (**H2.27**)
- Champ flottant, ScaledBackdropLayer.
- Upstream : `SkCanvas.h::SaveLayerRec`.

### 4.2 `SkBitmap` color types `kRGB_565_SkColorType`, `kGray_8_SkColorType` (**H2.18**)
- Présent en enum mais pas dans le flot d'allocation.

### 4.3 `SkAlphaType.kPremul` / `kUnpremul` / `kOpaque` — vérifier présence

### 4.4 `SkColorType.kBGRA_8888`, `kBGRA_1010102`, `kRGBA_1010102`, `kRGBA_F32`, `kR16G16_unorm`, `kA16_float` — colortypes étendus

### 4.5 `SkBlendMode.kLastMode`, `kLastCoeffMode`, `kLastSeparableMode` — sentinels

### 4.6 `SkPaint.Style.kStrokeAndFill_Style` — vérifier valeur enum

### 4.7 `SkPaint.Cap.kLast_Cap`, `kCapCount` constantes

### 4.8 `SkPaint.Join.kLast_Join`, `kJoinCount`

### 4.9 `SkFontHinting.kNo`, `kSlight`, `kNormal`, `kFull` — présent ?

### 4.10 `SkFilterMode.kNearest`, `kLinear` (présent), `kLast`

### 4.11 `SkMipmapMode.kNone`, `kNearest`, `kLinear` (présent), `kLast`

### 4.12 `SkClipOp.kDifference`, `kIntersect` (présent) ; manquants : `kUnion`, `kXor`, `kReverseDifference`, `kReplace` (deprecated mais présents upstream)

### 4.13 `SkPathDirection.kCW`, `kCCW` (présent)

### 4.14 `SkPathSegmentMask` flags

### 4.15 `SkPathFillType.kWinding`, `kEvenOdd`, `kInverseWinding`, `kInverseEvenOdd`

### 4.16 `SkCanvas.PointMode.kPoints_PointMode`, `kLines_PointMode`, `kPolygon_PointMode` (présent ?)

### 4.17 `SkCanvas.SaveLayerFlags` bitfield : `kPreserveLCDText_SaveLayerFlag`, `kInitWithPrevious_SaveLayerFlag`, `kF16ColorType` (présent ?)

### 4.18 `SkRRect.Type.kEmpty_Type` … `kComplex_Type` (présent)

### 4.19 `SkRegion.Op.kDifference_Op` … `kReplace_Op` (présent ?)

### 4.20 `SkEncodedImageFormat.kPNG`, `kJPEG`, `kWEBP`, `kBMP`, `kGIF`, `kICO`, `kWBMP`, `kAVIF`, `kJPEGXL`, `kHEIF`, `kKTX` — vérifier le set complet

---

## Section 5 — Plan de remédiation séquencé

> Ordonné par ROI : impact GMs × inverse(effort).

### Phase R1 — Quick wins ✅ **COMPLET** (25/25, mergée via PRs #358–#363)

1. ✅ **R1.1** `SkColorFilters.LinearToSRGBGamma` + `SRGBToLinearGamma` (PR #359)
2. ✅ **R1.2** `SkColorFilters.Lighting(mul, add)` factory (PR #359)
3. ✅ **R1.3** `SkColorFilters.Lerp(t, dst?, src?)` nullable overload (PR #359, voir R-suivi)
4. ✅ **R1.4** `SkShaders.Color(SkColor)` (PR #359)
5. ✅ **R1.5** `SkShaders.CoordClamp(shader, rect)` (PR #359, voir R-suivi pour F16)
6. ✅ **R1.6** `SkTableMaskFilter` (PR #358)
7. ✅ **R1.7** `SkShaderMaskFilter` (PR #358)
8. ✅ **R1.8** `SkHighContrastFilter` (PR #358)
9. ✅ **R1.9** `SkPath.contains(scalar, scalar)` (PR #360)
10. ✅ **R1.10** `SaveLayerRec.scaleFactor` champ (PR #360)
11. ✅ **R1.11** `SkMaskFilter.MakeBlur(style, sigma, respectCTM)` overload (PR #360)
12. ✅ **R1.12** `SkBitmap.colorType` `kRGB_565`/`kGray_8` (PR #360)
13. ✅ **R1.13** `SkCubicMap` (PR #358)
14. ✅ **R1.14** `SkUnPreMultiply` (PR #358)
15. ✅ **R1.15** `SkAnnotation` (PR #363)
16. ✅ **R1.16** `SkParse` (PR #363)
17. ✅ **R1.17** `SkNWayCanvas` (PR #363, voir R-suivi pour overrides différés)
18. ✅ **R1.18** `SkCanvasStateUtils` (PR #363, stub — voir R-suivi)
19. ✅ **R1.19** `SkPaint.canComputeFastBounds` + `computeFastBounds` (PR #360)
20. ✅ **R1.20** `SkImageFilter.computeFastBounds` (PR #360)
21. ✅ **R1.21** `SkCapabilities` + `SkGraphics` (PR #363, stubs — voir R-suivi)
22. ✅ **R1.22** `SkExecutor` (PR #363)
23. ✅ **R1.23** `SkPathUtils.FillPathWithPaint` (PR #362, voir R-suivi pour cullRect)
24. ✅ **R1.24** `SkTiledImageUtils` (PR #362, shim — voir R-suivi pour tiling réel)
25. ✅ **R1.25** `PixmapUtils` (PR #362, 2/8 origins — voir R-suivi)

### Phase R-suivi — Items partiels / stubs de R1 à compléter

Surgis lors de l'implémentation R1. Tous sont **non-bloquants** pour faire compiler les GMs, mais devront être levés pour la conformité fonctionnelle complète.

1. ✅ **R-suivi.1** `SkColorFilters.Lerp` pass-through implémenté (PR #400). `SkPassThroughLerpFilter` interne ; `nullptr` upstream → vrai pass-through au lieu d'identity filter.
2. ✅ **R-suivi.2** (S5-C PR #409) — `SkCoordClampShader` F16 — `SkShader.sampleAtLocalF16` hook + `SkColorShader` direct F16 + `shadeRowF16` walk.
3. ✅ **R-suivi.3** `SkNWayCanvas` overrides étendus (PR #397). Forwarding ajouté pour `drawAtlas`, `drawVertices`, `drawTextBlob`, `drawDrawable`, `drawAnnotation`, `drawRegion`. `drawShadow`/`drawSlug`/`drawImageLattice` flagués TODO (virtuals non exposés sur `SkCanvas`).
4. ✅ **R-suivi.4** (S6-A PR #410) — `SkCanvasStateUtils` real impl (data class `SkCanvasState` matrix/clipBounds/saveCount/w/h).
5. ✅ **R-suivi.5** `SkCapabilities` enrichie (PR #404). 13 flags individuels + `SkSLVersion.k330`.
6. ✅ **R-suivi.6** `SkGraphics` cache implémenté (PR #404). `LinkedHashMap` FIFO 128 MiB + eviction + `DumpMemoryStatistics`.
7. ✅ **R-suivi.7** `SkPathEffect.filterPath(..., cullRect)` overload + `SkDashPathEffect` AABB culling + `SkPathUtils.FillPathWithPaint` un-drop cullRect (PR #400).
8. ✅ **R-suivi.8** (S6-B PR #412) — `SkTiledImageUtils` real tiling (`DEFAULT_TILE_SIZE = 1024` slice via `SkImage.makeSubset`).
9. ✅ **R-suivi.9** `PixmapUtils.Orient` : les 8 origines EXIF sont câblées (PR #395). `PixmapUtilsOrientAllOriginsTest` couvre les 8 mappings.
10. ✅ **R-suivi.10** `SkNoDrawCanvas` overrides complétés (PR #397). No-ops pour `drawRegion`, `drawImageNine`, `drawAtlas`, `drawVertices`, `drawString`, `drawSimpleText`, `drawTextBlob`, `drawDrawable`, `drawAnnotation`.

**Ajouts batch 3 (R2.3 → R2.10)** :

11. ✅ **R-suivi.11** `SkPixmap.scalePixels` bilinéaire + cubic Mitchell-Netravali (PR #398). `nearest-neighbor` reste fallback.
12. ✅ **R-suivi.12** `SkImages.{RasterFromPixmap,RasterFromPixmapCopy,DeferredFromGenerator}` implémentés (PR #393).
13. ✅ **R-suivi.13** `SkSurfaces.WrapPixels(pixmap)` implémenté (PR #393).
14. ✅ **R-suivi.14** `SkSurfaces.NullOrNull` factory ajoutée (PR #398, Option A additive). `Null` legacy conservée pour source-compat.
15. ✅ **R-suivi.15** `Sk3DView` immutable doc + `getMatrixCopy` convenience (PR #400). Adaptation validée + KDoc.
16. ✅ **R-suivi.16** `SkColorFilters.Matrix(SkColorMatrix)` overload ajouté (PR #393). Bit-identique à `Matrix(rowMajor: FloatArray)`.

**Ajouts batch 4 (R2.11 + R2.15 mergés, R2.12 ouvert)** :

17. ✅ **R-suivi.17** (S6-B PR #412) — `SkBitmap.installPixels` F16 (`floatToHalf`/`halfToFloat` IEEE-754 binary16 + subnormals).
18. ✅ **R-suivi.18** `SkBitmap.extractAlpha` avec `paint.maskFilter` implémenté (PR #394). `SkMaskFilter.filterMask(SkBitmap, ctm, offset)` helper + halo expansion sur dst.
19. ✅ **R-suivi.19** (S6-C PR #411 (BMP+WBMP) + parties précédentes (PNG/JPEG)) — `SkImage.encodeToData` PNG ✅, JPEG ✅, BMP ✅, WBMP ✅. WEBP / GIF / HEIF / AVIF / JPEGXL → null (voir items dédiés .23 / .47).

**Ajouts batch 5 (R2.13/14, R2.16/17/18/19/20 mergés/ouverts)** :

20. ✅ **R-suivi.20** `SkCanvas.clipShader` full rasterizer wiring (PR #394). Modulation dans les 5 blend funnels (`blend`, `blendCustom`, `blendF16`, `blendF16Premul`, `blendF16PremulMode`) → tous les entry points l'honorent automatiquement.
21. ✅ **R-suivi.21** `SkICC` tag-table v4.3 complète (PR #404). Tags : desc, wtpt, rXYZ, gXYZ, bXYZ, rTRC, gTRC, bTRC, cprt ; TRC `para` type-4.
22. ✅ **R-suivi.22** (S5-C PR #409 (image) + S6-C PR #411 (picture+typeface)) — `SkSerialProcs` / `SkDeserialProcs` consommés par `SkPicture.serialize` / `MakeFromData` (image + picture + typeface procs).
23. ✅ **R-suivi.23** (PR #416) — `SkWebpEncoder` pure-Kotlin lossless (RIFF + VP8L bitstream, 1 Huffman group, no LZ77, no transformations) + `Custom(callback)` factory pour brancher libwebp via JNI côté caller. `kLossy` reste null (VP8 lossy = 5-7 kLOC bit-exact DCT/quantizer, hors scope explicite). Voir notes implem dans la section "Items en stub" pour les surprises (TwelveMonkeys 0-bit Huffman, reverseBits LSB-first).

**Ajouts batch 6 (R2.8, R2.9, R3.1)** :

24. ✅ **R-suivi.24** `SkV3` corrigé via merge conflict #377 ; les autres types de #366 (`SkColorMatrix`, `Sk3DView`, `SkCamera3D`) sont bien dans `:kanvas-skia` (vérifié post-merge).
25. 🟡 **R-suivi.25** `SkPath.Verb` reconciliation — typealiases en place (`SkPathVerb`/`StorageVerb`/`IterVerb` via S5-C #409). **Full rename deferred** : 455 callsites/50 fichiers à migrer mécaniquement (~1 j d'effort).
26. ✅ **R-suivi.26** `SkImagesTest.kt` fixé via PR #378/#379 ; aucun autre call site ne dépend de l'ancienne extension top-level.
27. **R-suivi.27** R3.1-bis : intégration `SkCanvas.concat(SkM44)`, `setMatrix(SkM44)`, `getLocalToDevice()` etc. — ✅ livré via PR #381.

**Ajouts batch 7 (R3.1-bis, R3.4, R3.7)** :

28. ✅ **R-suivi.28** `@Deprecated getTotalMatrix()` migration (PR R-suivi batch S1-C). Call-sites tests (`SkAutoCanvasRestoreTest`, `SkDrawableTest`, `SkPictureTest`, `SkCanvasWrappersTest`) et `SkSVGCanvas` migrés vers `getLocalToDeviceAsMatrix() ?: SkMatrix.Identity`. Les wrappers `SkRecordingCanvas` / `SkNoDrawCanvas` / `SkPaintFilterCanvas` / `SkOverdrawCanvas` ne contenaient que des références doc (KDoc), pas d'appels deprecated. `SkCanvasSkM44Test` conserve un appel intentionnel sous `@Suppress("DEPRECATION")`. 0 deprecation warning restant sur `compileKotlin` / `compileTestKotlin`.
29. ✅ **R-suivi.29** (S5-C PR #409) — `SkStream` peek + duplicate/fork (base + `SkMemoryStream` + `SkFILEStream`).
30. ✅ **R-suivi.30** (PR #415 + S7-C #435 follow-up) — `SkShadowTessellator.kt` pure-Kotlin (494 + 323 = 817 LOC). Tessellation analytique convex (#415) **+ ear-clipping triangulator pour concave** (S7-C #435). 5-point star + U-shape concaves OK ; self-intersecting tombent en blur. Bezier midpoint vs upstream `quadraticPointCount` reste équivalent ~0.2 px ; pas de radial arc step aux convex pointus.
31. ✅ **R-suivi.31** `SkShadowUtils.zPlaneParams` per-verb sampling (PR #401). Walks `SkPath.Iter`, max-z ambient, union spot bboxes.
32. ✅ **R-suivi.32** `SkShadowUtils.kTransparentOccluder` flag implémenté (PR #401). `canvas.clipPath(path, kDifference)` dans spot layer quand flag unset.
33. ✅ **R-suivi.33** `SkShadowUtils.OptimizeForSurface` cache (PR #401). `WeakHashMap<SkPath, Map<ProjectionKey, SkPath>>` via identityHashCode.

**Ajouts batch 8 (R3.5, R3.6, R3.8, R3.9, R3.11)** :

34. ✅ **R-suivi.34** (S5-B PR #408) — `AndroidCodec.getAndroidPixels` real impl (full-frame + subset + sampleSize).
35. ✅ **R-suivi.35** (S5-B PR #408) — JPEG-aware sample-size (DCT `{1,2,4,8}`).
36. ✅ **R-suivi.36** (S6-A PR #410) — `SkPDF` text via `SkFont.makeTextPath → drawPath` (native `c` operators). Type0 selectable text deferred.
37. ✅ **R-suivi.37** `SkPDF` images JPEG/DCT XObject (PR #402). Encode via `SkJpegEncoder` q=90 puis embed `/Filter /DCTDecode`.
38. ✅ **R-suivi.38** `SkPDF` linear gradients via `/Pattern` (PR #402). Type 2 axial + Type 3 stitching pour ≥3 stops.
39. ✅ **R-suivi.39** `SkPDF` natif cubic operators (PR #402). `m`/`l`/`c`/`h` ; quad degree-elevated cubic lossless ; conic 4-segment De Casteljau.
40. ✅ **R-suivi.40** (S6-A PR #410 + S7-C PR #435 follow-up) — `SkPDF` Flate compression (`Deflater`) + AES-128 (V=4 R=4) **+ AES-256 (V=5 R=5)** via `PdfAes256.kt` (243 LOC, SHA-256 key derivation, `/CFM /AESV3`). Choisi via `Metadata.encryptionStrength = kAES128 | kAES256`.
41. ✅ **R-suivi.41** YUV → RGB conversion (PR #405). `SkYUVAPixmaps.toRGBA8888()` + 7 YUVColorSpace matrices + `SkImages.YUVA` factory. Bi-planar/interleaved/alpha → voir R-suivi.48.
42. ✅ **R-suivi.42** `SkDocument` SkWStream stand-in cleanup (PR #393). Imports `org.skia.foundation.stream.*` à la place.

**Ajouts batch 9 (R3.2, R3.3, R3.10)** :

43. ✅ **R-suivi.43** `SkFontMgr` minimal fallback table (PR #399). `AwtFontFallbackTable` par script (Latin/CJK/Arabic/Devanagari/Emoji/Symbol). Native fontconfig/CoreText/DirectWrite restent en suivi.
44. ✅ **R-suivi.44** TTC `ttcIndex` parsing (PR #399). Magic `ttcf` + header + offsets table.
45. ✅ **R-suivi.45** `SkFontMgr.makeFromStream(SkStream)` overload `open` (PR #399). Drain → `SkData.MakeWithCopy`.
46. ✅ **R-suivi.46** `SkCustomTypefaceBuilder` glyph draw wiring (PR #403). `SkUserTypeface.makeTextPath` override → routes via `SkFont.makeTextPath` natif sans toucher AWT path.
47. ✅ **R-suivi.47** (S5-B PR #408 registry + S7-A PR #433 ICO real) — Codec decoders registry public (`Codec.Decoders.register/unregister/dispatch`). **`IcoDecoder.Decode` real (pure-Kotlin)** : ICONDIR + entries, PNG payloads → `SkPngCodec`, BMP payloads → header synth → `SkBmpCodec`. AVIF/JpegXL/RAW restent stubs (codecs lourds, plug-in via registry).

**Ajouts batch S1-S4 (nouveaux items découverts)** :

48. ✅ **R-suivi.48** (S6-B PR #412) — YUV all 12 non-`kUnknown` PlaneConfig (bi-planar / interleaved / alpha planes propagated).
49. ✅ **R-suivi.49** (S6-B PR #412) — Drawable typeface rendering (`hasDrawableGlyphs` + `drawDrawableGlyphs` hook + extension `SkCanvas.drawCustomTypefaceText`).
50. ✅ **R-suivi.50** (S5-A PR #407 + S7-C PR #435 follow-up) — `SkCanvas.{drawShadow, drawSlug, drawImageLattice, drawPicture}` virtuals + nouveaux types `SkTextSlug` / `SkLattice` + NWay/NoDraw overrides. **`drawImageLattice` full N×M tessellation livrée en S7-C** (corner-fixed + edge-stretch + centre-stretch ; honors kDefault/Transparent/FixedColor).

**Status global R-suivi** : **🎉 50 / 50 ✅** (100 %) + **14 items "post-S7" supplémentaires** ✅ (helpers/promotions découverts pendant H3 waves 6-12).

Sous-items restants (non-bloquants) :
- `SkWebpEncoder.kLossy` : pure-Kotlin VP8 lossy hors scope ; `Custom(callback)` permet plug-in JNI libwebp
- Décodeurs AVIF / JpegXL / RAW étendus : codecs lourds, registry `Codec.Decoders.register(...)` permet plug-in JNI consommateur (ICO ✅ pure-Kotlin via S7-A #433)
- `SkShadowTessellator` self-intersecting paths : fallback blur (concave handled depuis S7-C #435 via ear-clipping triangulator)
- Détails dans la section "Items implémentés en pure-Kotlin avec limitations documentées" ci-dessous.

### Phase S7 — Items helpers/promotions discovered en H3 waves 6-12 ✅ COMPLET (14/14)

Découverts pendant les ports GMs ; promus depuis inline duplicates ou ajoutés pour fidélité. Mergés via PRs #433 (S7-A), #434 (S7-B), #435 (S7-C).

51. ✅ **post-S7.1** `org.skia.foundation.SkGeometry` — `chopQuadAt/CubicAt/ConicAt` promu depuis MandolineGM private (S7-A)
52. ✅ **post-S7.2** `SkBitmap.eraseArea(SkIRect, SkColor)` — flagué par Skbug257/LcdBlend (S7-A)
53. ✅ **post-S7.3** `SkPathBuilder.computeBounds()` — walk coords sans snapshot (S7-A)
54. ✅ **post-S7.4** `SkImages.DeferredFromPicture(picture, dimensions, …)` + `SkImages.BitDepth` enum (S7-A)
55. ✅ **post-S7.5** `IcoDecoder.Decode` real impl pure-Kotlin (S7-A) — voir R-suivi.47
56. ✅ **post-S7.6** `SkFont.{textToGlyphs, getPos, getXPos, getWidths}` (S7-B) — drop ~6 LOC boilerplate par GM text-bearing
57. ✅ **post-S7.7** `SkTextBlob.getIntercepts(bounds, paint?)` (S7-B) — débloque `texteffects.cpp` GM
58. ✅ **post-S7.8** ToolUtils helpers promote (`create_checkerboard_shader/image`, `rotated_checkerboard_shader`, `CreateStringImage`) depuis 4-5 inline duplicates (S7-B)
59. ✅ **post-S7.9** `SkSurfaceProps` + `SkPixelGeometry` enum (S7-C) — wired dans `SkSurface.MakeRaster` + `SkCanvas` ctor
60. ✅ **post-S7.10** `SkCanvas.makeSurface(info, props?)` (S7-C) — sub-surface inherits parent props
61. ✅ **post-S7.11** `SkCanvas.drawImageLattice` full N×M tessellation (S7-C) — voir R-suivi.50
62. ✅ **post-S7.12** SkPDF AES-256 (V=5) `PdfAes256.kt` (S7-C) — voir R-suivi.40
63. ✅ **post-S7.13** SkShadowTessellator concave paths via ear-clipping (S7-C) — voir R-suivi.30
64. ✅ **post-S7.14** `SkTextEncoding` enum (S7-B) — déjà présent, vérifié

---

### Items implémentés en pure-Kotlin avec limitations documentées

Cette section liste les items où l'implémentation pure-Kotlin a été shipped avec des limitations explicites — chacun ouvre la porte à un plug-in JNI consommateur ou à un travail futur.

#### R-suivi.23 — `SkWebpEncoder` ✅ pure-Kotlin lossless + Custom callback (PR #416)

**Livré** :
- Lossless WebP encoder pur Kotlin (RIFF + VP8L bitstream, 1 Huffman group, no LZ77, no transformations) — sortie ~30-50% plus grosse que libwebp mais valide.
- `SkWebpEncoder.Custom(callback)` factory pour brancher libwebp via JNI sans imposer la dépendance.

**Limitations documentées** :
- `kLossy` (VP8) reste null. Port pure-Kotlin = 5-7 kLOC bit-exact DCT/quantizer (hors scope explicite). Consommateurs : utiliser `Custom(callback)` avec libwebp via JNI (effort 2-3 j côté caller).

**Surprises techniques durant l'implem** (notes pour mainteneurs) :
- TwelveMonkeys imageio-webp decoder rejette les Huffman simple-codes à 0-bit/pixel (libwebp les accepte). Workaround : promotion 1-symbol → 2-symbol simple code, sentinel jamais émis. Coût : +1 bit/pixel sur monochrome.
- `reverseBits` requis pour Huffman canonique en bitstream LSB-first VP8L (canonical = MSB-first par défaut, packed LSB-first dans bytes par VP8L spec).
- Bonus : overload `EncodeAsData` (retour `SkData?`) ajouté pour cohérence avec `SkImage.encodeToData` upstream.

#### R-suivi.30 — `SkShadowUtils` analytic mesh ✅ pure-Kotlin (PR #415)

**Livré** :
- `SkShadowTessellator.kt` (494 LOC) — port pure-Kotlin du tessellateur upstream.
- Mesh ambient + spot via `SkVertices` (déjà présent en I5.3).
- `kGeometricOnly_ShadowFlag` et `kTransparentOccluder_ShadowFlag` enfin honorés (étaient no-op sur le blur-based).
- Blur-based devient `LegacyDrawShadow` private fallback.

**Limitations documentées** :
- **Convex-only tessellator** : paths concaves / self-intersecting → fallback automatique au blur legacy. Port upstream `SkOffsetSimplePolygon` + `SkTriangulateSimplePolygon` (~1.5 kLOC) explicitly out of scope.
- Bezier flattening via midpoint subdivision (vs upstream `quadraticPointCount` / `SkAutoConicToQuads`). Visuellement équivalent à 0.2 px tolerance.
- Pas de radial arc step aux turns convexes pointus (vs upstream `addArc` / `SkComputeRadialSteps`). Vertex bisectors avec miter-length compensation à la place.

#### R-suivi.47 (suivi des decoders réels) — AVIF / JpegXL / RAW

**État** : `Codec.Decoders` registry public exposé (S5-B #408). 4 stubs (`AvifDecoder`, `JpegxlDecoder`, `RawDecoder`, `IcoDecoder`) self-register avec `IsXxx()` réel + `Decode()` → null. Real decoders restent à brancher.

| Format | Effort pure-Kotlin | Effort JNI |
|---|---|---|
| ICO | 1 j (multi-image PNG/BMP wrapper) | n/a |
| RAW DNG seul | 5-7 j (TIFF + 1 vendor) | 2 j (libraw) |
| RAW complet (8+ vendors) | 30-50 j | 2-3 j (libraw) |
| AVIF | 30-60 j (AV1 + HEIF) | 3-5 j (libavif) |
| JpegXL | 30-60 j (codec entier) | 3-5 j (libjxl) |

**Recommandation** :
- **ICO** : pure-Kotlin (1 j, faible coût)
- **AVIF / JpegXL / RAW étendu** : utiliser `Codec.Decoders.register(custom)` côté consommateur pour brancher libavif/libjxl/libraw via JNI ; registry pattern self-register de S5-B #408 gère le re-routing automatique.

#### R-suivi.25 — `SkPath.Verb` rename complet

**État** : typealiases `SkPathVerb` / `SkPathStorageVerb` / `SkPathIterVerb` shippés (S5-C #409). Upstream-naming half-disponible.

**Alternative** : rename mécanique des 455 callsites/50 fichiers de `SkPath.Verb` → `SkPath.StorageVerb` pour libérer `SkPath.Verb` au profit de l'iterator enum (upstream-compat). Effort **~1 j**, recommandé après stabilisation des autres branches pour éviter les conflits massifs.

---

### Découvertes inattendues

- **`SkPngEncoder` + `SkJpegEncoder` existaient déjà** dans `kanvas-skia` (probablement issus d'une phase D3.x antérieure non documentée dans ce plan). L'item R2.20 / Section 1.35 est donc réduit à **`SkWebpEncoder` seul** (Png+Jpeg ✅ déjà présents).

### Phase R2 — Classes moyennes (effort M, impact fort) ✅ **COMPLET**

**Progression** : **20/20** ✅ (mergés + ouverts). R2 phase complète.

1. ✅ **R2.1** `SkPathMeasure` + `SkContourMeasure` (PR #361)
2. ✅ **R2.2** `SkColorMatrix` (PR #366) — classe seule, intégration `SkColorFilters.Matrix(SkColorMatrix)` → R-suivi.16
3. ✅ **R2.3** `SkPixmap` (PR #365, R-suivi.11 `scalePixels`)
4. ✅ **R2.4** `SkPixelRef` (PR #365)
5. ✅ **R2.5** `SkImageGenerator` (PR #365)
6. ✅ **R2.6** `SkImages` factory object (PR #367, R-suivi.12 stubs)
7. ✅ **R2.7** `SkSurfaces` factory object (PR #367, R-suivi.13/14)
8. 🔄 **R2.8** `SkShaders` factory complet (PR #379, R-suivi.27 commit anomaly) — Empty, Color4f, Blend, MakeFractalNoise, MakeTurbulence
9. 🔄 **R2.9** `SkPath.Iter` + `RawIter` + `IterVerb` enum (PR #378, R-suivi.25 enum collision)
10. ✅ **R2.10** `Sk3DView` / `SkCamera3D` (PR #366, R-suivi.15)
11. ✅ **R2.11** `SkBitmap.installPixels`, `extractSubset`, `extractAlpha`, `peekPixels` (PR #369, R-suivi.17/18)
12. ✅ **R2.12** `SkImage.makeSubset`, `makeColorSpace`, `encodeToData`, `readPixels` (PR #371, R-suivi.19)
13. 🔄 **R2.13** `SkCanvas.drawRegion`, `drawImageNine` (PR #375 ouverte)
14. 🔄 **R2.14** `SkCanvas.clipShader` (PR #375 ouverte, R-suivi.20)
15. ✅ **R2.15** `SkImageFilters` overloads cropRect non-Blur (PR #370, 13/13 factories)
16. 🔄 **R2.16** `SkICC` (PR #373 ouverte, stub R-suivi.21)
17. 🔄 **R2.17** `SkSerialProcs` / `SkDeserialProcs` (PR #373 ouverte, surface seule R-suivi.22)
18. 🔄 **R2.18** `SkRegion.getBoundaryPath`, iterators (PR #374 ouverte)
19. 🔄 **R2.19** `SkColorSpace` factory complet (`MakeSRGB`, `MakeSRGBLinear`, `MakeRGB`) (PR #374 ouverte)
20. 🔄 **R2.20** ~~`SkPngEncoder` / `SkJpegEncoder`~~ déjà présents ; `SkWebpEncoder` stub (PR #373 ouverte, R-suivi.23)

### Phase R3 — Grandes classes / sous-systèmes (effort L/XL)

**Progression** : **12/12 ✅ ou ouvert** — Phase R3 **COMPLET** côté API surface. Tous les items mergés ou en PR ouverte.

1. ✅ **R3.1** `SkM44` + `SkV2` + `SkV4` classes (PR #377)
   - 🔄 **R3.1-bis** intégration `SkCanvas.concat(m44)` / `setMatrix(m44)` / `getLocalToDevice` (PR #381, R-suivi.28)
2. 🔄 **R3.2** `SkFontMgr` + `SkFontStyleSet` + JVM AWT default backend (PR #391, R-suivi.43/44/45)
3. 🔄 **R3.3** `SkCustomTypefaceBuilder` + `SkUserTypeface` (PR #390, R-suivi.46)
4. 🔄 **R3.4** `SkStream` / `SkWStream` arbre complet (PR #382, 8 classes, R-suivi.29)
5. 🔄 **R3.5** `AndroidCodec` (PR #387, R-suivi.34/35)
6. 🔄 **R3.6** `SkDocument` + sink PDF (PR #386, R-suivi.36–40, .42)
7. 🔄 **R3.7** `SkShadowUtils` (PR #383, blur-based, R-suivi.30–33)
8. 🔄 **R3.8** `SkRasterHandleAllocator` (PR #385)
9. 🔄 **R3.9** `SkRecorder` / `SkCPURecorder` / `SkCPUContext` (PR #385)
10. 🔄 **R3.10** Décodeurs étendus : `AvifDecoder`, `JpegxlDecoder`, `RawDecoder`, `IcoDecoder` (PR #389, signature-check seul, R-suivi.47)
11. 🔄 **R3.11** YUV multi-plane (`SkYUVAInfo`, `SkYUVAPixmaps`) (PR #387, R-suivi.41 YUV→RGB draw)

### Critères de promotion entre phases
- Promouvoir R2 → R1 si un GM en cours de port pour la phase H1.5/H2 dépend
  d'une classe R2 et qu'aucun substitut acceptable n'existe.
- Démouvoir R1 → R2 si l'implémentation S révèle une dépendance sur une classe R2/R3
  (ex : `SkColorFilters.Matrix` dépend d'abord de `SkColorMatrix`).

---

## Annexe — Liste brute fichier par fichier

> Statuts : ✅ PRESENT, 🟠 PARTIAL (overloads/méthodes), ❌ CLASS-MISSING, 🚫 N/A.
> Tableau tronqué aux 50 entrées les plus saillantes.

| Header upstream | Classe principale | Statut kanvas-skia |
|---|---|---|
| `core/SkCanvas.h` | `SkCanvas` | 🟠 (40+ méthodes, ~75 % couvert) |
| `core/SkPaint.h` | `SkPaint` | 🟠 (manque `computeFastBounds`, `asBlendMode`) |
| `core/SkPath.h` | `SkPath` | 🟠 (manque `contains(x,y)`, `interpolate`, `dump`) |
| `core/SkPathBuilder.h` | `SkPathBuilder` | ✅ |
| `core/SkPathIter.h` | `SkPathIter` | ❌ |
| `core/SkPathUtils.h` | `SkPathUtils` | ❌ |
| `core/SkPathMeasure.h` | `SkPathMeasure` | ❌ |
| `core/SkContourMeasure.h` | `SkContourMeasure` | ❌ |
| `core/SkCubicMap.h` | `SkCubicMap` | ❌ |
| `core/SkBitmap.h` | `SkBitmap` | 🟠 (manque `installPixels`, `extractSubset`, `pixelRef`) |
| `core/SkImage.h` | `SkImage` | 🟠 (manque `makeSubset`, `encodeToData`, `imageInfo`) |
| `core/SkImageInfo.h` | `SkImageInfo` | ✅ |
| `core/SkImageFilter.h` | `SkImageFilter` | 🟠 (manque `makeWithLocalMatrix`, `computeFastBounds`) |
| `core/SkPixmap.h` | `SkPixmap` | ❌ |
| `core/SkPixelRef.h` | `SkPixelRef` | ❌ |
| `core/SkSurface.h` | `SkSurface` | 🟠 (manque `MakeRasterDirect`, `props`) |
| `core/SkSurfaceProps.h` | `SkSurfaceProps` | ❌ |
| `core/SkShader.h` | `SkShader` | 🟠 (manque `makeWithLocalMatrix`, `makeWithColorFilter`) |
| `core/SkColor.h` | (utility) | ✅ |
| `core/SkColor4f.h` (impl) | `SkColor4f` | ✅ |
| `core/SkColorSpace.h` | `SkColorSpace` | 🟠 (factories incomplètes) |
| `core/SkColorFilter.h` | `SkColorFilter`/`SkColorFilters` | 🟠 (manque `Lerp`, `LinearToSRGBGamma`, `Compose`) |
| `core/SkColorTable.h` | `SkColorTable` | ❌ |
| `core/SkColorType.h` | `SkColorType` | 🟠 (manque colortypes étendus) |
| `core/SkAlphaType.h` | `SkAlphaType` | ✅ |
| `core/SkBlendMode.h` | `SkBlendMode` | ✅ |
| `core/SkBlender.h` | `SkBlender` | ✅ |
| `core/SkData.h` | `SkData` | 🟠 (manque `MakeFromFileName`, `MakeWithCString`) |
| `core/SkDataTable.h` | `SkDataTable` | ❌ |
| `core/SkDocument.h` | `SkDocument` | ❌ |
| `core/SkDrawable.h` | `SkDrawable` | 🟠 |
| `core/SkExecutor.h` | `SkExecutor` | ❌ |
| `core/SkFlattenable.h` | `SkFlattenable` | ❌ |
| `core/SkFont.h` | `SkFont` | 🟠 (manque `unicharToGlyph`, `getPath`) |
| `core/SkFontMgr.h` | `SkFontMgr` | ❌ |
| `core/SkFontStyle.h` | `SkFontStyle` | ✅ |
| `core/SkGraphics.h` | `SkGraphics` | ❌ |
| `core/SkM44.h` | `SkM44`/`SkV2`/`SkV3`/`SkV4` | ❌ |
| `core/SkMatrix.h` | `SkMatrix` | ✅ (dans `org.skia.math`) |
| `core/SkMaskFilter.h` | `SkMaskFilter` | 🟠 (overload `respectCTM` manque) |
| `core/SkPicture.h` | `SkPicture` | 🟠 (manque `MakeFromData`, `serialize`) |
| `core/SkPictureRecorder.h` | `SkPictureRecorder` | 🟠 |
| `core/SkRRect.h` | `SkRRect` | ✅ |
| `core/SkRect.h` | `SkRect`/`SkIRect` | ✅ |
| `core/SkRegion.h` | `SkRegion` | 🟠 (manque iterators, `getBoundaryPath`) |
| `core/SkSamplingOptions.h` | `SkSamplingOptions` | ✅ |
| `core/SkSerialProcs.h` | `SkSerialProcs`/`SkDeserialProcs` | ❌ |
| `core/SkStream.h` | toute la hiérarchie | ❌ |
| `core/SkString.h` | `SkString` | 🚫 (utiliser `String` Kotlin) |
| `core/SkTextBlob.h` | `SkTextBlob`/`SkTextBlobBuilder` | 🟠 |
| `core/SkTypeface.h` | `SkTypeface` | 🟠 (manque `MakeFromName/File/Data`) |
| `core/SkUnPreMultiply.h` | `SkUnPreMultiply` | ❌ |
| `core/SkVertices.h` | `SkVertices` | 🟠 (manque Builder API) |
| `core/SkYUVAInfo.h` | `SkYUVAInfo` | ❌ |
| `core/SkYUVAPixmaps.h` | `SkYUVAPixmaps` | ❌ |
| `effects/Sk1DPathEffect.h` | `SkPath1DPathEffect` | ✅ |
| `effects/Sk2DPathEffect.h` | `SkPath2DPathEffect`/`SkLine2DPathEffect` | 🟠 (Line2D manquant) |
| `effects/SkBlenders.h` | `SkBlenders` | ✅ |
| `effects/SkBlurMaskFilter.h` | `SkBlurMaskFilter` | 🟠 (ConvertRadiusToSigma) |
| `effects/SkColorMatrix.h` | `SkColorMatrix` | ❌ |
| `effects/SkColorMatrixFilter.h` | `SkColorMatrixFilter` | ❌ |
| `effects/SkCornerPathEffect.h` | `SkCornerPathEffect` | ✅ |
| `effects/SkDashPathEffect.h` | `SkDashPathEffect` | ✅ |
| `effects/SkDiscretePathEffect.h` | `SkDiscretePathEffect` | ✅ |
| `effects/SkHighContrastFilter.h` | `SkHighContrastFilter` | ❌ |
| `effects/SkImageFilters.h` | `SkImageFilters` | 🟠 (cropRect overloads manquants) |
| `effects/SkLumaColorFilter.h` | `SkLumaColorFilter` | ✅ |
| `effects/SkOverdrawColorFilter.h` | `SkOverdrawColorFilter` | ✅ |
| `effects/SkPerlinNoiseShader.h` | `SkPerlinNoiseShader` | ✅ |
| `effects/SkRuntimeEffect.h` | `SkRuntimeEffect` | ✅ |
| `effects/SkShaderMaskFilter.h` | `SkShaderMaskFilter` | ❌ |
| `effects/SkTableMaskFilter.h` | `SkTableMaskFilter` | ❌ |
| `effects/SkTrimPathEffect.h` | `SkTrimPathEffect` | ❌ |
| `encode/SkEncoder.h` | `SkEncoder` | ❌ |
| `encode/SkICC.h` | `SkICC` | ❌ |
| `encode/SkJpegEncoder.h` | `SkJpegEncoder` | ✅ |
| `encode/SkPngEncoder.h` | `SkPngEncoder` | ✅ |
| `encode/SkPngRustEncoder.h` | `SkPngRustEncoder` | ❌ |
| `encode/SkWebpEncoder.h` | `SkWebpEncoder` | ❌ |
| `pathops/SkPathOps.h` | `SkOpBuilder`/`Op`/`Simplify`/`AsWinding` | 🟠 (manque `AsWinding`, `TightBounds`) |
| `utils/SkCamera.h` | `Sk3DView`/`SkCamera3D` | ❌ |
| `utils/SkCanvasStateUtils.h` | `SkCanvasStateUtils` | ❌ |
| `utils/SkCustomTypeface.h` | `SkCustomTypefaceBuilder` | ❌ |
| `utils/SkNWayCanvas.h` | `SkNWayCanvas` | ❌ |
| `utils/SkNoDrawCanvas.h` | `SkNoDrawCanvas` | ✅ |
| `utils/SkOrderedFontMgr.h` | `SkOrderedFontMgr` | ❌ |
| `utils/SkPaintFilterCanvas.h` | `SkPaintFilterCanvas` | ✅ |
| `utils/SkParse.h` | `SkParse` | ❌ |
| `utils/SkParsePath.h` | `SkParsePath` | ✅ |
| `utils/SkShadowUtils.h` | `SkShadowUtils` | ❌ |
| `utils/SkTextUtils.h` | `SkTextUtils` | 🟠 (overloads `GetPath`) |
| `codec/AndroidCodec.h` | `AndroidCodec` | ❌ |
| `codec/AvifDecoder.h` | `AvifDecoder` | ❌ |
| `codec/SkBmpDecoder.h` | `SkBmpDecoder` | 🟠 (présent comme `SkBmpCodec`) |
| `codec/Codec.h` | `Codec` | 🟠 (manque options/sampling) |
| `codec/CodecAnimation.h` | (constantes) | ❌ |
| `codec/SkEncodedImageFormat.h` | `SkEncodedImageFormat` | 🟠 (formats étendus) |
| `codec/SkEncodedOrigin.h` | `SkEncodedOrigin` | 🚫 (à vérifier dans `org.skia.codec`) |
| `codec/SkGifDecoder.h` | `SkGifDecoder` | 🟠 (présent comme `SkGifCodec`) |
| `codec/IcoDecoder.h` | `IcoDecoder` | ❌ |
| `codec/SkJpegDecoder.h` | `SkJpegDecoder` | 🟠 (présent comme `SkJpegCodec`) |
| `codec/JpegxlDecoder.h` | `JpegxlDecoder` | ❌ |
| `codec/SkPngChunkReader.h` | `SkPngChunkReader` | ❌ |
| `codec/SkPngDecoder.h` | `SkPngDecoder` | 🟠 (présent comme `SkPngCodec`) |
| `codec/SkPngRustDecoder.h` | `SkPngRustDecoder` | ❌ |
| `codec/PixmapUtils.h` | `PixmapUtils` | ❌ |
| `codec/RawDecoder.h` | `RawDecoder` | ❌ |
| `codec/SkWbmpDecoder.h` | `SkWbmpDecoder` | 🟠 (présent comme `SkWbmpCodec`) |
| `codec/SkWebpDecoder.h` | `SkWebpDecoder` | 🟠 (présent comme `SkWebpCodec`) |

> Annexe tronquée (~108 lignes ci-dessus, soit l'essentiel ; les sous-types
> imbriqués — `SkPaint::Style`, `SkColor::*`, `SkSurface::AsyncReadResult` —
> ne sont pas listés en ligne mais référencés en Section 4.

## Notes méthodologiques

- Cet audit est statique : il s'appuie sur les noms de classes / méthodes
  déclarés. Les implémentations partielles (méthode présente mais comportement
  divergent : ex. `SkBitmapDevice.drawImageRect` ignorant CTM non axis-aligné)
  sont reportées en Sections 2.x et 3.x quand un overload upstream existe avec
  une signature non couverte.
- Le scope GPU est strictement exclu, comme requis : `SkRecorder*` du module
  upstream contient une partie GPU (Ganesh) qui doit être omise lors du portage
  CPU-only.
- Les classes `Sk*Codec` du module ont été renommées par rapport aux
  `SkBmpDecoder`/`SkPngDecoder` upstream. L'annexe les marque 🟠 lorsque le
  type est topologiquement présent mais sous un nom différent — un alias
  type/redirect serait peut-être souhaitable.
- Les compteurs en haut sont des estimations honnêtes mais non exhaustives :
  une partie de la longue traîne (helpers `SkPaint::FilterCanvas`, `SkColor::TYPES`,
  enums internes) n'est pas comptée et représente vraisemblablement 50–80
  entrées supplémentaires de très faible impact.
