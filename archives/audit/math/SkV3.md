# Audit — `SkV3.kt` ↔ `include/core/SkM44.h`

## Statistiques
- 15 symboles audités (depuis la TSV)
- 12 alignés
- 2 divergences fonctionnelles
- 0 divergences SIMD-only (skip)
- 1 à-vérifier

## Divergences fonctionnelles

### `SkV3.operator times(SkV3): Float` ↔ `SkV3::operator*(const SkV3&): SkV3`
- **Kotlin** : `math/src/main/kotlin/SkV3.kt:31-32`
- **C++** : `include/core/SkM44.h:74-76`
- **Nature** : sémantique d'opérateur différente
- **Détail** : Kotlin `v1 * v2` renvoie le **dot product** (`Float`). Upstream `v1 * v2` renvoie un **vecteur composant-par-composant** `SkV3{x*x, y*y, z*z}` ; le dot est exposé seulement via `.dot()` / `Dot()`. Le TSV ligne 9 explicite ce remap intentionnel.
- **Impact estimé** : correctness — toute traduction littérale de code Skia utilisant `SkV3 * SkV3` (composant-wise) produira un `Float` au lieu d'un `SkV3` et soit refusera de compiler, soit (pire) se silently coercera ailleurs. À documenter agressivement.

### `SkV3.normalize()` ↔ `SkV3::Normalize`
- **Kotlin** : `math/src/main/kotlin/SkV3.kt:46-49`
- **C++** : `include/core/SkM44.h:68`, `92`
- **Nature** : edge case zero-length géré
- **Détail** : Kotlin retourne `SkV3(0,0,0)` quand `length() <= 0f`. Upstream calcule `v * (1.0f / v.length())` → `Inf` / `NaN` pour vecteur nul (`length() == 0`). Comportement plus défensif côté Kotlin.
- **Impact estimé** : correctness — préfère le Kotlin (évite des `NaN` qui se propagent dans `SkM44` / `SkCamera3D`), mais peut masquer des bugs côté Skia qui s'appuieraient sur la propagation de NaN. À surveiller si un GM dépend du NaN.

## SIMD-only (skip)

- Néant.

## À vérifier

- `SkV3.length()` utilise `kotlin.math.sqrt(Double).toFloat()` (`SkV3.kt:43`) alors que upstream utilise `SkScalarSqrt` (`SkM44.h:88`) qui mappe à `sqrtf(float)`. Le `Double` round-trip côté Kotlin peut différer de `sqrtf` direct sur certaines architectures pour des valeurs limites (subnormal / overflow). Aligned dans 99 % des cas mais à vérifier si tests Skia comparent bit-à-bit. Note : `SkV2.length()` (qui utilise `SkScalarSqrt` → `kotlin.math.sqrt(Float)` via `SkScalar.kt`) suit le même pattern alignant — incohérence interne au port.

## Notes mineures

- Pas de variant `operator/(SkScalar)` (upstream `SkM44.h` n'en a pas non plus pour SkV3, contrairement à SkV2 — aligned).
- `SkV3.ZERO` est un ajout côté Kotlin (commodité), aligned avec usage idiomatique.
- Manque les `operator+= -= *=` (idem SkV2, immutable côté Kotlin — hors scope).
