# Task 11 — Runtime effect WGSL enregistré (2026-08-27)

## Résultat

`runtime.linear_gradient_rt@1` est maintenant un runtime effect Kanvas fermé
et réellement consommé par la voie WebGPU headless/offscreen :

- `LinearGradientRTDescriptor` enregistre l’ABI uniforme de 64 octets
  (`start`, `end`, `startColor`, `endColor`) ;
- `LinearGradientRTCPUOracle` fournit le comportement Kotlin/CPU de clamp et
  interpolation deux stops ;
- `LinearGradientRTWgsl` est le seul module GPU. Le resolveur l’accepte
  seulement après `wgsl4k` `parseWgslResult`, `Lowerer` et comparaison de la
  réflexion ABI ;
- le smoke construit `GPUMaterialDescriptor.RuntimeEffect`, qui traverse
  `KanvasPreparedRuntimeEffectResolver`, `GPUPreparedMaterialProgramCompiler`,
  `GPUPreparedSurfaceFrameTaskListBuilder` et le materializer prepared-vertices
  avant le readback WebGPU. C’est le runtime effect enregistré qui est donc
  réellement lié et évalué ; la route directe
  `GPURegisteredUniformProgram.LinearGradient` reste un chemin gradient legacy
  distinct et n’est pas employée comme preuve.

Le smoke `GPUWgpu4kPreparedVerticesNativeSmokeTest` rend deux triangles couvrant
4×4 en `rgba8unorm-srgb` (LinearPremul). L’oracle CPU et le readback GPU ont
64 canaux, zéro différence,
`maxDelta=0`, un submit et une copie de readback. Les artefacts CPU/GPU/diff,
statistiques, route et refus sont sous
`reports/gpu-renderer/evidence/registered-linear-gradient-runtime-effect-2026-08-27/`.

## Refus GM conservés

- `lineargradientrt` ne peut pas être promu : sa référence
  `/reference/lineargradientrt.png` est absente. De plus, `LinearGradientRTGm`
  dessine des rectangles et ne construit pas `RuntimeEffect` ; aucun lien
  factice avec `runtime.linear_gradient_rt@1` n’est revendiqué. Aucun golden
  n’a été inventé.
- `runtimecolorfilter` reste refusé par
  `unsupported.core_primitive.material.non_solid`. Il utilise des effets de
  filtre runtime dynamiques ; cette tâche ne les transforme pas en compilation
  SkSL/WGSL arbitraire.
- `runtime.runtimecolorfilter` est absent du registre et le resolveur retourne
  précisément `Runtime-effect descriptor is not registered`.

Ces résultats sont des refus/non-promotions visibles, pas des succès de GM.

## Vérifications reproductibles

```sh
rtk ./gradlew --no-daemon :gpu-renderer:test --rerun-tasks \
  --tests org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolverTest \
  --tests org.graphiks.kanvas.gpu.renderer.runtimeeffects.LinearGradientRTDescriptorTest \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedVerticesNativeSmokeTest.registered linear gradient runtime effect materializes through the prepared WebGPU route'

rtk ./gradlew --no-daemon :integration-tests:skia:test --rerun-tasks \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=lineargradientrt -Dkanvas.render.debugLevel=PIXEL

rtk ./gradlew --no-daemon :integration-tests:skia:test --rerun-tasks \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=runtimecolorfilter -Dkanvas.render.debugLevel=PIXEL
```

Le premier groupe est vert sans `--rerun-tasks` : les 24 tests ciblés, dont le
smoke natif, passent sur Apple M2 Max. La tentative exacte avec
`--rerun-tasks` a été arrêtée par la limite d’exécution de 30 s de l’hôte
pendant la recompilation forcée des dépendances `font`; elle n’est pas
présentée comme verte. Les deux commandes GM échouent volontairement avec les
raisons sérialisées dans `refusals.json` ; elles servent de preuves de
frontière, non de tests verts.

## Concerns

Pas de SkSL dynamique, Ganesh, Graphite, fenêtre native, enfant runtime,
blender runtime, filtre runtime générique ou support large de
`runtimecolorfilter`. Si le parser `wgsl4k` devenait ambigu, le resolveur
refuserait le programme ; aucun workaround Kanvas n’est ajouté.
