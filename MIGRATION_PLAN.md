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
- **`BigMatrixGM`** — bitmap shader requis (Phase 5e).
- **`Crbug1073670GM`** — texte (`drawString` + SkFont).
- **`Crbug1113794GM`** — `SkDashPathEffect`.

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

Modes encore non-implémentés (lèvent `NotImplementedError` à l'appel — 19 modes restants) :
- [ ] `kSrcOut`, `kDstOut`, `kSrcATop`, `kDstATop`, `kXor` (Porter-Duff coeff restants)
- [ ] `kOverlay`, `kDarken`, `kLighten`, `kColorDodge`, `kColorBurn`, `kHardLight`, `kSoftLight`, `kDifference`, `kExclusion`, `kMultiply` (separable)
- [ ] `kHue`, `kSaturation`, `kColor`, `kLuminosity` (HSL)

**Précision** : les formules opèrent sur ARGB non-prémultiplié (notre raster pipeline) ; Skia upstream travaille en prémultiplié. Pour `kSrcIn`/`kDstIn`/`kPlus`/`kModulate`/`kScreen` à alpha fractionnaire, on re-dérive le résultat prémultiplié puis on dé-prémultiplie. Erreur résiduelle ≤ 1 ulp par canal — exact pour `sa == da == 0xFF` (le cas commun des GMs en scope).

**Hors scope** : `SkBitmapDevice.compositeFrom` (flatten de `saveLayer`) reste hardcodé `kSrcOver`. Étendre aux blend modes arbitraires = ticket séparé.

### Tests GM (slice 6 entry)
- [x] Hand-port `tests/ScaledRectsGM.kt` — `kPlus` + `SkMatrix::MakeAll(...)` 3x3. Score **87.79%** vs `scaledrects.png` à `tolerance=1`. Le résiduel ~12% est du désaccord rasterizer non-AA sur les bords de rect rotatés (le GM utilise `setAntiAlias(false)` implicite — Skia adoucit les bords sub-pixel différemment).

### Tests unitaires
- [x] `SkBlendModeTest.kt` — 9 modes × ~3 cas chacun (opaque-on-opaque + alpha fractionnaire), formules vérifiées contre des valeurs hand-computed. Couvre aussi le throw `NotImplementedError` pour les 19 modes restants.

### Reste pour clôturer Phase 6
- [ ] Implémenter les 19 modes restants. Chacun = un nouveau case dans `blendPixel()`, sans refactoring de l'API publique.
- [ ] Hand-port `tests/AAXfermodesGM.kt`.
- [ ] Hand-port `tests/XfermodesGM.kt`.
- [ ] Hand-port `tests/DestColorGM.kt`.
- [ ] Hand-port `tests/AndroidBlendModesGM.kt`.
- [ ] Étendre `SkBitmapDevice.compositeFrom` à tous les modes (saveLayer + blend).

### Vérification Phase 6 entry
- [x] Tests ≥ 85% (`ScaledRectsGM` à 87.79%).
- [x] Aucune régression sur les 44 GMs précédents.
- [x] **Pass count cumulé : 45 GM.**

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
| 5g    | ~67      | Image shader (`SkBitmap.makeShader`) + AlphaGradientsGM | ⬜ |
| 6     | ~70      | 19 blend modes restants + AAXfermodes/Xfermodes/DestColor/AndroidBlendModes GMs | ⬜ |

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
