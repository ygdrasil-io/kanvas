# WIP 60 — Texte, vertices, mesh, atlas et picture

> Document temporaire. Les routes texte/codec non livrées restent dependency-
> gated. Aucun faux backend de fonte ou de shaping ne doit être créé pour faire
> passer les tests.

## Objectif du groupe

Rendre observables les opérations Canvas composées qui n'entrent pas dans les
rectangles ou images simples. Démarrer par les refus stables, puis promouvoir
seulement les routes dont les ressources, buffers et oracles existent réellement.

## Code et tests à lire

| Zone | Fichiers principaux |
| --- | --- |
| Texte | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt`, `GlyphAtlasTextureBuilder.kt`, `TextA8AtlasExecutor.kt`, `GPUDrawTextRunExecutor.kt` |
| Vertices/mesh | `.../vertices/VerticesContracts.kt`, `GPUPreparedVerticesPacker.kt`, `GPUPreparedVerticesRefusalCodes.kt`, `GPUMeshBatcher.kt`, `VerticesExecutor.kt` |
| Exécution | `.../execution/GPUWgpu4kPreparedTextRenderRunMaterializer.kt`, `GPUWgpu4kPreparedVerticesRenderRunMaterializer.kt` |
| Shaders | `.../wgsl/PreparedTextA8Shader.kt`, `PreparedVerticesShader.kt`, `VerticesSnippet.kt`, `TextAtlasSnippet.kt` |
| API | `../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt` (`drawText`, `drawString`, `drawPicture`, `drawVertices`, `drawMesh`, `drawAtlas`) |

## Matrice de scénarios

| Sous-famille | Scènes rendables à viser | Limites/refus à fixer |
| --- | --- | --- |
| Texte basique | Latin simple avec fonte réellement embarquée, baseline, alpha, clip, translation/scale/rotation, glyph manquant et deux frames chaudes. | Fonte absente, variation/CFF/outline non prouvé, script complexe/bidi/shaping réel absent et atlas hors budget. |
| Glyph atlas | Premier upload, réutilisation, éviction, page pleine, surface close/device loss. | Cache invalidé proprement, telemetry pages/bytes/hits/misses et refus si allocation impossible. |
| `drawVertices` | Triangle, quad indexé, couleur par sommet, texture, alpha/interpolation et local matrix. | Indices hors plage, buffer vide/malformé, texture absente, taille/budget ou layout incompatible. |
| `drawMesh` | Mesh sans programme routé par vertices ; mesh avec programme seulement si descriptor/route enregistrés. | Programme arbitraire, blend incompatible et layout non validé refusés avant draw. |
| `drawAtlas` | Atlas avec transforms, texRects, couleurs, paint et ordre de sprite. | Longueurs incohérentes, texture/crop invalide, trop d'instances et blend sans route. |
| `drawPicture` | Picture simple, état isolé, picture vide, replay dans une layer/clip. | Récursion, op non abaissable, ressources invalides et fuite d'état au replay. |

## Preuves à exiger

Pour texte, fournir la provenance de la fonte et la politique de glyph manquant.
Pour vertices/mesh/atlas, vérifier buffers, indices, interpolation prémultipliée,
bind groups, uploads et route GPU. Les cas rendables ont oracle/référence,
readback et diagnostics ; les cas dependency-gated ont un refus explicite sans
submission. Les GMs texte ne sont promus qu'avec la référence et les assets
traçables.

## Dépendances et sortie

Les refus, validation de buffers et tests de replay commencent après le lot 00
en parallèle avec les lots 10, 30, 40 et 50. Les rendus texte attendent les
fonctions dépendantes ; les rendus atlas/image partagent les garanties du lot
40. Intégrer le catalogue de scènes après les autres branches pour limiter les
conflits de fichier central.

## Vérification

```bash
./gradlew :gpu-renderer:test
./gradlew :integration-tests:gpu-evidence:test --tests '*Text*' --tests '*Vertices*' --tests '*Mesh*' --tests '*Atlas*'
./gradlew :integration-tests:gpu-evidence:test
```
