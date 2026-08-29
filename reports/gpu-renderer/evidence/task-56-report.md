# W80 — hairline sous scale uniforme

## Résultat

Le contrat hairline accepte maintenant un `Scale` uniforme strictement positif
(`scaleX == scaleY > 0`) en plus de l'identité et de la translation. Le segment
reste horizontal ou vertical après transformation et est toujours abaissé en
quad device-space d'un pixel. Les scales non uniformes, négatifs ou affines
restent refusés.

## Preuves

```text
NativePathHairlineContractTest — scale uniforme accepté ; scale non uniforme,
  scale négatif, AA et diagonale refusés — PASSED
FirstRoutePlannerTest — route native et refus existants — PASSED
GPUFramePathApiInventoryTest.single segment hairline with uniform scale
  lowers to direct device quad — PASSED
GPUFramePathApiInventoryNativeSmokeTest.single horizontal hairline with
  uniform scale renders one pixel row natively — PASSED
```

Le smoke test rend le segment local `(4,8)–(14,8)` avec un scale `(2,2)` ;
l'oracle RGBA CPU indépendant attend la ligne rouge device `(8,16)–(28,16)`.
Le résultat est `Succeeded`, avec un submit et un readback.

## Limites

Cette vague ne généralise pas les transformations : rotation, skew,
perspective, scale non uniforme et variantes AA restent hors contrat. Aucun
PNG ou score GM suivi, seuil de promotion, rebaseline ou `gpu-renderer-scenes`
n'a été modifié. Le replay `dstreadshuffle` reste refusé avant rendu car il
combine AA, rotations et autres opérations hors de cette route.
