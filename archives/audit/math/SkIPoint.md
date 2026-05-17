# Audit — `SkIPoint.kt` ↔ `include/private/base/SkPoint_impl.h` (SkIPoint) + `include/private/base/SkSafe32.h`

## Statistiques
- 16 symboles audités (depuis la TSV)
- 16 alignés
- 0 divergences fonctionnelles
- 0 divergences SIMD-only
- 0 à-vérifier

## Divergences fonctionnelles

Aucune.

- Saturating arithmetic `sk32SatAdd`/`sk32SatSub` (`SkIPoint.kt:74-81`) reproduit fidèlement `Sk32_pin_to_s32` / `Sk32_sat_add` / `Sk32_sat_sub` (`SkSafe32.h:16-26`) : promotion vers `Long`/`int64_t`, pin sur `[Int.MIN_VALUE, Int.MAX_VALUE]`.
- `operator+=`, `operator-=`, `operator+`, `operator-` saturent — identique upstream (`SkPoint_impl.h:82-94`, `134-150`).
- `unaryMinus()` retourne `SkIPoint(-fX, -fY)` — comme C++ (`SkPoint_impl.h:74-76`). Pour `Int.MIN_VALUE`, JVM `-Int.MIN_VALUE` wrap → `Int.MIN_VALUE` ; C++ `-INT_MIN` est UB mais wrap identique en pratique. Comportement équivalent.
- `isZero`: Kotlin `fX == 0 && fY == 0` vs C++ `(fX | fY) == 0` — résultat identique sur ints.
- `equals(x, y)` : strict `==` — identique.
- `set`, `negate`, `Make` — triviaux, identiques.

## SIMD-only (skip — résultat équivalent attendu)

Aucun.

## À vérifier

Aucun.
