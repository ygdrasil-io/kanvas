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
- `GPURegisteredUniformProgram.LinearGradient` enveloppe ce module pour la
  bind group ABI de la frame (`group(1)` du descripteur vers `group(0)` de la
  frame), sans compiler de source utilisateur.

Le smoke `GPUWgpu4kRegisteredLinearGradientRTSmokeTest` rend 4×4 en
`rgba8unorm`. L’oracle CPU et le readback GPU ont 64 canaux, zéro différence,
`maxDelta=0`, un submit et une copie de readback. Les artefacts CPU/GPU/diff,
statistiques, route et refus sont sous
`reports/gpu-renderer/evidence/registered-linear-gradient-runtime-effect-2026-08-27/`.

## Refus GM conservés

- `lineargradientrt` ne peut pas être promu : sa référence
  `/reference/lineargradientrt.png` est absente. Aucun golden n’a été inventé.
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
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kRegisteredLinearGradientRTSmokeTest

rtk ./gradlew --no-daemon :integration-tests:skia:test --rerun-tasks \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=lineargradientrt -Dkanvas.render.debugLevel=PIXEL

rtk ./gradlew --no-daemon :integration-tests:skia:test --rerun-tasks \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=runtimecolorfilter -Dkanvas.render.debugLevel=PIXEL
```

Le premier groupe est vert. Les deux commandes GM échouent volontairement avec
les raisons sérialisées dans `refusals.json` ; elles servent de preuves de
frontière, non de tests verts.

## Concerns

Pas de SkSL dynamique, Ganesh, Graphite, fenêtre native, enfant runtime,
blender runtime, filtre runtime générique ou support large de
`runtimecolorfilter`. Si le parser `wgsl4k` devenait ambigu, le resolveur
refuserait le programme ; aucun workaround Kanvas n’est ajouté.
