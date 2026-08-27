# Task 7 — filtres simples et DAG borné (2026-08-27)

## Résultat

Le seul sous-ensemble activé est le DAG déjà exécuté nativement pour un
`MaskFilter.Blur` sur `FillRect` borné : `mask -> blur-h -> blur-v -> style ->
composite`. Il emploie un masque local, quatre textures intermédiaires
frame-locales et une taille de kernel explicite (`sigma=2`, 5 taps actifs,
ABI de 25 poids). La dépendance source est le masque de rectangle local ; aucune
source image externe, lecture CPU ou fallback de masque n’est injecté.

La fixture `bounded-mask-blur-rect-v1` est CPU/GPU : l’oracle
`TopLevelMaskBlurPixelOracle` est indépendant des handles WebGPU et compare les
4 096 canaux du readback 32×32. La route native reporte une opération dispatchée
et zéro refus. Le seuil déjà existant de l’oracle est 24 par canal ; il n’a pas
été modifié. Les métriques de performance sont descriptives, non-gating.

## GMs et refus

Aucun GM n’est promu. Les tentatives Surface fraîches sont verrouillées par
`SimpleFilterGmSurfaceRefusalEvidenceTest` :

| GM | opérations | diagnostic terminal |
| --- | ---: | --- |
| `blurrects` | 145 | `unsupported.material.source_unimplemented` |
| `offsetimagefilter` | 17 | `unsupported.image.native_binding` |
| `blurquickreject` | 19 | `unsupported.stroke.width_invalid` |

`blurquickreject` reste explicitement hors contrat : son hairline/stroke est
refusé avant toute promotion du blur. `offsetimagefilter` reste refusé car le
binding image préparé natif complet manque ; le DAG offset n’est donc pas
présenté comme exécuté.

## Artefacts et vérification

Les artefacts CPU/GPU/diff/stats/route/refus sont sous
`reports/gpu-renderer/evidence/bounded-mask-blur-rect-2026-08-27/`.

```text
rtk ./gradlew --no-daemon :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUMaskBlurSurfaceTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUMaskBlurDispatchTest \
  :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SimpleFilterGmSurfaceRefusalEvidenceTest
```

Résultat : succès. Aucun Ganesh, Graphite, SkSL dynamique ou
`gpu-renderer-scenes` n’est utilisé.

## Concerns

- La promotion est strictement celle de la fixture rectangle/mask déjà native,
  pas de `blurrects` ni d’`offsetimagefilter` complet.
- Les image-filter DAGs avec source image, les transforms, hairlines et strokes
  restent des refus stables jusqu’à preuve native équivalente.
