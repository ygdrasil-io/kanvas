# Audit — `SkV4.kt` ↔ `include/core/SkM44.h`

## Statistiques
- 18 symboles audités (depuis la TSV)
- 18 alignés
- 0 divergences fonctionnelles
- 0 divergences SIMD-only (skip)
- 0 à-vérifier

## Divergences fonctionnelles

Aucune. Tous les opérateurs (`+ - *`, scalar `*`, dot, length, normalize, `operator[]`) suivent la transcription composant-par-composant de `SkM44.h:98-140`. La séquence FP du `Dot` (`a.x*b.x + a.y*b.y + a.z*b.z + a.w*b.w`) est strictement conservée.

Contrairement à `SkV3` :
- L'opérateur `times(SkV4): SkV4` est correctement **composant-par-composant** (`SkV4.kt:26`), aligné avec `SkM44.h:115-117`.
- Le port garde `Normalize(v) = v * (1f / v.length())` sans fast-path zero-length (idem upstream) — identique en comportement (`Inf`/`NaN` pour vecteur nul).

## SIMD-only (skip)

- Néant — `SkV4` upstream est scalaire dans le header (les SIMD apparaissent ailleurs dans `SkM44`).

## À vérifier

- Néant.

## Notes mineures

- `operator[]` Kotlin lance `IndexOutOfBoundsException` (`SkV4.kt:37-43`) là où upstream `SkASSERT` (debug-only). Comportement plus strict en release côté Kotlin, mais correctness identique pour les indices valides.
- Manque les mutating operators `+= -= *=` (idem SkV2/SkV3, immutable — hors scope).
- `ptr()` / `vec()` omis (non portable).
- Manque `operator==`/`operator!=` explicite — fournis par `data class` (égalité par champs), équivalent fonctionnel à `SkM44.h:101-103`.
