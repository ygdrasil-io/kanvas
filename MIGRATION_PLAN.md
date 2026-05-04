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

### Phase 3e — GM ports stroke-on-path (en cours)

- [x] Hand-port `tests/ConvexPathsGM.kt` (fill seulement, 35+ paths). L'entry skbug.40040207 utilise pure scale + translate dans sa matrice — appliquée inline au build du path (pas besoin de `SkPath.transform(SkMatrix)` encore). **Score : 99.68%** à `tolerance=1`. Tous les verbs (line/quad/conic/cubic/arc), toutes les factories (Rect/Circle/Oval/RRect/Line/Polygon), 4096-point polyline, paths dégénérés (point line/quad/cubic, moveTo-only).
- [x] Hand-port `tests/ArcOfZorroGM.kt` (200 stroked open arcs avec width=35, layout boustrophedon, BG `0xCCCCCC`). Premier vrai stress du stroker (Phase 3c) sur des courbes : chaque arc est 1-2 cubic Béziers stroked en bande de 35 px. Ajout de `SkCanvas.drawArc(rect, startAngleDeg, sweepAngleDeg, useCenter, paint)` (path = arcTo + optional moveTo-to-centre + close pour pie slice). **Score : 99.56%** à `tolerance=1`. Bonus learning : un BG color non-trivial nécessite `drawPaint` au début (eraseColor skip le colorspace transform — voir le commentaire dans `TestUtils.runGmTest`).
- [ ] Hand-port `tests/ArcToGM.kt` (nécessite `arcTo(p1, p2, radius)` ✅ Phase 3f + variant SVG endpoint, encore manquant).
- [ ] Hand-port `tests/CubicPathGM.kt` (nécessite caps/joins étendus, fill rules inverses, drawString — déféré).

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
| 3c    | 5        | Path stroker (kButt + kMiter, no GM ports yet) | ✅ |
| 3d    | 11       | GM harvest sur l'API existante (5 crbug + bitmaprect_rounding) | ✅ |
| 3e    | 13       | Stroke-on-path GM ports — `ConvexPathsGM` + `ArcOfZorroGM` ✅ ; ArcToGM/CubicPathGM TODO | 🔄 |
| 3f    | 13       | Path API extras (relative verbs, tangent arcTo, computeBounds, makeOffset) | ✅ |
| 3g    | 13       | Stroker caps & joins étendus (kSquare/kRound caps, kBevel/kRound joins) | ✅ |
| 3h    | 14       | `SkCanvas.drawLine` + `TeenyStrokesGM` (stroker sous CTM scales extrêmes) | ✅ |
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
