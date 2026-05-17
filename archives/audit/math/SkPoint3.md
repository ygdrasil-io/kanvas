# Audit — `SkPoint3.kt` ↔ `include/core/SkPoint3.h` + `src/core/SkPoint3.cpp`

## Statistiques
- 17 symboles audités (depuis la TSV)
- 16 alignés
- 1 divergence fonctionnelle
- 0 divergences SIMD-only
- 0 à-vérifier

## Divergences fonctionnelles

### `org.graphiks.math.SkPoint3.Companion.Length` ↔ `SkPoint3::Length`
- **Kotlin** : `math/src/main/kotlin/SkPoint3.kt:40-41`
- **C++** : `src/core/SkPoint3.cpp:29-39`
- **Nature** : ordre de calcul / précision intermédiaire différents.
- **Détail** : Kotlin calcule **toujours** en double (`sqrt((x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)).toFloat()`). C++ fait un fast-path float (`get_length_squared` puis `std::sqrt`) et ne bascule en double **que** si `magSq` overflow vers Inf. Pour des entrées finies modérées le résultat float final est identique au ULP près (sqrt monotone), mais sur des valeurs nearly-overflowing en float² mais ok en double, Kotlin évite des Infs intermédiaires que C++ accepte → Kotlin **plus précis** sur des inputs limites. Pas d'erreur, juste un floor de précision plus élevé.
- **Impact estimé** : aucun en pratique pour le rendu (épsilons usuels), mais un test exact-binary upstream sur des inputs extrêmes pourrait diverger d'1 ULP.

## SIMD-only (skip — résultat équivalent attendu)

Aucun.

## À vérifier

Aucun. Les méthodes upstream non listées dans la TSV (`normalize`, `scale`, `makeScale`, `dot`, `cross`, `+=`, `-=`) n'ont pas de contrepartie Kotlin — hors scope d'audit (pas un porting deliverable, c'est un trou de surface API à signaler côté plan de port, pas une divergence).
