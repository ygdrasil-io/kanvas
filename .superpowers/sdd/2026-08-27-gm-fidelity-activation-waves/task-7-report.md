# Task 7 — filtres simples et DAG borné (2026-08-27)

## Résultat

Le seul sous-ensemble activé est le DAG déjà exécuté nativement pour un
`MaskFilter.Blur` sur `FillRect` borné : `mask -> blur-h -> blur-v -> style ->
composite`. Il emploie un masque local, quatre textures intermédiaires
frame-locales et une taille de kernel explicite (`sigma=2`, 5 taps actifs,
ABI de 25 poids). La dépendance source est le masque de rectangle local ; aucune
source image externe, lecture CPU ou fallback de masque n’est injecté.

La fixture `bounded-mask-blur-rect-v1` est CPU/GPU : l’oracle dédié
`BoundedMaskBlurRectCpuOracle` réimplémente localement masque, kernel gaussien,
quantification RGBA8 intermédiaire, style NORMAL et composite noir opaque. Il
ne dépend ni de `MaskBlurPlanner`, ni du builder de kernel de production, ni de
payload WebGPU. Il calcule le buffer CPU 32×32 complet : son SHA-256 et celui du
readback GPU sont tous deux
`9735248adde7e8e966a03d90fe43ea70c468be2ddd748384985c2b9706dd1bae`, avec
`differentChannels=0`, `maxDelta=0` et `meanDelta=0.0`. La comparaison de cette
fixture est donc byte-exacte, sans tolérance 24.

La route contient `logicalOperations=1`, mais exactement `passes=5`
(`mask -> blur-h -> blur-v -> style -> composite`) ; ce ne sont pas cinq
opérations logiques ni un unique dispatch visuel. Les métriques restent
descriptives, non-gating.

L’admission est désormais bornée avant toute allocation intermédiaire : `sigma`
doit être fini et dans `0..12`; `sigma=12` utilise les 25 taps du kernel statique
et `NaN`, les infinis, les valeurs négatives ou `sigma=200` refusent de façon
stable avec `unsupported.mask-filter.blur.sigma`. Aucun clamp ou rendu réduit
n’est appliqué.

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
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanTest \
  :kanvas:test \
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
- La limite `sigma=12` est volontairement plus étroite que l’ancien chemin qui
  clampait/réduisait la résolution : ce dernier ne satisfait pas le contrat de
  fidélité bornée de cette vague.
