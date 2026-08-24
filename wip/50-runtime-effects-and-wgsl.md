# WIP 50 — Runtime effects enregistrés, WGSL et layouts

> Document temporaire. `SkRuntimeEffect` est une façade de compatibilité : ce
> lot ne permet ni compilation dynamique SkSL, ni VM, ni workaround caché.

## Objectif du groupe

Prouver que chaque runtime effect supporté est un descriptor Kanvas enregistré,
avec comportement CPU associé, WGSL parsé/réfléchi et bindings matériels
conformes. Tous les autres effets sont refusés avant création de pipeline.

## Code et tests à lire

| Zone | Fichiers principaux |
| --- | --- |
| Registry/dispatch | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasRuntimeEffectRegistry.kt`, `KanvasCustomRuntimeEffectRegistry.kt`, `GPURuntimeEffectDispatch.kt`, `GPURuntimeEffectExecutor.kt` |
| Lowering material | `.../materials/RuntimeEffectMaterialLowering.kt`, `GPUPreparedRuntimeEffectChildProgramAuthority.kt` |
| WGSL/reflection | `.../wgsl/WgslModuleCatalog.kt`, `WgslModuleAbi.kt`, `WgslReflection.kt`, `Wgsl4kReflectionReportConsumer.kt` |
| Descriptors/shaders | `.../runtimeeffects/*Descriptor.kt`, `.../wgsl/*Wgsl.kt` |
| Evidence | `integration-tests/gpu-evidence/.../programs/RendererRefusalPrograms.kt`, `.../catalog/GpuEvidenceCatalog.kt` |

## Matrice de scénarios

| Sous-famille | Cas à couvrir | Résultat exigé |
| --- | --- | --- |
| Descriptors enregistrés | Un scénario par descriptor, entrées usuelles, bornes de valeurs, uniforme scalaire/vector/matrice/tableau et uniforme non défini. | Même résultat CPU/GPU, ID du descriptor et programme WGSL signalés dans la route. |
| Children | Aucun child, shader child, image child, plusieurs children ordonnés, child absent et type incompatible. | Ordre et bindings conservés ; refus avant draw sur child impossible. |
| Layout/packer | Alignement, padding, offsets, taille finale, ordre des bindings, sampler/texture et tableau aux bornes. | Octets packés égaux au layout réfléchi ; le changement de valeur seule ne recrée pas le pipeline. |
| WGSL | Parse, reflection et impression déterministe de chaque module généré/enregistré ; entry point, binding ou type invalide. | Parse/diagnostic reproductible ; aucune supposition silencieuse quand wgsl4k est ambigu. |
| Refus | Effect inconnu, custom effect non enregistré, uniforme absent/en trop, type faux et WGSL non compatible. | Code stable, zéro pipeline/draw/submission et aucun artefact de réussite. |

## Assertions de preuve

Les tests de rendu passent par `Surface`, conservent les sources WGSL et la
sortie de reflection dans les diagnostics, comparent CPU/GPU et mesurent
draw/pipeline/fallback. En cas de comportement ambigu du parser/IR/generator,
réduire l'entrée, conserver l'évidence et ouvrir un ticket wgsl4k : ne pas
ajouter une transformation Kanvas non documentée.

## Dépendances et sortie

Peut démarrer après le lot 00 et s'exécuter en parallèle avec 10, 30, 40 et 60.
Il touche le catalogue partagé à l'intégration, donc les commits de scène sont
rebasés après le lot qui l'a modifié en dernier. Sa sortie est une matrice
descriptor/layout/refus entièrement exécutable.

## Vérification

```bash
./gradlew :gpu-renderer:test
./gradlew :integration-tests:gpu-evidence:test --tests '*Runtime*' --tests '*Wgsl*'
./gradlew :integration-tests:gpu-evidence:test
```
