# Audit — `SkcmsTransferFunction.kt` ↔ `modules/skcms/src/skcms_public.h`

## Statistiques
- 8 symboles audités (depuis la TSV)
- 8 alignés
- 0 divergences fonctionnelles
- 0 divergences SIMD-only (skip)
- 0 à-vérifier

## Divergences fonctionnelles

Aucune. Le port est une POD pure (struct → `data class`).

## SIMD-only (skip)

- Néant.

## À vérifier

- Néant.

## Aligned (verified)

- **Struct shape** : `data class SkcmsTransferFunction(g, a, b, c, d, e, f)` (`SkcmsTransferFunction.kt:15-23`) reflète exactement `struct skcms_TransferFunction { float g, a,b,c,d,e,f; }` (`skcms_public.h:71-73`). Même ordre des champs, même nombre.
- Types `Float` (Kotlin) ↔ `float` (C) — taille identique 32-bit IEEE 754.

## Notes (hors scope du TSV)

- **API associée (`skcms_TransferFunction_eval`, `skcms_TransferFunction_invert`, `skcms_TransferFunction_getType`, `skcms_TransferFunction_makePQish`, etc.)** : déclarés `SKCMS_API` dans `skcms_public.h:75-99` et implémentés en C (SIMD-heavy) dans `modules/skcms/src/skcms.cc`. **Non audité ici** : hors périmètre TSV (le TSV ne couvre que la struct elle-même). Pour Phase F4 (LUT eval), ces fonctions devront être portées séparément, avec attention spéciale à :
  - `TransferFunction_eval` : algorithme piecewise `sign(x) * (c*|x|+f)` (linear, |x|<d) vs `sign(x) * ((a*|x|+b)^g + e)` (curve). Edge case `|x| == d` : upstream prend la branche curve. NaN/Inf : `pow` peut produire NaN.
  - `TransferFunction_invert` : inversion analytique, doit gérer la discontinuité au point `d` et `a == 0`.
- La struct est marquée "Bit-compatible port" dans la doc Kotlin — appropriate puisque l'enjeu est uniquement la sérialisation/désérialisation depuis un profil ICC.

## Conclusion

Audit limité à la struct : aucune divergence. Les algorithmes associés (`eval`, `invert`, …) doivent être audités séparément quand ils seront portés.
