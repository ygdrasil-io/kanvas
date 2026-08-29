# WIP 60 — vertices, mesh, picture et texte livré

> Brief d'exécution de `W60` à `W65`. Les routes fonts absentes restent
> dependency-gated et ne reçoivent aucun substitut temporaire.

## Fichiers propriétaires

| Zone | Fichiers |
| --- | --- |
| API | `../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`, `../kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt` |
| Vertices WGSL | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedVerticesShader.kt` |
| Text WGSL | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedTextA8Shader.kt`, `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/GPUPreparedTextShaderComposer.kt` |
| Runtime effects | `../kanvas/src/main/kotlin/org/graphiks/kanvas/pipeline/RuntimeEffect.kt` |
| GMs | `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/`, `../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/text/` |

## W60 — vertices

- [ ] Tester positions seules, colors, texcoords, indices et non-indexed.
- [ ] Tester blend, image shader, color filter, clip et transform affine.
- [ ] Vérifier vertex/index buffer bounds, formats et ownership.
- [ ] Refuser indices hors plage, attributs incohérents et budgets dépassés.

## W61 — mesh

- [ ] Enregistrer un MeshProgram minimal avec CPU semantics et WGSL validé.
- [ ] Tester uniforms, ShaderChild, ColorFilterChild et BlenderChild.
- [ ] Tester interpolation, varyings, indices et transforms.
- [ ] Refuser programme inconnu, kind mismatch, layout invalide et ressources
      dépassant les budgets.

## W62 — picture

- [ ] Tester record/replay d'un draw simple puis d'un état imbriqué.
- [ ] Tester replay de clip, layer, image et effet déjà supportés.
- [ ] Vérifier snapshot des ressources et ordre des opérations.
- [ ] Refuser récursion, ressource expirée et opération non replayable avant
      exécution partielle.

## W63 — glyphes livrés

- [ ] Rejouer la route actuelle avec la font réellement livrée.
- [ ] Tester positions, couleurs, alpha, transform bornée et cache glyph.
- [ ] Vérifier upload atlas, eviction, generation device et dispose.
- [ ] Promouvoir uniquement les glyph runs dont la provenance font est dans le
      bundle.

## W64 — dependency gates fonts

- [ ] Tester diagnostics séparés pour shaping, fallback, variable fonts,
      color fonts et emoji non livrés.
- [ ] Vérifier qu'aucun fake glyph, font système implicite ou raster temporaire
      ne contourne le gate.
- [ ] Exclure ces GMs du burn-down non-font sans les présenter comme supportés.

## W65 — interactions texte livré

- [ ] Pour la seule route livrée, tester clip, transform affine, alpha, gradient
      supporté et saveLayer.
- [ ] Vérifier positions et métriques déterministes dans le périmètre livré.
- [ ] Conserver perspective, shaping et fallback en dependency gate.

## Sortie

Vertices, mesh et picture doivent être classés comme toute autre surface.
L'achèvement du programme non-font n'exige pas la levée des gates fonts.

## Vérification

```bash
./gradlew :kanvas:test
./gradlew :gpu-renderer:test --tests '*Vertices*' --tests '*Mesh*' --tests '*Text*'
./gradlew :integration-tests:gpu-evidence:test --tests '*Vertices*' --tests '*Mesh*' --tests '*Picture*' --tests '*Text*'
./gradlew :integration-tests:skia:test --tests '*Mesh*' --tests '*Text*'
```
