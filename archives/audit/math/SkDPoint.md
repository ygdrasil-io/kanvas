# Audit — `SkDPoint.kt` ↔ `src/pathops/SkPathOpsPoint.h`

## Statistiques
- 31 symboles audités (depuis `.upstream/source/map/math/SkDPoint.tsv`)
- 31 alignés (algorithmes identiques au bit près modulo conversions IEEE-754 standard)
- 0 divergence fonctionnelle
- 0 divergence epsilon / constante
- 1 cas à vérifier (mineur — sémantique d'`equals` Kotlin vs `operator==` C++)

## Divergences fonctionnelles

_Aucune._ Toutes les formules (cross 2D, dot, length, normalize, distance, Mid, approximately/roughly/wayRoughly variants) reproduisent strictement les bodies upstream :
- `cross` : `x*a.y - y*a.x` (math/src/main/kotlin/SkDPoint.kt:48 ↔ src/pathops/SkPathOpsPoint.h:60).
- `crossCheck` / `crossNoNormalCheck` : ordre `xy = x*a.y ; yx = y*a.x` puis test ULPs (16) ; xy−yx en fallback. (kt:54-58 / 64-68 ↔ h:65-69 / 72-76).
- `dot`, `length`, `lengthSquared` : ordre des opérations identique (kt:71-77 ↔ h:78-88).
- `normalize` : `invLen = 1.0 / length() ; x*=invLen ; y*=invLen ; return this`. Upstream utilise `sk_ieee_double_divide(1, length())` — Kotlin a la même sémantique IEEE (division par zéro produit ±Inf, pas d'exception). (kt:84-89 ↔ h:90-95).
- `approximatelyDEqual` / `approximatelyEqual` / `roughlyEqual` / `ApproximatelyEqual` / `RoughlyEqual` / `WayRoughlyEqual` : guards, mins/maxs et appel ULPs identiques (kt:121-220 ↔ h:157-273).
- `Mid` : `(a.x+b.x)/2, (a.y+b.y)/2` (kt:175-176 ↔ h:234-238).
- `distance` / `distanceSquared` : `dx*dx + dy*dy` (kt:152-161 ↔ h:224-232).

## Divergences epsilon / constante

_Aucune._ Toutes les constantes pertinentes vivent dans `SkPathOpsTypes` (cf. audit séparé). `SkDPoint` lui-même n'introduit aucun seuil littéral.

## À vérifier

### Sémantique d'égalité exacte (`SkDPoint == SkDPoint`)
- **Kotlin** : `math/src/main/kotlin/SkDPoint.kt:98` — `data class SkDPoint` → `equals` auto-généré : `x == a.x && y == a.y` (Double `==`).
- **C++** : `src/pathops/SkPathOpsPoint.h:115` — `friend bool operator==(const SkDPoint& a, const SkDPoint& b) { return a.fX == b.fX && a.fY == b.fY; }`.
- **Détail** : Kotlin `Double.equals` traite `NaN == NaN` comme `true` et `+0.0 != -0.0` ; C++ `==` traite `NaN == NaN` comme `false` et `+0.0 == -0.0`. Utilisé par `SkDLine.exactPoint` (kt:65-69 ↔ cpp:26-34). Pour pathops les coordonnées ne sont jamais NaN à ce stade (filtrées en amont) mais le cas ±0 est plausible (segments dégénérés). Voir aussi `SkPoint`/`SkVector` comparisons côté upstream qui partagent la même ambiguïté Kotlin.
- **Impact estimé** : correctness — en pratique probablement nul, mais à valider via les GM pathops (les tests skpwww_* ont historiquement débusqué ce genre de bord).

### Notes périphériques (alignées, mentionnées pour traçabilité)
- `WayRoughlyEqual` (kt:212-220 ↔ h:267-273) : `largest`/`largestDiff` restent Float côté Kotlin (puisque `SkPoint.fX`/`.fY` sont Float), conversion en Double seulement au passage à `roughly_zero_when_compared_to`. Identique au comportement implicite C++ (promotion float→double à l'appel).
- `ApproximatelyEqual` / `RoughlyEqual` (statiques) : `largest` est `float` côté upstream (h:209, h:261). Kotlin conserve aussi le type Float jusqu'à `largest.toDouble() + dist` (la conversion en double est implicite côté C++). Effet bit-pour-bit identique.
