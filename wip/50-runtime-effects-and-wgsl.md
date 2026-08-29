# WIP 50 — runtime effects enregistrés et WGSL

> Brief d'exécution de `W50` à `W53`. `SkRuntimeEffect` reste une façade de
> compatibilité; Kanvas ne compile pas de SkSL arbitraire.

## Fichiers propriétaires

| Zone | Fichiers |
| --- | --- |
| API/registry | `../kanvas/src/main/kotlin/org/graphiks/kanvas/pipeline/RuntimeEffect.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/pipeline/RuntimeEffectWgsl4kWiring.kt` |
| Lowering | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/RuntimeEffectMaterialLowering.kt` |
| WGSL | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/` |
| Mesh consumers | `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ColorFilter.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ImageFilter.kt` |
| GMs | `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/runtime_effect/` |

## W50 — descriptor registry

- [ ] Sélectionner au moins trois effets représentatifs : color-only, coordinate
      dependent et uniform-array/matrix.
- [ ] Définir pour chacun ID/version, uniform schema, Kotlin/CPU behavior et
      module WGSL parsé.
- [ ] Tester lookup, version, kind et descriptor inconnu.
- [ ] Vérifier que le descriptor est la seule entrée vers la route GPU.
- [ ] Promouvoir une scène par descriptor et un refus unregistered.

## W51 — children

- [ ] Tester shader, color-filter et blender children.
- [ ] Tester ordre, optional/null child, child kind mismatch et profondeur.
- [ ] Tester uniform + child + local coordinates dans le même descriptor.
- [ ] Vérifier que CPU et GPU utilisent la même arborescence sémantique.

## W52 — ABI et reflection

- [ ] Tester scalar/vector/matrix/array alignment et padding.
- [ ] Tester uniform slab, bindings, group/index et reflection parser.
- [ ] Tester cache key avec descriptor version, uniforms et children.
- [ ] Refuser layout/binding mismatch avant pipeline creation.
- [ ] Vérifier que les dumps n'exposent aucun padding non initialisé.

## W53 — frontières runtime

- [ ] Prouver RuntimeEffect comme Shader, ColorFilter, Blender et ImageFilter
      pour des descriptors enregistrés compatibles.
- [ ] Refuser SkSL arbitraire, descriptor absent, kind mismatch et WGSL invalide.
- [ ] Refuser tout besoin de dynamic compilation ou de VM cachée.
- [ ] En cas d'ambiguïté `wgsl4k`, produire une reproduction minimale et ouvrir
      le ticket amont avant de poursuivre.

## Sortie

Le programme est fermé quand un effet enregistré est portable CPU/GPU par son
descriptor et qu'un effet inconnu ne peut atteindre ni module builder, ni
pipeline cache, ni submission.

## Vérification

```bash
./gradlew :kanvas:test --tests '*RuntimeEffect*'
./gradlew :gpu-renderer:test --tests '*RuntimeEffect*' --tests '*Wgsl*' --tests '*ShaderAbi*'
./gradlew :integration-tests:gpu-evidence:test --tests '*RuntimeEffect*'
./gradlew :integration-tests:skia:test --tests '*Runtime*'
```
