# Audit — `SkPoint.kt` ↔ `include/private/base/SkPoint_impl.h` + `src/core/SkPoint.cpp`

## Statistiques
- 36 symboles audités (depuis la TSV)
- 35 alignés
- 0 divergences fonctionnelles
- 0 divergences SIMD-only
- 1 à-vérifier

## Divergences fonctionnelles

Aucune. L'algorithmique upstream est reproduite à l'identique :

- `Length(x, y)` : fast-path float (`x*x + y*y` puis `sqrt`), fallback double sur overflow. Identique côté Kotlin (`math/src/main/kotlin/SkPoint.kt:136-144`) et C++ (`src/core/SkPoint.cpp:79-88`).
- `setPointLength` (Kotlin, lignes 176-190) reproduit `set_point_length<false>` (`src/core/SkPoint.cpp:42-69`) y compris la double-précision pour gérer overflow et underflow, la division IEEE (`length / dmag` peut produire `±Inf`/NaN, attrapé par `!isFinite || (0,0)`), et le zéro-fallback final.
- `Normalize`, `setNormalize`, `setLength` délèguent au même core, comme upstream.
- Op overloads (`+=`, `-=`, `*`, `*=`, `+`, `-`, unary `-`), `setAbs`, `negate`, `offset`, `isFinite`, `isZero`, `equals(x,y)`, `dot`, `cross`, `Distance`, `DotProduct`, `CrossProduct` — formules identiques.
- `equals(x, y)` utilise raw `==` (`fX == x && fY == y`) — NaN-asymétrique comme C++, doc explicite.

## SIMD-only (skip — résultat équivalent attendu)

Aucun usage SIMD côté upstream pour ce header.

## À vérifier

### `data class equals` Kotlin vs C++ `operator==`
- **Kotlin** : `SkPoint.kt:21` (auto-généré par `data class`)
- **C++** : `SkPoint_impl.h:432-434`
- **Nature** : sémantique NaN différente. Kotlin `data class` utilise `Float.equals` → `NaN.equals(NaN) == true`. C++ raw `==` → `NaN == NaN` est faux.
- **Détail** : la divergence est documentée volontairement dans le Javadoc (`SkPoint.kt:16-19`). La méthode [equals]`(x, y)` (NaN-stricte) couvre le cas où les callers ont besoin de la sémantique C++. À reviewer : faut-il auditer les call-sites de `==`/`equals` pour confirmer qu'aucun caller hot-path ne dépend de la sémantique IEEE.
- **Impact estimé** : aucun (intentionnel, documenté). `hashCode` reste cohérent (NaN hash égal).
