# W27 — bounded `saveLayer` SrcOver

## Portee materialisee

Le cas `bounded-save-layer-src-over-opacity` valide la plus petite forme de
compositing `saveLayer` materialisable nativement par le renderer WebGPU : une
seule couche RGBA8 isolee, aux bornes device entieres `[8,8,56,56]`, avec deux
enfants `DrawRect` opaques sans anti-aliasing. La restauration applique une
opacite de `128 / 255` une seule fois avec `BlendMode.SRC_OVER`, au-dessus d'un
fond `DrawRect` opaque.

Le point de depart de l'evidence est
`5329f0d349c6c878977948121a93c47d1653ccd6`.

## Evidence promue

Les manifests v2 et les artefacts sont sous :

- `correctness/promoted/bounded-save-layer-src-over-opacity/`
- `correctness/promoted/bounded-save-layer-restore-blend-refusal/`

Le cas positif utilise l'oracle CPU independant
`surface-srgb-save-layer-src-over-opacity` (v2) : compositing en sRGB
linearise et premultiplie, couche transparente, enfants bleu puis orange,
opacite de groupe, puis SrcOver sur le fond. Ses valeurs representatives sont
`[13,20,33,255]` (fond), `[24,84,155,255]` (bleu) et
`[178,99,40,255]` (recouvrement orange). Il produit `similarityPercent=100`,
`differingPixels=0` et `maxChannelDifference=0` avec une tolerance de 2.

`route.json` enregistre une voie `kanvas.surface.render` terminee, une
soumission native, quatre `render.draw`, aucun snapshot/copy de destination et
aucune cible MSAA. `diagnostics.json` est vide.

Le refus `bounded-save-layer-restore-blend-refusal` essaie le meme contrat mais
remplace le blend de restauration par `MULTIPLY`. Il est refuse avant rendu
avec `unsupported.layer.restore_blend`; son `route.json` enregistre
`queue.submit=0`, `submissions=0` et aucune texture creee.

## Limites explicites

Cette evidence ne revendique pas la prise en charge de lecture de destination,
filtres, blend autre que SrcOver, couches imbriquees, MSAA, ni transforms hors
du sous-ensemble borne. Le backing target actuel suit l'ABI de coordonnees de
la scene; les budgets de frame peuvent toujours refuser une allocation.
`CoverageMask+Path` B3.4 reste hors de cette promotion, bloque par la
materialisation backend.

## Reproduction

```text
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence \
  -PsourceCommit=5329f0d349c6c878977948121a93c47d1653ccd6 \
  -PscenesFile=<fichier contenant les deux sceneId W27>
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence \
  -PsourceCommit=5329f0d349c6c878977948121a93c47d1653ccd6 \
  -PscenesFile=<fichier contenant les deux sceneId W27>
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
