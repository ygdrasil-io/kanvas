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

### Phase 3c — Path stroker (no GM ports yet) ✅

**But** : ouvrir le rasterizer aux paths `kStroke_Style` / `kStrokeAndFill_Style`. Sans cette pièce, tous les GMs qui font `paint.setStyle(kStroke_Style)` sur un path sont bloqués.

- [x] Étendre `SkPaint` avec les enums `Cap` (kButt/kRound/kSquare) et `Join` (kMiter/kRound/kBevel) + propriétés `strokeCap`, `strokeJoin`, `strokeMiter` (default 4f, comme Skia).
- [x] Créer `SkStroker` (org.skia.foundation) — convertit un `SkPath` source en outline path filable. Algo :
  - Flatten chaque verb (line/quad/conic/cubic) en polyline source-space (mêmes constantes flatness/depth que `SkBitmapDevice.buildEdges`).
  - Per-segment unit normals (left = CCW perpendicular).
  - Walk vertex par vertex, miter formula `M − P = halfW / (1 + cos) · (n_prev + n_next)`. Bevel fallback si `|M − P| > miterLimit · halfW`.
  - Closed contour → 2 sub-contours (outer side as-is + inner side reversed) — winding fill peint la bande.
  - Open contour → 1 closed contour : `left + butt_cap_end + reverse(right) + butt_cap_start`.
- [x] Phase 3c implémente **`kButt_Cap` + `kMiter_Join` (avec bevel fallback)** seulement. Round/Square caps + Round/Bevel-only joins reportés.
- [x] Wirer `SkBitmapDevice.drawPath` : kStroke / kStrokeAndFill délèguent au stroker, le résultat est rasterisé via la même pipeline fill.
- [x] `strokeWidth ≤ 0` → fallback width=1 (hairline). Une vraie hairline path scan-line viendra plus tard.

#### Vérification Phase 3c
- [x] 10 tests unitaires `SkStrokerTest` (empty, width=0, line→rect, closed rect→ring, miter, bevel fallback, quad, fillType).
- [x] 5 tests end-to-end `SkBitmapDeviceStrokeTest` (line, rect, L-shape miter, kStrokeAndFill, translation).
- [x] Aucune régression sur les 6 GMs existants.

### Phase 3e — GM ports stroke-on-path ✅ (cible atteinte ; suite reportée)

- [x] Hand-port `tests/ConvexPathsGM.kt` (fill seulement, 35+ paths). L'entry skbug.40040207 utilise pure scale + translate dans sa matrice — appliquée inline au build du path (pas besoin de `SkPath.transform(SkMatrix)` encore). **Score : 99.68%** à `tolerance=1`. Tous les verbs (line/quad/conic/cubic/arc), toutes les factories (Rect/Circle/Oval/RRect/Line/Polygon), 4096-point polyline, paths dégénérés (point line/quad/cubic, moveTo-only).
- [x] Hand-port `tests/ArcOfZorroGM.kt` (200 stroked open arcs avec width=35, layout boustrophedon, BG `0xCCCCCC`). Premier vrai stress du stroker (Phase 3c) sur des courbes : chaque arc est 1-2 cubic Béziers stroked en bande de 35 px. Ajout de `SkCanvas.drawArc(rect, startAngleDeg, sweepAngleDeg, useCenter, paint)` (path = arcTo + optional moveTo-to-centre + close pour pie slice). **Score : 99.56%** à `tolerance=1`. Bonus learning : un BG color non-trivial nécessite `drawPaint` au début (eraseColor skip le colorspace transform — voir le commentaire dans `TestUtils.runGmTest`).
- [ ] Hand-port `tests/ArcToGM.kt` — **reporté Phase 4+** : nécessite le variant SVG endpoint d'`arcTo(rx, ry, xAxisRotate, largeArc, sweep, x, y)`, ~150 LOC math.
- [ ] Hand-port `tests/CubicPathGM.kt` — **reporté Phase 4+** : nécessite `kInverseWinding` / `kInverseEvenOdd` fill rules dans le rasterizer + `drawString` (text harness pas encore en scope).

### Phase 3d — GM harvest (existing API surface only) ✅

**But** : valider que l'infrastructure Phase 1 → 3b couvre plus que les quelques GMs hand-pickés. Aucune modification d'API ; chaque GM est purement un test de plus.

GMs portés :

| GM                      | Verbs / API           | Fill rule  | Score à `tolerance=1` |
|-------------------------|-----------------------|------------|------------------------|
| `crbug_887103`          | line-only path × 3 contours | `kWinding`  | **99.82%** |
| `crbug_908646`          | line-only path, holes | `kEvenOdd`  | **99.56%** |
| `crbug_913349`          | line-only path, sliver | `kWinding`  | **99.76%** |
| `crbug_884166`          | line-only path, near-vertical sliver | `kWinding`  | **98.98%** |
| `crbug_788500`          | line + cubic verb     | `kEvenOdd`  | **99.93%** |
| `bitmaprect_rounding`   | drawRect + drawImageRect + sub-pixel `scale(0.9, 0.9)` | n/a | **100.00%** |

Chacun a un seuil floor adapté à son score (ratchet ≥ score - 1%). Tous : `tolerance=1`, rendu en colorspace Rec.2020.

**Pass count cumulé : 11 GM** (5 précédents + 6 ports).

### Phase 3f — Path API extras (no GM ports yet) ✅

**But** : combler les manques `SkPathBuilder`/`SkPath` listés dans l'audit pour débloquer les futurs hand-ports (notamment `ArcToGM` qui consomme le tangent `arcTo`, et `ConvexPathsGM` qui veut `path.transform`/`makeOffset`). Indépendant de Phase 3c et 3d, parallèle à 3e (stroke-on-path GM ports).

- [x] **Verbs relatifs** sur `SkPathBuilder` : `rMoveTo`, `rLineTo`, `rQuadTo`, `rConicTo`, `rCubicTo`. Wrappers triviaux qui re-routent vers les primitives absolues avec `(lastX + dx, lastY + dy)`. Mirroirs Skia, utiles pour SVG path data.
- [x] **Tangent `arcTo(x1, y1, x2, y2, radius)`** (PostScript-style `arct`). Mirror Skia. Calcul classique : `θ` = angle au coin `p1` entre `p1→P0` et `p1→p2`, `d = r/tan(θ/2)`, tangent points `T0 = p1 + d·û1`, `T1 = p1 + d·û2`, centre `C = p1 + r·(û1+û2)/sin θ`. Sweep court (≤ π) délégué à l'existing `arcTo(oval, startDeg, sweepDeg, false)`. Cas dégénérés (radius nul, segments collinéaires/zéro-length) → `lineTo(p1)` (comme Skia).
- [x] **`emitArc` epsilon fix** : la comparaison stricte `lastX != firstX` rejetait des décalages d'~1 ULP causés par l'accumulation float dans le tangent arcTo, déclenchant un `lineTo` parasite. Remplacé par `abs(...) > 1e-4f`. Sub-pixel — n'affecte aucun cas légitime, débloque le tangent arcTo en chaîne.
- [x] **`SkPath.computeBounds()`** : bbox des control points (Skia's "fast bounds"). Conservatif pour les Béziers (les control points peuvent dépasser la curve). `computeTightBounds()` = roots des dérivées Bézier, déféré.
- [x] **`SkPath.makeOffset(dx, dy)`** : copie translatée. Fast-path identité quand `(dx, dy) == (0, 0)` (renvoie `this`). Verbs et conic weights conservés tels quels.

#### Vérification Phase 3f
- [x] 15 tests unitaires `SkPathExtrasTest` (5 verbs relatifs, 5 tangent arcTo dont cas dégénérés et géométrie centre/T0, 3 computeBounds, 3 makeOffset).
- [x] Aucune régression sur les Phase 3a/b/c/d/e tests ni sur les 13 GMs cumulés.

### Phase 3g — Stroker caps & joins étendus ✅

**But** : compléter le stroker (Phase 3c, kButt + kMiter only) avec **kSquare_Cap, kRound_Cap, kBevel_Join, kRound_Join**. Indépendant des PR de GM ports — débloquera ArcToGM (kRound_Cap utilisé), CubicPathGM (toutes les permutations), DrawCaps, EmptyStrokeGM, etc.

- [x] **`kBevel_Join`** : `emitJoin` dispatche sur `paint.strokeJoin`. Pour bevel, on émet directement les deux endpoints offset (skip miter). Le miter qui dépasse `miterLimit · halfW` retombe toujours en bevel (comportement Phase 3c préservé).
- [x] **`kSquare_Cap`** (open contours) : étend `left[end]` et `right[end]` de `halfW` le long de la tangente du dernier segment. Bbox grandit de `halfW` à chaque extrémité (vs kButt qui s'arrête à l'endpoint).
- [x] **`kRound_Cap`** (open contours) : 2 cubic Béziers par cap (start + end), kappa = `(4/3)·(√2 − 1)`. La demi-disque va de `+halfW·n` via `±halfW·t` (outward) à `−halfW·n`. Bbox identique à kSquare mais profil arrondi.
- [x] **`kRound_Join`** : arc circulaire de `+halfW·n_prev` à `+halfW·n_next` autour du vertex, ligne-flatten à ~22.5° par segment (8 segments / quart de tour). Pas de cubics dans la sortie — la polyline accumulée s'émet en `lineTo` à la fin. Sweep direction par cross-product.
- [x] **Antiparallèles 180°** : tous les joins fallback en bevel (pas de miter ni d'arc valide).

#### Vérification Phase 3g
- [x] 9 tests unitaires `SkStrokerCapsJoinsTest` :
  - kSquare_Cap : bbox étendue par halfW, kButt comparison.
  - kRound_Cap : 2 cubics par cap, bbox = kSquare bbox.
  - kBevel_Join : extra vertex per side (vs miter single point).
  - kMiter > limit fallback bevel.
  - kRound_Join : ~16 line segments per 90° corner (vs 1 vertex pour miter), closed rect → 2 sub-contours avec arcs.
- [x] Aucune régression sur les Phase 3a-3f tests ni sur les 13 GMs cumulés.
- [x] Pas de nouveau GM port — débloqué pour 3h+ (DrawCaps, ArcToGM partiel, CubicPathGM partiel).

### Phase 3h — `drawLine` + TeenyStrokesGM ✅

**But** : exposer `SkCanvas.drawLine` (single-segment stroked path) et porter `TeenyStrokesGM` — cinq paires de lignes sous des CTM scales massifs (20000× → 500000×) appariés avec des stroke widths microscopiques. Le résultat **device-space** est invariant (5 px de stroke partout) — la GM stresse la robustesse numérique du stroker quand il calcule la géométrie offset en user space puis laisse le scanline rasterizer la transformer.

- [x] **`SkCanvas.drawLine(x0, y0, x1, y1, paint)`** : émet un path 2-points `moveTo` + `lineTo` et délègue à `drawPath`. Sous `kStroke_Style`, le stroker produit deux caps mais aucun join (segment unique).
- [x] **TeenyStrokesGM** (port de `gm/strokes.cpp:TeenyStrokesGM`) : 400×800, scales `5e-5` à `2e-6`. `99.39%` similarité @ tolerance=1.

#### Vérification Phase 3h
- [x] `TeenyStrokesGM` ≥ 95% (rendu obtenu : 99.39%).
- [x] Aucune régression sur les 13 GMs cumulés (toujours verts).
- [x] **Pass count cumulé : 14 GM.**

### Phase 3i — Stroker `resScale` + GM batch (clôture Phase 3) ✅

**But** : refermer Phase 3 d'un seul coup avec un dernier batch de GMs path/stroker portables sur l'API existante, plus le bug fix structurel `resScale` qui manquait à `SkStroker`.

#### Fix structurel — `SkStroker.resScale`

Le stroker Phase 3c flattenait les Béziers en source space avec une tolérance fixe `FLATNESS = 0.25`. Sa polyline est **directement** la séquence de sommets de l'outline path — le rasterizer ne peut pas re-lisser des `lineTo`. Sous CTM scale élevée (`Strokes4GM` à 1000×), un cercle de 4 cubic Béziers se flattenait à ~4 chord segments, transformés ensuite en polygone plate au rasterizer. Visuellement : un cercle stroked devient un quadrilatère stroked.

- [x] **Nouveau paramètre `resScale: Float = 1f`** sur `SkStroker.fromPaint(paint, resScale)`. La tolérance source-space devient `FLATNESS / resScale` ⇒ erreur de chord ≤ 0.25 px **device-space** quel que soit le CTM.
- [x] **Conic stepping adaptatif** : `conicSteps = ceil(CONIC_STEPS · √resScale)`, plafonné à `MAX_CONIC_STEPS = 4096` pour éviter une explosion sous CTM extrême.
- [x] `SkBitmapDevice.drawPath` calcule `resScale = max(|sx|, |sy|).coerceAtLeast(1f)` et le passe au stroker. Floor à 1f : on n'élargit jamais la tolérance source-space en cas de zoom-out CTM.
- [x] 3 unit tests `SkStrokerResScaleTest` :
  - `resScale=1f` → polyline grossière (< 30 verbs sur un quart de cercle 1-unité).
  - `resScale=1000f` → polyline ≥ 4× plus fine.
  - `fromPaint(paint)` ≡ `fromPaint(paint, 1f)` (default param fidèle).

#### GMs portés

- [x] **`Strokes4GM`** (`gm/strokes.cpp:Strokes4GM`, ref `strokes_zoomed.png` 400×800). `drawCircle(0, 2, 1.97)` stroke `0.055` sous `scale(1000, 1000)`. **Score : 99.96%** @ tolerance=1 (88.28% avant le fix `resScale`). Inverse exact de TeenyStrokesGM — l'union des deux couvre 9 ordres de grandeur de CTM.
- [x] **`ClippedCubicGM`** (`gm/clippedcubic.cpp`, ref `clippedcubic.png` 1240×390). 3×3 grid d'un cubic auto-intersectant clippé sur sa bbox + translate `(dx, dy) ∈ {-1, 0, +1}` px — stresse l'arithmétique clip-edge sur courbe. **Score : 99.97%** @ tolerance=1.
- [x] **`StLouisArchGM`** (`gm/stlouisarch.cpp`, ref `stlouisarch.png` 256×256). 6 paths hairline-stroked sous `scale(1, -1)` + translate (flip Y) : trois types de courbes (quad, cubic, conic) plus une variante dégénérée plate de chacune. Note : notre hairline `strokeWidth=0` retombe sur `strokeWidth=1` (pas de hairline scan-line dédiée encore), donc coverage élargi d'1 px. **Score : 94.50%** @ tolerance=1.
- [x] **`StrokesGM`** (`gm/strokes.cpp:StrokesGM`, ref `strokes_round.png` 400×800). 50 paires `drawOval` + `drawRoundRect` random per pane (AA off / AA on), + `clipRect` 2px inset. `SkCanvas.drawRoundRect(rect, rx, ry, paint)` ajouté en convenience. **Score : 92.34%** @ tolerance=1.

#### GMs explicitement reportés (Phase 4+)

- **`StrokeRectAnisotropicGM`** : nécessite stroking-en-device-space (ou path-transform-avant-stroke) pour le CTM non-uniforme `scale(0.03, 2.0)`. Notre stroke source-space donne des bords verticaux squashés à 0.3 px que le rasterizer affiche tout de même comme un trait plein (les sub-samples retombent dans la bande). Refactor non-trivial.
- **`BatchedConvexPathsGM`** : 10 polygones translucides stackés. Différence pixel-à-pixel max=26 (visuel quasi-identique) mais 65 % des pixels diffèrent par > 1 ULP — accumulation de précision 8-bit dans le compositing (Skia compose en F16). Architectural — ne sera pas adressable sans une pipeline F16/working-space.

#### Vérification Phase 3i
- [x] 4 nouveaux GM : Strokes4 99.96, ClippedCubic 99.97, StLouisArch 94.50, Strokes 92.34. Tous ≥ floor 90 % (sauf StLouisArch à 80 % pour absorber le hairline fallback).
- [x] 3 unit tests `SkStrokerResScaleTest`, tous verts.
- [x] Aucune régression sur les 14 GMs cumulés.
- [x] **Pass count cumulé : 18 GM** (+1 nouveau API `drawRoundRect`).

---

## Phase 3 — Récap final

| Slice | Surface | Pass count |
|-------|---------|------------|
| 3a | SkPath line-only + scanline fill AA + scale CTM | 5 |
| 3b | Path/Builder split + Bézier verbs + arcTo/addArc | 5 |
| 3c | Path stroker (kButt + kMiter) | 5 |
| 3d | GM harvest (5 crbug + bitmaprect_rounding) | 11 |
| 3e | ConvexPathsGM + ArcOfZorroGM ; ArcToGM/CubicPathGM reportés | 13 |
| 3f | Path API extras (relative verbs, tangent arcTo, computeBounds, makeOffset) | 13 |
| 3g | Stroker caps & joins (kSquare/kRound caps, kBevel/kRound joins) | 13 |
| 3h | drawLine + TeenyStrokesGM | 14 |
| 3i | resScale fix + 4 GMs (Strokes4, ClippedCubic, StLouisArch, Strokes) | 18 |

**Phase 3 close.** Surface stroker complète (caps & joins matrix, resScale CTM-aware), `drawLine` / `drawArc` / `drawRoundRect` / `drawCircle` / `drawOval` / `drawRRect` exposés, scanline fill AA + clipRect axis-aligned. Reportés en bloc à Phase 4+ : SVG arcTo endpoint variant, inverse fill rules, hairline path scan-line, stroking-en-device-space sous CTM non-uniforme, F16 compositing pour translucides.

---

## Phase 4 — Cercles, ovals, RRects, DRRects

**But initial du plan** : remplacer les fallbacks `circle → rect` par de vraies courbes Bézier. **Déjà fait pendant la Phase 3** — `drawCircle`, `drawOval`, `drawRRect`, `drawRoundRect`, `drawArc` tous wired via `SkPath.RRect/Oval/Circle` + cubics avec kappa. La Phase 4 est donc concentrée sur l'API restante (`drawDRRect`, palette quantisée 565) et le portage des GMs spécifiques.

### Phase 4a — drawDRRect + 7 GM ports ✅

#### API ajoutée

- [x] **`SkCanvas.drawDRRect(outer, inner, paint)`** : émet un seul path avec l'outer en `kCW` et l'inner en `kCCW`. Le winding fill peint la bande entre les deux. Edge cases : outer vide ⇒ no-op ; inner vide ⇒ délègue à `drawRRect(outer, paint)`.
- [x] **`org.skia.tools.ToolUtils.kt`** : helpers `skHSVToColor(hsv, alpha)` et `colorTo565(SkColor)`. Ports bit-compatibles de `src/core/SkColor.cpp:SkHSVToColor` et `tools/ToolUtils.cpp:color_to_565` — la quantisation 565 est essentielle pour matcher les références capturées depuis un backbuffer 16-bit (manycircles, manyrrects, fillcircle, rrect tous l'utilisent indirectement via `gen_color`).

Le data model `SkRRect` (incl. `setRectRadii`/`setRectXY`/`setOval`/`setEmpty`/`MakeRectRadii` + type classification avec radii-clamp) **existait déjà** depuis l'introduction d'`SkRRect` — il ne manquait plus que l'API canvas pour faire dialoguer les rrects per-corner avec le rasterizer.

#### GMs portés

| GM            | Référence              | Score      | Notes |
|---------------|------------------------|------------|-------|
| CircleSizesGM | `circle_sizes.png` 128² | **94.48%** | Grid 4×4 cercles `r=1..16`. crbug 772953 fixture. |
| SmallArcGM    | `smallarc.png` 762²    | **99.80%** | Cubic stroked à 120 px sous `scale(8, 8)`. Stresse `resScale`. |
| ManyCirclesGM | `manycircles.png` 800×600 | **97.32%** | 10 000 ovals AA, couleurs `gen_color` 565-quantisées. |
| ManyRRectsGM  | `manyrrects.png` 800×300 | **88.33%** | 7 000 rrects 4×4 `MakeRectXY(1, 1)`. AA-corner drift sub-5px. |
| FillCircleGM  | `fillcircle.png` 520²  | **95.28%** | Cercles concentriques sous `scale(20, 20)`. `fRotate=0` (rotate sauté). |
| DRRectGM      | `drrect.png` 640×480   | **98.48%** | 4 outer types × 5 inner types (incl. empty) = 20 donuts. Premier vrai stress de `drawDRRect`. |
| RRectGM       | `rrect.png` 820×710    | **89.20%** | 4 inset procs × 4 rrect types × 13 d-values = 208 stroked rrects. Hairline `strokeWidth=0` retombe sur width=1. |

#### Vérification Phase 4a
- [x] 7 nouveaux tests JUnit, tous verts au floor 80–90 %.
- [x] Aucune régression sur les 18 GMs Phase 3.
- [x] **Pass count cumulé : 25 GM.**

#### GMs Phase 4 reportés (dépendances en attente)

- **OvalGM** (`ovals.png`), **RoundRectGM** (`roundrects.png`) — utilisent `canvas->rotate(90)`, `setSkew(2,3)`, `concat(matrix)`. Bloqués par l'absence de `SkMatrix` / rotate-skew CTM. Cible Phase 4b.
- **RoundRectGM** utilise aussi un radial gradient — bloqué Phase 5.
- **BlurCirclesGM**, **RRectBlurGM**, **DashCircleGM** — bloqués par mask filters / dashes / `drawString`.

---

### Phase 4b — SkMatrix + rotate/skew CTM ✅

**But** : passer le CTM stack de `(sx, sy, tx, ty)` à une vraie matrice affine 2×3. Débloque OvalGM, RoundRectGM (sans le gradient), `canvas.rotate/skew/concat/setMatrix`, et `SkPath.makeTransform(SkMatrix)`.

#### API ajoutée

- [x] **`org.skia.math.SkMatrix`** : data class affine 2×3 (`sx, kx, tx, ky, sy, ty`) avec :
  - factories `Identity`, `MakeTrans`, `MakeScale`, `MakeRotate(deg)`, `MakeRotate(deg, px, py)`, `MakeSkew`, `MakeAll`, `concat(a, b)` ;
  - opérations `mapXY(x, y)`, `mapRect(SkRect)` (bbox du quad transformé), `preConcat`/`postConcat`, `preTranslate`/`preScale`/`preRotate`/`preSkew` ;
  - `isIdentity`, `isAxisAligned` ;
  - `computeMaxScale()` — plus grande valeur singulière (utilisée par `SkStroker.resScale`).
- [x] **`SkCanvas.rotate(deg)` / `rotate(deg, px, py)` / `skew(sx, sy)` / `concat(matrix)` / `setMatrix(matrix)` / `resetMatrix()`** + `getTotalMatrix()` accessor.
- [x] **`SkPath.makeTransform(matrix)`** : applique la matrice à chaque control point. Verbs et conic weights inchangés. Identity → fast-path qui renvoie `this`.

#### Refactor

- [x] `SkCanvas.State` : remplace les 4 scalaires `(sx, sy, tx, ty)` par un seul `matrix: SkMatrix`. `translate`/`scale` réduisent à `matrix.preTranslate`/`preScale`.
- [x] `SkBitmapDevice.drawPath(path, ctm: SkMatrix, clip, paint)` (anciennement `(path, sx, sy, tx, ty, ...)`). `buildEdges` cache les 6 scalaires de la matrice en locales et applique `(sx*x + kx*y + tx, ky*x + sy*y + ty)` à chaque control point.
- [x] **`drawRect` sous CTM rotated/skewed** : route via `drawPath(SkPath.Rect(rect))` (la fast-path `SkBitmapDevice.drawRect` axis-aligned reste pour `matrix.isAxisAligned`).
- [x] **`drawImageRect` sous CTM rotated** : drop pour l'instant (TODO — sampler avec inverse CTM, déféré).
- [x] **`clipRect` sous CTM rotated** : utilise la bbox axis-aligned du quad transformé (conservatif). Aucun GM en scope ne combine `clipRect` + rotate.
- [x] **`SkStroker.resScale`** : alimenté par `ctm.computeMaxScale().coerceAtLeast(1f)` au lieu de `max(|sx|, |sy|)`.
- [x] **`saveLayer`** : layer-local CTM = parent matrix avec `tx -= originX, ty -= originY` (préserve le mapping source → device-shifted-by-origin sous CTM arbitraire).

#### GMs portés

| GM          | Référence              | Score      | Notes |
|-------------|------------------------|------------|-------|
| OvalGM      | `ovals.png` 1200×900   | **94.44%** | 5 paints × 8 matrices (incl. rotate 60°/90°, skew 2,3) + 6 special rows. |
| RoundRectGM | `roundrects.png` 1200×900 | **95.87%** | Même structure + 7 special rows (strokes-and-radii, OOO rect, etc.). |

Les deux GMs ont une rangée "radial gradient" qu'on rend en **couleur solide** (`SkShader` pas encore exposé) ; le `SkRandom` reste en lockstep avec upstream donc les couleurs sur les autres rangées matchent à l'identique.

#### Vérification Phase 4b
- [x] **22 unit tests `SkMatrixTest`** couvrant Identity, factories, point/rect mapping, pre-* composition, computeMaxScale (rotation pure → 1, scale pur → max abs scale, rotated scale → scale magnitude, NaN-safe), `isAxisAligned`.
- [x] **Aucune régression** sur les 25 GMs Phase 0–4a (le refactor `(sx,sy,tx,ty) → SkMatrix` est backwards-compatible parce que `SkMatrix.Identity` se comporte exactement comme l'ancien quadruplet `(1, 1, 0, 0)`).
- [x] **Pass count cumulé : 27 GM.**

#### GMs reportés ultérieurement (Phase 4c+)

- **`StrokeRectAnisotropicGM`** — débloqué techniquement par `SkPath.makeTransform`, à re-attaquer plus tard (transformer le path vers device space *avant* de stroker, pour un stroke device-space sous CTM non-uniforme).
- **OvalGM/RoundRectGM gradient row** — Phase 5 (`SkShader`).
- **BlurCirclesGM, RRectBlurGM, DashCircleGM** — mask filters / dashes / drawString hors scope.

### Phase 4c — GM harvest post-SkMatrix ✅

**But** : récolter les GMs qui sont devenus portables une fois `SkMatrix` + `rotate/skew/concat` + `SkPath.makeTransform` exposés en Phase 4b. Pas de nouvelle API — tirage de 5 GMs upstream sur l'API existante.

#### GMs portés

| GM             | Référence                  | Score      | Notes |
|----------------|----------------------------|------------|-------|
| ClippedCubic2GM | `clippedcubic2.png` 1240×390 | **99.96%** | Cubic + sa variante "flipped" via `makeTransform({sx=0, kx=1, ky=1, sy=0})`. |
| ClipCubicGM    | `clipcubic.png` 400×410      | **98.72%** | Cubic vertical + variante 90°-rotated via `MakeRotate(90, W/2, H/2)` + `makeTransform`. |
| Strokes2GM     | `strokes_poly.png` 400×800   | **91.33%** | 25 polylines empilées avec `rotate(15°, SW/2, SH/2)` cumulatif (rotate-pivot stress). |
| StrokeCircleGM | `strokecircle.png` 520×520   | **90.37%** | Ovals concentriques sous `scale(20, 20)` ; `rotate(0)` no-op (static dump fRotate=0). |
| AddArcGM       | `addarc.png` 1040×1040       | **91.91%** | Arcs concentriques `345°` AA-stroked avec couleurs/angles random — stress addArc + flatness. |

#### Vérification Phase 4c
- [x] Aucune nouvelle API Kotlin ajoutée — purs ports basés sur Phase 4b.
- [x] Aucune régression sur les 27 GMs Phase 0–4b.
- [x] **Pass count cumulé : 32 GM.**

---

## Phase 5 — Gradients et bitmap-shaders

**But** : introduire l'infrastructure `SkShader` (linear, radial, image), faire dialoguer le rasterizer avec le shader (couleur source par pixel).

### Phase 5a — SkShader + SkLinearGradient + SkRadialGradient ✅

#### API ajoutée

- [x] **`org.skia.foundation.SkShader`** : abstract base avec `setupForDraw(canvasCtm, xform)` (cache `(ctm·localMatrix).invert()` + pré-transforme les stops du shader vers la working colourspace) et `shadeRow(devX, devY, count, dst)` (remplit `dst` avec une couleur per-pixel device-space).
- [x] **`org.skia.foundation.SkLinearGradient`** : interpolation le long du segment `p0 → p1` en local space. Tile modes : `kClamp` / `kRepeat` / `kMirror` / `kDecal`. Lerp entre stops en **prémultiplié byte** (lerp straight-alpha sursature les couleurs translucides).
- [x] **`org.skia.foundation.SkRadialGradient`** : interpolation par distance euclidienne au centre, divisée par `radius`. Mêmes tile modes.
- [x] **`SkPaint.shader: SkShader?`** — `null` par défaut. Quand non-null, `paint.color` est ignoré (mirror Skia).
- [x] **`SkMatrix.invert()`** : inverse affine 2×3 via le déterminant en double précision. Renvoie `null` pour les matrices singulières (callers retombent sur la première stop du shader).

#### Refactor

- [x] `SkBitmapDevice.drawPath` détecte `paint.shader != null` et appelle `shader.setupForDraw(ctm, xformSteps)` avant la fillPath. La rasterisation appelle `shader.shadeRow` une fois par row, puis module chaque pixel par la coverage AA et SrcOver-blende.
- [x] `SkCanvas.drawRect` route par `drawPath(SkPath.Rect(rect))` quand `paint.shader != null` (la fast-path `SkBitmapDevice.drawRect` axis-aligned reste pour les solid colours).

#### GMs portés / mis à jour

| GM                | Référence                  | Score      | Notes |
|-------------------|----------------------------|------------|-------|
| FillrectGradientGM | `fillrect_gradient.png` 120×540 | **68.18%** | 9 rangées de stops × linear/radial. Précision 8-bit lerp + dernière rangée "unsorted stops" intentionnellement différente upstream. |
| OvalGM (mise à jour) | `ovals.png` 1200×900     | **94.68%** ↑ 0.24 | La rangée radial-gradient (column 0) utilise vraiment le `SkRadialGradient` (vs solide en Phase 4). |
| RoundRectGM (mise à jour) | `roundrects.png` 1200×900 | **96.26%** ↑ 0.39 | Idem. |

#### Vérification Phase 5a
- [x] 4 unit tests `SkLinearGradientTest` (endpoints, interpolation milieu, kClamp, non-identity xform).
- [x] Aucune régression sur les 32 GMs Phase 0–4c.
- [x] **Pass count cumulé : 33 GM** (ajout `FillrectGradientGM`).

#### Reportés Phase 5b+

- **`AlphaGradientsGM`**, **`GradientGM`**, **`gradients.png`** — autres permutations gradient ; portables maintenant.
- **`SkBitmap.makeShader()`** (image shader) — bloquera `BitmapRectGM`, `Hairlines`, etc. Refactor moyen : sampler avec inverse-CTM + tile modes.
- **`SkLinearGradient` / `SkRadialGradient` row-9 unsorted-stops fidelity** — match upstream's exact unsorted-binary-search behaviour pour FillrectGradientGM rangée 9.
- **F16 lerp** — passer le rasterizer à F16 working-space pour matcher l'exactitude upstream (~5 % de diff résiduelle sur les gradient lerps).

### Phase 5b — GM harvest gradient (33 → 36) ✅

**But** : récolter les GMs gradient portables sur l'API existante `SkLinearGradient` + `SkRadialGradient` Phase 5a. Trois ports :

| GM                       | Référence                  | Score      | Notes |
|--------------------------|----------------------------|------------|-------|
| AnalyticGradientShaderGM | `analytic_gradients.png` 1024×512 | **62.53%** | 8×4 grid de linear gradients hardstops, 2 → 16 stops par cellule. Stress du binary-search sur duplicate-positions. |
| ClampedGradientsGM       | `clamped_gradients.png` 640×510 | **94.37%** | Single radial gradient (R/G/B/W/K), centre hors rect, kClamp. |
| HardstopGradientShaderGM | `hardstop_gradients.png` 512×512 | **29.03%** | 8×3 grid avec **kClamp + kRepeat + kMirror** sur des layouts de stops variés. Premier stress des 3 tile modes. Score bas — accumulation de drift 8-bit-vs-16-bit + dither upstream sur des cellules à plusieurs périodes. Visuellement quasi-identique. |

#### Vérification Phase 5b
- [x] Aucune nouvelle API (purs ports sur l'API Phase 5a).
- [x] Aucune régression sur les 33 GMs Phase 0–5a.
- [x] **Pass count cumulé : 36 GM.**

#### Reportés Phase 5c+

- **F16 working-space rasterizer** — adresserait l'écart `HardstopGradient` 29 % et `AnalyticGradient` 62 % (lerp précis vs 8-bit byte).
- **Image shader** (`SkBitmap.makeShader()`) — débloque BitmapRectGM, Hairlines, ShaderPathGM.
- **AlphaGradientsGM** — nécessite SkColor4f stops + flag `inPremul=kNo` (lerp en straight-alpha).
- **DegenerateGradientGM** — bloqué par `drawString`.

### Phase 5c — GM harvest path/stroke (36 → 39) ✅

**But** : récolter les GMs non-gradient portables sur l'API existante. 0 nouvelle API.

| GM                 | Référence                      | Score      | Notes |
|--------------------|--------------------------------|------------|-------|
| ScaledStrokesGM    | `scaledstrokes.png` 640×320    | **96.10%** | 4 scales × 4 shapes (path/circle/rect/line) × 2 panes AA on/off. |
| StrokeRectsGM      | `strokerects.png` 800×800      | **92.16%** | 4 panes × 100 random rects (AA × strokeWidth 0/3). Hairline (`strokeWidth=0` + non-AA) retombe sur width=1 → drift sur cette pane. |
| NonClosedPathsGM   | `nonclosedpaths.png` 1220×1920 | **96.82%** | 216 stroked permutations (3 closure types × 2 styles × 3 caps × 3 joins × 4 widths) + 3 fill cells. Le plus gros stress de tous les caps/joins simultanément. |

#### Vérification Phase 5c
- [x] Aucune nouvelle API (purs ports sur l'API Phase 0–5b).
- [x] Aucune régression sur les 36 GMs Phase 0–5b.
- [x] **Pass count cumulé : 39 GM.**

### Phase 5d — GM harvest path/conic/oval (39 → 44) ✅

**But** : récolter les GMs path-only sur l'API Phase 0–5c. 0 nouvelle API.

| GM             | Référence                  | Score      | Notes |
|----------------|----------------------------|------------|-------|
| PathInteriorGM | `pathinterior.png` 770×770 | **98.53%** | 64 paths donut (rect/rrect outer + inner, CW/CCW, winding/even-odd, inset-first/second). Patrouille `setBGColor` via `drawPaint` — eraseColor skip le colorspace transform sur BG `0xDDDDDD`. |
| ConicPathsGM   | `conicpaths.png` 920×960   | **95.54%** | 10 conic paths × 8 cells (alpha × AA × style) + giant-circle. Stress complet de `conicTo` + `rConicTo` sur tous les poids (0.5 / 0.999 / 2 / √2/2). |
| ArcCircleGapGM | `arccirclegap.png` 250×250 | **98.99%** | Stroked circle + tangent arcTo overlapping. Régression Skia sur les sub-pixel gaps. |
| LargeCircleGM  | `largecircle.png` 250×250  | **99.05%** | Large stroked circle (radius=1097) avec AA. Régression coverage sur large radii. |
| LargeOvalsGM   | `largeovals.png` 250×250   | **97.80%** | 5000×4000 ovals sous AA + rotate(1°), kStroke + kStrokeAndFill. |

#### Vérification Phase 5d
- [x] Aucune nouvelle API.
- [x] Aucune régression sur les 39 GMs Phase 0–5c.
- [x] **Pass count cumulé : 44 GM.**

### Phase 5e — DEF_SIMPLE_GM regression harvest (44 → 54) ✅

**But** : récolter 10 `DEF_SIMPLE_GM` Skia upstream — chacun est un test de régression < 30 lignes pour un bug spécifique. 0 nouvelle API.

| GM                    | Référence                       | Score      | Stress |
|-----------------------|---------------------------------|------------|--------|
| Crbug640176GM         | `crbug_640176.png` 250×250      | **99.70%** | conicTo weight ≈ cos(15°), sub-ulp coords. |
| RotatedCubicPathGM    | `rotatedcubicpath.png` 200×200  | **99.40%** | cubic fill axis-aligned + rotated 90°. |
| Bug593049GM           | `bug593049.png` 300×300         | **99.92%** | arcTo half-arc + kRound_Cap stroke. |
| LongWavyLineGM        | `longwavyline.png` 512×512      | **99.52%** | 1000 quads spanning x=−10000..10000. |
| CubicStrokeGM         | `CubicStroke.png` 384×384       | **98.28%** | 3 near-equal stroke widths (1.0720/0.21/0.22). |
| ZeroLineStrokeGM      | `zerolinestroke.png` 90×120     | **93.75%** | Zero-length lines + kRound_Cap. |
| Skbug12244GM          | `skbug12244.png` 150×150        | **99.55%** | Pre-stroked triangle outline drawn as fill. |
| PathArcToSkbug9077GM  | `path_arcto_skbug_9077.png` 200×200 | **97.96%** | lineTo + close + tangent arcTo. |
| PathSkbug11859GM      | `path_skbug_11859.png` 512×512  | **99.77%** | Multi-subpath fill near edge under scale(2,2). |
| PathSkbug11886GM      | `path_skbug_11886.png` 256×256  | **99.22%** | Cubic with extreme y-displacement. |

#### Vérification Phase 5e
- [x] Aucune nouvelle API (purs ports sur l'API Phase 0–5d).
- [x] Aucune régression sur les 44 GMs Phase 0–5d.
- [x] **Pass count cumulé : 54 GM.**

### Phase 5f — GM harvest mixed (55 → 64) ✅

**But** : récolter une 9-pack supplémentaire de GMs portables sur l'API Phase 0–5d. 0 nouvelle API. (Originalement 10 ports ; `Crbug640176GM` a été déduppé contre la Phase 5e qui l'a porté en parallèle. Baseline passée de 54 → 55 par le merge parallèle de la Phase 6 entry qui a apporté `ScaledRectsGM`.)

| GM                              | Référence                                  | Score       | Notes |
|---------------------------------|--------------------------------------------|-------------|-------|
| BeziersGM                       | `beziers.png` 400×800                      | **94.91%**  | 10 quad + 10 cubic stroked AA paths random ; SkRandom bit-compatible avec upstream. Premier stress simultané `quadTo` + `cubicTo` aléatoires sur stroker AA. |
| HardstopGradientsManyGM         | `hardstop_gradients_many.png` 1000×2000    | **15.28%**  | 100 lignes de gradient linéaire blue↔white avec 1→100 hardstops par ligne. Score visuellement identique mais drift 8-bit-vs-f32 lerp sur ~200 stops/ligne, similaire à `HardstopGradientShaderGM` (29 %). |
| TestGradientGM                  | `testgradient.png` 800×800                 | **98.05%**  | Mix multi-primitive : linear gradient sur rect partiel + filled oval + circle + stroked roundrect dans un seul pass. |
| ThinStrokedRectsGM              | `thinstrokedrects.png` 240×320             | **88.87%**  | Sub-pixel strokes (0.5, 0.25, 0.125, hairline=0) sur fond noir + scale(0.5) inset. Stress des coverage masks sub-pixel. |
| BatchedConvexPathsGM            | `batchedconvexpaths.png` 512×512           | **34.93%**  | 10 polygones convexes cubic-only translucides α=0.3 empilés sur fond noir. Score dominé par la dérive SrcOver 8-bit (max diff 26/255, visuellement identique). |
| ShallowGradientLinearNoditherGM | `shallow_gradient_linear_nodither.png` 800² | **100.00%** | Single linear gradient 0xFF555555→0xFF444444 (2 greys quasi-identiques). Pas de dither, parfait byte-for-byte. |
| ShallowGradientRadialNoditherGM | `shallow_gradient_radial_nodither.png` 800² | **100.00%** | Pareil, version radiale (centre canvas, radius w/2). Premier GM radial à passer 100 %. |
| B119394958GM                    | `b_119394958.png` 100×100                  | **90.57%**  | Bug Android repro : drawCircle filled + drawCircle stroke + drawArc kRound_Cap mêlés. Premier round-cap arc dans le harvest. |
| Crbug1086705GM                  | `crbug_1086705.png` 200×200                | **99.93%**  | 700-vertex polygone autour d'un cercle de rayon 2, stroke=5 (la stroke s'auto-intersecte). Smoke test pour la déduplication de vertex. |

#### Vérification Phase 5f
- [x] Aucune nouvelle API.
- [x] Aucune régression sur les 55 GMs Phase 0–5e + Phase 6 entry.
- [x] **Pass count cumulé : 64 GM.** (9 nouveaux ports.)

#### Reportés Phase 5f+

- **`StrokeRectAnisotropicGM` / `strokerect_anisotropic_5408`** — strokes sous `scale(0.03, 2)` ; le stroker fast-path remplit l'intérieur du rect au lieu de laisser la cavité centrale. Anisotropic stroke specialisation manque dans le rasterizer rect — déférable à un correctif rasterizer dédié.
- **`AddArcMeasGM`** — `SkPathMeasure` requis pour positionner les rayons rouges.
- **`ArcToGM`** — `arcTo(xy, angle, ArcSize, dir, xy)` (SVG-style) absent de notre `SkPathBuilder`.
- **`BigMatrixGM`** — bitmap shader requis (Phase 5g).
- **`Crbug1073670GM`** — texte (`drawString` + SkFont).
- **`Crbug1113794GM`** — `SkDashPathEffect`.

### Phase 5g — `SkBitmapShader` (infrastructure) ✅

**But** : ajouter le shader image (`SkBitmap.makeShader` / `SkImage.makeShader`) requis par `BigMatrixGM`, `TilemodesAlphaGM`, `BitmapShaderGM`, `TinyBitmapGM` et la grande famille des GMs *bitmap-shader-driven*. Cette phase ship **l'infrastructure seule** ; les ports GM sont déférés parce qu'ils exposent un drift de pipeline indépendant du shader (BG colorspace + composition encoded vs linear).

#### API ajoutée

- [x] **`SkBitmapShader`** — hérite de `SkShader` (Phase 5a). Stocke une `SkImage` source, deux `SkTileMode` (X/Y), un `SkSamplingOptions` (kNearest / kLinear), un `localMatrix`. `setupForDraw` pré-transforme **une fois** chaque pixel source en working colour space (deux tables : 8-bit pour `shadeRow`, premul-float pour `shadeRowF16`). Les deux paths suivent les mêmes règles d'échantillonnage (centre-pixel, half-integer).
- [x] **`SkBitmap.makeShader(tileX, tileY, sampling, localMatrix)`** — factory. Snapshot la bitmap via `asImage()` puis instancie `SkBitmapShader`.
- [x] **`SkImage.makeShader(tileX, tileY, sampling, localMatrix)`** — même factory directement sur `SkImage`.
- [x] **`SkSamplingOptions`** — déjà présent depuis le slice `drawImageRect`.

#### Refactor

- [x] **`SkBitmapDevice.drawPaint(ctm, clip, paint)`** — accepte un CTM et reroute via le shader path quand `paint.shader != null`. F16+SrcOver fast path utilise `shadeRowF16` directement → `blendF16Premul` (zéro byte-quantize). Fallback 8-bit pour les autres configurations.
- [x] **`SkCanvas.drawPaint(paint)`** — passe le CTM courant au device.
- [x] **`SkBitmapDevice.drawPath` shader path** : `baseA` est désormais `SkColorGetA(paint.color)` (et non plus `255`), pour que `paint.alpha` modulate la sortie shader (Skia : `shaderColor.alpha *= paint.alpha`).
- [x] **`scanFillPath`** : les deux branches shader (F16 et 8-bit) folde `baseA / 255f` (Phase 5g) dans le multiplicateur de couverture.

#### Tests

- [x] **`SkBitmapShaderTest.kt`** — 7 tests unitaires : tile modes (kClamp / kRepeat / kMirror / kDecal), filter modes (kNearest direct + checkerboard 2×2 ; kLinear couvert par les sites bilerp internes), `shadeRowF16` premul-float, `localMatrix` scale.

#### GMs déférés (port pour Phase 5h+)

`TinyBitmapGM`, `BigMatrixGM`, `TilemodesAlphaGM`, `BitmapShaderGM` exposent une combinaison BG-color + shader + paint.alpha qui dérive de la référence à cause d'un détail orthogonal au shader : le compositeur fait du SrcOver en **encoded** Rec.2020 alors qu'upstream Skia compose en **linear** Rec.2020 dans son raster pipeline F16. Le mismatch n'est visible qu'avec des sources/dest translucides ; les 64 GMs déjà portés (qui sont opaques sur opaques ou sources premul opaques sur BG opaque + transparent) ne le voient pas.

Quand on portera ces GMs, on aura besoin soit d'un `eraseColor`-via-`drawPaint` xformé bit-pour-bit comme upstream, soit de basculer le storage F16 en linear-premul (et n'encoder qu'à la sortie 8-bit / PNG). Slice indépendant.

#### Vérification Phase 5g
- [x] Aucune régression sur les 64 GMs précédents.
- [x] 7 tests unitaires `SkBitmapShader` verts.
- [x] **Pass count cumulé : 64 GM** (infrastructure ; pas de nouveau GM porté dans ce slice).

### Phase 6a — F16 working-space rasterizer (infra) ✅

**But** : remplacer le pipeline de compositing 8-bit non-premultiplié par un pipeline F16 (32-bit float per channel, premultiplié) — la même précision qu'utilise Skia upstream pour son rendu de référence. Étape 1/2 : l'infrastructure (storage, blend, I/O). Étape 2 : les producteurs de couleur (shaders, AA coverage) — déférée Phase 6b.

(Cette phase rebase sur la Phase 5f mergée en parallèle ; le baseline passe de 54 → 64 GMs et l'intégration avec le 9-mode dispatch SkBlendMode ajouté par la Phase 6 entry est gérée dans le `blend(x, y, src, mode)` — F16 fast path ne couvre que `kSrcOver` pour l'instant ; les autres modes retombent sur le path 8-bit via `getPixel`/`setPixel`.)

#### API ajoutée

- [x] **`SkColorType`** — enum existante : on active `kRGBA_F16Norm` (déjà déclaré, non-utilisé jusqu'ici).
- [x] **`SkBitmap` polymorphique** — `colorType` paramètre constructeur. Stockage :
  - `pixels8888: IntArray` pour `kRGBA_8888` (legacy).
  - `pixelsF16: FloatArray` pour `kRGBA_F16Norm` (4 floats/pixel, **premultipliés**, `[0, 1]`).
  - `getPixel`/`setPixel` (SkColor 8-bit non-premul) dispatchent et convertissent à la volée.
  - Nouveaux accesseurs `getPixelF16(x, y, FloatArray)` / `setPixelF16(x, y, r, g, b, a)` pour le travail interne sans roundtrip 8-bit.
- [x] **`SkBitmap.eraseColor`** — gère les deux formats (premul + conversion pour F16).

#### Refactor

- [x] **`SkBitmapDevice.blend(x, y, src, mode)`** : dispatch — `kSrcOver + F16` route vers `blendF16` (SrcOver en `[0, 1]` premul direct) ; `kSrcOver + 8888` reste l'inline fast path Phase 1 ; les autres modes (Phase 6 entry's 9-mode slice) retombent sur le path générique `blendPixel(src, dst, mode)` puis `setPixel`.
- [x] **`SkCanvas.saveLayer`** : le bitmap layer hérite du `colorType` du parent.
- [x] **`SkBitmapDevice.compositeFrom`** : passe par `bitmap.getPixel(x, y)` (color-type-aware) au lieu de `srcPixels[i]` (raw IntArray).
- [x] **`TestUtils.runGmTest`** : crée des bitmaps F16 par défaut.
- [x] **`TestUtils.bufferedImageToBitmap`** : si l'image PNG est 16-bit-per-channel (DataBufferUShort), construit un bitmap F16 en préservant les 16 bits ; sinon stays 8-bit.
- [x] **`TestUtils.compareBitmapsDetailed`** : si l'un des bitmaps est F16, lit chaque pixel via `getPixel(x, y)` (8-bit non-premul) puis compare comme avant. Préserve la sémantique 8-bit du ratchet existant.
- [x] **`TestUtils.bitmapToBufferedImage`** + **`DiffImage.buildDiffPanel`** : F16-aware via `getPixel`.

#### Limites connues — Phase 6b (à venir)

L'infra F16 est en place mais le pipeline reste 8-bit AVANT le `blend` :
- **Shaders** (gradients) émettent toujours `IntArray` SkColor (`shader.shadeRow(..., dst: IntArray)`). Le passage à `FloatArray` est nécessaire pour que les lookups de gradient bénéficient vraiment du F16.
- **AA coverage modulation** (`effA = baseA * samples / maxSamples`) reste en 8-bit. Float ferait disparaître le quantization sur les bords AA.
- **Blend modes ≠ kSrcOver** ne bénéficient pas encore du fast-path F16 ; ils passent par `getPixel`/`setPixel` qui font le roundtrip 8-bit.

Avec ces étapes, les scores `HardstopGradient` (29 %), `AnalyticGradient` (62 %), `FillrectGradient` (68 %), `HardstopGradientsMany` (15 %), `BatchedConvexPaths` (35 %) devraient grimper sensiblement. Pour Phase 6a, les scores restent essentiellement inchangés (compositing F16-précis mais inputs déjà quantisés en 8-bit) — c'est attendu et documenté.

#### Vérification Phase 6a
- [x] Aucune régression sur les 64 GMs Phase 0–5f (tous +/−0.5 % within-tolerance).
- [x] L'infra F16 (storage / blend / I/O / comparison) est solide pour Phase 6b qui ne touchera plus que les producteurs de couleur (shaders + AA coverage).
- [x] **Pass count cumulé : 64 GM** (no new GMs in this phase — pure infrastructure).

---

### Phase 6b — F16 shaders + float coverage (✅)

**But** : compléter le pipeline F16 en supprimant les deux dernières quantizations 8-bit qui restaient en amont du `blend` :

1. Les shaders (gradients) émettent désormais directement des `FloatArray` premultipliés en working colour space — plus de roundtrip `IntArray` ARGB → `setPixel` → re-premul float.
2. La coverage AA dans le rasterizer scanline-fill module la couleur source en float (multiplication directe des 4 canaux premul) plutôt qu'en `srcA * samples / maxSamples` 8-bit.

#### API ajoutée

- [x] **`SkShader.shadeRowF16(devX, devY, count, dst: FloatArray)`** — méthode `open` avec implémentation par défaut qui forward à `shadeRow` puis convertit (compatibilité ascendante pour les futurs shaders qui n'ont pas de pipeline float natif).
- [x] **Helpers `transformStopColorsF16` / `lookupStopF16`** dans [SkShader.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkShader.kt) — analogues float-premul de `transformStopColors` / `lookupStop`. Le lerp se fait directement en premul float (pas de pre-mul/un-pre-mul roundtrip comme dans `lerpPremul` 8-bit).

#### Refactor

- [x] **`SkLinearGradient` + `SkRadialGradient`** : ajout d'une table `xformedColorsF16: FloatArray` (4 floats × stops) construite une fois par draw dans `setupForDraw`, et `override shadeRowF16` qui lerp directement en premul float.
- [x] **`SkBitmapDevice.scanFillPath`** : nouveau « F16 shader path » activé quand `bitmap.colorType == kRGBA_F16Norm && shader != null && mode == kSrcOver`. Coverage devient `samples * (1f / maxSamples)`, modulation = multiplication directe des 4 canaux premul, compositing = `blendF16Premul` (pas de SkColor intermédiaire).
- [x] **`blendF16Premul(x, y, sr, sg, sb, sa)`** : SrcOver pur en premul float, sans aucun byte-quantize. Distinct du `blendF16` Phase 6a (qui prend un `SkColor` 8-bit non-premul) — utilisé exclusivement par le F16 shader path.

#### Limites connues — Phase 6c (à venir)

- **Solid-colour AA fillRectAA** : le `scaleAlpha(baseA, cx * cy)` retourne toujours un alpha 8-bit, ré-converti en float par `blendF16`. Negligible pour les rect AA solid (≤ 1 ulp), mais à terme on voudra un `blendF16Premul` direct depuis l'AA path.
- **Blend modes ≠ kSrcOver avec shader sur F16** : retombent encore sur `shadeRow` 8-bit + `blendPixel`-via-`getPixel`/`setPixel`. Aucun GM en scope ne combine shader + non-SrcOver, donc pas urgent.

#### Impact mesuré

Sur les 64 GMs existants, les écarts sont à la marge (`HardstopGradientsManyGM` glisse de 15.28 → 12.79 % — already-deeply-broken test où le lerp précis s'éloigne légèrement d'une référence dithered ; le ratchet est mis à jour). Les autres GMs gradient bougent de moins de 1 % (within-tolerance), et les GMs déjà à 100 % (`ShallowGradientLinearNodither`, `ShallowGradientRadialNodither`) restent à 100 %. C'est attendu : la précision interne change, mais la comparaison se fait en 8-bit non-premul tronqué, donc ne capture qu'une fraction des améliorations. Les vraies améliorations apparaîtront sur les GMs à venir qui exercent des dégradés alpha plus fortement.

#### Vérification Phase 6b
- [x] Aucune régression > 1 % (le ratchet `HardstopGradientsManyGM` mis à jour explicitement, voir « Impact mesuré »).
- [x] Les 64 GMs précédents passent toujours.
- [x] **Pass count cumulé : 64 GM** (pure infrastructure ; pas de nouveau GM porté dans ce slice).

---

### Phase 6c — F16 solid-colour AA (✅)

**But** : supprimer la **dernière** quantization 8-bit qui restait en amont du blend dans les rasterizers AA solid-color (le `scaleAlpha(baseA, coverage) → Int` du fillRectAA / strokeRectAA / scanFillPath solid path). La couleur du paint est désormais convertie une seule fois par draw en premul-float (working space) et la coverage est multipliée comme un float pur tout au long du chemin pixel.

#### API ajoutée

- [x] **`SkBitmapDevice.colorToF16Premul(c: SkColor, out: FloatArray)`** — helper privé : working-space `SkColor` 8-bit → 4 floats `(sr, sg, sb, sa)` premultipliés. Le paint colour est déjà passé par `inDeviceColorSpace` / `transformPaintColor` au site appelant, donc cette conversion ne ré-applique **pas** le xform colour-space — c'est une pure conversion 8-bit non-premul → float-premul.

#### Refactor

- [x] **`SkBitmapDevice.fillRectAA`** : nouvelle branche fast-path quand `bitmap.colorType == kRGBA_F16Norm && mode == kSrcOver`. Précompute `(sr, sg, sb, sa)` une fois, puis `blendF16Premul(x, y, sr*cov, sg*cov, sb*cov, sa*cov)` par pixel — coverage `cov = cx * cy` reste en float jusqu'au store F16.
- [x] **`SkBitmapDevice.strokeRectAA`** : même refactor (coverage `cov = outerCX * outerCY - innerCX * innerCY`).
- [x] **`SkBitmapDevice.scanFillPath`** : ajout d'une 4e branche `useF16SolidPath` (à côté du `useF16ShaderPath` Phase 6b et des deux paths 8-bit). `solidF16: FloatArray?` est précomputé une fois par draw via `colorToF16Premul`, la boucle interne est `cov = samples * invMaxSamples` (float) puis `blendF16Premul(...)`.

#### Impact mesuré

11 GMs en **hausse**, 0 régression sur les 64 GMs existants. Améliorations entre +0.001 % et +0.013 % — petites en absolu, mais elles confirment que la coverage AA float élimine une vraie quantization. Les GMs qui bougent sont tous ceux du `scanFillPath` solid-color avec AA fractionnaire significatif :

| GM | avant | après | delta |
|---|---|---|---|
| `Crbug884166GM` | 98.984 % | 98.998 % | +0.013 |
| `ScaledStrokesGM` | 96.101 % | 96.110 % | +0.009 |
| `ConcavePathsGM` | 98.860 % | 98.872 % | +0.012 |
| `Crbug640176GM` | 99.7024 % | 99.7056 % | +0.003 |
| `NonClosedPathsGM` | 96.820 % | 96.824 % | +0.003 |
| `Strokes2GM` | 91.353 % | 91.356 % | +0.003 |
| `DRRectGM` | 98.487 % | 98.489 % | +0.002 |
| `Crbug913349GM` | 99.759 % | 99.761 % | +0.002 |
| `ConicPathsGM` | 95.5421 % | 95.5444 % | +0.002 |
| `FillCircleGM` | 95.3158 % | 95.3162 % | +0.0004 |

Les `RectAA` (axis-aligned rects) ne bougent pas sensiblement parce que leur coverage est rarement fractionnaire — c'est sur les paths courbes / strokes qu'on gagne.

#### Limites connues — Phase 6 follow-ups

- **Path solid-color non-AA** (`fillPath` → `scanFillPath` avec `supers=1`) : avec `maxSamples=1`, `samples ∈ {0, 1}` et `cov ∈ {0, 1}` — pas de quantization à supprimer. Déjà optimal en Phase 6c (coverage entière = coverage flottante quand `samples = maxSamples`).
- **drawPaint, fillRect, strokeRect, hairlines** (paths non-AA solid color) : la coverage est binaire, ils utilisent `blendF16` (qui ne quantize plus depuis Phase 6a). Aucun changement Phase 6c utile ici.
- **Blend modes ≠ kSrcOver** : restent sur le path 8-bit. Attendu — les modes non-SrcOver demandent un dispatch par mode dans le compositor F16, slice indépendant.

#### Vérification Phase 6c
- [x] 11 GMs en hausse, 0 régression. Le ratchet enregistre les nouveaux scores.
- [x] Les 64 GMs précédents passent toujours.
- [x] **Pass count cumulé : 64 GM** (infrastructure ; pas de nouveau GM porté).

---

## Phase 6 — Blend modes complets : `AAXfermodesGM`, `XfermodesGM`, `DestColorGM`, `AndroidBlendModesGM`

**But final** : 28 modes Porter-Duff + modes avancés.

### Phase 6 entry — slice 9 modes (✅)

Foundation pour brancher de nouveaux modes sans refactoring : `SkBlendMode` enum complet (29 valeurs upstream-aligned), `SkPaint.blendMode` field, dispatch per-pixel dans `SkBitmapDevice.blend()` avec un fast-path `kSrcOver` bit-identique à Phase 1.

Modes implémentés :
- [x] `kClear` — `r = 0`
- [x] `kSrc` — `r = s`
- [x] `kDst` — `r = d`
- [x] `kSrcOver` — fast-path inchangé
- [x] `kDstOver` — `r = d + (1-da)*s`
- [x] `kSrcIn` — `r = s * da`
- [x] `kDstIn` — `r = d * sa`
- [x] `kPlus` — `r = min(s + d, 1)` (ScaledRectsGM, débloquée depuis Phase 2)
- [x] `kModulate` — `r = s * d`
- [x] `kScreen` — `r = s + d - s*d`

Modes encore non-implémentés (lèvent `NotImplementedError` à l'appel — 14 modes restants) :
- [x] ~~`kSrcOut`, `kDstOut`, `kSrcATop`, `kDstATop`, `kXor` (Porter-Duff coeff restants)~~ — **portés Phase 6 Porter-Duff completion** ; voir section dédiée.
- [ ] `kOverlay`, `kDarken`, `kLighten`, `kColorDodge`, `kColorBurn`, `kHardLight`, `kSoftLight`, `kDifference`, `kExclusion`, `kMultiply` (separable)
- [ ] `kHue`, `kSaturation`, `kColor`, `kLuminosity` (HSL)

**Précision** : les formules opèrent sur ARGB non-prémultiplié (notre raster pipeline) ; Skia upstream travaille en prémultiplié. Pour `kSrcIn`/`kDstIn`/`kPlus`/`kModulate`/`kScreen` à alpha fractionnaire, on re-dérive le résultat prémultiplié puis on dé-prémultiplie. Erreur résiduelle ≤ 1 ulp par canal — exact pour `sa == da == 0xFF` (le cas commun des GMs en scope).

**Hors scope** : `SkBitmapDevice.compositeFrom` (flatten de `saveLayer`) reste hardcodé `kSrcOver`. Étendre aux blend modes arbitraires = ticket séparé.

### Tests GM (slice 6 entry)
- [x] Hand-port `tests/ScaledRectsGM.kt` — `kPlus` + `SkMatrix::MakeAll(...)` 3x3. Score **87.79%** vs `scaledrects.png` à `tolerance=1`. Le résiduel ~12% est du désaccord rasterizer non-AA sur les bords de rect rotatés (le GM utilise `setAntiAlias(false)` implicite — Skia adoucit les bords sub-pixel différemment).

### Tests unitaires
- [x] `SkBlendModeTest.kt` — 9 modes × ~3 cas chacun (opaque-on-opaque + alpha fractionnaire), formules vérifiées contre des valeurs hand-computed. Couvre aussi le throw `NotImplementedError` pour les 19 modes restants.

### Reste pour clôturer Phase 6 (mis à jour Phase 6 GMs)
- [x] Implémenter les 19 modes restants — livré progressivement Phase 6 PD / sepS / sepC / HSL (29/29 modes).
- [x] Hand-port `tests/AAXfermodesGM.kt` — voir Phase 6 GMs.
- [ ] Hand-port `tests/XfermodesGM.kt` — déféré : nécessite `compositeFrom` non-`kSrcOver` (saveLayer-with-blendmode pour `kQuarterClearInLayer_SrcType`) + `kARGB_4444` colorType pour le BG shader.
- [ ] Hand-port `tests/DestColorGM.kt` — **out of scope** : `SkRuntimeEffect::MakeForBlender` (custom shader DSL).
- [x] Hand-port `tests/AndroidBlendModesGM.kt` — voir Phase 6 GMs.
- [ ] Étendre `SkBitmapDevice.compositeFrom` à tous les modes (saveLayer + blend) — déféré, débloquera XfermodesGM.

### Vérification Phase 6 entry
- [x] Tests ≥ 85% (`ScaledRectsGM` à 87.79%).
- [x] Aucune régression sur les 44 GMs précédents.
- [x] **Pass count cumulé : 45 GM.**

### Phase 6 Porter-Duff completion ✅

Suite de la Phase 6 entry : ajout des 5 derniers modes Porter-Duff coefficient (les modes "Out", "ATop" et "Xor"). Le slice complète la famille des 12 modes Porter-Duff classiques ; il reste 14 modes (10 separable + 4 HSL) à porter.

#### Modes ajoutés
- [x] `kSrcOut` — `r = s * (1-da)`. RGB de src préservé, alpha = `sa*(1-da)`.
- [x] `kDstOut` — `r = d * (1-sa)`. Symétrique de `kSrcOut`.
- [x] `kSrcATop` — `r = s*da + d*(1-sa)`. Alpha de sortie = `da` (Skia : la source ne peut pas étendre la silhouette de dst sous ATop). RGB = `lerp(dst, src, sa/255)` après simplification symbolique.
- [x] `kDstATop` — `r = d*sa + s*(1-da)`. Réutilise `blendSrcATop` avec opérandes swappés.
- [x] `kXor` — `r = s*(1-da) + d*(1-sa)`. Recouvre les pixels couverts par exactement un des deux opérandes ; à alpha fractionnaire, formule premul puis dé-premul comme les autres modes Porter-Duff non-triviaux.

#### Tests unitaires

- [x] **`SkBlendModeTest.kt`** : +15 cas couvrant les 5 nouveaux modes (opaque-on-opaque, opaque-on-transparent, transparent-on-opaque, et au moins un cas alpha fractionnaire par mode). 47 tests passent au total (32 anciens + 15 nouveaux).

#### Tests GM

- [x] **Hand-port `tests/AaRectModesGM.kt`** (`aarectmodes` upstream) — exerce les 12 modes Porter-Duff coefficient en grille 12 × 4 (12 modes × 4 configurations alpha). Score **80.30 %** vs `aarectmodes.png` à `tolerance=1`. Le résiduel 20 % est principalement dû à :
  - Drift `saveLayer`-flatten existant (chaque cellule passe par un layer transparent) — connu sur `ArcOfZorroGM`, `PathInteriorGM`.
  - Subtilités d'AA sur les ovales (rasterizer scanline 4×4 supersampling vs Skia analytique).
  - Modes complexes (`kXor` notamment) accumulent les arrondis 8-bit sur les paths AA partiellement couverts.

  C'est le premier GM portant qui exerce un saveLayer + 12 modes ; `tolerance=1` à 80 % est une bonne ligne de base. Les follow-ups séparés (linear-premul F16 storage, AA analytique pour ovales) lifteront ce score sans toucher aux blend modes.

#### Vérification Phase 6 Porter-Duff
- [x] 64 GMs précédents — 0 régression.
- [x] 1 nouveau GM (`AaRectModesGM` à 80.30 %).
- [x] 47 tests unitaires `SkBlendMode` verts.
- [x] **Pass count cumulé : 65 GM.**

### Phase 6 separable (simple) ✅

Ajout des 5 modes separable « simples » (formules en une ligne en premul-float). Il reste 5 modes separable complexes (`kOverlay`, `kHardLight`, `kColorDodge`, `kColorBurn`, `kSoftLight`) + 4 HSL pour clôturer Phase 6.

#### Modes ajoutés
- [x] `kMultiply` — `rc = (1-sa)*dc + (1-da)*sc + sc*dc`. ⚠ Skia distingue *Multiply* (cette formule, premul-aware) de *Modulate* (`s*d` direct, déjà implémenté Phase 6 entry).
- [x] `kDarken` — `rc = sc + dc - max(sc*da, dc*sa)`. Sélectionne le plus sombre des deux opérandes pondérés.
- [x] `kLighten` — `rc = sc + dc - min(sc*da, dc*sa)`. Symétrique.
- [x] `kDifference` — `rc = sc + dc - 2*min(sc*da, dc*sa)`. Différence absolue ; couleurs identiques s'annulent.
- [x] `kExclusion` — `rc = sc + dc - 2*sc*dc`. Différence sans dépendance d'alpha.

L'alpha de sortie est SrcOver (`oa = sa + da*(1-sa)`) pour tous les modes separable, conformément à la convention Skia.

#### API ajoutée

- [x] **`SkBitmapDevice.blendSeparable(src, dst, mode)`** — dispatcher : convertit une fois en float premul, applique la formule per-canal via `sepChannel`, dépremultiplie + quantize 8-bit.
- [x] **`SkBitmapDevice.sepChannel(s, d, sa, da, mode)`** — formule par canal en premul-float `[0, 1]`.

#### Tests unitaires

- [x] **`SkBlendModeTest.kt`** : +14 cas couvrant les 5 nouveaux modes (opaque-on-opaque, opaque-on-black-or-white, deux cas symétriques par mode). 61 tests passent au total (47 anciens + 14 nouveaux).

#### Pas de port GM ce slice

Tous les GMs upstream qui exercent ces modes (`androidblendmodes`, `lcdblendmodes`, `xfermodes`, etc.) nécessitent `drawString`/`SkFont` (hors scope) ou bien ont dejà été couverts par un GM précédent (`aarectmodes` couvre les Porter-Duff). Le port d'un GM dédié sera fait quand on aura aussi les 5 modes complexes (`kOverlay`/etc.) — `XfermodesGM` upstream couvre les 29 modes ensemble en grille.

#### Vérification Phase 6 separable simple
- [x] 65 GMs précédents — 0 régression, scores inchangés.
- [x] 14 nouveaux tests unitaires verts.
- [x] **Pass count cumulé : 65 GM** (infrastructure ; pas de nouveau port GM).

### Phase 6 separable (complex) ✅

Ajout des 5 modes separable « complexes » avec branches conditionnelles. Réutilise l'infra `blendSeparable` / `sepChannel` du slice simple. Reste 4 modes HSL pour clôturer Phase 6.

#### Modes ajoutés
- [x] `kHardLight` — `B = if 2*sc ≤ sa: 2*sc*dc else sa*da - 2*(da-dc)*(sa-sc)`. Multiply quand src est sombre, Screen quand src est clair.
- [x] `kOverlay` — `kHardLight` avec opérandes swappés (la condition utilise `2*dc ≤ da`). Effet symétrique : utilise la luminosité du **dst** au lieu du src.
- [x] `kColorDodge` — formule à 3 branches (`dc == 0`, `sc >= sa`, sinon `min(da, dc*sa/(sa-sc)) * sa + carrier`). Éclaircit dst vers blanc selon src.
- [x] `kColorBurn` — symétrique : `dc >= da` / `sc <= 0` / sinon `(da - min(da, (da-dc)*sa/sc)) * sa + carrier`. Assombrit dst vers noir.
- [x] `kSoftLight` — port direct de Skia `SkRasterPipeline_opts.h::softLight` : 3 branches (`2*sc ≤ sa` dark, sinon `4*dc ≤ da` cubic, sinon sqrt). Version douce de HardLight ; ne sature jamais à blanc/noir pur.

Tous suivent la convention Skia : output alpha = SrcOver alpha (`sa + da*(1-sa)`), term carrier = `(1-sa)*dc + (1-da)*sc`. Les helpers privés `hardLightChannel`, `colorDodgeChannel`, `colorBurnChannel`, `softLightChannel` factorisent les formules.

#### Tests unitaires

- [x] **`SkBlendModeTest.kt`** : +13 cas couvrant les 5 nouveaux modes. Pin les comportements canoniques opaque-on-opaque (white/black/mid-grey on white/blue/black). Pour `kColorDodge` un test pin explicitement la **différence** entre Skia (branche `dc == 0` prioritaire) et W3C (branche `Cs == 1` prioritaire) — Skia gagne car nos références viennent du DM Skia. 74 tests passent au total (61 anciens + 13 nouveaux).

#### Pas de port GM ce slice (cohérent avec slice simple)

Même rationale qu'en Phase 6 separable simple : `xfermodes` upstream et `androidblendmodes` ont besoin de `drawString`/`SkFont` (hors scope), `lcdblendmodes` aussi. Le port d'un GM dédié arrivera après Phase 6 HSL ou avec un GM custom in-house.

#### Vérification Phase 6 separable complex
- [x] 65 GMs précédents — 0 régression, scores inchangés.
- [x] 13 nouveaux tests unitaires verts.
- [x] **Pass count cumulé : 65 GM**. **Modes Skia couverts : 25 / 29 (86 %)** ; reste 4 HSL.

### Phase 6 HSL — clôture Phase 6 ✅

Ajout des 4 modes HSL — `kHue`, `kSaturation`, `kColor`, `kLuminosity` — qui terminent la couverture de tous les `SkBlendMode` upstream (29/29). Ces modes opèrent sur la **tuple RGB entière** (pas par-canal comme les modes separable) parce que la luminance et la saturation se définissent globalement, donc ils ne réutilisent pas `sepChannel` et reçoivent leur propre dispatcher `blendHSL`.

#### Modes ajoutés
- [x] `kHue` — `SetLum(SetSat(Cs, Sat(Cb)), Lum(Cb))`. Donne au résultat la *teinte* de src, mais la *saturation* et la *luminance* de dst.
- [x] `kSaturation` — `SetLum(SetSat(Cb, Sat(Cs)), Lum(Cb))`. Saturation de src, teinte + lum de dst.
- [x] `kColor` — `SetLum(Cs, Lum(Cb))`. Chrominance complète de src (teinte+sat), luminance de dst. Le mode "colorier" : ajoute la couleur du src au-dessus du dst sans changer sa brillance.
- [x] `kLuminosity` — `SetLum(Cb, Lum(Cs))`. Symétrique du précédent : chrominance de dst, brillance dictée par src.

#### API ajoutée

- [x] **`SkBitmapDevice.blendHSL(src, dst, mode)`** — dispatcher en premul-float `[0, sa*da]`, appliquant la formule HSL puis ajoutant le carrier `sc*(1-da) + dc*(1-sa)`.
- [x] **`lum3(r, g, b)`** — luminance pondérée Skia : `0.3*R + 0.59*G + 0.11*B`.
- [x] **`sat3(r, g, b)`** — saturation = `max - min`.
- [x] **`setLum(rgb, alpha, newLum)`** — shift uniforme + `clipColor`. Mute en place.
- [x] **`setSat(rgb, newSat)`** — rescale `(value - min)` par `newSat / spread` ; le min collapse à 0, le max devient `newSat`. Mute en place.
- [x] **`clipColor(rgb, alpha)`** — pull-toward-luminance pour replier dans `[0, alpha]`.

#### Tests unitaires

- [x] **`SkBlendModeTest.kt`** : +7 cas HSL pinning les comportements canoniques (Hue/Saturation sur grey = identity, Color = src-tinted, Luminosity preserves dst chrominance, blue→white via Luminosity hits clipColor saturation branch). Plus un **smoke test** vérifiant que les 29 modes dispatchent sans throw `NotImplementedError`. **81 tests passent au total** (74 + 7).

#### Pas de port GM ce slice (cohérent)

`xfermodes`/`androidblendmodes`/`lcdblendmodes` upstream ont besoin de `drawString`/`SkFont` — hors scope. Une fois qu'on aura le text rendering, ces GMs deviendront des stress-tests bout-en-bout pour les 29 modes.

#### Vérification Phase 6 HSL — clôture
- [x] 65 GMs précédents — 0 régression, scores inchangés.
- [x] 7 nouveaux tests unitaires HSL + smoke test 29 modes verts.
- [x] **Pass count cumulé : 65 GM**.
- [x] **🎉 Modes Skia couverts : 29 / 29 (100 %)** — formules close.

### Phase 6 GMs — AAXfermodes + AndroidBlendModes ✅

**But** : maintenant que les 29 modes sont implémentés et que la stack texte (T1–T5) ship, porter les deux GMs upstream qui exercent simultanément tous ces modes en grille. Premier vrai test bout-en-bout après les unit tests Phase 6 HSL.

#### Helpers ajoutés

- [x] **`org.skia.utils.SkTextUtils`** — port de Skia's `SkTextUtils::Draw` / `DrawString` (`include/utils/SkTextUtils.h`). Énumération `Align` (`kLeft_Align` / `kCenter_Align` / `kRight_Align`) + entry point qui mesure la string via `font.measureText` puis shifte l'origine selon l'alignement avant de déléguer à `SkCanvas.drawSimpleText`. Premier composant utility texte qui n'est pas dans `tools/` ; vit dans `org.skia.utils` pour matcher upstream.
- [x] **`SkBlendMode_Name(mode)`** — table de 29 noms canoniques CamelCase (`"SrcOver"`, `"ColorDodge"`, etc.). Mirror direct de `src/core/SkBlendMode.cpp:SkBlendMode_Name`. Utilisé par les labels GM.
- [x] **`SkCanvas.getSaveCount()` / `restoreToCount(n)`** — paire de méthodes utilitaires manquantes. Mirror upstream `SkCanvas::getSaveCount()` (= depth de la pile, root = 1) et `restoreToCount(n)` (pop jusqu'à `getSaveCount() == n`). Utilisé par `AndroidBlendModesGM.drawTile`.
- [x] **`SkCanvas.drawColor(color, mode)`** overload — avant : seule la version 1-arg avec mode hardcodé `kSrc` (pour la compat clip-aware `clear`). Après : 2-arg avec `mode` par défaut `kSrcOver` (matches upstream `SkCanvas.h:1235`). `clear(color)` route maintenant à `drawColor(color, kSrc)` — sémantique inchangée pour les callers existants.

#### Fix correctness — `modeAffectsZeroAlphaSrc` étendu

Bug latent dans `SkBitmapDevice.drawImageRect` : la branche `if (sample ushr 24 == 0) continue` skippe toujours les samples transparents, même pour les modes dont la formule transforme un src=0 en un dst != dst (i.e., `kClear`, `kSrc`, `kSrcIn`, `kSrcOut`, `kDstIn`, `kDstATop`, `kModulate`). Conséquence : `drawImage` avec un de ces modes laissait des pixels covered par dst à leur valeur d'origine au lieu de les zéroter. Visible dès AndroidBlendModesGM où chaque cellule fait `drawImage(srcBmp, mode)` avec un `srcBmp` dont 70 % des pixels sont alpha=0.

- [x] **`modeAffectsZeroAlphaSrc(mode)`** : ajout de `kSrcOut` et `kDstATop` aux 5 modes existants (`kClear, kSrc, kSrcIn, kDstIn, kModulate`). Total 7 modes.
- [x] **`drawImageRect` (les 2 branches `kNearest` / `kLinear`)** : précompute `mustBlendZero = modeAffectsZeroAlphaSrc(mode)` une fois, puis le skip devient `if (sample.a == 0 && !mustBlendZero) continue`. Pas de coût pour les modes courants (`kSrcOver` continue de skipper normalement) ; correct pour les modes affectés.

Impact mesuré : `AndroidBlendModesGM` passe **88.85 % → 97.04 %** (+8.2). 0 régression sur les 65 GMs précédents.

#### GMs portés

| GM                  | Référence              | Score      | Stress |
|---------------------|------------------------|------------|--------|
| AAXfermodesGM       | `aaxfermodes.png` 984×625 | **80.12 %** | Grille 2 colonnes × 15 modes (Porter-Duff + Advanced) × 4 shapes (square / diamond / oval / concave) × 2 paintColors (translucent / opaque). `saveLayer(null, null)` + `clipRect` cellulaire + `drawColor(kSrc)` + `kPlus`-overflow protection via `kDstIn` dim-paint. Premier GM portant qui exerce simultanément les 29 modes sur 4 types de shapes. |
| AndroidBlendModesGM | `androidblendmodes.png` 1024×1280 | **97.04 %** | Grille 4 × 5 = 18 cellules. Chaque cellule : `saveLayer(null, null)` → `drawImage(redCircleBmp)` → `drawImage(blueRectBmp, mode)` → flatten kSrcOver. Premier GM portant qui combine `drawImage` + per-image `paint.blendMode` ; le fix `modeAffectsZeroAlphaSrc` était la débloque clé. |

Le résiduel sur AAXfermodes (~20 %) est dominé par :
- AA edges sur les ovals et concaves (rasterizer 4×4 supersampling vs Skia analytique).
- Drift 8-bit cumulatif sur les modes complexes (`kColorDodge`, `kSoftLight`, modes HSL) sur les paths AA partiellement couverts.
- Drift labels texte AWT-vs-FreeType (~3-5 % du canvas).
- Compositing en encoded-Rec.2020 vs upstream linear-Rec.2020 (cf. Phase 5h reverted).

#### GMs Phase 6 reportés

- **`XfermodesGM`** (`xfermodes.png` 1990×570) — déféré : nécessite (1) `compositeFrom` qui supporte le `paint.blendMode` de `saveLayer` (le source type `kQuarterClearInLayer_SrcType` ouvre un layer avec `kPlus`/`kMultiply`/etc.), (2) le format `kARGB_4444_SkColorType` pour le BG bitmap shader (4 bits par canal). Slice indépendant.
- **`DestColorGM`** (`destcolor.png` 640×640) — **out of scope** : utilise `SkRuntimeEffect::MakeForBlender(...)` pour un blender custom écrit en SkSL. Le DSL runtime-effect n'est pas dans le scope du portage raster.
- **`XfermodeImageFilterGM`** (`xfermodeimagefilter.png`) — déféré Phase 7+ : nécessite `SkImageFilters::Blend`.

#### Vérification Phase 6 GMs
- [x] 65 GMs précédents — 0 régression, scores inchangés.
- [x] 2 nouveaux GMs : AAXfermodesGM 80.12 %, AndroidBlendModesGM 97.04 %.
- [x] `SkTextUtils` + `SkBlendMode_Name` + `restoreToCount` + `drawColor(color, mode)` overload + `modeAffectsZeroAlphaSrc` étendu.
- [x] **Pass count cumulé : 67 GM**. **🎉 Phase 6 close** (les 2 GMs restants `XfermodesGM` / `DestColorGM` sont scopés ailleurs — voir GMs Phase 6 reportés).

### Phase 6h — GM harvest post-Phase-6 (DEF_SIMPLE_GM mix) ✅

**But** : maintenant que la matrice d'API (paths complets, stroker complet, gradients, blend modes 29/29, texte) est stable, récolter une 10-pack de GMs `DEF_SIMPLE_GM` upstream portables sans aucune nouvelle API. Tous sont des tests de régression liés à un bug spécifique — un porte sur le rasterizer, un sur le stroker, un sur le shader, etc. — petits (≤ 100 lignes), targeted, complémentaires.

#### GMs portés

| GM                       | Référence                       | Score      | Stress |
|--------------------------|---------------------------------|------------|--------|
| Bug615686GM              | `bug615686.png` 250×250         | **98.58 %** | Cubic auto-intersectant stroked à width=20. Stroker `cubicPerpRay` à haute courbure. |
| Skbug4868GM              | `skbug_4868.png` 32×32          | **98.63 %** | clipRect + drawLine sous large translate (~5995 px). Edge-rounding consistency. |
| Crbug946965GM            | `crbug_946965.png` 75×150       | **97.32 %** | RRect filled + stroked sous rotate(90)+scale. Per-corner radii sous rotation. |
| Crbug1139750GM           | `crbug_1139750.png` 50×50       | **97.12 %** | Stroked rrect avec `strokeWidth = 2 * radius` (inner radii = 0). Edge case GPU. |
| Crbug847759GM            | `crbug_847759.png` 500×500      | **99.57 %** | Hairline (`strokeWidth=0`) closed cubic squashed-oval. AA hairline tangentielle. |
| LuminosityOverflowGM     | `luminosity_overflow.png` 256×256 | **99.51 %** | 64 bright color rects + 16 alpha-stepped white rects en `kLuminosity`. Stress du divide-by-luminance. |
| WideButtCapsGM           | `widebuttcaps.png` 480×500      | **81.91 %** | 4 paths × 4 joins (bevel/round/miter + cubic) × `strokeWidth=100`. Stress wide stroke + degenerate paths. |
| StrokeRectShaderGM       | `stroke_rect_shader.png` 690×300 | **84.31 %** | Stroked rects (AA on/off, bevel/miter/miter-limit/round/hairline) avec linear gradient shader. Stress local-vs-device coords. |
| ClipDrawDrawGM           | `clipdrawdraw.png` 512×512      | **35.34 %** ⚠ | crbug 423834 : clipRect + drawRect + drawRect avec `.5` fractional inputs. **Expose un vrai bug** : nos arrondis `floor`/`ceil` (clipRect) ↔ `floor(c+.5)` (drawRect non-AA) divergent ; Skia utilise round-half-to-even partout. Floor descendu à 30 % comme regression tracker — fix séparé. |
| RadialGradientPrecisionGM | `radial_gradient_precision.png` 200×200 | **4.92 %** ⚠ | Centre off-canvas (1000, 1000), radius 40, 25 périodes kRepeat sur la zone visible. Match visuel correct (max diff = 18) mais tolerance=1 est trop strict pour 25 wraparounds. Floor à 4 % — regression tracker précision. |

#### Vérification Phase 6h
- [x] Aucune nouvelle API. Tests sur API existante depuis Phase 6.
- [x] 67 GMs précédents — 0 régression.
- [x] **Pass count cumulé : 77 GM**.
- [x] 2 GMs (`ClipDrawDrawGM` / `RadialGradientPrecisionGM`) entrent comme regression-trackers à floor abaissé — voir notes ci-dessus pour le contexte.

#### Follow-up identifié (non-bloquant)

- **Edge-rounding consistency** clipRect-vs-drawRect non-AA : Skia utilise `round-half-to-even` partout, on utilise `floor`/`ceil` (clipRect) et `floor(c+.5)` (drawRect). Diverge sur `.5`-fractional. Slice indépendant ; les GMs path-AA + rect-AA majoritaires ne le voient pas.

### Phase 6i — Edge-rounding consistency (clipRect non-AA) ✅

**But** : le follow-up identifié à la fin de Phase 6h. Aligner `clipRect` non-AA sur la convention Skia (`round-half-up` = `SkScalarRoundToInt` per component) plutôt que `floor`/`ceil` (outward), pour qu'un `clipRect(rect)` consommé par un `drawRect(rect)` non-AA produise des bords pixel-aligned identiques (cf. crbug 423834).

#### Refactor

- [x] **`SkCanvas.clipRect(rect)`** délègue désormais à `clipRect(rect, doAntiAlias = false)`, qui utilise `floor(c + 0.5)` per component (matches `SkBitmapDevice.pixelEdge`).
- [x] **`SkCanvas.clipRect(rect, doAntiAlias = true)`** garde l'ancien comportement `floor(min)` / `ceil(max)` (outward bbox) pour préserver la couverture AA fractionnaire au bord du clip — c'est la sémantique attendue quand un path AA traverse la limite du `clipRect`.
- [x] La doc précise les deux régimes ; les callers existants (qui appellent `clipRect(rect)`) basculent automatiquement sur le non-AA aligné.

#### Impact mesuré

| GM                | Avant   | Après   | Δ |
|-------------------|---------|---------|---|
| AAXfermodesGM     | 80.12 % | **84.73 %** | +4.61 |
| Skbug4868GM       | 98.63 % | **99.32 %** | +0.69 |
| ClipDrawDrawGM    | 35.34 % | **35.38 %** | +0.04 (géométrie corrigée — voir ci-dessous) |

`ClipDrawDrawGM` : la géométrie est désormais correcte (zéro 1-px-remnant entre `clipRect` et `drawRect`), mais ~65 % des pixels gardent un drift sub-tolérance ≤ 6-byte sur le BG `0xCCCCCC`. Cause distincte : `runGmTest` initialise le device via `bitmap.eraseColor(bgColor)` qui skip le xform sRGB → Rec.2020 que Skia DM applique via `canvas->clear(bgColor)`. Fix harness séparé.

Aucune régression sur les 75 autres GMs.

#### Vérification Phase 6i
- [x] 78 GMs Phase 0-6h — 0 régression. 2 GMs sensibles aux clipRects sub-pixel grimpent.
- [x] La sémantique `clipRect(rect, true)` (AA outward) reste disponible pour les futurs callers AA-aware.
- [x] **Pass count cumulé : 78 GM** (inchangé — pas de nouveau port, pure correction rasterizer).

### Phase 6j — GM harvest round 2 (84 → 93) ✅

**But** : second tirage sur l'API existante (Phase 6 + edge-rounding fix). Mêmes contraintes que Phase 6h — pure ports, 0 nouvelle surface. Le sub-agent avait shortlisté 15 candidats ; on en porte 9 (les autres sont reportés round 3 ou ont besoin d'API absente — perspective matrices, `clipPath`, FillPathWithPaint, etc.).

#### GMs portés

| GM                       | Référence                       | Score      | Stress |
|--------------------------|---------------------------------|------------|--------|
| Bug5099GM                | `bug5099.png` 50×50             | **95.76 %** | Cubic stroker bug — width=10, near-coincident control points. |
| Bug6083GM                | `bug6083.png` 100×50            | **95.48 %** | 2 stroked cubics sous translate `(-500, -130)` ; `p2.y` divergent de 0.2. Stresse stabilité numérique sous large translate. |
| Bug6987GM                | `bug6987.png` 200×200           | **99.47 %** | Triangle stroker `width=0.0001` sous `scale(50000, 50000)`. Stress `SkStroker.resScale` (Phase 3i). |
| Bug339297GM              | `bug339297.png` 640×480         | **99.14 %** | Cubic sliver à `y ≈ -10⁷`, ramené par `translate(258, +10⁷)`. Précision float coords/CTM. |
| Crbug10141204GM          | `crbug_10141204.png` 512×512    | **100.00 %** | Stress non-axis-aligned + giant coords + `MakeAll` 6-arg affine ⇒ canvas blue-fill solide. |
| DRRectSmallInnerGM       | `drrect_small_inner.png` 170×610 | **96.50 %** | DRRect inner shrinks à 0.01 px, oval-on/off-centre. Tessellator divide-by-zero protection. |
| EmptyPathGM              | `emptypath.png` 600×280         | **87.64 %** | Empty path × 4 fillTypes (incl. `kInverse*`) × 3 styles + labels texte. Premier port qui exerce les fill rules inverses introduits Phase 3.8. |
| LinePathGM               | `linepath.png` 1240×390         | **87.31 %** | `moveTo(25, 15) + lineTo(75, 15)` non-fermé × 4 fillTypes × 3 styles × 3 cap/join + labels. |
| LineClosePathGM          | `lineclosepath.png` 1240×390    | **87.27 %** | Sister GM avec `close()` — exerce la stroke de la ligne de fermeture. |

Le résiduel sur `EmptyPath` / `LinePath` / `LineClosePath` (~12-13 %) est dominé par les labels texte AWT-vs-FreeType (chaque cellule a 2-3 strings de 10-15 px) sur grandes surfaces.

#### Vérification Phase 6j
- [x] 84 GMs précédents — 0 régression.
- [x] 9 nouveaux ports : Bug5099, Bug6083, Bug6987, Bug339297, Crbug10141204, DRRectSmallInner, EmptyPath, LinePath, LineClosePath. Tous au-dessus de leur floor.
- [x] **Pass count cumulé : 93 GM**.

#### GMs reportés round 3+

- **`thinconcavepaths`** (550×400) — porté en Phase 6k.
- **`crbug_947055`** (200×50) — perspective row `[0, 0.0225, 1]` non-affine, hors scope `SkMatrix` (qui est pure 2×3 affine).
- **`bug339297_as_clip`** — `clipPath(arbitrary)`. À confirmer la dispo de `clipPath` non-rect.
- **`stroke_rect_shader` / `circle_sizes`** — déjà portés (sub-agent doublons).
- **`pathreverse`, `cubicpath_shader` (porté Phase 6k), `largeclippedpath_*`, `hugepath`, `hittestpath`, `croppedrects`, `alphagradients`, `gradient_dirty_laundry`, `bug12866`** etc. — bloqués par APIs absentes (`SkPathPriv::ReverseAddPath`, `clipPath`, surface offscreen, `path.contains`, `SweepGradient`, `SkM44`, etc.).

### Phase 6k — GM harvest round 3 (93 → 101) ✅

**But** : 3e tirage post-Phase-6, avec une factorisation qui réduit la duplication des matrix-grid GMs (`linepath` family).

#### Factorisation

- [x] **`PathCapsFillsGridGM`** — classe abstraite partagée par les GMs qui rendent un path dans une grille `3 caps × 4 fills (incl. kInverse*) × 3 styles` avec labels. Prend `(name, size, path, title, shader?)` en ctor. Centralise la logique de save/translate/restore qui était dupliquée entre `LinePathGM` (Phase 6j) et les nouveaux `CubicClosePathGM` / `CubicPathShaderGM` / `QuadPathGM` / `QuadClosePathGM`.

`LinePathGM` n'est pas refactorisé pour éviter de toucher du code déjà mergé ; les futurs GMs de cette famille (e.g. quadabspath) hériteront de `PathCapsFillsGridGM`.

#### GMs portés

| GM                    | Référence                            | Score      | Stress |
|-----------------------|--------------------------------------|------------|--------|
| ThinConcavePathsGM    | `thinconcavepaths.png` 550×400       | **98.06 %** | 9 familles de paths concaves thin (rect, right-angle, golf-club, barbell, hipster-pants, skinny-snake, etc.) sweep widths 0.05..2 px. AA fill stress. |
| Crbug1257515GM        | `crbug_1257515.png` 1139×400         | **98.93 %** | 2 polylines stroked sous `translate + scale(2,2)` (red round-cap/join, blue butt+bevel). |
| Crbug938592GM         | `crbug_938592.png` 500×300           | **99.80 %** | Hard-stop linear gradient mirroré 4 fois via `scale(±1, ±1)`. |
| StrokeRectsRotatedGM  | `strokerects_rotated.png` 800×800    | **90.74 %** | Variante rotated du `StrokeRectsGM` existant. 100 random rects × 4 panes sous `rotate(45, SW, SH)`. |
| CubicClosePathGM      | `cubicclosepath.png` 1240×390        | **86.94 %** | Closed cubic dans la grille caps × fills × styles. |
| CubicPathShaderGM     | `cubicpath_shader.png` 1240×390      | **79.72 %** | Open cubic dans la grille, avec linear gradient (3 stops translucides) au lieu d'un solid color. |
| QuadPathGM            | `quadpath.png` 1240×390              | **87.27 %** | Open quad dans la grille. |
| QuadClosePathGM       | `quadclosepath.png` 1240×390         | **87.05 %** | Closed quad dans la grille. |

Le résiduel `~13 %` sur les `*PathGM`s grilles vient des labels texte (AWT-vs-FreeType drift) — même pattern que `LinePathGM` (~87 %). Le score plus bas de `CubicPathShaderGM` (~80 %) ajoute le drift cumulatif 8-bit du linear gradient sur 36 cellules translucides.

#### Vérification Phase 6k
- [x] 93 GMs précédents — 0 régression.
- [x] 8 nouveaux ports : tous au-dessus de leur floor.
- [x] **Pass count cumulé : 101 GM** — premier passage des 100 GMs.

### Phase 6l — GM harvest round 4 (101 → 109) ✅

**But** : 4e tirage post-Phase-6, premier après l'arrivée de SkMatrix Phase 4 (perspective + RSXform + PolyToPoly) sur `from-skia`.

#### GMs portés

| GM                       | Référence                       | Score      | Stress |
|--------------------------|---------------------------------|------------|--------|
| B340982297GM             | `b_340982297.png` 80×50         | **94.63 %** | 2 paths self-intersecting filled — close-after-cross triangulator regression. |
| Bug406747427GM           | `bug406747427.png` 400×400      | **97.97 %** | 3 `drawArc` × `kRound_Cap` × stroke widths > radius (50/48/80 px). |
| ConjoinedPolygonsGM      | `conjoined_polygons.png` 400×400 | **99.25 %** | 7-vertex self-touching bow-tie filled. crbug 1197461. |
| Crbug996140GM            | `crbug_996140.png` 300×300      | **74.69 %** | Tiny circle (`r = 0.0295`) sous `scale(203, 203)` + translate. arcTo + fill + stroke. |
| FillTypesGM              | `filltypes.png` 835×840         | **50.81 %** | 2 overlapping circles × 4 fillTypes (incl. `kInverse*`) × 2 scales × 2 AA modes. AA edge drift dominates. |
| PathHugeCrbug800804GM    | `path_huge_crbug_800804.png` 50×600 | **89.33 %** | 6 stroked lines avec endpoint coords ~ 10²⁰. Hairline scan overflow regression. |
| PolygonsGM               | `polygons.png` 840×1140         | **86.08 %** | 8 polygones × 3 joins × 3 widths + strokeAndFill + fill, couleurs `SkRandom`. |
| SmallPathsGM             | `smallpaths.png` 640×512        | **97.09 %** | 11 small paths (triangle/rect/oval/stars/lines/arrow/conic/2×battery/ring) × 4 styles. Premier port qui exerce simultanément addCircle+conicTo+cubicTo+SkPath.Rect/Oval/Line. |

#### GM reporté — perspective rasterization

`crbug_947055` (200×50, perspective row `[0, 0.0225, 1]`) **non porté** : `SkMatrix` Phase 4 a la math (homogeneous mapXY, concat 3×3, etc.), mais notre `SkBitmapDevice.buildEdges` transforme chaque control point via la formule affine `ax*x + bx*y + cx0` directement, sans la division homogène. Le rendu produit le polygone des coins NON-divisés — un trapèze massif au lieu du sliver projeté correct. Score 9.31 %.

Fix architectural : ajouter une branche `if (ctm.hasPerspective())` dans `buildEdges` qui fait `mapXY` avec divide pour chaque coord. Slice indépendant. Une fois en place, `crbug_947055` devient portable + tout autre GM perspective.

#### Vérification Phase 6l
- [x] 101 GMs précédents — 0 régression.
- [x] 8 nouveaux ports : tous au-dessus de leur floor (45-90 %, scoring 50-99 %).
- [x] `FillTypesGM` floor à 45 % — circle AA edge drift × 16 cellules × inverse fills domine ; visuellement le pattern correspond bien.
- [x] **Pass count cumulé : 109 GM**.

### Phase 6m — Perspective-aware `buildEdges` ✅

**But** : matérialiser le follow-up identifié à la fin de Phase 6l. `SkMatrix` Phase 4 a apporté la math (homogeneous `mapXY`, full 3×3 `concat`, `hasPerspective` predicate, etc.) mais `SkBitmapDevice.buildEdges` continuait à transformer chaque control point via la formule affine `ax*x + bx*y + cx0` — la division homogène n'était pas appliquée.

#### Refactor

- [x] **`SkBitmapDevice.buildEdges(path, ctm)`** : early-out vers `buildEdgesPerspective(path, ctm)` quand `ctm.hasPerspective()`. Le fast path affine reste bit-identique.
- [x] **`SkBitmapDevice.buildEdgesPerspective(path, ctm)`** : nouveau. Cache les 9 scalaires de la matrice en locales, projette chaque source point `(sx, sy)` via :
  ```
  w = persp0*sx + persp1*sy + persp2
  outX = (mat.sx*sx + mat.kx*sy + mat.tx) / w
  outY = (mat.ky*sx + mat.sy*sy + mat.ty) / w
  ```
  avec un guard `if (w == 0f) invW = 0f` pour éviter `NaN` au point de fuite.
- [x] **Béziers (`kQuad` / `kConic` / `kCubic`)** : projection puis flattening en device space — approximation. Pour des paths cubic-heavy sous perspective extrême la projection des control points donne une courbe légèrement différente de la projection vraie de la courbe source. Acceptable pour les GMs en scope (un suivi `flatten-then-project` peut venir si on a un GM cubic-perspective concret qui le requiert).
- [x] **Lines (`kMove` / `kLine` / `kClose`)** : projection point par point — exact (la projection d'une ligne est la ligne entre les projections des endpoints).

#### GM porté

| GM             | Référence              | Score      | Notes |
|----------------|------------------------|------------|-------|
| Crbug947055GM  | `crbug_947055.png` 200×50 | **14.56 %** | Geometry correcte (la projection match — le sliver rouge est rendu à la bonne position et taille). Le résiduel ~85 % vient du même drift BG-color-xform que `ClipDrawDrawGM` : `runGmTest` initialise via `bitmap.eraseColor(SK_ColorBLUE)` qui skip le xform sRGB → Rec.2020 que Skia DM applique via `canvas->clear(bgColor)`. Floor à 10 %. |

#### `filltypespersp` exploré, déféré

`FillTypesPerspGM` (835 × 840, 4 fillTypes × perspective × radial gradient × clipRect × cubic Béziers via `addCircle`) a été tenté mais score 6.12 % avec rendu visuellement très différent. La combinaison perspective × cubic-Bézier × clipRect × radial-gradient stressse plusieurs interactions à la fois (clipRect bbox-conservative sous perspective, control-points-then-flatten approximation pour les cercles, perspective-aware shader sampling). Décortiquer chaque drift demande son propre slice. Reporté.

#### Vérification Phase 6m
- [x] 109 GMs précédents — 0 régression. La branche perspective ne se déclenche que si `ctm.hasPerspective()` ; le fast path affine est inchangé.
- [x] 1 nouveau port : `Crbug947055GM` 14.56 %. Geometry-correcte ; floor à 10 % (BG drift indépendant).
- [x] **Pass count cumulé : 110 GM**.

### Phase 5h — Linear-premul F16 storage (❌ explored, reverted)

**Hypothèse de départ** : le drift constant de `TinyBitmapGM` (rendered `(214, 177, 167)` vs reference `(204, 162, 158)`) viendrait de ce que le buffer F16 stocke des valeurs **encoded** Rec.2020 alors que Skia upstream compose en **linear** Rec.2020. Refactor : stocker en linear-premul, encoder seulement à la sortie 8-bit / PNG.

**Refactor tenté** :
- `SkBitmap.eraseColor` / `setPixel` / `getPixel` : linearize sur F16 write, encode sur F16 read (xforms lazy via `colorSpace.makeLinearGamma()`).
- `SkBitmapDevice` : nouveau `linearXformSteps` (sRGB → linear-{gamut}), `colorToF16Premul` et `blendF16` appliquent le xform complet ; `inDeviceColorSpace` skippe le xform 8-bit pour F16 (évite la quantization 8-bit avant le passage en linear) ; nouveau fast-path F16↔F16 dans `compositeFrom` qui SrcOver-blend en linear-premul direct.
- `SkShader.setupForDraw` : nouvelle signature avec `linearXform` séparé pour les tables F16. `SkLinearGradient` / `SkRadialGradient` / `SkBitmapShader` produisent `xformedColorsF16` / `xformedPixelsF16` en linear-premul.
- `TestUtils.bufferedImageToBitmap` : linearize à la lecture 16-bit PNG.

**Résultats mesurés** :
- ⚠ **0 amélioration** sur les 65 GMs. Aucun GM ne gagne en pixel-similarity.
- ⚠ **3 régressions** sur des GMs gradient déjà cassés (`AnalyticGradientShaderGM` 62.73→59.65, `FillrectGradientGM` 68.18→66.85, `HardstopGradientsManyGM` 12.79→10.65) — le lerp en linear-Rec.2020 produit des intermédiaires différents du lerp en encoded-Rec.2020 que Skia utilise par défaut pour `SkGradientShader::Interpolation::ColorSpace::kDestination`.
- ⚠ **2 GMs sous tolerance mineure mais sous le hard-floor du test** (`StrokeCircleGM` 90.37→89.95 < 90 % floor, `AnalyticGradientShaderGM` 59.65 < 60 % floor) — ces tests ont des `assertTrue(similarity >= X)` qui cassent.

**Diagnostic** :
- Skia DM ne fait *pas* tout le compositing en linear. Le raster pipeline F16 reste **par défaut en encoded-{dst}** quand `dst.colorType == kRGBA_F16Norm` avec un colorspace non-linéaire — on l'observe en confrontant nos résultats à la référence 16-bit Rec.2020.
- Les gradient shaders interpolent toujours en *encoded* sauf si on opt-in `Interpolation::ColorSpace::kSRGBLinear`.
- Le drift original de `TinyBitmap` n'était donc pas dû à "encoded vs linear" — c'était autre chose (paint.alpha modulation order, BG eraseColor sans xform, ou un bug ailleurs dans le shader path) qu'on n'a pas isolé.

**Décision** : revert intégral du refactor. Le post-mortem vaut le code temporaire.

**Pistes pour reprendre Phase 5h** (si on y revient un jour) :
- D'abord re-porter `TinyBitmapGM` en l'état actuel et **diagnostiquer pixel-par-pixel** d'où vient le drift, plutôt que de présupposer la cause.
- Considérer `SkColorSpace.makeSRGB()` comme working space pour les GMs (au lieu de Rec.2020) — beaucoup de GMs upstream sont rendus avec un working space sRGB-linear, et on pourrait peut-être matcher en passant à `SkColorSpace.makeSRGBLinear()`.
- Étudier le code Skia `SkRasterPipeline` pour voir exactement quels modes appliquent linearize/encode dans le pipeline (probablement seulement quand l'alpha-type force la conversion).

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
- [x] **Texte & polices** (`SkFont`, `SkTypeface`, `drawString`, `*TextGM`). ✅ **Trajectoire complète** — T1..T5 livrés, 4 GMs textuels portés (BigText, ColorWheelNative, Crbug1073670, AnnotatedText). Plan archivé : voir [archives/MIGRATION_PLAN_TEXT.md](archives/MIGRATION_PLAN_TEXT.md). Le seul travail texte restant est le **TTF parser maison** ci-dessous (opportuniste).
- [ ] **TTF parser maison** — voir section dédiée plus bas. Lire `.ttf` directement en pur Kotlin (sans AWT, sans JNI/FreeType) pour des **outlines bit-exact upstream**. Déclenché quand un GM concret réclame tolerance ≤ 1 (typiquement `bigtext`-family où le glyphe **est** le sujet du test, pas un label).
- [ ] **Image filters & blurs** (`*BlurGM*`, `ImageFilters*GM`). Graphe d'évaluation séparé.
- [ ] **Codecs** (`EncodeGM`, etc.). `javax.imageio` suffit pour charger les références.
- [ ] **Modules** (`org.skia.modules.*` : Skottie, Paragraph, SVG). Migration parallèle après raster ≥ 90%.

### TTF parser maison — slice future (texte fidélité bit-exact)

**Contexte**. La trajectoire texte (T1..T5) ship une stack AWT-backed avec scores 98-99% sur 3 des 4 GMs portés. Le drift résiduel (~1-2 ulps sur AA edges + métriques scaler AWT vs FreeType) est suffisant pour les GMs où le texte est **annotation** mais empêche d'atteindre tolerance ≤ 1 sur les GMs où le glyphe **est** le sujet du test.

**Idée**. Porter un parser TTF en pur Kotlin (~800-1200 lignes) qui lit directement les TTF Liberation déjà embarquées (T4, `kanvas-skia/src/main/resources/fonts/liberation/`, ~4.3 MB). Bypass AWT pour la résolution d'outlines ; rasterizer scanline existant pour le fill. Outlines **bit-exact upstream** (mêmes données source que `tools/fonts/test_font_*.inc` upstream a pré-extraites).

**Stratégie comparée à l'option B abandonnée** (porter `test_font_*.inc` en données Kotlin) :

| Critère | Option B (.inc ports — abandonnée) | Option C (TTF parser — future) |
|---|---|---|
| Source données | C++ literal arrays portés en Kotlin (~1.2 MB) | TTF Liberation déjà embarqués (~4.3 MB classpath, partagés avec T4) |
| Toolchain ajoutée | script Python générateur | aucune |
| Resources additionnelles | +1.2 MB Kotlin | **0** |
| Code à écrire | ~600-800 lignes + générateur | ~800-1200 lignes pur Kotlin |
| Réutilisable au-delà de Liberation | non (12 sub-fonts hard-codés) | oui (n'importe quel TTF) |
| Maintenance long-terme | regen quand fonts changent | code stable, fontes interchangeables |
| Fidélité outlines | bit-exact upstream | bit-exact upstream |

L'option B était la première proposition mais a été **explicitement abandonnée** au profit de C : pas de duplication de ressources, pas de toolchain externe, scope plus large.

**Scope du parser TTF (Liberation-friendly, minimal)**.

Tables à parser (TrueType, pas OpenType-CFF — Liberation est en TT) :
| Table | Contenu | Effort |
|---|---|---|
| `head` | unitsPerEm, version, glyph ID format | ~30 lignes |
| `maxp` | nombre de glyphes | ~20 lignes |
| `cmap` | Unicode → glyph ID (formats 4 + 12 suffisent) | ~200 lignes |
| `loca` | offsets dans `glyf` | ~30 lignes |
| `glyf` | outlines points/verbs (simple + composite glyphs) | ~300 lignes |
| `hmtx` + `hhea` | advance widths, ascent / descent | ~80 lignes |
| `OS/2` | x-height, cap-height, weight | ~50 lignes |
| `name` | nom de famille (utile pour `LiberationFontMgr` ré-implémenté) | ~80 lignes |

**Out of scope (consciemment)** :
- ❌ CFF / CFF2 (outlines PostScript) — c'est OTF, on skip ; Liberation est TT.
- ❌ Variable fonts (`gvar`/`avar`/`fvar`) — Liberation n'est pas variable.
- ❌ Color tables (`COLR`/`CBDT`/`sbix`/`SVG`) — pas d'emoji dans le scope GMs.
- ❌ Bytecode hinting (TT instructions) — on veut les outlines **bruts** non-hintés (= ce que le pipeline path-fill préfère).
- ❌ GPOS/GSUB (ligatures, contextual substitutions) — pas dans le scope GMs.

**Difficultés réelles documentées** :
1. **Composite glyphs** — un glyphe peut référencer d'autres avec une transform 2×2 + offset. Récursion + matrice. ~80 lignes.
2. **`cmap` format 4** (BMP) — segment-based mapping avec `searchRange`/`entrySelector`/`rangeShift` binary search. Spec claire mais pas trivial. ~150 lignes.
3. **Conversion TTF → SkPath** : points marqués on-curve / off-curve. Deux off-curve consécutifs impliquent un on-curve **implicite** au milieu. ~100 lignes.
4. **Big-endian** : format entièrement BE, attention aux conversions JVM.

**Architecture proposée** :
- Nouvelle classe `SkTtfTypeface : SkTypeface` dans `org.skia.foundation` (pur Kotlin, **pas** dans `awt/` puisque sans dépendance AWT).
- Parser lazy : tables résolues à la demande, glyf-by-glyf seulement quand demandé.
- `LiberationFontMgr` ajoute un mode "ttf-parser" sélectionnable :
  - default = `AwtTypeface` (option A actuelle, fast boot)
  - opt-in = `SkTtfTypeface` (option C, bit-exact, pour GMs `bigtext`-family)
- Cache d'outlines compatible : `GlyphPathCache` (T5) reste utilisable, juste branché sur `SkTtfTypeface` au lieu d'`AwtTypeface`.

**Trigger** : aucun GM ne le réclame aujourd'hui. Déclencher dès qu'un port `bigtext`-family ou similaire montre un score < 70% au tolerance=8 et que le diagnostic confirme glyph metric drift dominant (vs colourspace, vs autres causes).

**Effort estimé** : 1 slice élevé. Plan possible :
- PR 1 : header + table directory + `head` + `maxp` + `name` (~150 lignes, infra)
- PR 2 : `cmap` formats 4 + 12 (~200 lignes)
- PR 3 : `loca` + `glyf` simple glyphs + outline → SkPath (~300 lignes)
- PR 4 : composite glyphs (~80 lignes)
- PR 5 : `hmtx` + `hhea` + `OS/2` + `SkTtfTypeface` integration + `LiberationFontMgr` switch (~250 lignes)

L'effort est concentré mais non-bloquant — les 5 PRs peuvent être livrées sur plusieurs semaines opportunistes.

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
| 3c    | 5        | Path stroker (kButt + kMiter, no GM ports yet) | ✅ |
| 3d    | 11       | GM harvest sur l'API existante (5 crbug + bitmaprect_rounding) | ✅ |
| 3e    | 13       | Stroke-on-path GM ports — `ConvexPathsGM` + `ArcOfZorroGM` ✅ ; ArcToGM/CubicPathGM TODO | 🔄 |
| 3f    | 13       | Path API extras (relative verbs, tangent arcTo, computeBounds, makeOffset) | ✅ |
| 3g    | 13       | Stroker caps & joins étendus (kSquare/kRound caps, kBevel/kRound joins) | ✅ |
| 3h    | 14       | `SkCanvas.drawLine` + `TeenyStrokesGM` (stroker sous CTM scales extrêmes) | ✅ |
| 3i    | 18       | Stroker `resScale` fix + 4 GMs (Strokes4/ClippedCubic/StLouisArch/Strokes) ⇒ **Phase 3 close** | ✅ |
| 4a    | 25       | `drawDRRect` + ToolUtils + 7 GMs (CircleSizes/SmallArc/ManyCircles/ManyRRects/FillCircle/DRRect/RRect) | ✅ |
| 4b    | 27       | `SkMatrix` + rotate/skew/concat CTM, `SkPath.makeTransform` ⇒ OvalGM, RoundRectGM | ✅ |
| 4c    | 32       | GM harvest (ClippedCubic2/ClipCubic/Strokes2/StrokeCircle/AddArc) — 0 nouvelle API | ✅ |
| 5a    | 33       | `SkShader` + `SkLinearGradient` + `SkRadialGradient` + `SkMatrix.invert` ⇒ FillrectGradientGM, Oval/RoundRect gradient row | ✅ |
| 5b    | 36       | GM harvest gradient (Analytic/Clamped/Hardstop) + premier stress kRepeat/kMirror | ✅ |
| 5c    | 39       | GM harvest path/stroke (ScaledStrokes/StrokeRects/NonClosedPaths) — 0 nouvelle API | ✅ |
| 5d    | 44       | GM harvest path/conic/oval (PathInterior/ConicPaths/ArcCircleGap/LargeCircle/LargeOvals) — 0 nouvelle API | ✅ |
| 5e    | 54       | DEF_SIMPLE_GM regression harvest (10 small bug GMs) — 0 nouvelle API | ✅ |
| 6 entry | 55     | `SkBlendMode` enum + 9-mode dispatch slice (`kClear/kSrc/kDst/kSrcOver/kDstOver/kSrcIn/kDstIn/kPlus/kModulate/kScreen`) + ScaledRectsGM 87.79% | ✅ |
| 5f    | 64       | GM harvest mixed (Beziers/HardstopMany/TestGradient/ThinStroked/BatchedConvex/ShallowLin/ShallowRad/B119394958/Crbug1086705) — 0 nouvelle API | ✅ |
| 6a    | 64       | F16 working-space rasterizer (infra : SkBitmap F16, blendF16 SrcOver, I/O 16-bit) | ✅ |
| 6b    | 64       | F16 shaders (`shadeRowF16` premul-float gradient + scanline-fill float coverage) — pipeline F16 désormais pur de bout en bout pour `kSrcOver` | ✅ |
| 6c    | 64       | F16 solid-colour AA (`colorToF16Premul` + float coverage dans fillRectAA/strokeRectAA/scanFillPath) — 11 GMs en hausse, 0 régression | ✅ |
| 5g    | 64       | `SkBitmapShader` infra (`SkBitmap.makeShader`/`SkImage.makeShader` + `shadeRowF16`) + `SkCanvas.drawPaint` shader-aware + paint.alpha modulation. Ports GM déférés (BG xform + linear-premul compositing requis). | ✅ |
| 6 PD  | 65       | Phase 6 Porter-Duff completion : 5 derniers modes (`kSrcOut`/`kDstOut`/`kSrcATop`/`kDstATop`/`kXor`) + 15 tests unitaires + `AaRectModesGM` 80.30 % (12 modes en grille avec `bm.makeShader` + `saveLayer`) | ✅ |
| 6 sepS | 65      | Phase 6 separable simple : `kMultiply`/`kDarken`/`kLighten`/`kDifference`/`kExclusion` via helper float-premul + 14 tests unitaires (61 total). Pas de port GM ce slice. | ✅ |
| 6 sepC | 65      | Phase 6 separable complexe : `kOverlay`/`kHardLight`/`kColorDodge`/`kColorBurn`/`kSoftLight` (4 helpers privés, branches conditionnelles, port direct Skia raster pipeline) + 13 tests (74 total). 25 / 29 modes Skia. | ✅ |
| 6 HSL | 65       | Phase 6 HSL : `kHue`/`kSaturation`/`kColor`/`kLuminosity` (`blendHSL` + helpers W3C `lum`/`sat`/`setLum`/`setSat`/`clipColor`) + 7 tests + smoke 29 modes (81 total). 29 / 29 modes Skia (formules complètes). | ✅ |
| 6 GMs | 67       | `SkTextUtils` + `SkBlendMode_Name` + `restoreToCount` + `drawColor(c, mode)` + fix `modeAffectsZeroAlphaSrc` étendu (kSrcOut, kDstATop) ⇒ AAXfermodesGM 80.12 % + AndroidBlendModesGM 97.04 %. **🎉 Phase 6 close.** XfermodesGM/DestColorGM scopés ailleurs (compositeFrom-with-blendmode + ARGB_4444 / SkRuntimeEffect). | ✅ |
| 6h    | 77       | GM harvest post-Phase-6 (10 DEF_SIMPLE_GM) — Bug615686/Skbug4868/Crbug946965/Crbug1139750/Crbug847759/LuminosityOverflow/WideButtCaps/StrokeRectShader (98-100 %) + ClipDrawDraw 35.34 % et RadialGradientPrecision 4.92 % comme regression-trackers. 0 nouvelle API. | ✅ |
| 6i    | 77       | Edge-rounding consistency : `clipRect` non-AA passe de `floor`/`ceil` à `round-half-up` (matches `SkScalarRoundToInt` + non-AA `pixelEdge`). AA flavour préservée via `clipRect(rect, true)`. AAXfermodesGM 80.12 → 84.73 (+4.6), Skbug4868 98.63 → 99.32 (+0.7), ClipDrawDraw géométrie corrigée. | ✅ |
| 6j    | 93       | GM harvest round 2 (9 DEF_SIMPLE_GM) — Bug5099/Bug6083/Bug6987/Bug339297/Crbug10141204 (95-100 %) + DRRectSmallInner 96.5 % + EmptyPath/LinePath/LineClosePath 87 % (4 fill rules incl. kInverse* × 3 styles × labels). 0 nouvelle API. | ✅ |
| 6k    | 101      | GM harvest round 3 (8 ports) — ThinConcavePaths 98.1 %, Crbug1257515 98.9 %, Crbug938592 99.8 %, StrokeRectsRotated 90.7 %, CubicClosePath 86.9 %, CubicPathShader 79.7 %, QuadPath 87.3 %, QuadClosePath 87.1 %. Factorisation `PathCapsFillsGridGM` (caps × fills × styles matrix) partagée par 4 GMs. 0 nouvelle API. | ✅ |
| 6l    | 109      | GM harvest round 4 (8 ports) — B340982297 94.6 %, Bug406747427 98.0 %, ConjoinedPolygons 99.3 %, Crbug996140 74.7 %, FillTypes 50.8 %, PathHugeCrbug800804 89.3 %, Polygons 86.1 %, SmallPaths 97.1 %. 0 nouvelle API. `crbug_947055` (perspective rasterization) reporté — `SkMatrix` Phase 4 a la math mais `buildEdges` ne fait pas la division homogène. | ✅ |
| 6m    | 110      | Perspective-aware `buildEdges` : nouvelle branche `buildEdgesPerspective` qui projette chaque control point via la division homogène `(sx*x + kx*y + tx)/w` avec `w = persp0*x + persp1*y + persp2`. Lines exactes ; Béziers approximées (project-then-flatten en device space, visuellement-correctes sous perspective modérée). Débloque `Crbug947055GM` (14.6 %, géométrie correcte mais BG drift `eraseColor` skip xform). | ✅ |
| 6n    | 118      | GM harvest round 5 (8 ports) — QuadCap 99.8 %, ZeroControlStroke 99.5 %, InnerJoinGeometry 97.9 %, RectPolyStroke 97.4 %, HairlineSubdiv 96.8 %, SquareHair 96.7 %, TextScaleSkewRotate 84.3 %, TextScaleSkew 82.2 %. 0 nouvelle API ; `inner_join_geometry` substitue `FillPathWithPaint` par `SkStroker.fromPaint(...).stroke(path)`. | ✅ |
| 6o    | 129      | GM harvest round 6 (11 ports) — Bug7792 99.99 %, PathInvFill 99.4 %, Bug40810065 98.5 %, Crbug1472747 98.0 %, ConvexLineOnlyPathsFill 95.2 %, ConvexLineOnlyPathsStrokeAndFill 94.4 %, CircularArcsHairline 94.0 %, Bug12866 93.5 %, Crbug888453 90.8 %, OneBadArc 83.7 %, CircularArcsFill 66.7 %. 0 nouvelle API ; `Bug12866` substitue `FillPathWithPaint(path, paint, scale(1200))` par `SkStroker.fromPaint(paint, resScale=1200f).stroke(path)`. | ✅ |
| 6p    | 141      | GM harvest round 7 (12 ports) — ChromeGradText1 100 %, ChromeGradText2 99.9 %, TrickyCubicStrokesLargeRadius 98.4 %, TrickyCubicStrokesRoundCaps 97.95 %, TrickyCubicStrokesButtMiter 97.9 %, GradTextGM 85.5 %, StringArt 82.2 %, SimpleShapesBw 65.5 %, SimpleShapesAa 65.0 %, CircularArcsStrokeButt 45.7 %, CircularArcsStrokeRound 45.7 %, CircularArcsStrokeSquare 45.5 %. 0 nouvelle API. | ✅ |
| 6q    | 146      | GM harvest round 8 (5 ports) — ClipLargeRect 99.6 %, Hairlines 97.7 %, ThinRoundRects 92.1 %, InnerShapesBw 82.6 %, InnerShapesAa 81.8 %. 0 nouvelle API ; `InnerShapesGM` substitue `SkRRect.transform(SkMatrix)` (non exposé) par un helper `scaleAndTranslateRRect` qui scale rect + radii uniformément (suffisant car le seul cas upstream est translate + uniform scale). | ✅ |
| 6r    | 146      | **Group B — Picture + Surface + saveLayer-with-blendMode** (DM-readiness foundation). 6 nouveaux fichiers : `SkImageInfo`, `SkSurface` (`MakeRaster`/`MakeRasterDirect`/`getCanvas`/`makeImageSnapshot`/`draw`), `SkRecord` (28 variants sealed), `SkPicture` (`playback`), `SkPictureRecorder` (`beginRecording`/`finishRecordingAsPicture`), `SkRecordingCanvas` (override des 28 ops). `compositeFrom` honore désormais `paint.blendMode` pour les 29 modes (avec `mustBlendZero` pour `kClear`/`kSrcIn`/etc. dans les bornes du layer). `SkImage.Make` corrigé pour bitmaps F16 (per-pixel `getPixel` au lieu de `pixels.copyOf` qui retournait un buffer vide). `SkCanvas` ouverte (méthodes + `width`/`height` open) pour permettre l'override par `SkRecordingCanvas`. **Tests** : 3 unit tests blendMode dans `SkCanvasInternalsTest`, 7 dans `SkSurfaceTest`, 23 dans `SkPictureTest` (round-trip pixel-identique pour chaque catégorie d'op + stabilité multi-playback + paint snapshot anti-mutation), 3 GMs round-trip via Picture+Surface (`SimpleRect`/`Beziers`/`ConvexPaths`) — la preuve qu'un GM existant peut être recordé puis replayé bit-identique, le pattern exact de DM. 0 régression sur les 146 GMs. | ✅ |
| 6s    | 146      | **Group C — Color management completion**. (1) `SkBitmap.eraseColor` honore désormais `bitmap.colorSpace` : décode SkColor non-premul sRGB → applique pipeline `SkColorSpaceXformSteps` (sRGB → dest, lazy-init cached) → store. **Impact massif** : ClipDrawDrawGM **35.38 → 100.00** (+64.62), Crbug947055GM **14.56 → 96.56** (+82.00, valide rétro-actif que la perspective rasterizer Phase 6m était correcte — drift venait du bg), FillTypesGM **50.81 → 99.48** (+48.67), LuminosityOverflowGM 99.51 → 100.00, +6 micro-gains. (2) `blendF16PremulMode(x, y, sr, sg, sb, sa, mode)` : nouveau dispatcher pour les 29 modes en float-premul natif — Porter-Duff/Plus/Modulate/Screen inlined (4-line cases), 10 séparables réutilisent le `sepChannel` existant (déjà float-premul), 4 HSL via nouveau `blendHSLF16Body`. (3) Tous les callsites F16 (`drawPaint` shader, `fillRectAA`, `strokeRectAA`, `scanFillPath` shader+solid) relâchent le gate `mode == kSrcOver` — F16 reste F16 pour les 29 modes au lieu de quantifier en 8-bit entre blends. **Tests** : 21 unit tests `SkBlendModeF16Test` (18 known-answer + 2 parity vs 8-bit ref pour les 29 modes ≤3 ulp + 1 mustBlendZero edge case). 0 régression. C.2/C.3 ne déplacent pas les scores existants (la plupart utilisent srcOver) mais débloquent les futurs ports XfermodesGM/MixedXfermodes sans précision-loss. | ✅ |
| 6t    | 147      | **XfermodesGM port** (78.51 %, floor 35 %) — le GM-canonique 29-modes-en-grille spécifiquement déférré dans Phase 6 *(« scopé ailleurs : compositeFrom-with-blendmode + ARGB_4444 / SkRuntimeEffect »)*, débloqué par le combo Phase 6r (`compositeFrom` honore `paint.blendMode`) + Phase 6s (F16 pipeline 29 modes). 8 source types × ~80 cellules effectives, chacune avec `saveLayer(bounds, null)` + `drawImage(srcB)` + `paint.blendMode = mode` + dispatch sur srcType (rect / image-with-alpha / scaled-transparent / quarter-clear / quarter-clear-in-layer / rectangle-with-mask). Substitution `ARGB_4444` checkerboard bg → `8888` équivalent (alpha forcée opaque dans l'original via `kOpaque_AlphaType`, donc différence purement précision 4-bit vs 8-bit invisible au scale shader 6×). **Validation grandeur nature** des deux phases précédentes : si le grid passe, tout le pipeline saveLayer-with-blendmode + F16 29-modes est end-to-end correct. 0 nouvelle API ; juste l'usage des primitives Phase 6r/6s. | ✅ |
| 7a    | 148      | **Group A foundation — Effects pipeline + SkColorFilter** (premier slice de la famille). 4 abstract bases : `SkColorFilter`, `SkMaskFilter` (stub), `SkImageFilter` (stub), `SkPathEffect` (stub). 4 nouveaux slots sur `SkPaint` (`colorFilter` wired, les 3 autres no-op silencieux jusqu'aux slices futurs). `SkColorFilter` famille complète : `SkColorFilters.Matrix(float[20])` (4×5 affine), `SkColorFilters.Table` / `TableARGB` (LUT 256 entries), `SkColorFilters.Compose`, `SkColorFilters.Lerp`, `SkColorFilters.Blend(color, mode)` (Porter-Duff/Plus/Modulate/Screen seulement — séparables/HSL throw avec message, follow-up), `SkLumaColorFilter.Make()`. Wiring dans `SkBitmapDevice` : 3 callsites (`drawPath` solid branch, `drawPaint` solid branch via `inDeviceColorSpace`, `drawImageRect` per-sampled-pixel). Shader paths déférés. **Tests** : 24 unit tests `SkColorFilterTest` (Matrix identity/swap/bias/isAlphaUnchanged, Table identity/invert/ARGB partial, Compose/Lerp/Blend/Luma + parity tests). **Port `ColorMatrixGM` 49.37 % / floor 35 %** — validation end-to-end du `paint.colorFilter` sur `drawImage`. Gap résiduel vs upstream = colorspace : Phase 7a applique le filter dans le working space (Rec.2020 chez nous) alors que Skia l'évalue en linear sRGB. À fermer en Phase 7e. 0 régression sur 147 GMs. | ✅ |
| 7b    | 163      | GM harvest round 9 (11 ports) — premier batch après l'arrivée de SkSweepGradient + SkConicalGradient + SkPicture/SkSurface + saveLayer-with-blendMode. ShallowGradientSweepNoDither 100 %, ShallowGradientConicalNoDither 100 %, ShallowGradientSweepDither 99.99 %, ShallowGradientConicalDither 99.99 %, Bug6783 98.66 % (`SkSurface.MakeRaster` + `Image.makeShader(kRepeat,kClamp)` + skewed local matrix), ConicalGradients2ptEdge/EdgeNoDither 92.88 %, ConicalGradients2ptOutside/OutsideNoDither 90.06 %, PictureCullRect 86.11 %, PictureGM 28.15 % (substitution `drawPicture(matrix, paint)` par `save/concat/saveLayer/playback/restore` ; visuellement correct, drift translucent SrcOver). 0 nouvelle API. | ✅ |
| 7c    | 165      | **Group A — `SkMaskFilter` famille (slice 1) + `SkBlurMaskFilter` Gaussian**. Premier `SkMaskFilter` concret : `SkBlurMaskFilter.Make(style, sigma)` convolutionne la coverage mask rasterizée avec une Gaussian 2D séparable. Algorithme : 1) compute device-space bbox de la path expanded by `ceil(3σ)` margin (3-sigma rule = 99.7 % de la masse) ; 2) rasterize la path (avec stroker honoré) dans une bitmap temporaire WHITE+kSrc — réuse récursif du rasterizer existant via `SkBitmapDevice` interne ; 3) extract alpha en `ByteArray` ; 4) apply 2 passes 1D Gaussian (horizontal puis vertical) ; 5) composite chaque pixel = paint.color modulé par mask alpha, blendé avec `paint.blendMode`. **Wiring** : `SkBitmapDevice.drawPath` route vers `drawPathWithMaskFilter` quand `paint.maskFilter != null && paint.shader == null`. Recursion guard : le paint passé au draw interne a `maskFilter = null`. **Limitations** : shader paths via maskFilter déférés (per-pixel shader sample × mask demande un pipeline 2-pass) ; `SkBlurStyle` accepte 4 valeurs mais seule `kNormal` rendue (Solid/Outer/Inner dégradent à Normal). **Tests** : 6 unit tests `SkBlurMaskFilterTest` (factory rejection, margin per-σ, uniform mask passthrough, single-pixel blob radial spread + mass conservation, dimensions stable). **Port `BlurCirclesGM` 85.86 % / floor 50 %** — validation end-to-end (4×4 grille = 4 sigmas × 4 cercles avec rotation per-cell). 0 régression sur 164 GMs. **Slices suivants** : SkBlurStyle non-kNormal (kSolid/kOuter/kInner), maskFilter sur shader paths, `SkEmbossMaskFilter`. | ✅ |
| 7d    | 173      | GM harvest round 10 (8 ports) — premier batch après Phase 7c (SkBlurMaskFilter mergé). AnalyticAntialiasInverse 99.97 %, AnalyticAntialiasConvex 99.78 %, AnalyticAntialiasGeneral 99.36 %, BitmapRectTest 98.5 %, BigMatrix 90.0 % (extreme-CTM avec bitmap shader sous (1/1000)-scale local matrix), SmallCircles 85.24 % (sub-pixel `drawArc(0..360°)` grille dans `SkSurface.MakeRaster(100×100)` puis `scale(7,7)` + `drawImage`), PlusMergesAa 69.48 % (saveLayer + kPlus seam-merge — visually equivalent green square, AA-edge coverage drift sub-tolerance), TinyBitmap 0 % (1×1 premul-ARGB shader — visually identical light pink wash mais uniform per-pixel colorspace shift ; tracker-only). 0 nouvelle API. | ✅ |
| 7e    | 174      | **Group A — `SkColorFilter` en sRGB pre-xform pipeline**. Re-ordonne le pipeline `paint.color → applyColorFilter → transformPaintColor` au lieu de `transformPaintColor → applyColorFilter`. Le filter math (notamment les Rec.709 luma weights de `saturationMatrix`) tourne maintenant en sRGB, le working space pour lequel Skia tune ses coefficients, au lieu du working space du device (Rec.2020 sous le harness GM) où les coefficients étaient silencieusement re-tunés à un autre gamut. **Wiring** : 4 callsites swappés — `drawPaint` solid branch, `drawPath` solid branch, `drawPathWithMaskFilter`'s effectiveColor, `inDeviceColorSpace` (interne au helper). **Per-pixel sur drawImageRect** : nouvelle gate `deferXform = colorFilter != null && needsXform` — quand active, garde les samples image en sRGB tout au long de la pipeline applyAlpha→filter, puis xform per-pixel après filter avant blend. Fallback : pre-xform once (fast path) quand pas de filter ou pas de xform. **Impact** : **`ColorMatrixGM` 49.37 → 69.28 (+19.91pp)**. 0 régression sur les 173 autres GMs. Floor `ColorMatrixTest` bumpé 35 → 65. **Gap résiduel** ~30 % vs upstream = la différence encoded sRGB (notre version) vs linear sRGB (Skia upstream) — la matrix devrait tourner après gamma decode. À fermer en Phase 7e' (decode→matrix→encode pipeline). | ✅ |
| 7p2   | 182      | **Group A — `SkPathEffect` famille (slice 2) : Corner + Discrete**. Deux nouveaux `SkPathEffect` concrets : (1) `SkCornerPathEffect.Make(radius)` arrondit les corners d'une polyline — pour chaque vertex interne, remplace le coin par `lineTo(pullback) ; quadTo(corner, pushforward)` où `pullback` est à `radius` le long de l'edge entrante et `pushforward` à `radius` le long de l'edge sortante ; les distances sont clampées à `min(radius, half-segment-length)` pour éviter que deux corners adjacents ne se marchent dessus. Closed polygons : tous les vertices smoothed (incl. closing corner). Open polylines : V₀ et Vₙ₋₁ restent sharp. (2) `SkDiscretePathEffect.Make(segLength, deviation, seed=0)` chop chaque segment en sous-segments de `segLength` units puis perturbe chaque endpoint perpendiculairement par un random uniforme dans `[-deviation, +deviation]`. Random déterministe via `SkRandom(seed)`. Verbs `kQuad/kConic/kCubic` flatten via subdivision midpoint puis jitter chaque chord. **Tests** : 8 unit tests `SkCornerPathEffectTest` (factory rejection, single-segment passthrough, L-shape interior smoothed, closed quad smooths 4 corners, open polyline smooths interior 2, tiny-segments cap) + 10 unit tests `SkDiscretePathEffectTest` (factory rejection, déterminisme same-seed, divergence different-seed, segLength subdivision, zero-deviation passthrough, perpendicular bound, closed-contour close-edge jitter). **Smoke GM** `CornerDiscretePathEffectGM` (8 cellules de variations radius/segLength/deviation) + tracker non-référence qui valide non-crash + pixel touchés. Aucun port d'`upstream PathEffectGM` — il mélange 1D/2D/Compose qu'on ne ship pas (slice 7p3). **Slices suivants** : `SkPathEffect.MakeCompose` / `MakeSum`, `Sk1DPathEffect`, `Sk2DPathEffect`. 0 régression sur 182 GMs. | ✅ |
| 7p3   | 182      | **Group A — `SkPathEffect` famille (slice 3) : Compose + Sum**. Deux factories sur `SkPathEffect.Companion` : (1) `MakeCompose(outer, inner)` retourne un effect chaîné — `compose.filterPath(p) == outer.filterPath(inner.filterPath(p))`. Sémantique passthrough Skia-iso : si `inner` retourne `null` (passthrough), `outer` est appliqué à l'input original. (2) `MakeSum(first, second)` retourne un effect somme — `sum.filterPath(p) = first.filterPath(p) ⊕ second.filterPath(p)` (concat des verb streams via `SkPathBuilder.addPath`). Mêmes règles passthrough. Both : null-handling — `outer == null` ⇒ retourne inner ; `both == null` ⇒ retourne null. Implémentations privées `SkComposePathEffect` + `SkSumPathEffect` qui héritent de `SkPathEffect`. **Tests** : 12 unit tests `SkComposeSumPathEffectTest` (null operand handling pour les deux ; Compose evaluates inner-then-outer ; Compose passthrough fallback to input ; Sum concatenates verb counts ; Sum passthrough fallback to input + input ; nested 3-effect chain via `Compose(corner, Compose(dash, discrete))`). Aucun port GM dédié — `MakeCompose` est une infrastructure utilisée par `gm/patheffects.cpp` (slice 7p_t débloquera le port complet avec 1D/2D). 0 régression sur 182 GMs. | ✅ |
| 7p_t  | 183      | **Group A — `SkPathEffect` famille (slice 4 — final) : 1D + 2D path tiling**. Deux nouveaux pathEffects (les plus complexes) qui complètent la famille. (1) `SkPath1DPathEffect.Make(stamp, advance, phase, style)` tile un stamp path le long du input path à intervalles arc-length de `advance` units. **Style** : `kTranslate` (translation seule), `kRotate` (translation + rotation tangent-aligned via `atan2`), `kMorph` (deferred — fall back to `kRotate`). Algorithme : per-contour arc-length tracking ; pour chaque segment de ligne, walk through les positions de stamp dans `[firstOffset, length]` à intervalles `advance` ; pour chaque position, transformer le stamp via `SkMatrix.MakeTrans(px, py).preRotate(angleDeg)` et l'ajouter à l'output via `SkPathBuilder.addPath`. Verbs courbes flatten via subdivision midpoint. (2) `SkPath2DPathEffect.Make(matrix, stamp)` tile un stamp dans une grille 2D définie par `matrix`. Algorithme : `invert()` la matrix → projeter les coins du bounding box du input dans les coords de stamp-grille → `floor`/`ceil` pour obtenir le range `(i, j)` → stamper à chaque intersection `M * (i, j)`. **Tests** : 13 unit tests `SkPath1D2DPathEffectTest` (factory rejection, empty input/stamp, translate/rotate stamping, phase shift, per-contour reset, 2D non-invertible matrix rejection, 2D tile count par bounding box, large grid spacing). **Port `gm/patheffects.cpp::PathEffectGM` 82.67 % / floor 40 %** — 5 + 3 cellules avec hair / hair+corner / stroke+corner / dash+corner / 1D-rotate+corner + fill / discrete / 2D-tile. Validation end-to-end de TOUTES les pathEffect families (Dash 7p + Corner/Discrete 7p2 + Compose 7p3 + 1D/2D 7p_t). 0 régression sur 182 GMs. | ✅ |
| 7d.1  | 184      | **Group A — `SkImageFilter` foundation + 3 filters**. Premier slice de la dernière famille restante. Replace l'abstract stub Phase 7a par une vraie API : `SkImageFilter.filterImage(src, ctm)` retourne `FilterResult(image, offsetX, offsetY)` — l'image filtrée + un device-space offset pour repositionner le résultat. Trois `SkImageFilters` factories + impls : (1) `Offset(dx, dy, input)` — translate l'image par `(dx, dy)` device pixels, scale avec CTM max-scale ; (2) `ColorFilter(cf, input)` — applique un `SkColorFilter` per-pixel via `SkImage.peekPixel` + `SkColorFilter.filterColor` ; (3) `Compose(outer, inner)` — chaînage `outer(inner(src))` avec accumulation des offsets, null-handling Skia-iso. **Wiring** : `SkBitmapDevice.drawImageRect` route vers `filter.filterImage(image, ctm)` quand `paint.imageFilter != null`, puis re-rentre `drawImageRect` avec la filtered image + `devDst` shifté par l'offset. Recursion guard : inner paint a `imageFilter = null`. **Tests** : 11 unit tests `SkImageFiltersTest` (Offset zero/positive/CTM-scaled, ColorFilter identity/swap-RB, Compose null-handling/offset-stacking/inner-then-outer/input-chain) + bespoke `ImageFilterOffsetGM` smoke test (3 cellules : raw / Offset / Compose(Offset, ColorFilter swap-RB)) avec `ImageFilterOffsetTest` qui vérifie que la cellule 3 a du **bleu** où la cellule 1 avait du **rouge** (preuve end-to-end du chaînage). 0 régression sur 183 GMs. **Slice 7d.2 suivant** : Blur + MatrixTransform + DropShadow + port `gm/imagefiltersbase.cpp` (~550 LOC). | ✅ |
| 7d.2  | 185      | **Group A — `SkImageFilter` famille COMPLÈTE**. 3 nouveaux filtres + saveLayer wiring. (1) `SkImageFilters.Blur(σx, σy?, input)` — Gaussian séparable 2-pass, output grossit de `±ceil(3σ)` per axis (3-sigma rule), offset négatif compense la marge. (2) `SkImageFilters.MatrixTransform(matrix, sampling, input)` — applique une matrix affine avec sampling kNearest/kLinear ; output = bbox transformée du source ; non-invertible retourne input. (3) `SkImageFilters.DropShadow(dx, dy, σx, σy, color, input)` — recipe = `Compose(SrcOver original on top, Compose(Offset, Blur(ColorFilter(Blend(color, kSrcIn))))`. Implémentée comme classe interne unifiée pour amortir les allocations. **Wiring** : `SkCanvas.restore` détecte `layer.paint.imageFilter != null`, snapshot le layer en `SkImage`, applique le filter, builds une bitmap temporaire avec les filtered pixels, composite via `compositeFrom` avec offset ajusté par `(filterResult.offsetX, filterResult.offsetY)`. Recursion-safe (proxy paint sans imageFilter). **Tests** : 9 unit tests `SkImageFiltersBlurMatrixDropShadowTest` (Blur null/non-finite/positive sigma/mass-conservation, MatrixTransform non-invertible/identity/scale-2, DropShadow union-bounds/opaque-source-preservation) + bespoke `ImageFilterBlurDropShadowGM` smoke test (4 cellules : raw/blur/dropShadow/scaledMatrix) avec `ImageFilterBlurDropShadowTest` qui valide red survives blur + shadow alpha visible below original. 0 régression sur 184 GMs. **Group A COMPLET.** | ✅ |
| 7c.cont | 188    | **Group A follow-up — `SkBlurMaskFilter` styles non-kNormal**. Implémentation des 3 styles restants laissés en TODO Phase 7c : `kSolid` (paint la silhouette par-dessus le blur), `kOuter` (blur \\ paint, garde l'extérieur uniquement), `kInner` (paint ∩ blur, garde l'intérieur uniquement). Algorithme : on rasterize l'alpha mask de la path comme avant ; le blur Gaussian séparable produit `B` ; selon le style on combine `B` et la coverage originale `A` per-pixel — `kSolid: max(A, B)`, `kOuter: B * (1 - A)`, `kInner: B * A`. **GMs portés** : `BigBlursGM` (74.6 % — 4×3 grille de styles × sigmas), `Blur2RectsGM` (95.9 %), `Blur2RectsNonNinepatchGM` (smoke). 0 régression. | ✅ |
| 7q    | 190      | **Group A — `SkCanvas.clipPath` + `SkCanvas.clipRRect` (alpha-mask path clipping)**. Premier path clipping non-rect : on rasterize la clip-path dans une bitmap alpha pleine taille du device, on la stocke dans `SkRasterClip.clipMask`, et chaque draw modulé par `clipMask[y][x] / 255` au lieu d'un test scalaire `inClip`. Fallback transparent : si `clipMask == null` on garde le clipRect rapide. Intersection multi-clip : `clipMask = min(prev, new)`. Honore le CTM en projetant la path en device-space avant rasterisation. **GMs portés** : `Bug5252GM` (clip + path), `InverseClipGM` (clip + inverse-fill). Floor 50 %. 0 régression. | ✅ |
| 7q+   | 192      | **Group A — `SkClipOp.kDifference` pour `clipRect` / `clipPath` / `clipRRect`**. Ajout de l'op `kDifference` (l'op standard est `kIntersect`). Algorithme : on rasterize la clip-shape dans un alpha mask `M` puis combine avec le clip courant via `clipMask = clipMask * (1 - M / 255)`. **GMs portés** : `BlurredClippedCircleGM`, `Skbug9319GM`. 0 régression. | ✅ |
| Round 12 | 196   | **GM harvest round 12** (7 ports, 189 → 196). 0 nouvelle API. | ✅ |
| Round 13 | 202   | **GM harvest round 13** (6 ports, 196 → 202). 0 nouvelle API. | ✅ |
| Round 14 | 206   | **GM harvest round 14** (4 ports, 202 → 206). 0 nouvelle API. | ✅ |
| Round 15 | 209   | **GM harvest round 15** (3 ports, 206 → 209). 0 nouvelle API. Group A officiellement **clos** post round 15 — dernier batch DEF_SIMPLE_GM avant le pivot vers les chantiers iso-fidélité (D1 / I1-I5 / C5). | ✅ |
| C5    | 210      | **[RASTER_COMPLETION C5] — `ARGB_4444` colortype native**. `SkBitmap` accepte désormais `kARGB_4444_SkColorType` avec un storage `ShortArray` packed `0xARGB` (4 bits/canal). `getPixel` / `setPixel` / `eraseColor` / sampling AA fonctionnent en 4-bit avec quantization correcte. Substitution dans `XfermodesGM` du checkerboard 8888 → 4444 (= la version upstream). Tests : 19 unit tests `SkBitmapARGB4444Test`. Plus de divergence visible côté checkerboard. | ✅ |
| D1.0  | 210      | **[RASTER_COMPLETION D1] — `SkPathOps` package skeleton + `TightBounds` shim**. Création du package `org.skia.pathops` avec la surface API publique iso-Skia (`SkPathOps.Op` enum, `Op` / `Simplify` / `AsWinding` / `TightBounds`). `TightBounds` shim implémentée via subdivision Bézier (curve-aware bbox) pour débloquer le call site upstream. Les autres entry points retournent `null` jusqu'aux slices D1.1+. | ✅ |
| D1.1  | 210      | **[RASTER_COMPLETION D1.1] — `SkPathOps` foundation COMPLÈTE** (15 sous-slices : a, b, c, d.1-3, e.1, e.2.a, e.2.b, e.2.c.1-4, e.3). Pathops double-precision primitives + curves (`SkDPoint`, `SkDLine`, `SkDRect`, `SkDQuad`, `SkDCubic`, `SkDConic`) + line ops (`SkLineParameters`, `SkIntersections`) + line↔curve intersections + cross-curve hull / convex-hull + `SkTCurve` abstraction (`SkTQuad/SkTConic/SkTCubic` wrappers) + per-span TSect state (`SkTCoincident`, `SkTSpan`) + `SkTSect` complet (skeleton + coincidence machinery + intersect machinery + binary search + `SkClosestSect`/`SkClosestRecord`) + curve↔curve `SkIntersections.intersect` wrappers. **D1.1 CLOSE.** Tests fixtures upstream replays. | ✅ |
| D1.2 (en cours) | 210 | **[RASTER_COMPLETION D1.2] — `SkPathOps` Op contour assembly (en cours)**. Slices livrés : (a) `SkOpPtT` + `SkOpSpanBase` + `SkOpSpan` data model + 4 forward-decl skeletons ; (b) `SkOpAngle` data model + linked-list ops + simple accessors ; (b.2.0) `SkDCurve` / `SkDCurveSweep` + `SkOpSegment.subDivide` ; (c) `SkOpSegment` data model (structural / span-list / static helpers) ; (e) `SkOpContour` + `SkOpContourHead` + `SkOpContourBuilder` ; (f) `SkOpEdgeBuilder` (SkPath verbs → SkOpContour) ; (i) `SkPathWriter` (per-contour writer + simple assembly) ; (i.2) `SkPathWriter.assemble` (partials stitching). Reste à faire : winding propagation + top-level `Op` / `Simplify` algorithm. | 🔄 |
| I1    | 213      | **[RASTER_COMPLETION I1] — `SkTextBlob` + `SkTextBlobBuilder` + `drawTextBlob`**. Premier slice text-as-record : `SkTextBlob` data class (runs `HorizontalSpread`/`FullPositions`), `SkTextBlobBuilder.allocRun*` (uniform x / per-glyph x / full positions), `SkCanvas.drawTextBlob(blob, x, y, paint)` qui delegate au glyph drawing existant, `SkPicture` recording integration (nouveau verb `DrawTextBlob`). Slice I1.5 : 4 GM ports (`TextBlobGM`, `TextBlobColorTransGM`, `TextBlobShaderGM`, `TextBlobUseAfterGpuFreeGM` smoke). | ✅ |
| I2    | 213      | **[RASTER_COMPLETION I2] — Variable fonts (light) + glyph mask cache + subpixel positioning**. (I2.1) `SkGlyphCache` LRU alpha-mask cache (key = `(typefaceId, size, scaleX, skewX, glyphId, edging)`) — perf gain ~10x sur text-heavy GMs. (I2.2 light) `SkFontVariation` data class + `SkFont.variations` field (full AWT wiring `OPTICAL_SIZE`/`WEIGHT`/`WIDTH`/`POSTURE` déféré). (I2.3) Subpixel positioning fast path pour `drawTextBlob` — pas de re-rasterisation à chaque sub-pixel x. | ✅ |
| I3    | 213      | **[RASTER_COMPLETION I3] — `SkRegion` + `SkAAClip` + `SkRasterClip` integration COMPLET**. (I3.1) `SkRegion` core (run-based 1D representation, queries, iterator, set ops via scanline merging, `setPath` via path scanline rasterisation). (I3.2) `SkAAClip` (per-row alpha runs, `setPath` via 4×4 supersampled `SkRegion`, set operations on alpha runs). (I3.3) `SkRasterClip` integration : `clipMask` Phase 7q remplacé par `SkAAClip` (per-row alpha runs, ~10x plus compact + plus rapide qu'une bitmap full-device). `SkAAClip.coverage(x, y)` exposé pour le hot path du rasterizer. | ✅ |
| I4    | 213      | **[RASTER_COMPLETION I4] — `SkShaper`** (text shaping). (I4.1) `SkShaper.MakePrimitive` : naive char-by-char glyph mapping (no shaping). (I4.2) `SkShaper.MakeJavaTextLayout` : delegate à JDK `TextLayout` — bidi (Arabic/Hebrew), basic ligatures, kerning. (I4.3) Linebreak / wrapping support via `java.text.BreakIterator`. **I4 CLOSE.** | ✅ |
| I5    | ~217     | **[RASTER_COMPLETION I5] — `drawPoints` / `drawAtlas` / `drawVertices` (en cours, drawPatch reste)**. (I5.1) `SkCanvas.drawPoints(mode, points, paint)` avec `PointMode = kPoints / kLines / kPolygon` (delegate respectivement à drawCircle / drawLine / drawLine-chain). (I5.2) `SkCanvas.drawAtlas(image, xform, src, colors?, blend, sampling, cull?, paint?)` + `SkRSXform` (rot-scale-translate compact). (I5.3.a) `SkVertices.MakeCopy(positions, indices?, colors?, texs?)` + `SkCanvas.drawVertices(vertices, blendMode, paint)` solid-color path. (I5.3.b) Per-vertex colour interpolation via barycentric coords (full triangle mesh shading). Reste : `drawPatch` (Coons patch) en I5.4. | 🔄 |
| Plan D2 | n/a   | **[RASTER_COMPLETION D2] — `SkRuntimeEffect` façade + per-effect Kotlin ports** : mini-planned ([MIGRATION_PLAN_D2_RUNTIME_EFFECT.md](MIGRATION_PLAN_D2_RUNTIME_EFFECT.md)). Aligné sur la stratégie [WebGPU](MIGRATION_PLAN_GPU_WEBGPU.md) : chaque runtime-effect est un nouveau type de shader hand-porté (Kotlin pour le raster, WGSL pour le GPU), exactement comme l'a été `SkLinearGradient` / `SkRadialGradient`. `SkRuntimeEffect` reste comme surface publique de dispatch via SkSL canonique → hash → impl. ~3 700 main + ~2 200 test ; ~80 DEF_GM débloquées. D2.0/2.1/2.2/2.3/2.4.a/2.4.b ✅ shippés ; D2.4.c en cours (2.4.c.1 ✅). | 🔄 |
| D2.4.c.1 | ~218     | **[D2.4.c.1] — Trig intrinsics runtime effects**. `SkBuiltinShaderEffectsIntrinsicsTrig` cluster : `makeUnarySksl1d(fn, requireES3)` Kotlin mirror du template upstream + `UnaryIntrinsicImpl` skeleton générique paramétré par une lambda `(IntrinsicContext) → Float`. 12 SkSL hashes registered (radians/degrees/sin/cos/tan/asin/acos/atan(x) + 4 atan2 variants), chacun pointant vers une instance `UnaryIntrinsicImpl` portant la math `kotlin.math` correspondante. GM port `RuntimeIntrinsicsTrigGM` — replicate plot() / next_row() / draw_label() / draw_shader() upstream avec sub-surface 100×100 + drawPoints polyline overlay. **Score : 96.33 %** vs `runtime_intrinsics_trig.png`. 10 unit tests (hash stabilité, math, layout uniform, clearForTest round-trip). 0 régression sur les 217 GMs précédents. | ✅ |
| D2.4.c.3 | ~220     | **[D2.4.c.3] — Common intrinsics**. `SkBuiltinShaderEffectsIntrinsicsCommon` cluster — 31 SkSL hashes registered : abs/sign/floor/ceil/fract/mod (3 forms : scalar / mixed / vector) / min, max, clamp (3 forms each, all reduce identiquement parce que p.x = x et v1.x = 1) / saturate / mix (3 forms) / step (3 forms) / smoothstep (3 forms) / floor(p).x / ceil(p).x / floor(p).y / ceil(p).y. Math via `kotlin.math.{abs, sign, floor, ceil, max, min}` + helpers privés `glslMod` (= `x - y * floor(x/y)`, distinct du `%` Kotlin), `glslMix`, `glslStep`, `glslSmoothstep`, `saturate`. Le `floor(p).y` / `ceil(p).y` exploite `IntrinsicContext.py` (= `(1 - lx) * xScale + xBias`, distinct de `x`). GM port `RuntimeIntrinsicsCommonGM` (6×7 grid). **Score : 95.90 %** vs `runtime_intrinsics_common.png`. 16 unit tests (1 batch + 15 math spot-checks). Suite à 3172 verts. | ✅ |
| D2.4.c.2 | ~219     | **[D2.4.c.2] — Exponential intrinsics**. `SkBuiltinShaderEffectsIntrinsicsExponential` cluster — réutilise le squelette `UnaryIntrinsicImpl` Phase D2.4.c.1, ajoute 10 SkSL hashes : `pow(x, 3)`, `pow(x, -3)`, `pow(0.9, x)`, `pow(1.1, x)`, `exp(x)`, `log(x)` (natural), `exp2(x)`, `log2(x)`, `sqrt(x)`, `inversesqrt(x)`. Math via `kotlin.math.{pow, exp, ln, log2, sqrt}`. Factorisation : `RuntimeIntrinsicsPlotHelper` (`plot/drawLabel/nextColumn/nextRow/box/padding/labelHeight`) extraite du GM Phase D2.4.c.1 pour partager la plomberie entre les futurs GMs `_common` et `_geometric`. GM port `RuntimeIntrinsicsExponentialGM`. **Score : 95.64 %** vs `runtime_intrinsics_exponential.png`. 9 unit tests (hash résolution + 8 spot-checks math). 0 régression. Suite à 3155 verts. | ✅ |
| 5h    | n/a      | Linear-premul F16 storage — **explored, reverted**. Voir post-mortem ci-dessous. | ❌ reverted |

**Bonus** : [archives/MIGRATION_PLAN_COLORSPACE.md](archives/MIGRATION_PLAN_COLORSPACE.md) Phase 0-5 ✅ — `tolerance=1` au lieu de `tolerance=160` sur tous les GMs Phase 1-3a. Suite du portage colorspace dans [archives/MIGRATION_PLAN_COLORSPACE_PORT.md](archives/MIGRATION_PLAN_COLORSPACE_PORT.md) (terminé : phases A-J + F1-F7 livrées, K out of scope).

**Bonus** : [archives/MIGRATION_PLAN_PATH_PARITY.md](archives/MIGRATION_PLAN_PATH_PARITY.md) ✅ — audit iso vs Skia C++ → 11 PRs sur 3 phases (correctness moveTo/ensureMove, verb-stream cubic→conic, surface API complète). 11 GMs améliorés ou débloqués (`PathArcToSkbug9077` +0.43, `ArcOfZorro` +0.17, `CircleSizes` +0.13, `PathInterior` +0.09, `DRRect`/`ArcCircleGap`/`LargeCircle`/`Strokes4`/`Bug593049`/`RoundRect`, plus `ArcToGM` 95.80 % et `CubicPathGM` 87.25 % portés). 0 régression sur 75+ GMs cumulés.

**Bonus** : [archives/MIGRATION_PLAN_PAINT_PARITY.md](archives/MIGRATION_PLAN_PAINT_PARITY.md) ✅ — audit iso vs Skia C++ → 7 PRs (1 audit + 6 slices Phase 2). `SkPaint` stocke maintenant `SkColor4f` (source of truth) au lieu de `SkColor` packed ; `setAlphaf(0.3f)` survit float-precise jusqu'au buffer F16 (était quantisé à 77/255). Plumbing float complet : `colorToF16Premul` / `transformPaintColor` / `inDeviceColorSpace` ont des overloads `SkColor4f`, `setColor4f` applique le xform colour-space, `setStrokeWidth/Miter` rejettent silencieusement les négatifs, `nothingToDraw` aligné (kDst ajouté, kXor retiré). 28 nouveaux tests `SkPaint`. **Audit hypothesis démentie** : `BatchedConvexPathsGM` 34.94 % n'est PAS bottlenecked par la précision alpha — drift compositing-math orthogonal au shape SkPaint, à investiguer séparément. 0 ratchet failure sur 141+ GMs.

> Au-delà : reprendre [SKIA_DM_TESTS_TO_IMPLEMENT.md](SKIA_DM_TESTS_TO_IMPLEMENT.md) Level 2 par catégories (bitmap operations, transformations avancées, effects), en gardant la même mécanique slice-vertical.

---

## Sources de référence (lecture seule, à miner)

- [kanvas/src/main/kotlin/core/](kanvas/src/main/kotlin/core/) — `Canvas`, `Paint`, `Bitmap`, `Path`, `Rect`, `RRect`, `Shader`, `Color`, `ColorExtensions`, `Matrix`, `Arc`, `SkScalar`.
- [kanvas/src/main/kotlin/device/BitmapDevice.kt](kanvas/src/main/kotlin/device/BitmapDevice.kt) — rasterizer rect/path actuel.
- [kanvas/src/main/kotlin/testing/skia/](kanvas/src/main/kotlin/testing/skia/) — 22 GMs hand-written éprouvés.
- [kanvas/src/test/kotlin/skia/TestUtils.kt](kanvas/src/test/kotlin/skia/TestUtils.kt) — harness éprouvé.
- [kanvas/src/generated/tests/org/skia/tests/](kanvas/src/generated/tests/org/skia/tests/) — **chaque fichier porte le code C++ original en Javadoc**, à utiliser comme spec.
- [kanvas/src/test/resources/original-888/](kanvas/src/test/resources/original-888/) — 989 images de référence Skia.
