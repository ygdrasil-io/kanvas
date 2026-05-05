# Migration plan — `SkPath` / `SkPathBuilder` parity

> **Status** : ✅ **closed** (archived). 11 PRs delivered, 0 regression
> across 75+ GMs, 11 GMs improved or unblocked. See the *Closeout
> recap* section below.

Original scope = aligner le port Kotlin avec `include/core/SkPath.h`,
`include/core/SkPathBuilder.h`, `src/core/SkPathBuilder.cpp` de Skia 4.x.

## Goal

Ramener `org.skia.foundation.SkPath` et `SkPathBuilder` à un comportement
observable identique à upstream pour les opérations utilisées par les GMs
portées (Phase 4 actuelles + GMs à venir). On ne vise **pas** la parité
bit-à-bit de la sérialisation interne — on vise :

1. les bugs visibles à l'œil (verbe stream qui produit un mauvais rendu),
2. la sémantique des helpers que le code C++ porté appelle effectivement.

## Phases

### Phase 1 — bug fixes (impact rendu direct)

Petite, isolée, sans dépendance rasterizer.

| # | Divergence | Fix | Test à ajouter |
|---|------------|-----|----------------|
| 1.1 | `moveTo` consécutifs append au lieu de remplacer | si dernier verbe = `kMove`, remplacer le dernier point | `moveTo après moveTo collapse` |
| 1.2 | Après `close()`, `ensureContour` émet `moveTo(0,0)` au lieu du dernier point de `kMove` | track `lastMoveX/Y`, l'utiliser dans `ensureContour` quand le dernier verbe est `kClose` | `lineTo après close repart du dernier moveTo`, et le GM `path_arcto_skbug_9077` (déjà en place) |

**Référence Skia** : `SkPathBuilder.cpp:136-156` (moveTo collapse),
`SkPathBuilder.h:1011-1018` (ensureMove après kClose).

**Impact GM attendu** :
- `PathArcToSkbug9077GM` est *literally* un test de régression pour 1.2.
  Le port actuel rend incorrectement (l'`arcTo` après `close` part de (0,0)
  au lieu du `moveTo` initial). Score ratchet actuel : 97.96 %. Le fix
  devrait améliorer ou laisser stable la similarité, jamais la dégrader.
- Aucun autre GM Phase 4 ne combine `moveTo`-doublon ou `lineTo`-après-`close`,
  d'après un grep manuel des sources.
- Si **un** GM régresse, on le note dans la PR — la cause sera nécessairement
  un `close()` suivi d'un verbe non-`moveTo` qu'on ne voyait pas.

### Phase 2 — alignement verb-stream (✅ implémenté)

Le rasterizer (`SkBitmapDevice.buildEdges`) et le stroker (`SkStroker`)
gèrent `kConic`, donc le passage de `addOval` / `addRRect` / `arcTo` en
conics se fait sans toucher au pipeline de rendu. Conic = représentation
**exacte** de l'ellipse, vs ~0.027 % de chord error pour l'approximation
kappa cubique.

Tous les sous-points implémentés :

1. ✅ `addOval(rect, dir)` → `kMove + 4 × kConic + kClose`, weight `√2/2`,
   contrôles aux coins de la bbox, ends aux cardinaux de l'oval. Mirrors
   `src/core/SkPathRawShapes.cpp:48-86`.
2. ✅ `addCircle` — délègue à `addOval`, aucun changement direct.
3. ✅ `addRRect` → `kMove + (kLine, kConic) × 4 + kClose`, weight `√2/2`
   aux coins. Préserve le `startIndex = 0` du port (différent du `6/7`
   de Skia 4.x — divergence reportée en Phase 3).
4. ✅ `arcTo(rect, start, sweep, forceMoveTo)` / `addArc` → conics par
   sous-arc ≤ 90°, weight = `cos(θ/2)`, contrôle = intersection des
   tangentes (formule unit-circle scaled).
5. ✅ `arcTo(p1, p2, r)` PostScript → 1 lineTo + 1 conicTo, algorithme
   Skia exact (cf. `src/core/SkPathBuilder.cpp:477-511`). `weight =
   sqrt(0.5 + 0.5·cosh)`.

**Impact GM mesuré** : 10 GMs améliorent, 0 régresse. Diff complet :

| GM | Avant | Après | Δ |
|---|---|---|---|
| ArcOfZorroGM | 99.562 | 99.732 | +0.170 |
| CircleSizesGM | 94.495 | 94.629 | +0.134 |
| PathInteriorGM | 98.532 | 98.618 | +0.086 |
| ArcCircleGapGM | 98.995 | 99.029 | +0.034 |
| DRRectGM | 98.489 | 98.520 | +0.031 |
| LargeCircleGM | 99.053 | 99.083 | +0.030 |
| Strokes4GM | 99.964 | 99.986 | +0.022 |
| Bug593049GM | 99.924 | 99.930 | +0.006 |
| RoundRectGM | 96.264 | 96.270 | +0.006 |
| PathArcToSkbug9077GM | 98.388 | 98.390 | +0.002 |

Suite complète `:kanvas-skia:test` : **627 tests, 0 failure**.

Tests unitaires mis à jour pour les nouveaux verb streams :
- `addOval emits 4 conic Bezier arcs with weight sqrt2 over 2`
- `conic addOval lands cardinal points exactly on the ellipse`
- `addCircle delegates to addOval centred on the given point` (assert
  `kConic` au lieu de `kCubic`)
- `simple rrect emits moveTo plus 4 line-conic pairs plus close`
- `simple rrect CCW starts at the same point as CW` (CCW first verb =
  `kConic`)
- `rrect conics carry the bbox corner as control and the next edge
  cardinal as end`
- `complex rrect with per-corner radii uses each corner's own radii`
  (indices recalculés pour conic = 4 floats)
- `addArc emits conic segments matching the start point on the ellipse`
- `addArc splits sweeps wider than 90 degrees into multiple conics`
- `arcTo without forceMoveTo joins to the existing contour via lineTo`
- `tangent arcTo on a 90 degree corner inserts a lineTo to T0 then a
  conic arc`
- `tangent arcTo after close repeats the last contour's start, then
  arcs` (dernier verbe = `kConic`)

### Phase 3 — surface API à compléter (✅ implémenté, 9/9 slices)

Phase 3 = additif. Aucun GM n'était strictement bloqué dessus, mais
plusieurs sont désormais débloqués :
- `ArcToGM` (variantes SVG-arc) → 3.7 ouvre `rArcTo` et
  `arcTo(rx, ry, xAxisRotate, ArcSize, sweep, x, y)`.
- `CubicPathGM` (`kInverse*` fill rules) → 3.8 supporte le rasterizer
  inverse.
- les futurs GMs qui utilisent `addPath(matrix, mode)`, `polylineTo`,
  `startIndex`, etc.

#### Sous-tranches livrées

| Slice | Surface | Risque | Statut |
|-------|---------|--------|--------|
| 3.1 | **Introspection read-only** : `isLine`/`isRect`/`isOval`/`isRRect`/`isFinite`/`isLastContourClosed`/`countPoints`/`countVerbs`/`getSegmentMasks`/`getLastPt` | nul | ✅ |
| 3.2 | **Fill type** : `makeFillType`/`makeToggleInverseFillType`/`isInverseFillType` (sur `SkPath`), `fillType()`/`isInverseFillType`/`toggleInverseFillType` (sur `SkPathBuilder`), helpers `SkPathFillType.isEvenOdd/isInverse/toggleInverse/convertToNonInverse` | nul | ✅ |
| 3.3 | **Builder ergonomics** : ctor `(SkPathFillType)`/`(SkPath)`, `reset` public, `polylineTo`, `addLine`, `incReserve`, `offset`/`transform` (mutate), `setPoint`/`setLastPt`, `getLastPt`/`countPoints` | nul | ✅ |
| 3.4 | **`addPath` modes & matrix** : `addPath(src, dx, dy, mode)`, `addPath(src, matrix, mode)`, `AddPathMode::kAppend`/`kExtend` | faible (sémantique `kExtend` + interaction Phase 1.2) | ✅ |
| 3.5 | **`startIndex` overloads** : `addRect`/`addOval`/`addRRect` avec `startIndex`, `Rect(rect, fillType, dir, startIndex)`/`Oval`/`RRect` factories ; `isRRect` étendu au verb stream `ConicStart` | faible | ✅ |
| 3.6 | **Geometry helpers** : `computeTightBounds` (Bézier derivative roots), `tryMakeTransform`/`tryMakeOffset`/`tryMakeScale`, `makeScale`, `IsLineDegenerate`/`IsQuadDegenerate`/`IsCubicDegenerate` | faible | ✅ |
| 3.7 | **SVG-arc** : `arcTo(rx, ry, xAxisRotate, ArcSize, sweep, x, y)` + `rArcTo(...)` ; `ArcSize` enum | moyen (port direct W3C SVG endpoint-to-conic) | ✅ |
| 3.8 | **`kInverse*` fill rules** dans `SkBitmapDevice.scanFillPath` : itération étendue à `[clip.top, clip.bottom]` + walker unifié + early-out ajustés pour empty paths | moyen (touche le rasterizer, suite GM 0 régression) | ✅ |
| 3.9 | **Interpolation & dump** : `isInterpolatable`/`makeInterpolate`/`interpolate`, `dumpToString`/`dump` | nul | ✅ |

#### Reste hors-scope (à ouvrir si un GM le demande)

- `isConvex` (algorithme convexité / sign-tracking sur cross products)
- `contains(x, y)` (point-in-path ray-cast winding/even-odd)
- `conservativelyContainsRect` (dépend de `isConvex`)
- `points()`/`verbs()`/`conicWeights()` accesseurs publics côté
  consommateurs externes (les arrays sont `internal` ; les détecteurs
  3.1 couvrent les besoins courants)
- `iter()` / `IterRec` API d'itération sucrée
- `Raw(...)` factory + `addRaw` builder helper
- `swap`/`reset` sur `SkPath` (port immutable)
- `setFillType` / `setIsVolatile` mutable sur `SkPath` (port immutable)
- `isVolatile` / `makeIsVolatile` (pas de cache layer consommateur)
- variant `arcTo(SkPoint r, ...)` (passage par `SkPoint` plutôt que
  scalars — ergonomie)
- "expectIntegers" rounding optimisation dans SVG-arc (cf. PR 3.7)

## Risque résumé

- Phase 1 = sûr. Pas de changement de représentation, juste deux bugs
  comportementaux qui produisaient des verbe streams non-conformes.
- Phase 2 = visuellement neutre mais casse des tests verbe-stream et
  peut décaler 1-2 pixels en bord d'arc.
- Phase 3 = additif, pas de risque sauf si un nouvel API
  (`makeFillType`) entre en collision avec un usage existant. Chaque
  sous-tranche est livrable et testable indépendamment.

---

## Closeout recap

| PR | Phase / Slice | Surface | Impact GM |
|----|---------------|---------|-----------|
| [#53](https://github.com/ygdrasil-io/kanvas/pull/53) | 1 — correctness | `moveTo` collapse + `ensureMove` après `kClose` | `PathArcToSkbug9077GM` 97.96 → 98.39 (+0.43) |
| [#58](https://github.com/ygdrasil-io/kanvas/pull/58) | 2 — verb-stream | cubic→conic (`addOval` / `addRRect` / `addArc` / tangent `arcTo`) | 10 GMs améliorés (`ArcOfZorro` +0.17, `CircleSizes` +0.13, `PathInterior` +0.09, `DRRect` / `ArcCircleGap` / `LargeCircle` / `Strokes4` / `Bug593049` / `RoundRect` / `PathArcToSkbug9077`) |
| [#62](https://github.com/ygdrasil-io/kanvas/pull/62) | 3.1 — introspection | `isLine` / `isRect` / `isOval` / `isRRect` / `isFinite` / `isLastContourClosed` / `countPoints` / `countVerbs` / `getSegmentMasks` / `getLastPt` | 0 |
| [#63](https://github.com/ygdrasil-io/kanvas/pull/63) | 3.2 — fill type | `makeFillType` / `makeToggleInverseFillType` / `isInverseFillType` ; `SkPathFillType.isEvenOdd` / `isInverse` / `toggleInverse` / `convertToNonInverse` ; builder `fillType()` / `toggleInverseFillType` | 0 |
| [#67](https://github.com/ygdrasil-io/kanvas/pull/67) | 3.3 — builder ergonomics | ctor `(SkPathFillType)` / `(SkPath)`, `reset` public, `polylineTo`, `addLine`, `incReserve`, `offset` / `transform` (mutate), `setPoint` / `setLastPt`, `getLastPt` / `countPoints` | 0 |
| [#68](https://github.com/ygdrasil-io/kanvas/pull/68) | 3.4 — `addPath` modes | `addPath(src, dx, dy, mode)`, `addPath(src, matrix, mode)`, `AddPathMode::kAppend` / `kExtend` | 0 |
| [#72](https://github.com/ygdrasil-io/kanvas/pull/72) | 3.5 — `startIndex` overloads | `addRect` / `addOval` / `addRRect` (+ factories) avec `startIndex` ; `isRRect` étendu au verb-stream `ConicStart` | 0 |
| [#73](https://github.com/ygdrasil-io/kanvas/pull/73) | 3.6 — geometry helpers | `computeTightBounds` (Bézier roots), `tryMakeTransform` / `tryMakeOffset` / `tryMakeScale`, `makeScale`, `IsLineDegenerate` / `IsQuadDegenerate` / `IsCubicDegenerate` | 0 |
| [#74](https://github.com/ygdrasil-io/kanvas/pull/74) | 3.7 — SVG-arc | `arcTo(rx, ry, xAxisRotateDeg, ArcSize, sweep, x, y)` + `rArcTo(...)` (port direct W3C endpoint-to-conic) | unblocks `ArcToGM` |
| [#78](https://github.com/ygdrasil-io/kanvas/pull/78) | 3.8 — `kInverse*` rasterizer | `SkBitmapDevice.scanFillPath` itération étendue à `[clip.top, clip.bottom]` + walker unifié + `drawPath` / `fillPath` early-out ajustés | unblocks `CubicPathGM` |
| [#79](https://github.com/ygdrasil-io/kanvas/pull/79) | 3.9 — interpolation | `isInterpolatable` / `makeInterpolate` / `interpolate` ; `dumpToString` / `dump` | 0 |
| [#81](https://github.com/ygdrasil-io/kanvas/pull/81) | GM port | `ArcToGM` (port direct upstream `gm/arcto.cpp`) | **95.80 %** au premier shot |
| [#83](https://github.com/ygdrasil-io/kanvas/pull/83) | GM port | `CubicPathGM` (port direct upstream `gm/cubicpaths.cpp`) | **87.25 %** au premier shot |

**Total** : 13 PRs (11 Phase + 2 GM) ; +150 tests unitaires ; 0 régression GM cumulée.

### Hors-scope (déférés, opt-in à la demande)

- `isConvex` (algorithme convexité / sign-tracking sur cross products)
- `contains(x, y)` (point-in-path ray-cast winding/even-odd)
- `conservativelyContainsRect` (dépend de `isConvex`)
- `points()` / `verbs()` / `conicWeights()` accesseurs publics côté
  consommateurs externes
- `iter()` / `IterRec` API d'itération sucrée
- `Raw(...)` factory + `addRaw` builder helper
- `swap` / `reset` sur `SkPath` (port immutable)
- `setFillType` / `setIsVolatile` mutable sur `SkPath` (port immutable)
- `isVolatile` / `makeIsVolatile` (pas de cache layer consommateur)
- variant `arcTo(SkPoint r, ...)` (passage par `SkPoint` plutôt que
  scalars — ergonomie)
- "expectIntegers" rounding optimisation dans SVG-arc

Ces items s'ouvriront en slice dédiée si un GM ou un consommateur
externe les requiert. Aucun n'est bloquant aujourd'hui.
