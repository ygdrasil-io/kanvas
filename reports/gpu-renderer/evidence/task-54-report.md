# W78 — capability dédiée pour les hairlines de path

## Résultat

La route `native.path_hairline.direct` ne dépend plus de
`first_slice.path_fill.stencil_cover`. Elle est maintenant gouvernée par la
capability explicite `first_slice.path_hairline.direct.native`.

La capability est publiée par le runtime WebGPU natif et par les product flags
lorsque le path fill et les strokes sont activés. La capability stencil garde sa
sémantique propre pour les routes qui utilisent réellement la couverture
stencil.

## Preuves

Les tests de `gpu-renderer` vérifient :

- la route hairline reste native avec la seule capability directe, sans
  capability stencil ;
- le runtime publie la capability avec son evidence label ;
- les product flags retirent la capability lorsque `pathFillEnabled` ou
  `strokeEnabled` est désactivé ;
- les refus AA et les contrats de route existants restent inchangés.

Le smoke test WebGPU de Kanvas a ensuite été rejoué avec le nouveau gating :

```text
GPUFramePathApiInventoryNativeSmokeTest.single horizontal hairline renders one pixel row natively — PASSED
GPUFramePathApiInventoryTest.single axis aligned hairline lowers to direct device quad — PASSED
```

Le rendu natif conserve l'oracle RGBA CPU indépendant, `Succeeded`, un submit et
un readback. Aucun PNG, score GM, seuil de promotion ou `gpu-renderer-scenes`
n'a été modifié.

## Limites conservées

La vague ne généralise pas les hairlines : diagonales, anti-aliasing, scale,
dash, path effects, clips complexes et scopes non racine restent refusés selon
le contrat W77. La centralisation des prédicats du planner, du semantic builder
et du mapper est laissée à une vague dédiée afin de ne pas élargir la surface de
changement sans preuve supplémentaire.
