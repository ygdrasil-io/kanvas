# Audit — `SkRect.kt` (partie SkRect) ↔ `include/core/SkRect.h` + `src/core/SkRect.cpp`

## Statistiques
- 51 symboles audités (depuis la TSV, sous-ensemble SkRect)
- 50 alignés
- 1 divergence fonctionnelle
- 0 divergences SIMD-only (Bounds : SIMD upstream 32-bit only, scalaire Kotlin — résultat équivalent par construction)
- 1 à-vérifier

## Divergences fonctionnelles

### `org.graphiks.math.SkRect.Companion.Bounds` ↔ `SkRect::Bounds`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:273-287`
- **C++** : `src/core/SkRect.cpp:50-119`
- **Nature** : `min`/`max` semantics divergent sur NaN.
- **Détail** : Kotlin utilise `<`/`>` (`if (p.fX < l) l = p.fX`). C++ utilise `std::fminf`/`std::fmaxf` qui ont une sémantique NaN-propre **différente** : `fminf(NaN, x) == x` (ignore NaN si l'autre opérande est numéraire). En Kotlin, si `p.fX == NaN`, `NaN < l` est faux → `l` n'est pas mis à jour, ce qui est conforme. Mais lors de l'**initialisation** Kotlin pose `l = points[0].fX` directement : si `points[0].fX` est NaN, alors `l = NaN` et les comparaisons suivantes (`p.fX < NaN`) seront toujours fausses → `l` reste NaN. C++ : `L = std::fminf(p.fX, L)` : si `L=NaN` au début, `fminf(num, NaN) = num`, donc `L` est rapidement remplacé par un numéraire. **MAIS** ensuite le check `nx *= p.fX` propagera NaN → la fonction renverra `null` (Kotlin) / `{}` (C++). Donc le résultat **observable** (le `null` / empty optional) est identique. La divergence d'algorithme intermédiaire ne se voit pas en sortie.
- **Impact estimé** : aucun (les deux branches retournent "non-finite" via le check final `nx == 0 && ny == 0`).

## SIMD-only (skip — résultat équivalent attendu)

- `SkRect.Companion.Bounds` (path 32-bit) : upstream utilise `skvx::float4` sur plateformes 32-bit, scalaire sur 64-bit. Kotlin scalaire — équivalent au résultat upstream 64-bit attendu sur la cible JVM.

## À vérifier

### `org.graphiks.math.SkRect.centerX` / `centerY` ↔ `SkRect::centerX/centerY`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:41-44`
- **C++** : `include/core/SkRect.h:788-799`, util `sk_float_midpoint` `SkFloatingPoint.h:146-149`
- **Nature** : ordre des opérations — Kotlin `(0.5 * (left.toDouble() + right)).toFloat()`, C++ `static_cast<float>(0.5 * (static_cast<double>(a) + b))`. **Identique** au final, à confirmer car la promotion `.toFloat()` doit être semantiquement équivalente au cast C++ `static_cast<float>`. La conversion d'un double finite trop grand pour float donne `±Inf` côté Kotlin (JVM `d2f`) ; côté C++, `sk_double_to_float` est marqué `SK_NO_SANITIZE("float-cast-overflow")` mais sémantique runtime identique (`±Inf`). Aligné. *(noté dans "à vérifier" par prudence, pas une divergence avérée.)*
- **Impact estimé** : aucun.
