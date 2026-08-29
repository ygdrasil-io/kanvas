# W109 — cap square sous clip path Winding

## Objectif

Transformer la preuve de refus du cap `square` en preuve native pour un segment
diagonal mono-segment, non-AA, sous `ClipOp.INTERSECT` Winding.

## Cause et correction

Le mapper reconnaissait déjà ce stroke comme admissible et `GPUStroke` produisait
son outline exact sous forme de trois quads adjacents (corps, extension de début,
extension de fin). La préparation CorePrimitive refusait ensuite la composition
avec `StencilCoverage`, car les validators DirectTriangles n’acceptaient que le
proof `SingleSegmentButtV1` et la sémantique restait en `StrokeStencilEdgeFan`.

La correction reste bornée à `SingleSegmentSquareV1` : pour un contour unique de
deux points, l’outline square existant est réduit à ses quatre coins externes
device (8 coordonnées), avec les indices canoniques `[0,1,2,0,2,3]`, Winding
non-inverse et couverture `FullOrScissor`. Les validators du payload, du direct
native route et du frame builder acceptent désormais uniquement la paire
cohérente `cap=square` / `SingleSegmentSquareV1`. Les autres caps, joins, dashes
et topologies restent refusés.

## Preuve native

Le test `diagonal square cap stroke under winding path clip renders natively`
vérifie la route `native.path_stroke.stencil_cover`, le producer stencil
`IncrementWrap/DecrementWrap`, le consumer `NotEqual`, une préparation native
réussie, un submit et un readback uniques, puis compare le RGBA complet à un
oracle CPU indépendant. L’oracle étend le segment de la demi-largeur le long de
sa tangente et intersecte le résultat avec le triangle Winding au centre de
chaque pixel.

## Vérification

Commande ciblée :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.diagonal square cap stroke under winding path clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a également été
relancée avec succès, tous les tests PASS. `git diff --check` est propre.

## Limites

La promotion concerne uniquement le cap square mono-segment, join miter, sans
AA, sous clip path Winding non-inverse. Aucun seuil, PNG ou
`gpu-renderer-scenes` n’a été modifié.
