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

## Carte Paint / produit croisé observée

Cette carte décrit les bornes effectivement exercées ; elle n'autorise pas à
élargir le support. Le lot 30 possède le matériau gradient et le comportement
tile mode, tandis que le lot 10 possède la géométrie et l'état du stroke.

| Axe Paint | `repeat-gradient-refusal` (ID historique, rendu actuel) | `gradient-stroke-refusal` (ID historique, rendu actuel) | Hors borne actuelle |
| --- | --- | --- | --- |
| Style | `Fill` sur `drawRect` borné | `Stroke` sur `drawRect` | Le renderer refuse le gradient `REPEAT` sur `FillRRect` et `FillPath`; le generic gradient `drawPath` stroke est également refusé. |
| Largeur de stroke | Sans objet | 4, largeur entière paire | Hairline, largeur nulle/non finie, impaire ou fractionnaire sont refusées. |
| Anti-aliasing | Probe non-AA | Non-AA | Le stroke AA est refusé. Le probe `REPEAT` non-AA ne prouve pas un refus renderer du `REPEAT` AA. |
| Cap / join / miter | Sans objet | `Butt` / `Miter` / 4 (défaut) | Les autres caps/joins, miter non fini ou sous le minimum sont refusés. |
| Shader / tile mode | Dégradé linéaire sRGB `REPEAT` | Dégradé linéaire sRGB `CLAMP` nu, valide | `MIRROR`, `DECAL`, radial/sweep `REPEAT` sont refusés; le stroke refuse autre shader, tile mode ou local matrix. |
| Transform | Identité dans le probe | Identité | Le stroke gradient refuse translate et tout transform non identité; le probe `REPEAT` n'élargit pas la frontière transform. |
| Mask/path/color filters | Aucun filtre | Aucun filtre ni path effect/local matrix/blender | Le renderer refuse `FillRect` `REPEAT` mask-filtered; le stroke refuse mask/image/color filter, path effect et blender. |

`repeat-gradient-refusal` est le probe catalogue : rectangle rempli `Surface`
borné, linéaire sRGB `REPEAT`, sans filtre, à transform identité et non-AA. La
frontière renderer est plus précise : l'exception `REPEAT` est seulement le
FillRect linéaire non mask-filtered; RRect, Path, FillRect mask-filtered,
radial/sweep `REPEAT`, `MIRROR` et `DECAL` sont actuellement refusés. L'absence
d'un probe AA ne transforme pas le non-AA de ce probe en refus renderer.

`gradient-stroke-refusal` est le probe exact du stroke rectangle décrit dans
la table; il ne teste pas `drawPath`, `clipRRect` ou `clipPath`. Un tile mode
ou un refus dit « distinct » désigne ici un comportement explicitement testé,
pas nécessairement un code d'erreur de production unique.

## Dépendances et sortie

Peut commencer après le lot 00 et se développer en parallèle avec 10, 40, 50
et 60. La promotion de chaque sous-famille nécessite une référence Skia ou une
étiquette explicite « cohérence interne », jamais une confusion des deux.

## Vérification

```bash
./gradlew :integration-tests:gpu-evidence:test --tests '*Gradient*' --tests '*SrcOver*' --tests '*OracleMath*'
./gradlew :integration-tests:gpu-evidence:test
```
