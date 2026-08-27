# Task 8 — compositing borné et blend `SRC` / `SRC_OVER` (2026-08-27)

## Résultat

Le sous-ensemble activé est une `saveLayer` finie, isolée, unique et enfant de
la cible racine. La texture de layer est clear à transparent, les enfants sont
rendus dans cette texture, puis le restore emploie un blend WebGPU fixed-function
`SRC_OVER` ou `SRC`. L’opacité du paint de layer est appliquée à la source
premultiplied échantillonnée avant le blend. Le restore ne lit jamais la cible
parente (`destinationRead=none`) et n’échantillonne jamais son attachment actif.

La nouvelle fixture `bounded-save-layer-src-opacity-isolation-v1` verrouille le
cas `SRC` réellement matérialisé : cible 4×4 headless/offscreen, parent bleu
opaque, enfant rouge dans la région bornée `[1,1)-[3,3)`, alpha de layer 0,5.
L’oracle CPU local écrit explicitement les 64 canaux RGBA8 attendus, sans dépendre
du planner ou du materializer. Le readback GPU a le même SHA-256
`7b8a10f512a72f3430e3e89e975556ba0b5d29246cbe9db8ddd5955652aa275c` :
`differentChannels=0`, `maxDelta=0`, `meanDelta=0.0`, avec un submit et une copie
de readback. Le test conserve une marge locale de deux unités seulement pour la
quantification sRGB inter-adapter ; la capture présente est byte-exacte. Le
smoke `SRC_OVER` à opacité partielle existant reste couvert par la même route.

## Routes et refus

La cible isolée est une resource frame-local `render_attachment,texture_binding`.
La séquence est `scene-parent -> isolated-layer-child -> layer-SRC-composite ->
readback`; il y a trois render passes, sans lecture de destination ni aliasing.
Les modes de restore hors `{SRC_OVER, SRC}`, les bounds non finis, l’aliasing,
les filtres, `initWithPrevious`, F16 et les layers imbriqués conservent leurs
refus stables.

Les GMs ne sont pas promus : la couverture requise n’est pas disponible avant
la couche bornée. Une exécution fraîche fige `srcmode` à
`invalid.preflight.text.blend` (texte avec blend hors contrat), et
`rasterallocator` à
`unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted` (ovale AA
précédant son `saveLayer`). Aucun refus AA n’est masqué ou transformé en succès.

Les artefacts CPU/GPU/diff/stats/route/refus sont sous
`reports/gpu-renderer/evidence/bounded-save-layer-src-2026-08-27/`.

## Vérification

```text
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kLayerTargetCompositeSmokeTest \
  --tests org.graphiks.kanvas.gpu.renderer.layers.SaveLayerIsolatedTargetGateTest \
  --tests org.graphiks.kanvas.gpu.renderer.layers.SaveLayerLiveMaterializationTest

rtk ./gradlew --no-daemon --rerun-tasks :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest

rtk ./gradlew --no-daemon --rerun-tasks :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.CompositingGmSurfaceRefusalEvidenceTest
```

## Concerns

- C’est une preuve de fixture bornée et native, pas une promesse de composition
  Skia générale ni de rendu de GM complet.
- Les métriques sont descriptives et non-gating ; aucun seuil global ou budget
  global n’a été modifié.
- Aucun Ganesh, Graphite, SkSL dynamique ou `gpu-renderer-scenes` n’est employé.
