# Task 3 — re-review qualité round 1

## Verdict

`Quality: FAIL`

## Critical — perte silencieuse publique

Avec `u = Float.fromBits(1f.toRawBits() + 2)`, le contour :

```kotlin
moveTo(1f, 1f).cubicTo(u, 1f, 1f, u, 1f, 1f).close()
```

est significatif : `PathOpsF32.simplify(C)` lève
`path-f32-projection-collapse`. En revanche, si `A` contient un rectangle
distant plus `C`, `PathOpsF32.op(A, C, INTERSECT)` renvoie un `PathF32` vide,
alors que `(rectangle union C) intersect C = C` et doit donc rejeter.

La cause est la classification isolée de chaque contour collapsed :
`INTERSECT(0,0)`, `(1,0)` et `(0,1)` sont faux, sans jamais évaluer l'effet
conjoint `(1,1)` vrai. Les aliases provisoires ont déjà supprimé les
contributions du DCEL/winding.

## Important

- La classification partielle rejette un contour dès qu'un carrier distant est
  sélectionné; les rays calculés ne servent pas à tester les deux voisins
  réels de l'incidence.
- Plusieurs voies locales sont quadratiques mais sous-débités : déduplication
  `firstOrNull` par cut, recherche cyclique des voisins, rescans faces/half-edges
  par contour et accumulation d'expansions d'aire.
- `maxVertices` est appliqué aux vertices initiaux plus identités de cuts avant
  union des aliases, ce qui peut rejeter à tort un n-way donnant un seul vertex
  canonique final.
- Le comparator de transactions peut retourner `0` dès qu'un côté est égal,
  sans ordre total; tri JVM/JS et équivalence de groupe peuvent diverger.
- Le no-face multi-contour rejette systématiquement, même lorsque l'effet
  conjoint prouve une sortie vide, par exemple sous `XOR`.

## Minor

Le seul changement de test est la frontière budgétaire `5_316` vers `5_773`;
aucune nouvelle branche du materializer/collapse n'a de couverture publique.

Full JVM+JS vert, 61 tâches; diff-check/status propres. Comptage source,
namespace structural, paramètres inversés, rematerialization des indices et
seuil inclusif ont été contrôlés positivement. Aucun changement hors périmètre.
