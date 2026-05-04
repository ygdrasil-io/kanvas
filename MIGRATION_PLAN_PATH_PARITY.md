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

### Phase 2 — alignement verb-stream (déféré)

Plus risqué : le rasterizer (`SkBitmapDevice.buildEdges`) et le stroker
(`SkStroker`) gèrent déjà `kConic`, donc passer `addOval`/`addRRect`/
`arcTo` en conics est techniquement possible. Mais :

- l'approximation cubique kappa actuelle est dans la tolérance pixel des
  tests existants (`MAX_DROP_PERCENT = 1 %`),
- changer la représentation casse `assertArrayEquals(verbs, …)` dans
  `SkPathBuilderTest`,
- la tolerance numérique du rasterizer conic-vs-cubic peut décaler des
  pixels en bord d'arc.

Donc **Phase 2 = pas dans cette PR**. Sera ouverte quand un GM upstream
exigera le verb-stream conic exact (`isOval`, `isRRect`, sérialisation, …).

Ordre prévu si Phase 2 ouverte :

1. `addOval(rect, dir)` → `kMove + 4 × kConic + kClose`, weight `√2/2`.
2. `addCircle` → délègue déjà à `addOval`. Aucun changement direct.
3. `addRRect` → `kMove + (kLine, kConic) × 4 + kClose`, weight `√2/2` aux coins.
4. `arcTo(rect, …)` → conics via `SkConic::BuildUnitArc` (jusqu'à 4 conics).
5. `arcTo(p1, p2, r)` PostScript → 1 line + 1 conic (cf. `SkPathBuilder.h:638`).

Tests existants à mettre à jour :
- `addOval emits 4 cubic Bezier arcs with the kappa approximation`
- `addCircle delegates to addOval centred on the given point`
- `addArc emits cubic segments matching the start point on the ellipse`
- `addArc splits sweeps wider than 90 degrees into multiple cubics`
- `arcTo without forceMoveTo joins to the existing contour via lineTo`

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
