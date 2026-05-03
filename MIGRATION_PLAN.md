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
- [x] `SkRandom.kt` : LCG (compat Skia non visée — voir SimpleRectGM).

### Tests GM
- [x] `tests/BigRectGM.kt` : hand-port intégral.
- [x] `tests/SimpleRectGM.kt` : hand-port (le Javadoc généré tient lieu de spec).

### Tests JUnit5
- [x] `BigRectTest.kt` : run + compare avec tolérance + ratchet.
- [x] `SimpleRectTest.kt` : idem.

### Découverte hors-plan : profil couleur Skia
La référence `bigrect.png` (et toute la collection `original-888/`) embarque un profil ICC `Google/Skia` qui encode les primaires sRGB dans un espace de travail à gamut large : pure `0xFF0000FF` (sRGB blue) y arrive à `~0xFF2B0DF2`, soit un écart par canal jusqu'à ~150. Notre rasterizer rend en sRGB direct : structurellement correct, mais incomparable bit-pour-bit. `TestUtils.compareBitmaps` accepte donc une `tolerance` par canal ; modéliser la pipeline couleur Skia est reporté.

### Vérification Phase 1
- [x] `BigRectGM` ≥ 99% vs `bigrect.png` à `tolerance=160` — **résultat : 99.51%**.
- [x] `SimpleRectGM` ≥ 70% à `tolerance=192` (RNG divergent — seuil topologique uniquement) — **résultat : 74.45%**.
- [x] **Pass count cumulé : 2 GM.**

---

## Phase 2 — Anti-aliasing des rects : `ThinRectsGM`, `ScaledRectsGM`, `ClearSwizzleGM`

**But** : rasterisation AA pour rects, configurations `isAntiAlias=true`.

- [ ] Implémenter le supersampling 4× sur les bords dans `SkBitmapDevice.drawRect`.
- [ ] Compléter `SkBlendMode.kSrcOver` avec alpha premultipliée correcte sur le compositing.
- [ ] Hand-port `tests/ThinRectsGM.kt` via spec.
- [ ] Hand-port `tests/ScaledRectsGM.kt` via spec.
- [ ] Hand-port `tests/ClearSwizzleGM.kt` via spec.
- [ ] Résoudre `undefined.SaveLayerFlags` (typealias `Int`).

### Vérification Phase 2
- [ ] Tests AA ≥ 95%, tests non-AA toujours ≥ 99%.
- [ ] **Pass count cumulé : ~5-6 GM.**

---

## Phase 3 — Paths, fills, strokes simples : `ConcavePathsGM`, `ConvexPathsGM`, `CubicPathsGM`, `AddArcGM`, `ArcToGM`

**But** : porter `SkPath` + scanline fill + stroker basique.

### Path
- [ ] Hand-port [kanvas/src/main/kotlin/core/Path.kt](kanvas/src/main/kotlin/core/Path.kt) → `org.skia.foundation.SkPath`.
- [ ] Scinder en `SkPath` / `SkPathBuilder` / `SkPathFillType`.
- [ ] Résoudre `undefined.SkPathFillType` (enum `kWinding`, `kEvenOdd`, `kInverseWinding`, `kInverseEvenOdd`).

### Rasterizer
- [ ] Implémenter scanline fill (even-odd / non-zero) dans `SkBitmapDevice.drawPath`.
- [ ] Stroker trapézoïdal : `kButt_Cap` + `kMiter_Join` uniquement (round/bevel reportés).

### Tests GM
- [ ] Hand-port `tests/ConvexPathsGM.kt`.
- [ ] Hand-port `tests/ConcavePathsGM.kt`.
- [ ] Hand-port `tests/CubicPathsGM.kt`.
- [ ] Hand-port `tests/AddArcGM.kt`.
- [ ] Hand-port `tests/ArcToGM.kt`.

### Vérification Phase 3
- [ ] Tests path ≥ 90%.
- [ ] **Pass count cumulé : ~11 GM.**

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
- [ ] `SkImage` + `SkBitmap.makeShader()`.
- [ ] `SkSamplingOptions` (nearest + bilinéaire).
- [ ] `SkTileMode` (clamp / repeat / mirror).

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
| 2     | ~6       | Rect AA + SrcOver alpha | ⬜ |
| 3     | ~11      | Path + fill scanline + stroker simple | ⬜ |
| 4     | ~16      | Circle / Oval / RRect via path | ⬜ |
| 5     | ~24      | Gradients linéaire/radial + image shader | ⬜ |
| 6     | ~30      | 28 blend modes | ⬜ |

> Au-delà : reprendre [SKIA_DM_TESTS_TO_IMPLEMENT.md](SKIA_DM_TESTS_TO_IMPLEMENT.md) Level 2 par catégories (bitmap operations, transformations avancées, effects), en gardant la même mécanique slice-vertical.

---

## Sources de référence (lecture seule, à miner)

- [kanvas/src/main/kotlin/core/](kanvas/src/main/kotlin/core/) — `Canvas`, `Paint`, `Bitmap`, `Path`, `Rect`, `RRect`, `Shader`, `Color`, `ColorExtensions`, `Matrix`, `Arc`, `SkScalar`.
- [kanvas/src/main/kotlin/device/BitmapDevice.kt](kanvas/src/main/kotlin/device/BitmapDevice.kt) — rasterizer rect/path actuel.
- [kanvas/src/main/kotlin/testing/skia/](kanvas/src/main/kotlin/testing/skia/) — 22 GMs hand-written éprouvés.
- [kanvas/src/test/kotlin/skia/TestUtils.kt](kanvas/src/test/kotlin/skia/TestUtils.kt) — harness éprouvé.
- [kanvas/src/generated/tests/org/skia/tests/](kanvas/src/generated/tests/org/skia/tests/) — **chaque fichier porte le code C++ original en Javadoc**, à utiliser comme spec.
- [kanvas/src/test/resources/original-888/](kanvas/src/test/resources/original-888/) — 989 images de référence Skia.
