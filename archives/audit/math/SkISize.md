# Audit — `SkISize.kt` (et `SkSize`) ↔ `include/core/SkSize.h`

## Statistiques
- 8 symboles audités (depuis la TSV)
- 8 alignés
- 0 divergences fonctionnelles
- 0 divergences SIMD-only
- 0 à-vérifier

## Divergences fonctionnelles

Aucune.

- `SkISize.isEmpty` (`SkISize.kt:4`) ↔ C++ (`SkSize.h:31`) : `width <= 0 || height <= 0`. Identique.
- `SkISize.isZero` (`SkISize.kt:5`) ↔ C++ (`SkSize.h:28`) : `0 == fWidth && 0 == fHeight`. Identique.
- `SkSize.isEmpty` (`SkISize.kt:14`) ↔ C++ (`SkSize.h:71`) : `fWidth <= 0 || fHeight <= 0` (float). Identique, y compris sémantique NaN (`NaN <= 0` est faux des deux côtés → NaN-size n'est pas empty).
- `Make` / `MakeEmpty` : factories triviales, identiques.

## SIMD-only (skip — résultat équivalent attendu)

Aucun.

## À vérifier

Aucun. (Les méthodes upstream non listées dans la TSV — `SkSize.isZero`, `SkISize.area`, `SkSize.toRound/toCeil/toFloor`, `width()`/`height()` accessors, `set`, `setEmpty`, `equals` — sont absentes côté Kotlin mais hors scope d'audit.)
