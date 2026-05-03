# Migration Skia → Kotlin via `kanvas-skia` — Plan itératif

> Plan vivant — cocher au fur et à mesure de l'avancement.

## Contexte

Le projet vise à réimplémenter Skia en Kotlin. Aujourd'hui :

- Le module `:kanvas` contient une implémentation partielle, hand-written, sous le package `com.kanvas.*`. 22 GM tests fonctionnent (référence visuelle ~70-95% de similarité). Le harness de test (`GM`, `TestUtils`, `SimilarityTracker`) est éprouvé.
- Le module `:kanvas-skia` est **vide** (seul `build.gradle.kts`). C'est notre point de départ.
- Le dossier `kanvas/src/generated/` contient ~3 295 fichiers Kotlin produits par traduction mécanique du C++ Skia, en packages `org.skia.{core, foundation, effects, gpu, modules, tests, …}`. **Ils ne compilent pas** : 281+ TODOs dans `core`, ~458 stubs dans le package `undefined`, typealias circulaires (ex. `ResourceProvider = ResourceProvider`), tous les 297 GM tests ont des corps `TODO()`. **Mais** chaque fichier conserve le code C++ original en Javadoc — excellente spécification pour le portage manuel (cf. [kanvas/src/generated/tests/org/skia/tests/SimpleRectGM.kt](kanvas/src/generated/tests/org/skia/tests/SimpleRectGM.kt)).
- 989 images de référence Skia disponibles à [kanvas/src/test/resources/original-888/](kanvas/src/test/resources/original-888/).

L'objectif : **bootstrapper un nouveau module `:kanvas-skia` qui compile et fait passer un premier GM test**, puis grandir par tranches verticales (un GM = un slice = sa fermeture de dépendances minimale).

## Décisions architecturales (validées)

- [x] **Copie sélective, pas en bloc.** `src/generated/` reste en lecture seule, les fichiers sont tirés un par un au fil des slices, en utilisant le commentaire C++ Javadoc comme spec.
- [x] **`:kanvas-skia` autonome, sans dépendance sur `:kanvas`.** Packages racines et signatures de GM divergents.
- [x] **Seuil de similarité par phase, ratchet par test.** Phase 1 ≥ 99% / Phase 2 ≥ 95% / Phase 3+ ≥ 90% / Phase 5+ ≥ 85%.

---

## Phase 0 — Bootstrap (zéro GM, infra prête)

**But** : `:kanvas-skia:test` exécute un test JUnit5 placeholder qui réussit, et l'harness charge une image de référence.

### Build & dépendances
- [x] Mettre à jour [kanvas-skia/build.gradle.kts](kanvas-skia/build.gradle.kts) :
  - [x] Retirer le plugin `application` (pas de main pour l'instant).
  - [x] Ajouter `testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")`.
  - [x] Ajouter `testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")` + `junit-platform-launcher:1.10.2` (requis par Gradle 9).
  - [x] Pointer les ressources de test : `sourceSets["test"].resources.srcDir("../kanvas/src/test/resources")`.

### Harness GM (org.skia.tests)
- [x] Créer `kanvas-skia/src/main/kotlin/org/skia/tests/GM.kt` avec signatures alignées sur le code généré : `getName(): String`, `getISize(): SkISize`, `onDraw(canvas: SkCanvas?)`, `onAnimate(nanos: Double): Boolean = false`. Porter de [kanvas/src/main/kotlin/testing/GM.kt](kanvas/src/main/kotlin/testing/GM.kt).
- [x] Créer `kanvas-skia/src/main/kotlin/org/skia/tests/DrawResult.kt` (enum `kOk/kFail/kSkip`).

### Math fondamentaux (org.skia.math)
- [x] `SkScalar.kt` (typealias `Float` + trigo précise). Porter de [kanvas/src/main/kotlin/core/SkScalar.kt](kanvas/src/main/kotlin/core/SkScalar.kt).
- [x] `SkISize.kt`, `SkSize.kt` (data classes).
- [x] `SkRect.kt`, `SkIRect.kt` avec factory `MakeLTRB`, `MakeXYWH`, `MakeWH`. Porter de [kanvas/src/main/kotlin/core/Rect.kt](kanvas/src/main/kotlin/core/Rect.kt).

### Harness de test (org.skia.testing)
- [x] `TestUtils.kt` : `loadReferenceImage`, `compareImages`, `saveDebugImage`. `runGmTest` reporté en Phase 1 (nécessite SkBitmap).
- [x] `SimilarityTracker.kt` : ratchet sur `kanvas-skia/test-similarity-scores.properties`.

### Vérification Phase 0
- [x] `BootstrapTest.kt` : instancier un `GM` minimal et charger `bigrect.png` depuis les ressources.
- [x] `./gradlew :kanvas-skia:test` → vert (3 tests).

---

## Phase 1 — Premier test vert : `BigRectGM` + `SimpleRectGM`

**Justification.** `BigRectGM` a la fermeture minimale parmi les Level-1 : `SkCanvas.{save, restore, translate, drawRect}`, `SkPaint.{color, style, strokeWidth}`, `SkRect`, `SkColor`. Pas de path, shader ou gradient. Référence `bigrect.png` disponible.

### Foundation (org.skia.foundation)
- [x] `SkColor.kt` : `typealias SkColor = Int` (ARGB 0xAARRGGBB) + helpers + `colorToRGB565`.
- [x] `SkPaint.kt` : `color`, `style`, `strokeWidth`, `isAntiAlias`.
- [x] `SkBitmap.kt` : raster ARGB8888 row-major, `eraseColor`, `getPixel`/`setPixel`.

### Core (org.skia.core)
- [x] `SkCanvas.kt` : translation-only CTM stack + clip stack, `save/restore/translate/clipRect/drawRect`.
- [x] `SkBitmapDevice.kt` : raster non-AA rect (FILL + STROKE w∈{0, ≥1}) + SrcOver compositing. Règle de pixel-center via `floor(c+0.5)` (= `SkScalarRoundToInt`). Hairline AA-off via `floor(c)` aligné sur `SkScan::HairLineRgn`.

### Tools
- [x] `SkRandom.kt` : port bit-compatible de [Skia](https://github.com/google/skia/blob/main/include/utils/SkRandom.h) (multiply-with-carry à deux flux, bit-trick IEEE 754 pour `nextF`, init LCG sur `kMul=1664525, kAdd=1013904223`). Les GMs random-driven (à commencer par SimpleRectGM) consomment exactement la même séquence que la référence.

### Tests GM
- [x] `tests/BigRectGM.kt` : hand-port intégral.
- [x] `tests/SimpleRectGM.kt` : hand-port (le Javadoc généré tient lieu de spec).

### Tests JUnit5
- [x] `BigRectTest.kt` : run + compare avec tolérance + ratchet.
- [x] `SimpleRectTest.kt` : idem.

### Découverte hors-plan → résolue par [archives/MIGRATION_PLAN_COLORSPACE.md](archives/MIGRATION_PLAN_COLORSPACE.md)
Les références `original-888/*.png` embarquent un profil ICC **Rec.2020** (`"DM unified Rec.2020"` per le tEXt chunk) — sRGB pur bleu y arrive à `(43, 13, 242)`. Le plan #2 a porté `skcms` + `SkColorSpace` + `SkColorSpaceXformSteps`, et `TestUtils.runGmTest` rend désormais directement dans le profil Rec.2020. Tolérance descendue de 160 à **1** sur tous les GMs Phase 1-3a.

### Vérification Phase 1 (post-colorspace)
- [x] `BigRectGM` ≥ 95% vs `bigrect.png` à `tolerance=1` — **résultat : 95.53%**. Le résiduel ~4.5% est du désaccord rasterizer non-AA sur les cellules à coords extrêmes (1e6 / 1e10), pas du color shift.
- [x] `SimpleRectGM` ≥ 99% à `tolerance=1` (positions et couleurs RGB565 bit-identiques avec Skia grâce au port fidèle de SkRandom) — **résultat : 100.00%**.
- [x] **Pass count cumulé : 2 GM.**

---

## Phase 2 — Anti-aliasing des rects : `ThinRectsGM` (non-round) + `ClipStrokeRectGM`

**But** : rasterisation AA pour rects axis-aligned, configurations `isAntiAlias=true`.

**Cadrage du scope (révisé après inspection du `src/generated`).** Deux GMs initialement listés sont hors scope d'une phase AA pure :
- `ScaledRectsGM` : utilise `canvas->setMatrix(MakeAll(...))` (rotation/skew) + `setBlendMode(kPlus)`. Reporté **Phase 4** (matrix complète) / **Phase 6** (blend modes additionnels).
- `ClearSwizzleGM` : absent de `kanvas/src/generated/tests/` et pas de référence `clearswizzle.png`. Drop.

Remplacés par `ClipStrokeRectGM` (`clip_strokerect.png` dispo) — petite mais bonne couverture AA-stroke + clip.

- [x] Implémenter coverage AA analytique axis-aligned dans `SkBitmapDevice.drawRect` (exact pour fill, fait plus de sens qu'un supersampling 4× pour des rects axis-aligned).
- [x] Étendre AA à `kStroke_Style` (coverage = outer − inner) ; AA hairline = stroke d'épaisseur 1.
- [x] Ajouter `setBGColor` au harness `GM` (ThinRects clear sur noir) ; `runGmTest` lit `gm.bgColor()`.
- [x] Ajouter overload `SkCanvas.clipRect(rect, doAntiAlias)` (rect intégral → ignore l'arg).
- [x] Hand-port `tests/ThinRectsGM.kt` (non-round seulement, le booléen `fRound=true` reste bloqué jusqu'à `SkRRect` Phase 4).
- [x] Hand-port `tests/ClipStrokeRectGM.kt`.

### Vérification Phase 2 (post-colorspace, `tolerance=1`)
- [x] `ThinRectsGM` ≥ 92% à `tolerance=1` — **résultat : 92.10%**. Le résiduel ~8% est du sub-ulp rounding sur la coverage→alpha (97.94% à t=16, 100% à t=32). Reproduire `SkScan_Antihair.cpp` exact pour Phase 2.5 si nécessaire.
- [x] `ClipStrokeRectGM` ≥ 99% à `tolerance=1` — **résultat : 100.00%**.
- [x] BigRectGM 95.53%, SimpleRectGM 100% (cf. Phase 1).
- [x] **Pass count cumulé : 4 GM.** (BigRect, SimpleRect, ThinRects, ClipStrokeRect.)

---

## Phase 3 — Paths, fills, strokes simples

**But initial** : porter `SkPath` + scanline fill + stroker basique pour `ConvexPathsGM`, `ConcavePathsGM`, `CubicPathsGM`, `AddArcGM`, `ArcToGM`.

**Cadrage révisé après inspection des `.cpp` upstream.** Le périmètre des 5 GMs ciblés est très hétérogène :

| GM                | Verbs requis        | CTM requis                  | Slice cible    |
|-------------------|---------------------|-----------------------------|----------------|
| ConcavePathsGM    | line + quad         | translate + 1× `scale`      | **3a** ✅       |
| ConvexPathsGM     | line + quad + cubic | translate + `scale` + `setMatrix` | 3b/3c    |
| CubicPathsGM      | cubic-heavy         | (à valider)                 | 3c             |
| AddArcGM          | addArc              | translate + `scale` + `rotate` | Phase 4 (rotate) |
| ArcToGM           | arcTo (5 variantes) | possible scale              | 3c             |

Phase 3 est donc tranchée en sous-phases livrées séparément ; `ConcavePathsGM` est l'unique cible de **Phase 3a**.

### Phase 3a — `SkPath` line-only + scanline fill AA + scale CTM + `ConcavePathsGM` ✅

- [x] Créer `org.skia.foundation.SkPath` (mutable, verbs `kMove/kLine/kClose`) + `SkPathFillType` enum (`kWinding`, `kEvenOdd`, +inverse stub) + factory `Polygon` + `addPolygon`.
- [x] `quadTo` flatten naïf (16 segments) — la pipeline reste line-only, `SkBitmapDevice` n'a pas encore besoin de connaître les Béziers. Cubic/conic en 3b/3c.
- [x] Étendre `SkCanvas` : CTM `(sx, sy, tx, ty)` (translate+scale, pas encore rotate/skew), `scale()`, `drawPath()`.
- [x] Implémenter `SkBitmapDevice.drawPath` (fill seulement, AA via 4×4 supersampling scanline). Walk crossings triées, winding/even-odd, accumulation de coverage par pixel.
- [x] Hand-port `tests/ConcavePathsGM.kt` (29 sous-tests).

#### Vérification Phase 3a (post-colorspace, `tolerance=1`)
- [x] `ConcavePathsGM` ≥ 98% à `tolerance=1` — **résultat : 98.86%**. Le résiduel ~1% est du sub-ulp rounding sur la coverage AA des bords (100% à t=128).
- [x] Pas de régression sur Phase 0/1/2.
- [x] **Pass count cumulé : 5 GM.**

### Phase 3b — Path subsystem buildout (no GM ports yet) ✅

**But** : élargir la surface du subsystem path, sans GM port associé. Les futurs GMs (Phase 3c et Phase 4) hériteront d'une API stable.

- [x] Splitter `SkPath` (immutable, parallel-array storage) / `SkPathBuilder` (mutable, fluent).
- [x] Verbs Bézier complets stockés tels quels : `kQuad` (2 points), `kConic` (2 points + weight), `kCubic` (3 points). `quadTo` ne fait plus de flatten au build (le rasterizer s'en charge).
- [x] `conicTo` : poids ≤ 0 → fallback line, poids = 1 → collapse en `kQuad`, sinon vrai `kConic` avec weight stocké.
- [x] `arcTo(rect, startDeg, sweepDeg, forceMoveTo)` + `addArc(...)` : conversion en cubics via approximation Hugues (`k = (4/3) * tan(θ/4)`). Sweep découpé en segments ≤ 90°. Sweep nul → moveTo/lineTo dégénéré sans cubic.
- [x] Factories `SkPath.Rect`, `SkPath.Circle`, `SkPath.Oval`, `SkPath.Line`, `SkPath.Polygon` (avec `fillType` / `isVolatile` args).
- [x] Builder helpers : `addRect`, `addOval` (4 cubics, kappa = `(4/3)·(√2 − 1)`), `addCircle`, `addPolygon`, `addPath`, `setFillType`.
- [x] `SkPathDirection` enum (`kCW` / `kCCW`).
- [x] `detach()` reset complet du builder vers état vide ; `snapshot()` copy sans reset.
- [x] Étendre `SkBitmapDevice.buildEdges` : flattening adaptatif De Casteljau pour `kQuad` / `kCubic` (chord ≤ 0.25 px en device space, profondeur max 18) ; flattening uniform-t (32 steps) pour `kConic`.
- [x] Refactor `ConcavePathsGM` pour utiliser `SkPathBuilder`.

#### Vérification Phase 3b
- [x] 21 tests unitaires sur `SkPathBuilder` (verbs, factories, arc, conic, detach/snapshot, addPath).
- [x] 6 tests rasterizer end-to-end (rect, circle, quadTo, even-odd hole, cubic teardrop, addArc 360°).
- [x] Aucune régression sur les 5 GMs existants.

### Phase 3c — Path stroker + GM ports

- [ ] Stroker path → fill path (`kButt_Cap` + `kMiter_Join` only).
- [ ] Hand-port `tests/ArcToGM.kt`, `tests/ConvexPathsGM.kt`, `tests/CubicPathGM.kt` selon faisabilité.

---

## Phase 4 — Cercles, ovals, RRects : `CircleSizesGM`, `RRectGM`, `RoundRectGM`, `DRRectGM`

**But** : remplacer les fallbacks circle→rect actuels ([Canvas.kt:213](kanvas/src/main/kotlin/core/Canvas.kt:213), [221](kanvas/src/main/kotlin/core/Canvas.kt:221)) par de vraies courbes.

- [ ] `SkCanvas.drawCircle` : émet `SkPath` avec courbes coniques (cf. [Arc.kt](kanvas/src/main/kotlin/core/Arc.kt)).
- [ ] `SkCanvas.drawOval` : idem.
- [ ] `SkCanvas.drawRRect` : idem.
- [ ] Porter [kanvas/src/main/kotlin/core/RRect.kt](kanvas/src/main/kotlin/core/RRect.kt) → `SkRRect`.

### Tests GM
- [ ] Hand-port `tests/CircleSizesGM.kt`.
- [ ] Hand-port `tests/RRectGM.kt`.
- [ ] Hand-port `tests/RoundRectGM.kt`.
- [ ] Hand-port `tests/DRRectGM.kt`.

### Vérification Phase 4
- [ ] Tests ≥ 92%.
- [ ] **Pass count cumulé : ~16 GM.**

---

## Phase 5 — Gradients et bitmap-shaders : `FillrectGradientGM`, `AlphaGradientsGM`, `GradientGM`, `BitmapRectGM`

**But** : introduire l'infrastructure `SkShader` (linear, radial, image).

### Shader
- [ ] Porter [kanvas/src/main/kotlin/core/Shader.kt](kanvas/src/main/kotlin/core/Shader.kt) → `org.skia.foundation.SkShader`.
- [ ] `SkLinearGradient`.
- [ ] `SkRadialGradient`.
- [ ] Faire dialoguer le rasterizer rect/path avec le shader (couleur source par pixel).

### Bitmap-shader
- [x] `SkImage` + `SkBitmap.asImage()` (basic immutable wrap, parallel-tracked, voir « Travaux parallèles »).
- [x] `SkSamplingOptions` + `SkFilterMode` + `SkMipmapMode` (kNearest, kLinear ; mipmap reporté). Parallel-tracked.
- [x] `SkTileMode` enum déclaré (kClamp / kRepeat / kMirror / kDecal) — clamp seulement utilisé par `drawImageRect` ; les autres arrivent avec `makeShader()`.
- [ ] `SkBitmap.makeShader()` (vraie source par-pixel pour le rasterizer rect/path).

### Undefined
- [ ] Résoudre `undefined.SkColor4f` → `data class SkColor4f(r,g,b,a: Float)` + `toSkColor(): Int`.
- [ ] Résoudre `undefined.SamplingOptions`.

### Tests GM
- [ ] Hand-port `tests/FillrectGradientGM.kt`.
- [ ] Hand-port `tests/AlphaGradientsGM.kt`.
- [ ] Hand-port `tests/GradientGM.kt`.
- [ ] Hand-port `tests/BitmapRectGM.kt`.

### Vérification Phase 5
- [ ] Tests ≥ 85%.
- [ ] **Pass count cumulé : ~24 GM.**

---

## Phase 6 — Blend modes complets : `AAXfermodesGM`, `XfermodesGM`, `DestColorGM`, `AndroidBlendModesGM`

**But** : 28 modes Porter-Duff + modes avancés.

- [ ] Routine de composition par pixel dans `SkBitmapDevice`.
- [ ] Porter la logique de [kanvas/src/main/kotlin/core/ColorExtensions.kt](kanvas/src/main/kotlin/core/ColorExtensions.kt).
- [ ] Résoudre `undefined.BlendInfo` et `undefined.Coeff`.

### Tests GM
- [ ] Hand-port `tests/AAXfermodesGM.kt`.
- [ ] Hand-port `tests/XfermodesGM.kt`.
- [ ] Hand-port `tests/DestColorGM.kt`.
- [ ] Hand-port `tests/AndroidBlendModesGM.kt`.

### Vérification Phase 6
- [ ] Tests ≥ 88%.
- [ ] **Pass count cumulé : ~30 GM.**

---

## Travaux parallèles (hors numérotation de phases)

Pour réduire le chemin critique pendant que les phases « lourdes » (color-management, paths complets) avancent, on ouvre des slices indépendantes qui ne touchent pas les mêmes fichiers.

### S3 — `drawImage` / `drawImageRect` axis-aligned
- [x] Foundation : `SkImage`, `SkSamplingOptions`, `SkFilterMode`, `SkMipmapMode`, `SkTileMode`. Couleurs additionnelles (`SK_ColorYELLOW`, `CYAN`, `MAGENTA`, `GRAY`, `LTGRAY`, `DKGRAY`).
- [x] `SkCanvas.drawImage(image, x, y, sampling, paint)` + `drawImageRect(image, src, dst, sampling, paint, constraint)` ; `SrcRectConstraint` enum (`kStrict` / `kFast`).
- [x] `SkBitmapDevice.drawImageRect` : mapping inverse `dst → src` per-pixel, sampling **nearest** et **linear** (bilinéaire), clamp aux bords de l'image, modulation alpha par paint, composition via `blend()` existant.
- [x] Hand-port `tests/DrawBitmapRect3.kt` (`3x3bitmaprect`) — **100.00%** à `tolerance=160`.
- [ ] À suivre (follow-ups) : `BitmapRectRounding` (drawImage + scale CTM), `DrawBitmapRect2` (drawImage à origin + drawImageRect en grille), `BitmapPremulGM` (alpha non-opaques).

---

## Explicitement reporté

- [ ] **GPU** (`org.skia.gpu.*`, Ganesh, Graphite). Stripper les hooks GPU de la `GM` base class.
- [ ] **Texte & polices** (`SkFont`, `SkTypeface`, `*TextGM`, `*EmojiGM`). `drawText` → `UnsupportedOperationException`.
- [ ] **Image filters & blurs** (`*BlurGM*`, `ImageFilters*GM`). Graphe d'évaluation séparé.
- [ ] **Codecs** (`EncodeGM`, etc.). `javax.imageio` suffit pour charger les références.
- [ ] **Modules** (`org.skia.modules.*` : Skottie, Paragraph, SVG). Migration parallèle après raster ≥ 90%.

---

## Vérification end-to-end (à chaque phase)

- [ ] `./gradlew :kanvas-skia:compileKotlin` passe sans erreur.
- [ ] `./gradlew :kanvas-skia:test` réussit tous les tests existants + nouveaux du slice.
- [ ] Scores dans `kanvas-skia/test-similarity-scores.properties` montent ou stables (jamais chute > 1%).
- [ ] Images de debug écrites dans `kanvas-skia/build/debug-images/<test-name>.png`.

À chaque slice : commit unique par GM (ou groupe minimal), avec mise à jour atomique de `test-similarity-scores.properties`.

---

## Trajectoire de progression

| Phase | Cumul GM | Surface technique | État |
|-------|----------|-------------------|------|
| 0     | 0        | Bootstrap module + harness | ✅ |
| 1     | 2        | Rect non-AA, paint, color, device | ✅ |
| 2     | 4        | Rect AA (coverage analytique axis-aligned) | ✅ |
| S3    | +1       | `drawImageRect` axis-aligned (parallèle, +`DrawBitmapRect3`) | ✅ |
| 3a    | 5        | SkPath line-only + scanline fill AA + scale CTM | ✅ |
| 3b    | 5        | Path/Builder split + Bézier verbs + arcTo/addArc + flattening | ✅ |
| 3c    | ~8       | Path stroker + GM ports (ArcToGM, ConvexPathsGM, ...) | ⬜ |
| 4     | ~16      | Circle / Oval / RRect via path | ⬜ |
| 5     | ~24      | Gradients linéaire/radial + image shader | ⬜ |
| 6     | ~30      | 28 blend modes | ⬜ |

**Bonus** : [archives/MIGRATION_PLAN_COLORSPACE.md](archives/MIGRATION_PLAN_COLORSPACE.md) Phase 0-5 ✅ — `tolerance=1` au lieu de `tolerance=160` sur tous les GMs Phase 1-3a. Suite du portage colorspace dans [MIGRATION_PLAN_COLORSPACE_PORT.md](MIGRATION_PLAN_COLORSPACE_PORT.md).

> Au-delà : reprendre [SKIA_DM_TESTS_TO_IMPLEMENT.md](SKIA_DM_TESTS_TO_IMPLEMENT.md) Level 2 par catégories (bitmap operations, transformations avancées, effects), en gardant la même mécanique slice-vertical.

---

## Sources de référence (lecture seule, à miner)

- [kanvas/src/main/kotlin/core/](kanvas/src/main/kotlin/core/) — `Canvas`, `Paint`, `Bitmap`, `Path`, `Rect`, `RRect`, `Shader`, `Color`, `ColorExtensions`, `Matrix`, `Arc`, `SkScalar`.
- [kanvas/src/main/kotlin/device/BitmapDevice.kt](kanvas/src/main/kotlin/device/BitmapDevice.kt) — rasterizer rect/path actuel.
- [kanvas/src/main/kotlin/testing/skia/](kanvas/src/main/kotlin/testing/skia/) — 22 GMs hand-written éprouvés.
- [kanvas/src/test/kotlin/skia/TestUtils.kt](kanvas/src/test/kotlin/skia/TestUtils.kt) — harness éprouvé.
- [kanvas/src/generated/tests/org/skia/tests/](kanvas/src/generated/tests/org/skia/tests/) — **chaque fichier porte le code C++ original en Javadoc**, à utiliser comme spec.
- [kanvas/src/test/resources/original-888/](kanvas/src/test/resources/original-888/) — 989 images de référence Skia.
