# Audit — `SkV2.kt` ↔ `include/core/SkM44.h`

## Statistiques
- 18 symboles audités (depuis la TSV)
- 18 alignés
- 0 divergences fonctionnelles
- 0 divergences SIMD-only (skip)
- 0 à-vérifier

## Divergences fonctionnelles

Aucune. Tous les opérateurs (`+ - * /`, dot, cross, length, normalize) sont des transcriptions composant-par-composant fidèles à la version inline `SkM44.h:19-54`. La séquence FP exacte est conservée (ex. `Dot(a, b) = a.x*b.x + a.y*b.y`).

Note : `SkV2.Normalize` ne traite pas le cas vecteur nul (divise par `length() = 0` → `+Inf`/`NaN`) — comportement **identique à l'upstream** (`SkM44.h:27`), donc aligné.

## SIMD-only (skip)

- Néant — `SkV2` upstream n'utilise pas Sk4f (scalaire en C++ aussi).

## À vérifier

- Néant.

## Notes mineures

- L'opérateur upstream `friend SkV2 operator/(SkScalar s, SkV2 v) { return {s/v.x, s/v.y}; }` (`SkM44.h:37`) n'a pas d'équivalent Kotlin. Hors scope du TSV ; non utilisé par les call-sites portés.
- Les variantes `operator+= / -= / *= / /=` (mutation in-place) sont omises côté Kotlin car `SkV2` est `data class` immutable. Hors scope.
- `ptr()` (raw pointer aliasing) n'est pas portable et est correctement omis.
