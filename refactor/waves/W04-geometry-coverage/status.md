# État W04 — geometry/coverage

Révision vérifiée : `23f672caf` (preuves publiques W4a), avec la correction de frange `65b64626` et les validations globales fraîches ci-dessous.

## Tranche W4a atteinte

W4a publie la capability `solid-rect-scalar-aa-simple-scissor-src-over-srgb-v1`. Elle rend des frames entières de rectangles solides fractionnaires axis-aligned avec `CoverageRequest.ANTIALIASED`, coverage analytique `ScalarAA`, blend `SrcOver`, cible sRGB 1x et clip vide ou `DeviceRect` intégral non-AA. Les bounds device exacts restent des `RectF32` de `:math`; les raster bounds et le scissor scellés restent des `RectI32` de `:math`.

La sélection est handle-free et ordonnée : W3 est essayé avant W4a. Les rectangles pixel-aligned conservent donc la capability W3 `solid-rect-pixel-aligned-simple-clip-src-over-srgb-v1`; une frame qui contient au moins une arête fractionnaire et satisfait le contrat fermé devient W4a. Le device n'est acquis qu'après un candidat sélectionné. Après `Ready`, un refus est terminal : ni reclassement, ni fallback legacy, ni allocation double ne sont permis.

## Ressources, capacités et budget

Un graphe W4a possède exactement cinq ressources frame-local :

| Rôle | Kind / usages | Capacité ou taille planifiée |
| --- | --- | --- |
| `LogicalTarget` | texture 2D, render attachment + copy source | `4 × width × height` bytes |
| `ReadbackStaging` | buffer, copy destination + map read | row bytes alignés × height |
| `VertexData` | buffer, vertex + copy destination | capacité pool power-of-two exacte |
| `IndexData` | buffer, index + copy destination | capacité pool power-of-two exacte |
| `UniformData` | buffer, uniform + copy destination | capacité pool power-of-two exacte |

Les useful bytes sont `32N` pour vertex, `24N` pour index et `N × alignUp(80, minUniformBufferOffsetAlignment)` pour uniform. Les trois dernières ressources sont les buffers du lease natif, pas des buffers ordinary supplémentaires. Le pic est la somme checked des cinq ressources, conservées jusqu'à completion/readback.

La preuve publique de frontière sur une cible 1×1 fixe les capacités exactes : pour 512 draws, target `4`, staging `256`, vertex `16 384`, index `16 384` et uniform `131 072` bytes, soit `164100` bytes. À 513 draws, les capacités sont target `4`, staging `256`, vertex `32 768`, index `16 384` et uniform `262 144` bytes, soit `311556` bytes. La seconde frame reste legacy : elle ne peut pas être promue silencieusement au-delà de la borne W4a.

## Preuves pixels publiques

Les 15 tests ciblés de `GPUPlanSurfacePixelTest` passent. Ils comparent les bytes de `Surface.render()` à l'oracle CPU indépendant W4a : aire de coverage fractionnaire, `SrcOver` prémultiplié linear, quantification `rgba8unorm-srgb` entre draws, scissor qui coupe une frange, ordonnancement RGBA/BGRA, frame mixte intégrale/fractionnaire, scène legacy non admise et frontières 512/513. Les frames W4a vérifient via le résultat public les scopes natifs exactement `{Render, Readback}`; la preuve legacy ne leur impose aucune forme interne.

## Commandes fraîches

| Commande | Résultat |
| --- | --- |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsTest :math:matrix:jvmTest :math:matrix:jsTest :math:color:jvmTest :math:color:jsTest` | Succès. |
| `rtk ./gradlew :render-ir:test :gpu-plan:test` | Succès. |
| `rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*'` | Succès. |
| `rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*'` | 2 033 tests ; les seules 45 failures sont les `GPUAllApiBlendSurfaceTest :: DrawPoint` connues ; aucune divergence W4a. |
| `rtk ./gradlew :kanvas:test --rerun-tasks` | 3 617 tests, 51 failures connues, 0 error. |

Ni `:integration-tests:skia`, ni tâche GM, ni `jpg-color-cube` n'ont été exécutés. `jpg-color-cube` reste en quarantaine ; `font` et `codec` restent hors périmètre W4a.

## Baseline globale exacte

Les 51 noms rouges du run frais sont exactement :

- `ImageTest :: ColorType enum values()` ;
- `GPUAllApiBlendSurfaceTest :: DrawPoint/{PLUS, MULTIPLY, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY}/{UNCLIPPED, SCISSOR, ALPHA_MASK}` — les 45 combinaisons du produit cartésien affiché ;
- `GPUMaskBlurDispatchTest :: local path mask scales dash intervals and phase()` ;
- `GPUPreparedSurfaceFrameBuilderTest :: public non finite singular and perspective transforms refuse before frame task assembly()` ;
- `GPUPreparedSurfaceFrameBuilderTest :: prepared atlas expands to ordered sampled packets sharing one artifact with distinct uniforms()` ;
- `GPUPreparedTextStrokeTest :: prepared stroke path key seals exact geometry and verb count seals every contour()` ;
- `GPURefusalGuardsTest :: direct fill guard refuses radial and sweep non identity matrix facts before dispatch()`.

Le scan `rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml` retourne 51 matches ; aucun élément `<error>` n'est présent. Aucun nom nouveau ne bloque donc W4a.

## Limites ouvertes

W4 reste ouverte. W4b doit ajouter les RRect et la normalisation de leurs rayons dans `:math`; W4c les fills de paths; W4d les strokes et hairlines; W4e les clips path, inverse et booléens. Materials W5, layers/effets W6 et convergence GM W7 ne font pas partie de cette tranche.
