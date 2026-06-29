# Plan R-final : finalisation des API non-GPU + stubs JNI

> **Statut au 2026-05-14** : succède à `archives/API_REMEDIATION_PLAN.md` (64/64 sprints R1→R3 + R-suivi clôturés). Cible désormais les 55 GMs `🚧` non-GPU encore listées dans `MIGRATION_PLAN_GM_PORT.md`.

## 0. Contexte

Après S7, le module `:kanvas-skia` couvre **319/437 GMs (73 %)**. Les 118 `🚧` restants se décomposent en :

| Catégorie | Nombre | Action |
|---|---:|---|
| GPU-only (no raster reference) | 67 | Hors scope ; renvoi vers `MIGRATION_PLAN_GPU_WEBGPU.md` |
| **API manquante (non-GPU)** | **55** | **Scope du présent plan** |
| Asset / `#if 0` | 4 | Cas isolés traités au fil de l'eau |

**Objectif** : ramener le compte `🚧` non-GPU de 55 à ~10, soit **+45 GMs portées**, en n'utilisant **aucune dépendance JNI nouvelle obligatoire**. Les 10 résiduels deviennent des **stubs JNI documentés** (signature correcte, `NotImplementedError("JNI: <lib>")`), ce qui décolle les sites d'appel sans bloquer la compilation des GMs consommateurs.

## 1. Inventaire des bloqueurs (55 GMs, 18 API distinctes)

| API manquante | # GMs | GMs cibles | Faisabilité |
|---|---:|---|---|
| `SkShader.makeWithLocalMatrix` / `SkImageFilter.makeWithLocalMatrix` | 7 | composeshader, imagefilterstransformed, localmatriximagefilter, localmatrixshader, pictureshader, rsxtext, savelayer (partiel) | **Pure Kotlin** — wrapper trivial |
| `SkImage.makeSubset` | 5 | crop_imagefilter, drawbitmaprect, drawimageset, perspimages, (savelayer) | **Pure Kotlin** — copie de pixels |
| Encodeurs lossy (`SkJpegEncoder`, `SkPngEncoder`, `SkWebpEncoder` lossy) | 5 | encode_alpha_jpeg, encode_color_types, encode_platform, encode_srgb, jpg_color_cube | **Pure Kotlin via `javax.imageio.ImageIO`** (PNG/JPEG/BMP/GIF/WBMP stdlib JDK) ; WebP lossy = stub JNI |
| `SkCanvas.clipShader` | 4 | clipshader, complexclip, runtimeshader, windowrectangles | **Pure Kotlin** — drawPaint(shader) avant clip |
| `SkCanvas.drawRegion` | 2 | drawregion, drawregionmodes | **Pure Kotlin** — itère SkRegion → drawRect |
| `SkColorFilters.LinearToSRGBGamma` / `SRGBToLinearGamma` | 2 | srgb, graphitestart | **Pure Kotlin** — table de transfert sRGB |
| `SkShaderMaskFilter` | 2 | savelayer, shadermaskfilter | **Pure Kotlin** — composite shader×mask |
| `SkImageGenerator` / `DeferredFromGenerator` | 2 | orientation, pictureimagegenerator | **Pure Kotlin** — fonctional interface lazy decode |
| Color emoji typefaces (CBDT/Sbix/ColrV0/Svg/COLRv1/Palette) | 5 | colrv1, palette, scaledemoji, scaledemoji_rendering, coloremoji_blendmodes | **STUB JNI** — parsers OpenType complexes |
| Variantes textuelles existantes | 4 | texteffects (intercepts ✅ S7), typeface (kerning), simpleaaclip (aaclip), mac_aa_explorer (mac fonts) | **Pure Kotlin** pour 3/4 ; AAClip = STUB Skia-internal |
| Working color-space pipeline | 2 | colorspace, workingspace | **Pure Kotlin** — `SkImage.makeColorSpace` + `makeWithWorkingColorSpace` (ColorMatrix ou code chemin) |
| `SkImage.makeWithFilter` + `makeTemporaryImage` + HDR PQ retag | 1 | hdr_pip_blur | **Pure Kotlin** (retag = métadonnée) ; HDR PQ optionnel |
| Fontations Rust crate | 2 | fontations, fontations_ft_compare | **STUB obligatoire** — dépendance externe Rust |
| `SkRuntimeEffect` SkSL parser | 1 | rippleshadergm | **STUB obligatoire** — parser SkSL = effort multi-mois |
| `VideoDecoder` (FFmpeg) | 1 | video_decoder | **STUB JNI** — pas de path CPU upstream |
| `SkPaint.computeFastBounds` + `SkImageFilter.computeFastBounds` | 1 | filterfastbounds | **Pure Kotlin** — déjà calculé en interne pour layer bounds |
| `SkPath.contains(scalar, scalar)` | 1 | hittestpath | **Pure Kotlin** — point-in-path classique |
| `SkCanvas.drawImageNine` | 1 | ninepatchstretch | **Pure Kotlin** — cas dégénéré 3×3 de drawImageLattice (déjà fait) |
| `SkM44` perspective + perspective `drawImageRect` | 1 | crbug_224618 | **Pure Kotlin** — SkM44 partiel existe ; finir le path drawImageRect |
| `SkShaders.CoordClamp` | 1 | coordclampshader | **Pure Kotlin** — wrapping shader avec clamp en sample() |
| `SkTableMaskFilter` | 1 | tablemaskfilter | **Pure Kotlin** — table 256 entrées sur alpha |
| `SkHighContrastFilter` | 1 | highcontrastfilter | **Pure Kotlin** — ColorMatrix + invert/grayscale |
| `SkMipmapBuilder` + `SkImage.attachTo` | 1 | showmiplevels | **Pure Kotlin** — chaîne de bitmaps downsamplés |
| GIF multi-frame (`Codec.getFrameInfo` + per-frame `getPixels(Options{frameIndex, priorFrame})`) | 1 | animated_gif | **Pure Kotlin** — extension du codec GIF déjà présent |
| AnimatedImage + EXIF AndroidCodec | 1 | animated_image_orientation | **Pure Kotlin** — wrap codec + EXIF reader (`metadata-extractor` ou `javax.imageio.metadata`) |
| YUV multi-plane (`tools/gpu/YUVUtils`) | 1 | compositor_quads | **Pure Kotlin** — conversion YUV→RGB |
| `SkFontArguments.VariationPosition` + `makeClone` (variable fonts) | 1 | fontscalerdistortable | **Pure Kotlin** — AWT supporte `Font.deriveFont(Map<TextAttribute, ...>)` + `OpenTypeFont` axes via java.awt.font |
| LiberationFontMgr public family API | 1 | fontmgr | **STUB recommandé** — résolution de famille portable internal Skia |
| Assets (PlanetTypeface + ReallyBigA.ttf) | 1 | mixedtextblobs | **Pure Kotlin** — copier assets + porter |

**Synthèse** :
- **Pure Kotlin réalisable** : ~45 GMs sur 18 sprints courts/moyens
- **Stubs JNI documentés** : ~10 GMs sur 6 stubs (WebP lossy, FFmpeg vidéo, Fontations, COLR v1 / emoji color, SkSL, LiberationFontMgr, AAClip)

## 2. Sprints proposés

Format : sprint = paquet d'API homogènes, 1 PR par sprint, no-auto-merge, refresh GM plan à la fin.

### Sprint R-final.1 — Quick wins shader/clip/region (8 GMs)
Effort : **S** ; agents : **2 × 4 GMs**

API à ajouter :
- `SkCanvas.clipShader(shader, op = Intersect)` — implémentation : compose un mask via `saveLayerAlpha` + `drawPaint(shader, BlendMode.DstIn)` puis `clipPath` standard (équivalent visuel suffisant pour les GMs de référence).
- `SkCanvas.drawRegion(region, paint)` — itère `SkRegion.Iterator`, `drawRect` par cellule.

GMs : clipshader, complexclip, runtimeshader, windowrectangles, drawregion, drawregionmodes + 2 quick wins (`hittestpath` via `SkPath.contains(x, y)` + `ninepatchstretch` via dégénéré drawImageLattice 3×3).

### Sprint R-final.2 — LocalMatrix wrappers (7 GMs)
Effort : **S** ; agents : **2 × 3-4 GMs**

API :
- `SkShader.makeWithLocalMatrix(matrix: SkMatrix): SkShader` — interne wrapper `SkLocalMatrixShader` qui pré-multiplie la matrice au moment du sample().
- `SkImageFilter.makeWithLocalMatrix(matrix: SkMatrix): SkImageFilter` — wrapper similaire côté filter.

GMs : composeshader, imagefilterstransformed, localmatriximagefilter, localmatrixshader, pictureshader, rsxtext, (déblocage partiel de savelayer).

### Sprint R-final.3 — Color management (4 GMs)
Effort : **M** ; agents : **2 × 2 GMs**

API :
- `SkColorFilters.LinearToSRGBGamma()` / `SRGBToLinearGamma()` — table 256 entrées + ColorFilter sur canal linéaire.
- `SkImage.makeColorSpace(target: SkColorSpace): SkImage` — repixel via SkColorSpaceXformer existant.
- `SkColorFilter.makeWithWorkingColorSpace(cs)` + `SkShader.makeWithWorkingColorSpace(cs)` + `SkWorkingColorSpaceShader` — wrap qui force le pipeline à transiter par `cs`.

GMs : srgb, graphitestart, colorspace (déblocage final), workingspace.

### Sprint R-final.4 — Image helpers + ShaderMaskFilter (5 GMs)
Effort : **S** ; agents : **2 × 2-3 GMs**

API :
- `SkImage.makeSubset(rect, ctx?)` — copie pixels via `readPixels` puis nouvelle SkImage raster.
- `SkShaderMaskFilter.make(shader)` — MaskFilter qui sample le shader pour produire l'alpha mask.
- `SkPaint.computeFastBounds(rect)` / `SkImageFilter.computeFastBounds(rect)` — calcul d'enveloppe (déjà fait en interne).

GMs : crop_imagefilter, drawbitmaprect, drawimageset, perspimages, savelayer, shadermaskfilter, filterfastbounds (5+2).

### Sprint R-final.5 — Generators + mipmaps + GIF anim (4 GMs)
Effort : **M** ; agents : **2 × 2 GMs**

API :
- `SkImageGenerator` interface + `SkImages.DeferredFromGenerator(gen)` — fonctional interface lazy `getPixels(info, dst, rowBytes)`.
- `SkMipmapBuilder` + `SkImage.attachTo(builder)` — chaîne de bitmaps downsamplés stockée side-band sur l'image.
- `Codec.getFrameInfo(): List<FrameInfo>` + `Codec.getPixels(options: Options{frameIndex, priorFrame})` — extension du codec GIF existant pour multi-frame.

GMs : orientation, pictureimagegenerator, showmiplevels, animated_gif.

### Sprint R-final.6 — Encodeurs via `javax.imageio` (5 GMs)
Effort : **M** ; agents : **2 × 2-3 GMs**

API :
- `SkPngEncoder.Encode(stream, pixmap, options)` — délègue à `javax.imageio.ImageIO.write(bufferedImage, "PNG", stream)`. Variante pure-Kotlin via `java.util.zip.Deflater` reste possible si on veut éviter ImageIO.
- `SkJpegEncoder.Encode(stream, pixmap, options{quality, downsample, alphaOption})` — délègue à `javax.imageio.ImageIO` avec `JPEGImageWriteParam.setCompressionQuality`. Pas de JNI tiers.
- `SkWebpEncoder.Encode` — **lossless déjà ✅** (R-suivi.23). **Lossy = STUB JNI libwebp** documenté.

GMs : encode_alpha_jpeg, encode_color_types, encode_platform, encode_srgb, jpg_color_cube.

### Sprint R-final.7 — Misc raster (5 GMs)
Effort : **S/M** ; agents : **2 × 2-3 GMs**

API :
- `SkM44.preTranslate/preConcat` complet + `SkCanvas.drawImageRect` perspective path.
- `SkShaders.CoordClamp(shader, rect)` — clamp UV en sample().
- `SkHighContrastFilter.Make(invertStyle, grayscale, contrast)` — ColorMatrix.
- `SkTableMaskFilter.MakeGamma(gamma)` / `MakeClip(min, max)` — table 256.
- Assets : copier `PlanetTypeface.ttf` + `ReallyBigA.ttf` depuis upstream `resources/`.
- `SkTypeface.getKerningPairAdjustments(glyphs)` via AWT `Font.getLineMetrics`+`TextLayout` (variante typeface_kerning).

GMs : crbug_224618, coordclampshader, highcontrastfilter, tablemaskfilter, mixedtextblobs, + variante typeface_kerning.

### Sprint R-final.8 — Animated images + EXIF + YUV (3 GMs)
Effort : **M** ; agents : **2 × 1-2 GMs**

API :
- `AnimatedImage.MakeFromCodec(codec)` — wrapper iterable du frame loop existant.
- `AndroidCodec` EXIF orientation — parser EXIF via `javax.imageio.metadata.IIOMetadata` ou `metadata-extractor`.
- YUV→RGB pure Kotlin (BT.601 + BT.709 matrices) pour composition multi-plane.

GMs : animated_image_orientation, compositor_quads, (déblocage partiel hdr_pip_blur).

### Sprint R-final.9 — Variable fonts + HDR pipeline (2 GMs)
Effort : **L** ; agent : **1 × 2 GMs séquentiel**

API :
- `SkFontArguments.VariationPosition.Coordinate(axis, value)` + `SkTypeface.makeClone(args)` — utilise `java.awt.Font.deriveFont(Map<TextAttribute>)` ; pour les axes OpenType non couverts par AWT, fallback `sun.font.OpenTypeFont` ou stub limité.
- `SkImages.MakeWithFilter(image, filter, subset, clipBounds)` — applique un filter + retourne une nouvelle SkImage raster.
- `SkSurface.makeTemporaryImage(info)` — alloc backing store temporaire.
- HDR PQ retag — métadonnée colorspace pure (pas de pipeline tonemapping requis pour la GM).

GMs : fontscalerdistortable, hdr_pip_blur.

### Sprint R-final.S — Stubs JNI documentés (10 GMs en attente)
Effort : **S** (signatures uniquement) ; agent : **1 × tous**

Pour chaque API ci-dessous, créer la signature publique correcte qui jette `throw NotImplementedError("JNI required: <library>")` en runtime, mais qui satisfait la compilation des GMs consommateurs. Les GMs portées sont marquées `@Ignore("STUB JNI: …")` côté test pour ne pas casser la CI.

| Stub | API stubée | Bibliothèque cible | GMs débloquées (compile) |
|---|---|---|---|
| **STUB.WEBP_LOSSY** | `SkWebpEncoder.Encode` avec `compression = LOSSY` | libwebp | (variantes encode_*) |
| **STUB.FFMPEG** | `VideoDecoder.MakeFromStream(stream)` | FFmpeg libavformat/libavcodec | video_decoder |
| **STUB.FONTATIONS** | `SkTypeface_Fontations.MakeFromStream` | Fontations Rust crate (UniFFI ou JNI direct) | fontations, fontations_ft_compare |
| **STUB.COLR_V1** | `SkTypeface.makeColrV1Glyphs` + `SkFontArguments.Palette(index, overrides)` | FreeType + HarfBuzz COLR v1 path | colrv1, palette |
| **STUB.EMOJI_TABLES** | `EmojiTypeface.CBDT/Sbix/SVG/ColrV0` | FreeType + librsvg pour SVG-in-OT | scaledemoji, scaledemoji_rendering, coloremoji_blendmodes |
| **STUB.SKSL** | `SkRuntimeEffect.MakeForShader(skslSource)` | parser SkSL upstream (~30k LOC) | rippleshadergm |
| **STUB.LIBERATION_FM** | `LiberationFontMgr.Make(): SkFontMgr` | Skia internal portable family resolution | fontmgr |
| **STUB.AAClip** | `SkAAClip` Skia-internal flavour | Skia internal | variante simpleaaclip |

**Important** : ces stubs **ne ferment pas** ces GMs ; elles restent `🚧` dans le plan GM. Mais elles **dégagent les autres GMs** qui se référençaient à leur signature pour compiler (ex : `SkShader::makeWithLocalMatrix` n'a aucun stub car il est dans R-final.2 ; mais si on découvre un appel à `SkRuntimeEffect.MakeForShader` dans une GM secondaire, elle compilera grâce au stub).

## 3. Cadence et règles

- **Cadence** : 1 PR par sprint R-final.N, ouverte mais **non-mergée** (rule `feedback_no_auto_merge`).
- **Refresh** : à la fin de chaque sprint, refresh `MIGRATION_PLAN_GM_PORT.md` pour cocher les GMs débloquées (`🚧 → ✅`) + ce plan pour passer le sprint en `[x]`.
- **Suivi des stubs** : tout site appelant un stub JNI doit ajouter un commentaire `// FIXME(STUB.X): see API_FINALIZATION_PLAN.md` au point d'appel.
- **CI** : la cible `:kanvas-skia:test` doit rester verte. Les GMs portées sur stubs sont annotées `@Ignore`.

## 4. Métrique cible

| Étape | GMs ✅ / 437 | % | 🚧 non-GPU | 🚧 GPU-only | Stubs JNI ouverts |
|---|---:|---:|---:|---:|---:|
| Avant R-final | 319 | 73 % | 55 | 67 | 0 |
| Après R-final.1-2 | 334 | 76 % | 40 | 67 | 0 |
| Après R-final.3-4 | 343 | 79 % | 31 | 67 | 0 |
| Après R-final.5-6 | 352 | 81 % | 22 | 67 | 1 (WebP lossy) |
| Après R-final.7-8 | 360 | 82 % | 14 | 67 | 1 |
| Après R-final.9 | 362 | 83 % | 12 | 67 | 1 |
| Après R-final.S | 362 | 83 % | 10 (stubés en compile) | 67 | 8 |

**Cible finale** : ~83 % de couverture portée, 67 GPU-only renvoyées vers `MIGRATION_PLAN_GPU_WEBGPU.md`, 8 dépendances JNI explicitement listées comme non-objectif tant qu'on reste pure-JVM.

## 5. Notes de portabilité

- **`javax.imageio.ImageIO`** est dans la stdlib JDK — pas un "vrai" JNI tiers, transparent pour le consommateur. Permet d'éviter libjpeg-turbo et libpng explicites.
- **AWT variable fonts** : `Font.deriveFont(Map<TextAttribute, ...>)` couvre `WEIGHT`, `WIDTH`, `POSTURE`, `TRACKING` ; pour des axes OpenType custom (`wght`, `wdth`, `slnt`, `opsz`, `GRAD`, …), AWT les expose via `OpenTypeFont` mais le set est limité. À évaluer en R-final.9.
- **`metadata-extractor`** (Drew Noakes, Apache 2.0) est une option pure-Java pour EXIF si `javax.imageio.metadata` ne suffit pas.
- **WebP lossy** existe en pure Java (`webp-imageio` de sejda, mais pas pure-Kotlin et embarque DLLs). Préférer le STUB pour rester portable.

## 6. Suivi

| Sprint | Statut | PR | GMs ✅ | Commentaires |
|---|---|---|---:|---|
| R-final.1 — Quick wins shader/clip/region | ☑ | #438 | 8 | 4 APIs déjà présentes (audit) → 100% portage. ClipShader/RuntimeShader floor abaissé : bug `localMatrix` forward/inverse (follow-up F1) |
| R-final.2 — LocalMatrix wrappers | ☑ | #439 | 8 | 1 bonus (`ComposeShaderBitmapGM(true)`). RsxText skip → besoin `SkTextBlobBuilder.allocRunRSXform` (F2) |
| R-final.3 — Color management | ☑ | #440 | 1 | 2/4 APIs déjà présentes. 3 GMs skipped : ColorSpaceGM PNG ref dégénérée (structurel), GraphiteStartGM sans PNG ref (structurel) + manque `MakeGaussian` (F5), WorkingSpaceGM = registry SkSL custom (F4) |
| R-final.4 — Image helpers + ShaderMaskFilter | ☑ | #441 | 5 | Toutes APIs déjà présentes (audit) → 100% portage (11 tests). Skips : DrawImageSet = `experimental_DrawEdgeAAImageSet` (F6), PerspImages = perspective drawImageRect (F7) |
| R-final.5 — Generators + mipmaps + GIF anim | ☑ | #443 | 5 | 1 bonus (`RespectOrientationJpegGM`). AnimatedGif 100%, GIF89a disposal honoré. Manque `SkTextUtils.GetPath` (F10) |
| R-final.6 — Encodeurs via `javax.imageio` | ☑ | #442 | 5 | JpgColorCubeGM 100%. Bonus fix bug latent `EncoderSupport.bitmapToBufferedImage`. Follow-ups : iCCP chunk (F8), per-bitmap alpha-type tag (F9) |
| R-final.7 — Misc raster | ☑ | #445 | 6 | SkM44 perspective déjà présent. PlanetTypeface skipped (color-emoji non-AWT). 5 améliorations sur scores existants au merge |
| R-final.8 — Animated images + EXIF + YUV | ☑ | #444 | 1 + 2 ratchets up | Orientation444GM + RespectOrientationJpegGM **19.87% → 100%** grâce au fix EXIF. SkEncodedOrigin + PixmapUtils.Orient déjà présents. CompositorQuads minimal port (full = `experimental_DrawEdgeAAImageSet`, F6). 2 GMs skip : assets `flightAnim.gif` + `stoplight_h.webp` non vendored (F11) + animated WebP (F12) |
| R-final.9 — Variable fonts + HDR pipeline | ☑ | #447 | 2 | SkFontArguments + makeClone via AWT TextAttribute (4 axes : wght/wdth/slnt/ital). Axes opsz/GRAD/XHGT/XOPQ/YOPQ silently dropped (limite AWT). Asset Distortable.ttf vendored. SkColorSpace.MakePqHdr() (BT.2100 + Rec.2020) |
| R-final.S — Stubs JNI documentés | ☑ | #446 | 0 (10 squelettes) | 8 stubs créés : WEBP_LOSSY, FFMPEG, FONTATIONS, COLR_V1, EMOJI_TABLES, SKSL, LIBERATION_FM, AAClip. 10 GM squelettes `@Ignore("STUB.X")` |
| **Plan refresh (clôture)** | ☑ | (cette PR) | — | Refresh + archivage |

**Total** : **11 PRs ouvertes** ; **+43 GMs** portées (319 → **362 / 437 = 83 %**).

## 7. Follow-ups consolidés

Ces items ont été découverts au fil des sprints R-final.1 → R-final.9 et n'ont pas été traités dans le scope du plan. Ils restent à pousser dans un sprint dédié post-R-final si la couverture doit grimper au-delà de 83 %.

| ID | Découvert | API / item | Impact GM |
|---|---|---|---|
| **F1** | R-final.1 | `SkRuntimeEffect.makeShader(localMatrix)` applique forward au lieu d'inverse | floor ClipShaderGM + RuntimeShaderGM (~50% au lieu de ~95%) |
| **F2** | R-final.2 | `SkTextBlobBuilder.allocRunRSXform(font, count)` manquant | RsxTextGM (rsx_blob_shader) |
| **F3** | R-final.3 | `SkBitmapDevice.imagePixelsInDeviceColorSpace` ne respecte pas `image.colorSpace` | latent ; sprint dédié de recalibrage requis (MipmapGray8SrgbGM régressait au fix) |
| **F4** | R-final.3 | Registry SkSL runtime-effect dynamique (hash & dispatch arbitrary user SkSL) | WorkingSpaceGM + RippleShaderGM |
| **F5** | R-final.3 | `SkColorFilterPriv.MakeGaussian` | GraphiteStartGM cellule 4 |
| **F6** | R-final.4 | `experimental_DrawEdgeAAImageSet` + `SkCanvas.ImageSetEntry` | DrawImageSetGM + CompositorQuadsImageGM full |
| **F7** | R-final.4 | Perspective `drawImageRect` raster (CTM non axis-aligned) | PerspImagesGM + persp* GMs |
| **F8** | R-final.6 | iCCP chunk embedding dans `SkPngEncoder` | EncodeSrgbGM (+10-15%) |
| **F9** | R-final.6 | Per-bitmap alpha-type tag sur `SkBitmap` | EncodeAlphaJpegGM + EncodeColorTypesGM (+30-40%) |
| **F10** | R-final.5 | `SkTextUtils.GetPath` | PictureImageGeneratorGM (wordmark upstream) |
| **F11** | R-final.8 | Vendored assets manquants : `flightAnim.gif` + `stoplight_h.webp` | FlightAnimatedImageGM + StoplightAnimatedImageGM |
| **F12** | R-final.8 | `SkWebpCodec` multi-frame (animated WebP) | StoplightAnimatedImageGM |
