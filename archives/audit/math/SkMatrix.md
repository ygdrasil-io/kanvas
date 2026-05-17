# Audit — `SkMatrix.kt` ↔ `include/core/SkMatrix.{h,cpp}`

## Statistiques

- **97 symboles** audités (depuis la TSV)
- **88 alignés** (algorithmes mathématiquement équivalents)
- **3 divergences fonctionnelles** (impact correctness possible)
- **3 SIMD-only** (skip — `skvx::float4` upstream, scalaire Kotlin équivalent)
- **3 à vérifier** (cas ambigus / sémantiques NaN/Inf subtiles)

## Divergences fonctionnelles

### `SkMatrix.mapVectors` ↔ `SkMatrix::mapVectors`
- **Kotlin** : `math/src/main/kotlin/SkMatrix.kt:487-501`
- **C++** : `src/core/SkMatrix.cpp:1108-1122`
- **Nature** : code path perspective absent
- **Détail** : upstream pour `hasPerspective()` calcule `origin = mapPointPerspective({0,0})` puis pour chaque vecteur fait `mapPointPerspective(src[i]) - origin` — c'est-à-dire qu'il applique le divide perspective et soustrait le translate effectif. Kotlin ignore complètement la branche perspective : son fallback `else` n'utilise jamais `persp0/persp1/persp2`, il fait simplement `(sx*x + kx*y, ky*x + sy*y)`. Pour une matrice avec perspective, le résultat scalaire est faux.
- **Impact estimé** : correctness sur matrices perspective (rare en pratique pour `mapVectors`, mais utilisé par des paths de gradient / shaders quand la CTM a de la perspective).

### `SkMatrix.Companion.MakeRectToRect` ↔ `SkMatrix::Rect2Rect`
- **Kotlin** : `math/src/main/kotlin/SkMatrix.kt:797-820`
- **C++** : `src/core/SkMatrix.cpp:559-599`
- **Nature** : remplacement de `sk_ieee_float_divide` par une branche `== 0f → +Inf`
- **Détail** : upstream fait `sx = sk_ieee_float_divide(dst.width(), src.width())`, ce qui retourne NaN pour `0/0`, +Inf pour `+x/0`, **-Inf pour `-x/0`**. Kotlin force `+Inf` quand `src.width() == 0f` indépendamment du signe de `dst.width()`. La garde `src.isEmpty` en début prévient la plupart des cas (isEmpty renvoie true si `left >= right`), mais les rects avec `left == right == NaN` ou ordres dégénérés peuvent l'éviter.
- **Impact estimé** : correctness sur edge cases NaN/Inf très rares ; pour les rects "normaux" aucun impact.

### `SkM44.invert` (indirectement) — semantic check sur `invert()` 3×3
- **Kotlin** : `math/src/main/kotlin/SkMatrix.kt:709-723`
- **C++** : `src/core/SkMatrix.cpp:818-881`
- **Nature** : Kotlin saute le fast path `mask <= kScale|kTranslate`
- **Détail** : upstream dispatch `mask == kIdentity_Mask → return *this`, et `mask & ~(kScale|kTranslate) == 0 → fast path` (division directe `1/sx`, `1/sy`, vérifie `SkIsFinite`, met `kRectStaysRect`). Kotlin va toujours dans le path déterminant via `dcrossDscale` pour tout cas affine (identité comprise). Numériquement équivalent dans le commun, mais perd la garde anti-overflow `SkIsFinite(invTX, invTY)` après `invTX = -tx*invSX` du fast path : un translate gigantesque + un sx microscopique peut produire un `invTX` Inf qui passe à travers (Kotlin fait `dcrossDscale(kx, ty, sy, tx, invDet)` qui mélange tx avec un cofactor — même problème asymptotiquement mais détecté différemment).
- **Impact estimé** : correctness sur cas dégénérés extrêmes ; le résultat dans le commun (det non-singulier, valeurs finies) est identique modulo bruit FP.

## SIMD-only (skip)

- `mapPoints` (kTranslate / kScale fast paths) — upstream `Trans_pts` / `Scale_pts` (`src/core/SkMatrix.cpp:903`, `:931`) utilisent `skvx::float4` à 2 ou 4 points à la fois. Kotlin fait boucle scalaire `src[i]` un par un. Résultat équivalent modulo ordre d'addition FP (différences inférieures à `ulp(result)`).
- `mapPoints` (affine fallback) — upstream `Affine_vpts` (`SkMatrix.cpp:988`) avec swizzle `<1,0,3,2>`. Kotlin scalaire, équivalent.
- `mapRectScaleTranslate` — upstream utilise `sort_as_rect(skvx::float4)` (`SkMatrix.cpp:1124,1133`). Kotlin fait `minOf/maxOf` scalaire. Équivalent.

## À vérifier

- **`SkMatrix.invert()` perspective branch** (`SkMatrix.kt:732-755`) — Kotlin recalcule les cofactors directement via `dcrossDscale`. Upstream `ComputeInv` (`SkMatrix.cpp:787-816`) utilise `scross_dscale` (cross en float, scale en double) au lieu de `dcross_dscale` (cross en double, scale en double). Pour les entrées non-perspective Skia C++ utilise `dcross_dscale` (Kotlin aligné), mais pour perspective il utilise `scross_dscale`. La précision diffère : upstream perspective fait `(float*float - float*float) * double_scale`, Kotlin fait `(double*double - double*double) * double_scale`. **Kotlin est plus précis** que upstream sur ce path — divergence existante mais "meilleure" ; à confirmer si voulu pour bit-parité.
- **`SkMatrix.preTranslate` cas `mask <= kTranslate_Mask`** (`SkMatrix.kt:575-580`) — upstream (`SkMatrix.cpp:267-283`) dans ce cas fait `fMat[kMTransX] += dx`, alors que Kotlin tombe dans le closed-form `tx + sx*dx + kx*dy = tx + 1*dx + 0*dy = tx + dx`. Algébriquement identique mais l'évaluation `1*dx + 0*dy` peut différer FP avec `dx = +Inf`, `dy = NaN` etc. À vérifier en pratique.
- **`SkMatrix.preservesRightAngles` tolérance** (`SkMatrix.kt:147-155`) — upstream (`SkMatrix.cpp:240`) appelle `SkScalarNearlyZero(dot, SkScalarSquare(tol))`. Kotlin utilise `tol * tol`. Identique sauf si `SkScalarSquare` a un comportement spécial pour Inf/NaN (à confirmer dans `SkScalar`). Probablement aligné.

## Notes annexes

- `SkMatrix.det()` (`SkMatrix.kt:198-201`) — non listé dans la TSV mais utilisé en interne. Le développement cofactor sur la première ligne est correct.
- `MakeRSXform` 6-arg pivoted (`SkMatrix.kt:1095-1102`) — variante Kotlin-only sans contrepartie upstream. La formule `ty + (-ssin*anchorX - scos*anchorY)` semble avoir une erreur de signe par rapport au composé `T(tx,ty)·R(scos,ssin)·T(-anchorX,-anchorY)` attendu (devrait donner `tx - scos*aX + ssin*aY`, pas `tx - scos*aX - ssin*aY`). Hors-scope TSV mais à signaler pour review humaine.
- `MakeRotate(deg, px, py)` (`SkMatrix.kt:981-984`) — composé via `preConcat` au lieu du closed-form `setSinCos(sin, cos, px, py)` upstream. Algébriquement équivalent, plus de rounding FP.
