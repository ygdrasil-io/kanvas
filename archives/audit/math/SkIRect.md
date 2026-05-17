# Audit — `SkIRect` (dans `SkRect.kt`) ↔ `include/core/SkRect.h` (SkIRect) + `src/core/SkRect.cpp`

## Statistiques
- 35 symboles audités (depuis la TSV, sous-ensemble SkIRect)
- 27 alignés
- 8 divergences fonctionnelles
- 0 divergences SIMD-only
- 0 à-vérifier

## Divergences fonctionnelles

### `org.graphiks.math.SkIRect.isEmpty` ↔ `SkIRect::isEmpty`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:320`
- **C++** : `include/core/SkRect.h:207-215`
- **Nature** : edge case manqué — overflow int32 sur width/height.
- **Détail** : C++ retourne `true` aussi quand `width64() | height64()` ne tient pas dans int32 (`!SkTFitsIn<int32_t>(w|h)`). Kotlin ne checke que `<= 0`. Un rect tel que `(INT_MIN, 0, INT_MAX, 1)` a `width64() = UINT_MAX` (positif, > INT_MAX) → C++ dit empty, Kotlin dit non-empty.
- **Impact estimé** : correctness sur rects pathologiques. `isEmpty64()` Kotlin renvoie la même chose que `isEmpty()` Kotlin (alias en ligne 322), donc cette divergence existe pour les deux.

### `org.graphiks.math.SkIRect.setXYWH` ↔ `SkIRect::setXYWH`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:332-334` (via `setLTRB(x, y, x+w, y+h)`)
- **C++** : `include/core/SkRect.h:273-278`
- **Nature** : saturating add manquant.
- **Détail** : C++ utilise `Sk32_sat_add(x, width)` et `Sk32_sat_add(y, height)`. Kotlin utilise `+` qui wrap sur overflow.
- **Impact estimé** : correctness pour des callers qui poussent près de `Int.MAX_VALUE` — comportement upstream produit un rect saturé, Kotlin produit un rect wrappé. Le doc-comment de la classe (`SkRect.kt:293-295`) mentionne le wrap pour `width()`/`height()` mais pas pour les mutateurs.

### `org.graphiks.math.SkIRect.Companion.MakeXYWH` ↔ `SkIRect::MakeXYWH`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:416`
- **C++** : `include/core/SkRect.h:109-111`
- **Nature** : saturating add manquant.
- **Détail** : C++ : `{ x, y, Sk32_sat_add(x, w), Sk32_sat_add(y, h) }`. Kotlin : `SkIRect(x, y, x+w, y+h)` — wrap.
- **Impact estimé** : correctness, même nature que `setXYWH`.

### `org.graphiks.math.SkIRect.offset` ↔ `SkIRect::offset`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:338-340`
- **C++** : `include/core/SkRect.h:372-377`
- **Nature** : saturating add manquant.
- **Détail** : les 4 `+=` Kotlin wrap. C++ utilise `Sk32_sat_add` partout. Idem pour la surcharge `offset(delta: SkIPoint)`.
- **Impact estimé** : correctness aux bornes ; clip-rect / scroll-offset upstream s'attendent à des bornes saturées.

### `org.graphiks.math.SkIRect.offsetTo` ↔ `SkIRect::offsetTo`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:344-349`
- **C++** : `include/core/SkRect.h:399-404`
- **Nature** : saturating add manquant — calcul en 32-bit au lieu de 64-bit pinned.
- **Détail** : C++ `fRight = Sk64_pin_to_s32((int64_t)fRight + newX - fLeft)`. Kotlin fait `right += newX - left` en int32 → double overflow possible.
- **Impact estimé** : correctness aux bornes.

### `org.graphiks.math.SkIRect.inset` / `outset` ↔ `SkIRect::inset` / `outset`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:351-355`
- **C++** : `include/core/SkRect.h:416-433`
- **Nature** : saturating add/sub manquant.
- **Détail** : Kotlin utilise `+`/`-` ; C++ utilise `Sk32_sat_add` (gauche/top) et `Sk32_sat_sub` (droite/bas). `outset` Kotlin appelle `inset(-dx, -dy)` — propage la divergence.
- **Impact estimé** : correctness.

### `org.graphiks.math.SkIRect.adjust` ↔ `SkIRect::adjust`
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:357-359`
- **C++** : `include/core/SkRect.h:451-456`
- **Nature** : saturating add manquant.
- **Détail** : C++ utilise `Sk32_sat_add` sur les 4 composants ; Kotlin `+=`.
- **Impact estimé** : correctness.

### `org.graphiks.math.SkIRect.makeOffset` / `makeInset` / `makeOutset` ↔ équivalents C++
- **Kotlin** : `math/src/main/kotlin/SkRect.kt:371-377`
- **C++** : `include/core/SkRect.h:305-360`
- **Nature** : saturating arithmetic manquant.
- **Détail** : Kotlin construit le rect avec `+`/`-` plain ; C++ utilise `Sk32_sat_add` / `Sk32_sat_sub`.
- **Impact estimé** : correctness aux bornes.

## SIMD-only (skip — résultat équivalent attendu)

Aucun.

## À vérifier

Aucun. (Les divergences saturating ci-dessus sont systématiques et faciles à corriger : appeler `SkIPoint.sk32SatAdd` / `sk32SatSub` partout.)
