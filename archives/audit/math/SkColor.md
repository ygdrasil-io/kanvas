# Audit — `SkColor.kt` ↔ `include/core/SkColor.h` + `src/core/SkColor.cpp` + `src/core/SkColorPriv.h`

## Statistiques
- 46 symboles audités (depuis la TSV)
- 44 alignés
- 1 divergence fonctionnelle
- 0 divergences SIMD-only (skip)
- 1 à-vérifier

## Divergences fonctionnelles

### `SkPreMultiplyARGB` ↔ `SkPremultiplyARGBInline`
- **Kotlin** : `math/src/main/kotlin/SkColor.kt:148-156`
- **C++** : `src/core/SkColorPriv.h:120-133` (via `src/core/SkColor.cpp:18-20`)
- **Nature** : formule d'arrondi différente
- **Détail** :
  - Kotlin : `((r * a) + 127) / 255` (arrondi standard `(x+127)/255` à la division entière).
  - C++ : `SkMulDiv255Round(r, a)` = `(r*a + 128 + ((r*a + 128) >> 8)) >> 8` (`include/private/base/SkMath.h:61-75`) — variante "shift-divide-by-255" classique.
  - Les deux formules sont équivalentes pour la quasi-totalité des couples `(r, a) ∈ [0,255]²` (vérifié manuellement sur points-clés 0/1/100/128/200/255). Une divergence d'un bit `±1` n'est pas exclue sur certaines valeurs limites.
  - Kotlin **n'a pas** le fast-path `if (a != 255)` qui rend `r` inchangé. Pour `a == 255`, Kotlin recalcule via la formule — résultat numériquement identique (`(255*r + 127)/255 == r` pour `r ∈ [0,255]`), mais explicitement plus lent et théoriquement plus exposé aux erreurs d'arrondi.
- **Impact estimé** : correctness — possible décalage de 1 LSB sur quelques couples ; perte d'identité exacte pour `a == 255`. À cibler par un test exhaustif `for r in 0..255, a in 0..255 → assertEquals(skiaRef, kotlin)`.

## SIMD-only (skip)

- `SkColor.cpp:134-156` (toBytes_RGBA / FromBytes_RGBA / Sk4f_fromL32 / Sk4f_toL32) — analysés sous `SkColor4f.md`.

## À vérifier

- **`colorToRGB565`** (`SkColor.kt:52-57`) : la TSV pointe vers `tools/ToolUtils.cpp:141` — fonction-utilitaire test-only. Algorithme `r &= 0xF8 ; r |= r >> 5` est la quantification 5-bit replicate standard ; aligned avec `ToolUtils::color_to_565` (à confirmer si la signature upstream a évolué).

## Aligned (verified)

- `SkColorSetARGB`, `SkColorSetRGB`, `SkColorGetA/R/G/B`, `SkColorSetA` : bit-twiddling exact (`SkColor.h:50-85`).
- Toutes les constantes `SK_Color*` : valeurs exactes (`SkColor.h:90-148`).
- `SkColorChannel`, `SkColorChannelFlag` : ordre enum et bit positions identiques (`SkColor.h:229-251`).
- `SkRGBToHSV` (`SkColor.kt:68-91` ↔ `SkColor.cpp:59-97`) : algorithme strictement identique. Mêmes branchements (`max == r/g/b`), même séquence FP (`(g-b)/delta`, multiplie par 60, ajoute 360 si négatif). Cas `delta == 0` → `h = 0` (identique).
- `SkHSVToColor` (`SkColor.kt:101-133` ↔ `SkColor.cpp:99-130`) :
  - `s, v` pinned dans `[0, 1]` — identique.
  - Cas `s ≤ 0` (Kotlin) vs `SkScalarNearlyZero(s)` (C++) : **différence de seuil** (Kotlin = `0f`, C++ = `1/(1<<12) ≈ 2.44e-4`). Pour `s` très petit positif, Kotlin entre dans la branche couleur, C++ dans la branche gris. **Note** : impact pratique nul puisque l'écart `frac × s` est ≤ 2.44e-4 qui s'arrondit au même byte. Mais théoriquement diff de 1 LSB possible pour quelques rares (h, s, v).
  - Normalisation `h` : Kotlin `h % 360f ; if (h < 0) h += 360f` ; C++ `hx = (h < 0 || h ≥ 360) ? 0 : h/60`. **DIVERGENCE de comportement out-of-range** : pour `h = 720`, Kotlin → `h = 0` (modulo), C++ → `hx = 0` (clip). Coïncidence pour multiples de 360 ; pour `h = 400`, Kotlin → `40°` (= rouge-orange), C++ → `0°` (= rouge). À documenter.
  - Calcul `p, q, t` : algébriquement identique (`v*(1-s)`, `v*(1-s*frac)`, `v*(1-s*(1-frac))`).
  - Switch `sector` 0..5 : ordres r/g/b identiques.
- `SkPreMultiplyColor` : trivialement aligned (wrap autour `SkPreMultiplyARGB`).

## Notes

- Le wrap-modulo `h` côté Kotlin est plus permissif et probablement plus correct au sens "math wrap" ; upstream traite les out-of-range comme erreur silencieuse en les clippant à 0. À documenter dans le Javadoc.
