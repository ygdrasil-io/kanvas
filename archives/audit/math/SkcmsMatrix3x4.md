# Audit — `SkcmsMatrix3x4.kt` ↔ `modules/skcms/src/skcms_public.h`

## Statistiques
- 2 symboles audités (depuis la TSV)
- 2 alignés
- 0 divergences fonctionnelles
- 0 divergences SIMD-only (skip)
- 0 à-vérifier

## Divergences fonctionnelles

Aucune. POD pure (struct C → classe Kotlin avec `Array<FloatArray>`).

## SIMD-only (skip)

- Néant.

## À vérifier

- Néant.

## Aligned (verified)

- **Layout row-major** : `vals[row][col]` côté Kotlin (`SkcmsMatrix3x4.kt:11`, contrainte `vals.size == 3 && vals.all { it.size == 4 }`) ↔ `float vals[3][4]` upstream (`skcms_public.h:59-62`, commentaire `// A row-major 3x4 matrix (ie vals[row][col])`). Convention identique.
- `equals` / `hashCode` (`SkcmsMatrix3x4.kt:19-34`) : override par valeur, identique au pattern utilisé dans `SkcmsMatrix3x3.kt`. `Float.toRawBits()` pour la stabilité du hash.

## Notes (hors scope TSV)

- Aucun helper C public sur `skcms_Matrix3x4` dans `skcms_public.h` — la struct est consommée uniquement par l'évaluation LUT A2B/B2A interne au skcms (`modules/skcms/src/skcms.cc`, SIMD-heavy → skip selon les directives d'audit).
- La doc Kotlin mentionne "Currently only stored as a passthrough for Phase F1; consumers arrive in Phase F4 (LUT evaluation)." — cohérent : pas d'algorithme à auditer ici.
- Le port omet le helper `Companion.of(...)` (présent sur `SkcmsMatrix3x3`), mais c'est cohérent puisqu'aucun consumer ne le réclame pour l'instant. Hors scope.

## Conclusion

Struct alignée. L'usage réel (LUT eval) viendra en Phase F4 et nécessitera son propre audit.
