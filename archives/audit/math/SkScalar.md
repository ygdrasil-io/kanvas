# Audit — `SkScalar.kt` ↔ `include/core/SkScalar.h` + `include/private/base/SkFloatingPoint.h`

## Statistiques
- 55 symboles audités (depuis la TSV)
- 50 alignés
- 4 divergences fonctionnelles
- 0 divergences SIMD-only
- 1 à-vérifier

## Divergences fonctionnelles

### `SkScalarRound` ↔ `SkScalarRoundToScalar` (`sk_float_round`)
- **Kotlin** : `math/src/main/kotlin/SkScalar.kt:95-96`
- **C++** : `include/core/SkScalar.h:32` → `SkFloatingPoint.h:38` (`(float)sk_double_round((double)(x))` = `(float)floor(x + 0.5)`)
- **Nature** : convention d'arrondi différente sur les *ties* (`.5`).
- **Détail** : Kotlin `kotlin.math.round(Double)` est **half-to-even** (banker's rounding) — `round(0.5) == 0`, `round(1.5) == 2`, `round(2.5) == 2`. C++ `floor(x + 0.5)` est **half-toward-positive-infinity** — `round(0.5) == 1`, `round(1.5) == 2`, `round(2.5) == 3`. Pour `-0.5` : C++ → `0` (`floor(0) = 0`), Kotlin → `0` (half-to-even). Pour `-1.5` : C++ → `-1` (`floor(-1.0) = -1`), Kotlin → `-2`. Divergence ULP-locale, mais peut casser les tests de référence GM si Skia compare bit-exact.
- **Impact estimé** : correctness sur arrondis aux demis. Affecte tous les pipelines qui passent par `SkScalarRoundToInt` (cf. divergence suivante).

### `SkScalarRoundToInt` ↔ `SkScalarRoundToInt` (`sk_float_round2int`)
- **Kotlin** : `math/src/main/kotlin/SkScalar.kt:103`
- **C++** : `include/core/SkScalar.h:37` → `SkFloatingPoint.h:119` (`sk_float_saturate2int(sk_float_round(x))`)
- **Nature** : (a) convention d'arrondi half-to-even vs half-up (cf. ci-dessus) ; (b) saturation NaN différente.
- **Détail** : Kotlin `kotlin.math.round(value).toInt()` — la conversion JVM `f2i` mappe NaN à `0`. C++ `sk_float_saturate2int` mappe NaN à `SK_MaxS32FitsInFloat` (2147483520). Pour les valeurs finies hors-range, JVM `f2i` sature à `Int.MIN_VALUE`/`Int.MAX_VALUE`, C++ sature à `±SK_MaxS32FitsInFloat`. Pour les valeurs courantes (rounding pixel-aligned), aligné ; pour NaN/Inf, diverge.
- **Impact estimé** : correctness sur NaN/Inf et arrondis aux demis. Utilisé dans `SkRect.round()` (Kotlin: `SkRect.kt:212-215`).

### `SkScalarFloorToInt` / `SkScalarCeilToInt` ↔ `sk_float_floor2int` / `sk_float_ceil2int`
- **Kotlin** : `math/src/main/kotlin/SkScalar.kt:101-102`
- **C++** : `SkFloatingPoint.h:118-120` (`sk_float_saturate2int(std::floor(x))` etc.)
- **Nature** : saturation NaN différente.
- **Détail** : Kotlin `kotlin.math.floor(value).toInt()` : NaN → `0` (via JVM f2i). C++ : NaN → `SK_MaxS32FitsInFloat` (2147483520). Pour les valeurs finies dans/au-delà de la range int32, JVM sature à `Int.MIN/MAX_VALUE` (les valeurs JVM standard), C++ sature à `SK_MaxS32FitsInFloat` ≈ `Int.MAX_VALUE - 127` (la plus grande valeur représentable exactement en float). Sur des inputs >2³¹⁻¹²⁷ (rare), divergence d'1-128 unités.
- **Impact estimé** : correctness sur NaN / inputs hors-range. Utilisé dans `SkRect.roundOut/roundIn` (Kotlin: `SkRect.kt:218-227`).

### `SkScalarTruncToInt` ↔ `sk_float_saturate2int`
- **Kotlin** : `math/src/main/kotlin/SkScalar.kt:104` (`value.toInt()`)
- **C++** : `include/core/SkScalar.h:59` → `sk_float_saturate2int(x)`
- **Nature** : saturation NaN différente.
- **Détail** : Kotlin `Float.toInt()` est `f2i` JVM : NaN → `0`. C++ : NaN → `SK_MaxS32FitsInFloat`. La valeur de saturation diffère aussi (`Int.MAX_VALUE` vs `SK_MaxS32FitsInFloat = Int.MAX_VALUE - 127`).
- **Impact estimé** : correctness sur NaN et inputs extrêmes.

## SIMD-only (skip — résultat équivalent attendu)

Aucun.

## À vérifier

### `SkScalarSin/Cos/Tan/ASin/ACos/ATan2/Exp/Log/Log2/Pow` — précision
- **Kotlin** : `math/src/main/kotlin/SkScalar.kt:44-79`
- **C++** : `include/core/SkScalar.h:45-53` (`(float)std::sin(double)`, etc.)
- **Nature** : Kotlin promote en double puis cast → float, comme C++. Mais `std::sin(float)` (C++ surcharge float) vs `std::sin(double)` peut différer ; Skia force le cast `(float)std::sin(radians)` où `radians` est float, donc la surcharge sélectionnée est `sin(double)`. Aligné.
- **Détail** : à confirmer que la JVM `Math.sin` utilise bien IEEE-754 strict (cf. `StrictMath` vs `Math`). Sur HotSpot, `Math.sin` peut utiliser des intrinsics SSE/x87 avec une précision ≤ 1 ULP du résultat IEEE — Skia utilise libm. Différence ≤ 1 ULP attendue.
- **Impact estimé** : aucun en pratique (différences sub-ULP sur trig), mais GM bit-exact pourrait diverger marginalement.
