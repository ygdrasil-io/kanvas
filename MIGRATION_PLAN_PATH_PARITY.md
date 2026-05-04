# Migration plan — `SkPath` / `SkPathBuilder` parity

Status: **draft 1**, scope = aligner le port Kotlin avec `include/core/SkPath.h`,
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

### Phase 3 — surface API à compléter (à la demande)

À ouvrir au fur et à mesure des GMs / fonctionnalités portées. Liste non
exhaustive :

**`SkPath` manquant :**
- `isOval` / `isRRect` / `isRect` / `isLine` / `isConvex` / `isFinite` /
  `isLastContourClosed` / `isInverseFillType` / `isVolatile`
- `setFillType` / `makeFillType` / `toggleInverseFillType` / `makeIsVolatile`
- `getLastPt` / `countPoints` / `countVerbs` / `getSegmentMasks`
- `points()/verbs()/conicWeights()` publics (actuellement `internal`)
- `computeTightBounds`, `conservativelyContainsRect`, `contains`
- `interpolate` / `makeInterpolate` / `isInterpolatable`
- `tryMakeTransform` / `tryMakeOffset` / `tryMakeScale` / `makeScale`
- `swap` / `reset` / `iter()`
- `Raw(...)`, `Rect(rect, fillType, dir, startIndex)`
- variantes `kInverse*` côté rasterizer

**`SkPathBuilder` manquant :**
- constructeurs `(SkPathFillType)` et `(const SkPath&)`
- `setIsVolatile` / `isVolatile` / `reset` public
- `polylineTo`, `rArcTo` (SVG-arc), `arcTo(r, xRot, ArcSize, sweep, xy)` (SVG-arc)
- `incReserve`, `offset`, `transform` (mutent le builder)
- `isFinite`, `toggleInverseFillType`, `getLastPt`, `setPoint`,
  `setLastPt`, `countPoints`, `isInverseFillType`
- `points()/verbs()/conicWeights()` (actuellement absents)
- `addRaw`, `iter`, `dump`, `dumpToString`, `contains`
- `addPath(SkPath, dx, dy, mode)` et `addPath(src, matrix, mode)` avec
  `AddPathMode::kAppend` / `kExtend`
- `addRect(rect, dir, startIndex)` / `addOval(rect, dir, startIndex)` /
  `addRRect(rrect, dir, startIndex)` (avec `startIndex` actuellement
  manquant — le port démarre toujours top-left/right-of-center)

## Risque résumé

- Phase 1 = sûr. Pas de changement de représentation, juste deux bugs
  comportementaux qui produisaient des verbe streams non-conformes.
- Phase 2 = visuellement neutre mais casse des tests verbe-stream et
  peut décaler 1-2 pixels en bord d'arc.
- Phase 3 = additif, pas de risque sauf si un nouvel API
  (`makeFillType`) entre en collision avec un usage existant.

## Plan de cette PR

1. Phase 1.1 — moveTo collapse + test
2. Phase 1.2 — ensureContour après close + test
3. Run `gradle test` (kanvas-skia)
4. Si une similarity régresse, l'inscrire dans la PR avec diagnostic.
