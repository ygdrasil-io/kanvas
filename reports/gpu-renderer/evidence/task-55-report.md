# W79 — contrat partagé des hairlines de path

## Résultat

Le contrat d'admission de la route `native.path_hairline.direct` est maintenant
défini une seule fois dans `gpu-renderer` via
`isBoundedNativePathHairline()`.

Le planner, le semantic builder Kanvas et le mapping de coverage réutilisent ce
même contrat. Ils ne peuvent donc plus diverger sur les points suivants :
segment unique, largeur zéro, `Butt`/`Miter`, absence d'anti-aliasing, absence de
dash/path effect, transform identité/translation, clip sans stencil, matériau
uni, `SRC_OVER`, scope racine, coordonnées finies et axe horizontal ou vertical.

## Vérification

```text
NativePathHairlineContractTest — horizontal/vertical acceptés,
  scale/AA/diagonale refusés — PASSED
FirstRoutePlannerTest — route native, refus AA et capability directe — PASSED
GPUFramePathApiInventoryTest — quad device direct — PASSED
GPUFramePathApiInventoryNativeSmokeTest — rendu WebGPU avec oracle RGBA CPU — PASSED
```

Le smoke test natif confirme `Succeeded`, un submit, un readback et le même
rendu pixel que l'oracle indépendant. Aucun nouveau cas n'est promu : les
variantes hors contrat restent refusées. Aucun PNG, score GM, seuil de
promotion ou `gpu-renderer-scenes` n'a été modifié.
