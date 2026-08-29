# W141 — subdivision adaptative des courbes quadratiques

Date : 2026-08-29

## Objectif

Réduire la pression sur le budget de géométrie des chemins quadratiques sans
augmenter les limites WebGPU et sans transformer un refus en rendu partiel.
La modification reste dans `PathTessellator`, utilisé par le lowering natif
des chemins de Kanvas.

## Preuve déterministe

La fixture de test est un contour fermé formé de trois `quadTo`, avec une
tolérance de `0.25` pixel :

| Mesure | Ancien estimateur uniforme | W141 adaptatif borné |
| --- | ---: | ---: |
| Segments par courbe | 183 | 16 |
| Sommets du contour | 551 | 50 |
| Triangles du stencil edge-fan | 551 | 50 |

La subdivision utilise De Casteljau, mesure la distance du point de contrôle à
la corde, et est limitée à 16 niveaux. Le test vérifie aussi le nombre exact
de sommets, la topologie de l'edge-fan, les extrémités et le respect du budget
de 1 024 triangles.

## Portée et limites

Cette vague améliore la géométrie des fills quadratiques déjà admis par la
route `native.path_fill.stencil_cover`. Elle ne prétend pas promouvoir un GM
complet : `conicpaths` contient encore des variantes stroke/hairline refusées
par `unsupported.stroke.width_invalid`, et les coniques rationnelles gardent
leur estimateur uniforme jusqu'à l'introduction d'une vraie subdivision
rationnelle.

La politique de refus reste fail-closed : les budgets de vertices, triangles
et octets ne sont pas augmentés, et aucun fallback CPU caché n'est ajouté.

## Vérifications

```text
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.geometry.PathTessellatorTest' --no-daemon
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest' --no-daemon
```

Le second run est la preuve de non-régression du mapper public ; les cas de
quadratic fill sous clip restent explicitement refusés par la route de clip
actuelle.
