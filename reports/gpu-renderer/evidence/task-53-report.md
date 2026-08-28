# W77 — hairline de path simple

## Résultat

Une route native minimale est maintenant admise pour une hairline de path
(trait de largeur `0`) uniquement si elle respecte le contrat suivant : un seul
segment horizontal ou vertical, deux points, `Butt`/`Miter`, sans anti-aliasing,
sans dash ni path effect, et transform identité ou translation.

Le lowering Kanvas convertit ce segment en un quad device-space d'épaisseur un
pixel, avec deux triangles (`DirectTriangles`). L'analyse GPU et le lowering
partagent désormais le même périmètre : aucune route n'est acceptée sans
géométrie matérialisable.

## Refus conservés

Les variantes multi-segments, diagonales, AA, cap/join non supportés, dash,
perspective ou scale restent refusées explicitement. Le cas de régression
existant avec une hairline sous scale continue de produire
`unsupported.core_primitive.stroke.hairline_exact_lowering`.

## Vérification

```text
:gpu-renderer:test
  FirstRoutePlannerTest.fill path single segment hairline builds native direct route — PASSED
  FirstRoutePlannerTest.fill path hairline with AA remains refused — PASSED

:kanvas:test
  GPUFramePathApiInventoryTest.single axis aligned hairline lowers to direct device quad — PASSED
  GPUFramePathApiInventoryTest.single segment hairline with scale remains refused before native preparation — PASSED
```

Le test positif vérifie la géométrie `DirectTriangles`, les indices
`0,1,2,0,2,3`, les deux points source et la couverture pixel attendue
`GPUPixelBounds(4, 7, 14, 9)`. Aucun PNG, score, seuil, scène ou rebaseline
n'a été modifié.

## Preuve native

`GPUFramePathApiInventoryNativeSmokeTest.single horizontal hairline renders one
pixel row natively` est passée avec un vrai backend WebGPU/offscreen. Le test
vérifie un oracle RGBA CPU indépendant (la ligne rouge sur la rangée pixel 15),
`GPUFrameStructuralOutcome.Succeeded`, `submits=1` et `readbackCopies=1`.

Cette première exécution a révélé puis corrigé une incompatibilité de contrat :
le quad direct était initialement classé dans la lane `Stencil1x`. Le mapping de
coverage l'envoie désormais dans `FullOrScissor`, cohérent avec
`DirectTriangles`; elle est publiée comme `native.path_hairline.direct`.
La route native est donc matérialisée et exécutée, pas seulement admise par
l'analyse.
