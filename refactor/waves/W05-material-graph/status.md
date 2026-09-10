# État W05 — material graph, W5a Solid/Opacity

Révision vérifiée : Task 7 sur `13003cc5766c8d7191c0a7fc0f5b2865069ca829`, après les gates READY des Tasks 1–6.

## W5a est complet

W5a ferme uniquement `Transparent`, `Solid` et `Opacity` sous `SRC_OVER`. Chaque draw promu porte une `MaterialV1` vers une `MaterialPlanTable` immuable; la couleur legacy ne peut pas coexister avec cette autorité. Les draws W3 historiques conservent précisément `CAPABILITY_ID` avec table `null` et `LegacyColorV1`; `W5A_CAPABILITY_ID` exige une table non nulle et uniquement des `MaterialV1`. Le lowerer refuse les deux formes hybrides.

| Cellule publique | Preuve retenue |
| --- | --- |
| Rect hard-edge / Rect fractional | fixtures publiques nested-opacity et fractional Rect de `W5aMaterialSurfacePixelTest` (Tasks 1–2) |
| RRect analytique | fixture publique fractional RRect (Task 2) |
| Path fill direct et stencil cover | fixtures publiques direct-triangle et stencil-cover Path (Task 3) |
| Path stroke / hairline | fixtures publiques stroke et hairline (Task 3) |
| Point / Points | fixtures publiques trois commandes, points multiples et hairline (Task 4) |
| Text pré-résolu | fixture publique A8 déjà résolue (Task 5), sans génération de font |
| Vertices, avec et sans couleurs vertex | fixtures publiques `Picture` mutation-sensitive (Task 6) |
| Frame Rect + RRect + Path | `Picture playback composes planned Rect RRect and Path bindings in recorded order` : trois bindings distincts, ordre observé dans l'oracle, puis mutation du `Path` après capture |
| Refus W5b puis récupération | `public W5b gradient refusal leaves the runtime able to render a later W5a frame` : gradient linéaire refuse `unsupported.material.w5a.kind`; une frame W5a Solid/Opacity suivante rend dans le même runtime de production |

La dernière preuve a d'abord échoué de manière sémantique : le frame mixte retombait vers la route préparée historique (`unsupported.material.mapping.opacity_child`), et le gradient W5b se rendait silencieusement par legacy. W4c convertit maintenant uniquement les Rect/RRect hard-edge de ce frame mixte en géométrie `Path` scellée, tandis que le routeur termine toute `EffectiveMaterialPlanner.Result.Refused` avant le fallback legacy. Les gradients et les autres kinds W5b+ restent donc des gaps typés; ils ne sont ni remplacés par Transparent ni abaissés en Solid.

## Audit des compilations alternatives Solid/Opacity

| Site de production | Décision W5a |
| --- | --- |
| `W3SolidRectPlanCompiler`, `W4aAnalyticRectPlanCompiler`, `W4bAnalyticRRectPlanCompiler`, `W4cPathFillPlanCompiler`, `W4d*` | les branches W5a consomment `EffectiveMaterialPlanner` et publient seulement une table/ref; les branches historiques gardent seulement `LegacyColorV1` |
| `GpuPlanTaskListLowerer` et lowerers W4 | les `MaterialV1` sont évaluées depuis la table scellée; le contrat W3 historique/W5a est rejeté s'il est mélangé |
| bridges points, text et vertices | les refs W5a sont capturées avant la route préparée, puis compilées via `compileW5a*`; la compilation générique n'est conservée que pour les lanes non-W5a |
| `GPUMaterialMapper` | `Shader.Opacity` produit déjà `Unsupported(OPACITY_CHILD)` dans les lanes legacy; aucun aplatissement Solid n'est conservé après sélection W5a |
| image, glyph couleur, mesh, stroke-rect, analyses et dispatchs legacy | hors scope W5a ou sans source `Shader.SolidColor`/`Shader.Opacity` promue; aucune migration ne leur est appliquée |

Cet audit est documentaire : les tests restent uniquement des scénarios publics `Surface`/`Picture`/pixels/refus, sans inspection de source, réflexion, détails privés, compteurs ou nombre d'appels.

## Limites explicites

- Le test AA4 authentique demeure `SKIPPED` seulement lorsque le runtime annonce exactement `w4d.general.texture-sample-support-unavailable`; aucune capability AA4 ni réussite native n'est simulée.
- La frontière d'immuabilité est publique : `Shader.SolidColor`, `Shader.Opacity` et `Paint` sont des valeurs immuables. La preuve de mutation possible porte sur `Path`/vertices après capture; introduire une mutation artificielle des valeurs W5a serait hors périmètre.
- Le code JVM `133` des runs `Picture`/Skia natifs est une caveat historique de l'intégration (hors W5a), pas une preuve de cette tranche. Aucun GM, dashboard, baseline, Skia, font, codec ou `jpg-color-cube` n'a été exécuté ni modifié ici.
- W5b est la prochaine tranche : gradients, images, BlendShader, local matrices, color/image filters, noise et runtime effects restent refusés typés lorsqu'une frame W5 est sélectionnée.

## Vérification Task 7

`rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5aMaterialSurfacePixelTest.Picture playback composes planned Rect RRect and Path bindings in recorded order' --tests 'org.graphiks.kanvas.surface.W5aMaterialSurfacePixelTest.public W5b gradient refusal leaves the runtime able to render a later W5a frame' --no-parallel`

Résultat : `BUILD SUCCESSFUL`, 2 tests publics, 0 failure, 0 error. Un second sous-ensemble public W5a sans font (Rect, RRect, fills/stroke/hairline Path, Points, vertices, frame mixte et récupération) a exécuté 11 tests, 0 failure, 0 error. La compilation transitive Gradle peut charger des dépendances font, mais aucune suite font ni codec n'a été sélectionnée.
