# Audit — `SkPathOpsTypes.kt` ↔ `src/pathops/SkPathOpsTypes.{h,cpp}`

## Statistiques
- 99 symboles audités (depuis `.upstream/source/map/math/SkPathOpsTypes.tsv`)
- 96 alignés
- 0 divergence fonctionnelle bloquante
- 1 divergence constante mineure (extension Kotlin — `approximately_positive_double`)
- 2 cas à vérifier (paramètre `depsilon` non-utilisé en C++ vs Kotlin, et `argumentsDenormalized` arrondi Float)

## Divergences fonctionnelles

_Aucune correctness-blocker._ Les ULPs primitives, leurs publics overloads Float/Double, les ~70 prédicats `approximately_*` / `precisely_*` / `roughly_*` reproduisent les bodies upstream avec les mêmes constantes, le même ordre d'évaluation FP et le même bit-pattern reinterpret.

Vérifications-clés effectuées :
- **`signBitTo2sComplement`** (kt:62-63) — équivalent strict à `SkSignBitTo2sCompliment` upstream (`src/base/SkFloatBits.h:20-26`) : `if x<0 → x = -(x & 0x7FFFFFFF)`. Identique au bit.
- **`floatAs2sComplement`** (kt:65) ↔ `SkFloatAs2sCompliment` (FloatBits.h:59-61) : utilise `Float.toRawBits()` = `SkFloat2Bits` (`memcpy uint32_t`).
- **`equalUlps`** (kt:72-77 ↔ cpp:25-33) : early-return sur `arguments_denormalized(a, b, depsilon)`, sinon comparaison `aBits < bBits + epsilon && bBits < aBits + epsilon`. ✓
- **`equalUlpsPin`** (kt:85-91 ↔ cpp:42-53) : early-return `false` si non-fini ; sinon denorm check + bit compare. ✓
- **`notEqualUlps`**, **`notEqualUlpsPin`**, **`dEqualUlps`**, **`dNotEqualUlps`**, **`lessUlps`**, **`lessOrEqualUlps`** (kt:99-133 ↔ cpp:62-110) : guards, branches denorm et formules identiques.
- **Publics ULPs** (kt:137-186) : tous les `epsilon=16/8/2/256` matchent exactement les constantes hardcodées dans `cpp:114-187` (`AlmostBequalUlps=2`, `AlmostPequalUlps=8`, `AlmostEqualUlps=AlmostDequalUlps=AlmostEqualUlpsNoNormalCheck=AlmostEqualUlpsPin=NotAlmost*=AlmostLess*=16`, `RoughlyEqualUlps=256/depsilon=1024`, `AlmostBetweenUlps=2`).
- **`AlmostDequalUlps(double)`** (kt:153-161 ↔ cpp:128-136) : branche fast-path `< SK_ScalarMax` → cast Float, fallback `|a-b| / max(|a|,|b|) < FLT_EPSILON*16`. Le `sk_ieee_double_divide` upstream est équivalent à `denom == 0 → false` puis `/` IEEE en Kotlin (NaN propagation → comparaison false dans les deux mondes). ✓
- **`UlpsDistance`** (kt:193-200 ↔ cpp:190-201) : `toRawBits()`, signs différents → `MaxS32` sauf si `a == b` (gestion `+0 == -0`), sinon `|aBits - bBits|`. ✓
- **`approximately_*` family** (kt:206-287 ↔ h:323-551) : 70+ helpers — chaque seuil littéral, chaque signe `>`/`>=`/`<`/`<=`, chaque addition/soustraction `± FLT_EPSILON*`, chaque test `x == 0 ||` est vérifié identique.
- **`between`** (kt:279 ↔ h:530-534) : `(a-b)*(c-b) <= 0`. (L'assert upstream `precisely_zero(a/b/c)` n'a pas d'équivalent runtime — pas un divergence.)
- **`SkDInterp`** (kt:292) : `A + (B-A)*t` — strict match h:581-583.
- **`SkDSign`** (kt:295) : `(x>0 ? 1 : 0) - (x<0 ? 1 : 0)` ≡ C++ `(x>0)-(x<0)`. ✓
- **`SKDSide`** (kt:298) : `(x>0)+(x>=0)`. ✓
- **`SkDSideBit`** (kt:301) : `1 shl SKDSide(x)`. ✓
- **`SkPinT`** (kt:304-308 ↔ h:603-605) : seuils `precisely_less_than_zero` / `precisely_greater_than_one` (= `< DBL_EPSILON_ERR` et `> 1 - DBL_EPSILON_ERR`). ✓

## Divergences epsilon / constante

### `FLT_EPSILON` et `DBL_EPSILON` (Kotlin littéraux vs C `<cfloat>`)
- **Kotlin** : `math/src/main/kotlin/SkPathOpsTypes.kt:25` = `1.1920928955078125e-7` (Double = `0x1.0p-23` exact).
- **C++** : macro libc `FLT_EPSILON` (float) = `0x1.0p-23f`. Promotion à Double pour `const double FLT_EPSILON_* = FLT_EPSILON * …` → résultat IEEE-754 identique au bit.
- **Kotlin** : kt:28 `DBL_EPSILON = 2.220446049250313e-16` (= `0x1.0p-52`).
- **C++** : macro libc `DBL_EPSILON` = `0x1.0p-52`.
- **Détail** : équivalents bit-pour-bit. ✓

### Constantes dérivées (kt:30-49 ↔ h:305-319)
Toutes vérifiées :
- `FLT_EPSILON_CUBED` = `FLT_EPSILON³` — both compile-time.
- `FLT_EPSILON_HALF` = `/2`. ✓
- `FLT_EPSILON_DOUBLE` = `*2`. ✓
- `FLT_EPSILON_ORDERABLE_ERR` = `*16`. ✓
- `FLT_EPSILON_SQUARED` = `²`. ✓
- `FLT_EPSILON_SQRT` = **`0.00034526697709225118`** — littéral 17-digit identique au upstream (h:312). Ni recomputed via `sqrt` runtime, ni divergent au bit.
- `FLT_EPSILON_INVERSE` = `1 / FLT_EPSILON`. ✓
- `DBL_EPSILON_ERR` = `DBL_EPSILON * 4`. ✓
- `DBL_EPSILON_SUBDIVIDE_ERR` = `DBL_EPSILON * 16`. ✓
- `ROUGH_EPSILON` = `FLT_EPSILON * 64`. ✓
- `MORE_ROUGH_EPSILON` = `FLT_EPSILON * 256`. ✓
- `WAY_ROUGH_EPSILON` = `FLT_EPSILON * 2048`. ✓
- `BUMP_EPSILON` = `FLT_EPSILON * 4096`. ✓

### Extension Kotlin : `approximately_positive_double`
- **Kotlin** : `math/src/main/kotlin/SkPathOpsTypes.kt:262` — `x > -FLT_EPSILON_DOUBLE`.
- **C++** : _aucun équivalent_ — le upstream n'a que `approximately_positive` (`> -FLT_EPSILON`) et `approximately_zero_or_more_double` (`> -FLT_EPSILON_DOUBLE`).
- **Détail** : helper Kotlin ajouté (probablement pour cohérence d'API avec les autres `_double` variants ; sa valeur est identique à `approximately_zero_or_more_double`). Sans impact sur la correction si non utilisé en place d'un helper distinct upstream.
- **Impact estimé** : aucun (ajout pur).

### `INVERSE_NUMBER_RANGE`
- **C++** : `src/pathops/SkPathOpsTypes.h:321` — `const SkScalar INVERSE_NUMBER_RANGE = FLT_EPSILON_ORDERABLE_ERR;`
- **Kotlin** : non porté (alias trivial). Sans usage dans les 3 fichiers audités — à vérifier si nécessaire ailleurs.
- **Impact estimé** : aucun (alias d'une constante déjà publique).

## À vérifier

### Paramètre `depsilon` inutilisé dans `equal_ulps_no_normal_check`
- **C++** : `src/pathops/SkPathOpsTypes.cpp:35` — `equal_ulps_no_normal_check(float, float, int epsilon, int depsilon)` mais `depsilon` n'est jamais lu (pas de denorm check).
- **Kotlin** : `math/src/main/kotlin/SkPathOpsTypes.kt:79` — signature simplifiée à `(a, b, epsilon)` (élimination du paramètre mort).
- **Détail** : refactor cosmétique. Le callsite Kotlin (kt:140) passe `16` ; le callsite C++ (cpp:145) passe `(16, 16)`. Résultat identique.
- **Impact estimé** : aucun.

### Arrondi `argumentsDenormalized` — Double vs Float intermédiaire
- **Kotlin** : `math/src/main/kotlin/SkPathOpsTypes.kt:67-70` — `val denorm = (FLT_EPSILON * epsilon / 2).toFloat()` (calcul en Double, conversion finale Float).
- **C++** : `src/pathops/SkPathOpsTypes.cpp:18-21` — `float denormalizedCheck = FLT_EPSILON * epsilon / 2` (calcul en float dès le départ, `FLT_EPSILON` étant `float`).
- **Détail** : pour les valeurs utilisées (`epsilon ∈ {2, 8, 16, 256, 1024}`), `FLT_EPSILON * epsilon / 2` = `2^(-23) * 2^k` = puissance de 2 exacte représentable en Float comme en Double — la conversion intermédiaire est sans perte. Mais pour des `epsilon` arbitraires non-puissances-de-2 (cas hypothétique), Kotlin pourrait diverger d'un ULP. À surveiller si de nouveaux appels apparaissent.
- **Impact estimé** : aucun en pratique pour les usages actuels ; à vérifier à chaque nouvel appel à `equalUlps` etc.

### `lessUlps` / `lessOrEqualUlps` — branche denorm
- **C++** : `cpp:92-100` / `cpp:102-110` — `return a <= b - FLT_EPSILON * epsilon;` / `return a < b + FLT_EPSILON * epsilon;` (calcul en float — `FLT_EPSILON` est macro float).
- **Kotlin** : `kt:121-126` / `kt:128-133` — `return a <= b - FLT_EPSILON.toFloat() * epsilon` / `return a < b + FLT_EPSILON.toFloat() * epsilon`.
- **Détail** : `FLT_EPSILON.toFloat()` retourne `0x1.0p-23f` exact ; `epsilon` est Int converti implicitement à Float ; `b - (Float * Float)` en Float. Identique au C++.
- **Impact estimé** : aucun. ✓ (rangé ici pour traçabilité, pas une vraie divergence.)
