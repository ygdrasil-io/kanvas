# Audit — `SkM44.kt` ↔ `include/core/SkM44.{h,cpp}` + `SkMatrixInvert.cpp`

## Statistiques

- **40 symboles** audités (depuis la TSV)
- **35 alignés** (algorithmes mathématiquement équivalents)
- **1 divergence fonctionnelle**
- **3 SIMD-only** (skip — `skvx::float4` upstream, scalaire Kotlin équivalent)
- **1 à vérifier**

## Divergences fonctionnelles

### `SkM44.invert` — early-out légèrement plus strict
- **Kotlin** : `math/src/main/kotlin/SkM44.kt:520-575`
- **C++** : `src/core/SkM44.cpp:247-254` (qui appelle `src/core/SkMatrixInvert.cpp:72-144`)
- **Nature** : early-out sur déterminant non-finite
- **Détail** : upstream calcule `invdet = sk_ieee_double_divide(1.0, determinant)` même quand `determinant == 0` (produit +Inf), puis remplit `outMatrix` avec des Inf/NaN, et finalement vérifie `if (!SkIsFinite(outMatrix, 16)) determinant = 0.0f` pour échouer. Kotlin court-circuite avant : `if (determinant == 0.0 || !determinant.isFinite()) return null`. Les deux retournent "échec" dans les mêmes cas, mais Kotlin évite le calcul intermédiaire avec Inf/NaN. **Pas de différence observable côté caller** (même résultat null/false), à condition que `determinant.isFinite()` capture exactement les cas où upstream produirait un non-finite final.
- **Impact estimé** : aucun en pratique ; juste un re-arrangement du contrôle de flux. Coefficients algébriques identiques.

## SIMD-only (skip)

- `setConcat` — upstream `SkM44.cpp:48-68` utilise `skvx::float4::Load` + `c0*r[0] + (c1*r[1] + ...)` (ordre d'addition imposé par les parenthèses). Kotlin `SkM44.kt:348-367` déroule la boucle scalaire `a00*b0 + a01*b1 + a02*b2 + a03*b3`. **Ordre d'addition différent** : upstream est `((a*b0 + (b*b1 + (c*b2 + d*b3))))` (associativité à droite), Kotlin est `(((a*b0 + b*b1) + c*b2) + d*b3)` (à gauche). Pour des entrées non-extrêmes la différence reste en `~ulp`, mais détectable bit-à-bit. Note "SIMD upstream, scalaire Kotlin — résultat ≈ équivalent au ulp près".
- `preTranslate` / `postTranslate` / `preScale` — upstream `SkM44.cpp:89,100,109,118` utilisent `skvx::float4` SIMD. Kotlin scalaire ligne par ligne. Coefficients identiques modulo ordre d'addition FP.
- `map(x, y, z, w)` — upstream `SkM44.cpp:129-138` SIMD avec `c0*x + (c1*y + (c2*z + c3*w))` (associativité à droite). Kotlin scalaire `fMat[0]*x + fMat[4]*y + fMat[8]*z + fMat[12]*w` (à gauche). Différence ulp possible.

## À vérifier

- **`SkM44.Companion.lookAt`** (`SkM44.kt:138-157`) — la construction matricielle via `cols.setCol(...)` puis `cols.invert()` est strictement parallèle à upstream `SkM44.cpp:331-341` qui utilise `SkM44::Cols(...).invert(&m)`. Kotlin n'a pas de `Cols(...)` factory ; il construit un SkM44 vide puis appelle 4× `setCol`. Le résultat est identique. Le fallback `setIdentity()` quand l'inverse échoue est aussi aligné. Aligné, juste vérifier que la convention de signe (`-f` pour la troisième colonne) est bien préservée — oui (`SkM44.kt:146` : `v4(-f, 0f)`). ✓
- **`SkM44.normalizePerspective`** (`SkM44.kt:621-631`) — upstream utilise SIMD (`SkM44.cpp:226-239`). Kotlin boucle scalaire `for i in 0..15`. Une différence subtile : Kotlin fait `inv = 1.0 / fMat[15]` (Double), puis chaque `fMat[i] = (fMat[i] * inv).toFloat()`. Upstream fait `double inv = 1.0 / fMat[15]`, puis `(skvx::float4::Load(fMat+i) * inv).store(...)` — la conversion intermédiaire se fait en SIMD avec arrondi par défaut. Équivalent au ulp près.

## Notes annexes (hors-TSV)

- `SkM44.asM33` (`SkM44.kt:594-598`) ↔ `SkM44::asM33` (`SkM44.h:409-413`) — extraction colonne/ligne exacte. Aligné. La signature Kotlin retourne `SkMatrix?` mais ne renvoie jamais null (juste pour symétrie avec `setM33`).
- `SkM44.mapRect` (`SkM44.kt:492-511`) — **non présent dans la TSV**, mais à signaler : Kotlin projette les 4 coins puis fait `min/max`. Upstream `SkMatrixPriv::MapRect` (`SkM44.cpp:216-224`) distingue affine (chemin SIMD `map_rect_affine`) vs perspective (`map_rect_perspective` avec **clipping contre le plan w=0** via `kW0PlaneDistance`). Kotlin ne fait pas le clipping w<0 : pour une matrice qui projette un coin derrière la caméra, Kotlin retournera des valeurs Inf/NaN au lieu d'une rect bornée. **Divergence fonctionnelle réelle sur perspective**, à considérer comme bug potentiel à porter si SkCanvas/M44 intègre la projection 3D.
- `SkM44.mapPoint(SkPoint)` (`SkM44.kt:475-483`) — non-TSV ; convention de divide par w identique au sens upstream.
- `SkM44.equals` (`SkM44.kt:641-648`) — boucle scalaire `!= ` au lieu du SIMD `~0` upstream (`SkM44.cpp:18-35`). NaN-friendly côté Kotlin (Float.equals renvoie true pour NaN == NaN), NaN-asymmetric côté upstream (IEEE `==`). Note Kotlin-style `==` operator follows data-class/`Float.equals` which treats `NaN == NaN` as true; this diverges from upstream's IEEE semantic. Hors-TSV mais à signaler si comparaison bit-exacte des matrices contient des NaN.
