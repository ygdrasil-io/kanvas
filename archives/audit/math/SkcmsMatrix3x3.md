# Audit — `SkcmsMatrix3x3.kt` ↔ `modules/skcms/src/skcms_public.h`

## Statistiques
- 2 symboles audités (depuis la TSV)
- 2 alignés
- 0 divergences fonctionnelles
- 0 divergences SIMD-only (skip)
- 0 à-vérifier

## Divergences fonctionnelles

Aucune. Le port est une POD pure (struct C → classe Kotlin avec `Array<FloatArray>`).

## SIMD-only (skip)

- Néant.

## À vérifier

- Néant.

## Aligned (verified)

- **Layout row-major** : `vals[row][col]` côté Kotlin (`SkcmsMatrix3x3.kt:10`, contrainte `vals.size == 3 && vals.all { it.size == 3 }`) ↔ `float vals[3][3]` upstream (`skcms_public.h:50-53`, commentaire `// A row-major 3x3 matrix (ie vals[row][col])`). Convention identique.
- `equals` / `hashCode` (`SkcmsMatrix3x3.kt:20-35`) : override par valeur (comparaison cellule par cellule). Sans cet override, `Array<FloatArray>` ferait référence-equality — bug évité, sémantique alignée avec l'égalité par contenu attendue.
- `hashCode` utilise `Float.toRawBits()` — correct pour la stabilité (deux NaN avec bits différents ne hash pas pareil ; deux floats égaux mais bits différents pour `+0.0` / `-0.0` non-collidants — mais c'est cohérent avec `equals` qui utilise `!=` sur Float).

## Notes (hors scope TSV)

- **Helpers C `skcms_Matrix3x3_invert` et `skcms_Matrix3x3_concat`** (`skcms_public.h:55-57`) — non audités (hors TSV). Quand ils seront portés :
  - `_invert` : Cramer 3×3 ou cofacteurs, attention à la singularité (`|det| < ε` → fail). Upstream signature `bool` indique qu'il reporte un échec — le port devra propager.
  - Le commentaire upstream `// It is _not_ safe to alias the pointers to invert in-place` doit être documenté côté Kotlin si la signature passe `dst` en out-param.
  - `_concat` : trivial `Σ a[i][k] * b[k][j]` ; aucune subtilité d'aliasing.
- Le helper `Companion.of(...)` (`SkcmsMatrix3x3.kt:50-58`) est une commodité côté Kotlin, sans équivalent C direct (struct init avec brace-initializer suffit en C). Hors scope.

## Conclusion

Struct alignée. Les helpers `_invert` / `_concat` doivent être portés et audités séparément.
