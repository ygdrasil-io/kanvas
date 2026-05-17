# Audit — `SkDLine.kt` ↔ `src/pathops/SkPathOpsLine.{h,cpp}`

## Statistiques
- 12 symboles audités (depuis `.upstream/source/map/math/SkDLine.tsv`)
- 12 alignés
- 0 divergence fonctionnelle
- 0 divergence epsilon / constante
- 1 cas à vérifier (mineur — pin de `t` dans `nearPoint` après `SkPinT`)

## Divergences fonctionnelles

_Aucune._ Les corps de `ptAtT`, `exactPoint`, `nearPoint`, `nearRay`, et les 4 variantes H/V reproduisent exactement les implémentations C++ :

- `ptAtT` (kt:51-59 ↔ cpp:14-24) : early-returns à `t==0` / `t==1`, puis `one_t * p0 + t * p1` avec **le même ordre des opérandes** sur chaque coordonnée. Lerp form upstream `oneT*A + t*B` (et non `A + (B-A)*t`).
- `exactPoint` (kt:65-69 ↔ cpp:26-34) : comparaison par `==` data-class (cf. note ci-dessous).
- `nearPoint` (kt:81-103 ↔ cpp:36-68) : tests `AlmostBetweenUlps` sur x et y, `len = pts[1] - pts[0]`, `denom = len.x² + len.y²`, **`numer = len.x*ab0.x + ab0.y*len.y`** (ordre identique au upstream), `between(0, numer, denom)`, `denom == 0 → 0`, sinon `t = numer/denom`, distance projetée, scan magnitude-aware, `AlmostEqualUlpsPin` puis `SkPinT(t)`.
- `nearRay` (kt:110-122 ↔ cpp:70-84) : même formule projection + `RoughlyEqualUlps`.
- `ExactPointH` / `ExactPointV` (kt:140-146 / 165-171 ↔ cpp:86-96 / 121-131) : identiques.
- `NearPointH` (kt:149-162 ↔ cpp:98-119) : `AlmostBequalUlps(xy.y, y)`, `AlmostBetweenUlps(left, xy.x, right)`, `t = (xy.x-left)/(right-left)`, `SkPinT`, `realPtX = (1-t)*left + t*right`, distance euclidienne, `AlmostEqualUlps(largest, largest+dist)`. **Note** : le `SkDVector distU = {xy.fY - y, xy.fX - realPtX}` upstream est répliqué côté Kotlin (`dx = xy.y - y, dy = xy.x - realPtX`) — l'asymétrie axes apparente (dx prend Y, dy prend X) est fidèle à l'upstream (h:109).
- `NearPointV` (kt:174-187 ↔ cpp:133-154) : symétrique de NearPointH, alignée.

## Divergences epsilon / constante

_Aucune._ Les ULPs (16 pour AlmostEqualUlps, 256/1024 pour RoughlyEqualUlps, 2 pour AlmostBequalUlps et AlmostBetweenUlps) sont définies dans `SkPathOpsTypes` et utilisées indirectement — voir audit `SkPathOpsTypes.md`.

## À vérifier

### `nearPoint` : assertion absente
- **Kotlin** : `math/src/main/kotlin/SkDLine.kt:101-102` — `t = SkPinT(t); return t`
- **C++** : `src/pathops/SkPathOpsLine.cpp:65-67` — `t = SkPinT(t); SkASSERT(between(0, t, 1)); return t;`
- **Détail** : l'assertion C++ ne change pas le résultat en release, donc divergence "informationnelle" seulement. Idem pour `NearPointH` (cpp:107) et `NearPointV` (cpp:142). Ne pas en faire un blocker.
- **Impact estimé** : aucun (assert de debug).

### Égalité dans `exactPoint`
- **Kotlin** : `math/src/main/kotlin/SkDLine.kt:65-69` — `xy == pts[0]` (data-class Kotlin).
- **C++** : `src/pathops/SkPathOpsLine.cpp:27-31` — `xy == fPts[0]` (`operator==` SkDPoint).
- **Détail** : héritage du cas `SkDPoint.equals` (cf. audit `SkDPoint.md`) — `NaN==NaN` Kotlin vs C++, `±0` distincts en Kotlin. Probablement sans impact pour pathops, mais à connaître.

### Lerp form
- Upstream et Kotlin utilisent **`(1-t)*A + t*B`** dans `ptAtT`, `NearPointH`, `NearPointV` — pas `A + (B-A)*t`. `SkDInterp` (helper séparé dans `SkPathOpsTypes`) utilise par contre la forme `A + (B-A)*t`. Cohérent avec upstream — pas une divergence.
