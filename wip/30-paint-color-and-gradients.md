# WIP 30 — Paint, couleur, blend et gradients

> Document temporaire. Les seuils de pixels sont propres à chaque famille et
> ne doivent jamais être élargis globalement pour absorber une régression.

## Objectif du groupe

Vérifier les sémantiques paint qui rendent les erreurs visuelles les plus
subtiles : alpha prémultiplié, couleur, blend et paramétrage des gradients.
Chaque combinaison sans route WebGPU explicite reste un refus, pas une
approximation par le renderer CPU.

## Code et tests à lire

| Zone | Fichiers principaux |
| --- | --- |
| Gradients | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GradientWgslShaderProvider.kt` |
| Blend/couleur | `.../materials/BlendWgslBuilder.kt`, `.../color/GPUColorWgsl.kt` |
| Contrats pipeline | `.../pipelines/PipelineContracts.kt`, `.../execution/GPUWgpu4kCorePrimitivePipelineDescriptor.kt` |
| Oracles existants | `integration-tests/gpu-evidence/.../oracle/SurfaceSrgbGradientCpuOracle.kt`, `SurfaceSrgbOracleMath.kt`, `SurfaceSrgbSrcOverCpuOracle.kt` |
| API source | `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint` et `.../canvas/Canvas.kt` |

## Matrice de scénarios

| Sous-famille | Scènes rendables à viser | Limites/refus à fixer |
| --- | --- | --- |
| Composition | Tous les Porter-Duff exposés avec source/destination opaques, alpha partiel et destination non opaque. | Modes avancés sans route fixed-function/layer, destination read indisponible et combinaisons paint incompatibles. |
| sRGB/premul | Alpha 0/1/intermédiaire, transparent coloré, unpremul→premul, clamp et arrondi RGBA8. | Couleurs/matrices non finies et format destination non compatible. |
| Gradients linéaires | Deux/trois/multi-stops, stops coïncidents, transparence, coordonnées négatives, géométrie dégénérée et local matrix. | Positions invalides, stop count hors contrat et tile mode sans implémentation. |
| Radial/sweep | Centre focal/décalé, rayon nul, angle partiel, passage 0/360°, transform et clipping. | Domaines/angles incompatibles et transform non admis par le shader. |
| Tile modes | `CLAMP`, `REPEAT`, `MIRROR`, `DECAL` selon ce que le code expose. | Chaque tile mode absent a un refus distinct ; `REPEAT` inclut les périodes négatives et éloignées. |
| Color filters | Identité, alpha-only, matrice complète, valeurs hors [0,1], blend color filter et composition. | Matrice malformée, ordre de composition non pris en charge, child/resource absent. |

## Assertions de route et de cache

Vérifier la correspondance CPU/GPU au pixel, l'absence de halo au bord et le
sens exact du local matrix. Deux scènes dont seules les valeurs d'uniforms
changent doivent réutiliser le même pipeline ; un changement de tile mode,
shader ou blend state peut former une nouvelle clé. Capturer draw count,
pipeline creations/hits et fallback reason.

## Dépendances et sortie

Peut commencer après le lot 00 et se développer en parallèle avec 10, 40, 50
et 60. La promotion de chaque sous-famille nécessite une référence Skia ou une
étiquette explicite « cohérence interne », jamais une confusion des deux.

## Vérification

```bash
./gradlew :integration-tests:gpu-evidence:test --tests '*Gradient*' --tests '*SrcOver*' --tests '*OracleMath*'
./gradlew :integration-tests:gpu-evidence:test
```
