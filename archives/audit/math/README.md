# Audit `:math` ↔ Skia upstream

Campagne de vérification d'alignement des **algorithmes** entre le module `:math` (Kotlin, `org.graphiks.math`) et le code Skia C++ upstream (`/Users/chaos/workspace/kanvas-forge/skia-main/`). Les divergences SIMD-only sont exclues — Kotlin n'a pas de SIMD natif, on compare le résultat sémantique, pas le déroulé instruction.

Sources des données :

- Code Kotlin : `math/src/main/kotlin/*.kt`
- Map de correspondance Kotlin ↔ C++ : `.upstream/source/map/math/*.tsv` (~774 entrées)
- Skia upstream : checkout local lecture seule

Méthode : 4 agents parallèles, 1 rapport markdown par source Kotlin.

## Décompte global

| Famille | Fichiers | Symboles audités | Alignés | Divergences fonctionnelles | SIMD-only | À vérifier |
|---|---:|---:|---:|---:|---:|---:|
| Point/Rect/Size/Scalar | 7 | 218 | 202 | 14 | 1 | 3 |
| Matrix (SkMatrix + SkM44) | 2 | 137 | 123 | 4 | 6 | 4 |
| Vector / Color / Skcms | 9 | 153 | 148 | 5 | 2 | 2 |
| Pathops doubles | 3 | 142 | 139 | 0 (+ 1 epsilon mineur) | 0 | 4 |
| **Total** | **21** | **650** | **612** | **23** | **9** | **13** |

**~94 % d'alignement strict.** 23 divergences fonctionnelles identifiées, dont une partie sont sévères et méritent un fix dédié.

## Divergences correctness-blocker (priorité haute)

Triées par impact estimé. Voir le rapport par fichier pour le détail.

### 1. `SkIRect` arithmétique non saturante (8 sites)
- **Fichier** : [`SkIRect.md`](SkIRect.md)
- **Issue** : `setXYWH`, `MakeXYWH`, `offset`, `offsetTo`, `inset`, `outset`, `adjust`, `makeOffset/Inset/Outset` utilisent `+`/`-` Kotlin qui **wrap au lieu de saturer**. Upstream utilise systématiquement `Sk32_sat_add` / `Sk32_sat_sub`.
- **Impact** : chemins clip-rect avec coordonnées proches de `INT32_MAX/MIN`. Régression silencieuse sur des cas extrêmes.
- **Fix** : remplacer par `SkIPoint.sk32SatAdd` / `sk32SatSub` (déjà portés dans `:math`).

### 2. `SkColor4f.toBytes_RGBA` / `FromBytes_RGBA` : byte order inversé
- **Fichier** : [`SkColor4f.md`](SkColor4f.md)
- **Issue** : Kotlin produit `R<<24 | G<<16 | B<<8 | A`, C++ stocke `[R,G,B,A]` en mémoire = uint32 little-endian `A<<24 | B<<16 | G<<8 | R`.
- **Impact** : roundtrip Kotlin OK mais **incompatible avec tout buffer natif** (textures GPU, surfaces, shaders attendant `GrColor`). Touche le pipeline d'interop.
- **Fix** : inverser l'ordre dans `toBytes_RGBA` et `FromBytes_RGBA`.

### 3. `SkV3.operator*(SkV3)` : dot product vs componentwise
- **Fichier** : [`SkV3.md`](SkV3.md)
- **Issue** : Kotlin `times(SkV3): Float` retourne le **dot product** ; upstream `operator*(SkV3, SkV3): SkV3` retourne le produit **componentwise**.
- **Impact** : toute traduction littérale de code C++ Skia utilisant `SkV3 * SkV3` sera buggy en Kotlin. Sémantique d'opérateur incompatible.
- **Fix** : renommer la méthode Kotlin actuelle en `dot()` (ou supprimer) et exposer un `times(SkV3): SkV3` componentwise.

### 4. `SkMatrix.mapVectors` ignore la perspective
- **Fichier** : [`SkMatrix.md`](SkMatrix.md)
- **Issue** : la branche fallback applique seulement le 2×2 linéaire ; upstream calcule `mapPointPerspective(src) - mapPointPerspective({0,0})`.
- **Impact** : bug réel sur matrices avec perspective non triviale. Affecte shaders et gradients sous CTM perspective.
- **Fix** : porter la branche perspective de `SkMatrix.cpp:1108-1122`.

### 5. `SkM44.mapRect` ne clippe pas `w=0`
- **Fichier** : [`SkM44.md`](SkM44.md)
- **Issue** : projette 4 coins puis `min/max` sans gérer les coins où `w < 0`. Upstream `map_rect_perspective` clippe contre `kW0PlaneDistance`.
- **Impact** : `Inf`/`NaN` possibles dans le résultat pour matrices très perspective.

### 6. `SkScalarRound*` : banker's rounding vs half-toward-+∞
- **Fichier** : [`SkScalar.md`](SkScalar.md)
- **Issue** : `kotlin.math.round` arrondit half-to-even (banker's), upstream utilise `floor(x + 0.5)` (half-toward-+∞).
- **Impact** : `round(0.5)` → `0` côté Kotlin, `1` côté C++. Diverge sur tous les `.5`. Propage à `SkRect.round()`.
- **Fix** : implémenter `floor(x + 0.5)` à la main.

### 7. `SkIRect.isEmpty` : overflow check manquant
- **Fichier** : [`SkIRect.md`](SkIRect.md)
- **Issue** : C++ retourne `true` quand `width64() | height64()` dépasse `int32` ; Kotlin ne checke que `<= 0`.
- **Impact** : rect `(INT_MIN, 0, INT_MAX, 1)` empty pour C++ mais non-empty pour Kotlin.

## Divergences à impact moindre

### 8. `SkPreMultiplyARGB` : formule d'arrondi
- **Fichier** : [`SkColor.md`](SkColor.md)
- Kotlin `(r*a + 127)/255` vs upstream `SkMulDiv255Round` `(prod + (prod>>8)) >> 8` avec `prod = r*a + 128`. Divergence possible de ±1 LSB sur cas limites.

### 9. `SkHSVToColor` : modulo wrap vs clip
- **Fichier** : [`SkColor.md`](SkColor.md)
- Kotlin normalise `h` out-of-range par modulo 360. Upstream clip à 0 pour `h ≥ 360`. Diverge pour `h ≥ 360` non-multiples.

### 10. `MakeRectToRect` NaN/Inf
- **Fichier** : [`SkMatrix.md`](SkMatrix.md)
- Kotlin force `+Inf` quand `src.width() == 0f` ; upstream IEEE divide produit `NaN`/`-Inf` selon signe `dst.width()`. Edge case rare (`src.isEmpty` garde en amont).

### 11. `SkV3.normalize()` : NaN vs zero
- **Fichier** : [`SkV3.md`](SkV3.md)
- Kotlin retourne zero sur vecteur nul. Upstream propage NaN.

### 12. `SkPathOpsTypes.approximately_positive_double` : helper ajouté
- **Fichier** : [`SkPathOpsTypes.md`](SkPathOpsTypes.md)
- Pas dans upstream. Helper additif (équivalent à `approximately_zero_or_more_double`). Risque si un appel ultérieur le substitue à un helper upstream distinct.

## Différences intentionnelles (NaN handling — `data class equals`)

Plusieurs types Kotlin (`SkPoint`, `SkRect`, `SkM44`, `SkDPoint`...) sont `data class` avec un `equals` IEEE-different : `NaN == NaN` → `true` côté Kotlin, `false` côté C++. C'est **volontaire** côté `:math` et documenté dans les KDocs (les variantes raw-IEEE existent : `equals(x, y)`, `equalsLTRB`, etc.).

## SIMD-only — équivalence à vérifier

9 occurrences notées. Kotlin scalaire vs upstream `Sk2f/Sk4f/__m128/_mm_*` :

- `SkPoint::mapPoints` (Sk2f 2-pixels au coup)
- `SkM44::operator*(SkM44, SkM44)` (Sk4f)
- `SkColor4f::Pin` (Sk4f saturate)
- ... (cf rapports par fichier pour la liste complète)

Pour ces sites, **résultat sémantique équivalent attendu** au ~ulp près. À valider en suite via tests par échantillonnage si besoin.

## À vérifier (review humaine)

13 cas marqués ambigus par les agents — pas certains que ce soit une divergence ou un alignement parfait. Listés par fichier dans la section "À vérifier" de chaque rapport.

## Suite

- **Issues** : ouvrir une issue par divergence correctness (1-7 ci-dessus, 7 issues).
- **PRs ciblées** : commencer par #1 (SkIRect saturating), le plus mécanique. #2 (SkColor4f byte order) bloquera l'interop GPU/Skia natif quand on l'attaquera.
- **Re-run** : ce script d'audit pourra être relancé après chaque resync upstream pour détecter de nouveaux drifts.

## Rapports détaillés

| Fichier | Rapport |
|---|---|
| `SkPoint.kt` | [`SkPoint.md`](SkPoint.md) |
| `SkIPoint.kt` | [`SkIPoint.md`](SkIPoint.md) |
| `SkPoint3.kt` | [`SkPoint3.md`](SkPoint3.md) |
| `SkRect.kt` | [`SkRect.md`](SkRect.md) |
| `SkIRect.kt` | [`SkIRect.md`](SkIRect.md) |
| `SkISize.kt` (et `SkSize`) | [`SkISize.md`](SkISize.md) |
| `SkScalar.kt` | [`SkScalar.md`](SkScalar.md) |
| `SkMatrix.kt` | [`SkMatrix.md`](SkMatrix.md) |
| `SkM44.kt` | [`SkM44.md`](SkM44.md) |
| `SkV2.kt` | [`SkV2.md`](SkV2.md) |
| `SkV3.kt` | [`SkV3.md`](SkV3.md) |
| `SkV4.kt` | [`SkV4.md`](SkV4.md) |
| `SkColor.kt` | [`SkColor.md`](SkColor.md) |
| `SkColor4f.kt` | [`SkColor4f.md`](SkColor4f.md) |
| `SkColorMatrix.kt` | [`SkColorMatrix.md`](SkColorMatrix.md) |
| `SkcmsTransferFunction.kt` | [`SkcmsTransferFunction.md`](SkcmsTransferFunction.md) |
| `SkcmsMatrix3x3.kt` | [`SkcmsMatrix3x3.md`](SkcmsMatrix3x3.md) |
| `SkcmsMatrix3x4.kt` | [`SkcmsMatrix3x4.md`](SkcmsMatrix3x4.md) |
| `SkDPoint.kt` (et `SkDVector`) | [`SkDPoint.md`](SkDPoint.md) |
| `SkDLine.kt` | [`SkDLine.md`](SkDLine.md) |
| `SkPathOpsTypes.kt` | [`SkPathOpsTypes.md`](SkPathOpsTypes.md) |
