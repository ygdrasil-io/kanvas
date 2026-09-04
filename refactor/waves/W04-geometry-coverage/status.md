# État W04 — geometry/coverage

Révision de code vérifiée : `d4c683433637e0c6529bdca3290ced120902d98b` (`fix: close W4b final review gaps`), empilée sur W4a. Les vérifications fraîches ci-dessous ont été exécutées le 2026-09-04 ; elles ne lancent ni GM, ni Skia.

## Tranches W4a et W4b atteintes

W4a publie `solid-rect-scalar-aa-simple-scissor-src-over-srgb-v1` pour les frames de `Rect` solides, axis-aligned et fractionnaires. W4b ajoute sa branche sœur fermée, `solid-rect-rrect-scalar-aa-simple-scissor-src-over-srgb-v1`, pour une frame ordonnée de `Rect` et `RRect` remplis, `SolidColor` prémultipliée, `SrcOver`, AA scalaire, cible sRGB 1× et scissor entier simple.

W4b n'est sélectionnée que si la frame comporte au moins une primitive de provenance `DrawOrigin.RRECT`. Chaque `AnalyticRRectDraw` représente une primitive, conserve son ordinal et sa provenance `RECT`/`RRECT`, et transporte les faits device-space normalisés, le scissor et le slot Uniform80 scellés. Un `Rect` cohabitant est un `RRectF32` à huit `+0f`, mais reste d'origine `RECT`. Les RRect sont normalisés par l'unique API backend-neutral de `:math`, avant puis après `mapAxisAligned`, avec mêmes vecteurs et bits F32 sur JVM et JS.

La chaîne reste W3 → W4a → W4b : un Rect seul demeure W3/W4a suivant son enveloppe. Après `Ready` W4b, toute divergence du graphe, lowering, preflight ou matérialisation est un refus terminal : aucun reclassement W4b → W4a/W3, fallback direct, recalcul de scissor/bounds/rayons/transform, ni allocation « best effort » n'est permis.

## Ressources, durées de vie et ABI

Un graphe W4b matérialise exactement cinq `PlanResource`; pipeline et bind group sont des faits scellés, jamais des substituts au staging de readback.

| Rôle | Kind / usages | Taille planifiée | Durée de vie |
| --- | --- | --- | --- |
| `LogicalTarget` | texture 2D, render attachment, copy source | `4 × width × height` | `[0, 2)` |
| `ReadbackStaging` | buffer, copy destination, map read | `alignUp(4 × width, 256) × height` | `[1, 2)` |
| `VertexData` | buffer, vertex, copy destination | capacité pool réservée | `[0, 2)` |
| `IndexData` | buffer, index, copy destination | capacité pool réservée | `[0, 2)` |
| `UniformData` | buffer, uniform, copy destination | capacité pool réservée | `[0, 2)` |

Pour `N` primitives : `vertexBytes = 32 × N`, `indexBytes = 24 × N`, `uniformStride = alignUp(80, minUniformBufferOffsetAlignment)` et `uniformBytes = uniformStride × N`. Avant `Ready`, les multiplications checked imposent aussi `uniformBytes <= Int.MAX_VALUE` (taille hôte représentable) et `(N - 1) × uniformStride <= UInt.MAX_VALUE` (dernier dynamic offset). Le pic checked est la somme des cinq tailles physiques, avec les capacités poolées V/I/U, et les buffers ne retournent au pool qu'après completion/readback. La frontière publique reste 512 draws mixtes avec au moins un RRECT en W4b ; 513 est `NotCandidate` et ne promeut aucune allocation.

W4b réutilise sans modification `Uniform80` d'`AnalyticShape` : target/padding aux octets 0..15, couleur prémultipliée 16..31, bounds device 32..47, `TL, TR` 48..63, puis `BR, BL` 64..79. Les Rect ont huit rayons positifs nuls et conservent la couverture rectangulaire exacte ; les RRect non nuls suivent la branche SDF native existante.

## Preuves pixels non-GM

L'oracle CPU W4b est test-only et indépendant : aire de chevauchement exacte avec huit rayons nuls, et équation SDF/ramp d'AA native reproduite sans importer shader, renderer, materializer ou helper privé. La cible RGBA8 sRGB stockée puis quantifiée est relue comme destination par chaque draw suivant ; une cible de précision différente demanderait une nouvelle capability et un nouvel oracle, pas une réutilisation implicite de W4b. Les comparaisons sont byte-exact (`assertContentEquals`), sans seuil ni tolérance. Elles couvrent les RRect asymétriques, normalisation à la limite, échelles positive/X/Y/XY, scissor, ordre `SrcOver` avec quantification sRGB entre draws, ordres de pixels RGBA/BGRA et la frontière 512/513.

`RRectNormalizationF32Result.Accepted` conserve un snapshot `RRectF32` profond et privé ; `copyShape()` rend un nouveau snapshot défensif. Le contrat public expose `Rejected.reason` et les raisons singulières `NonFiniteRadius` / `NegativeRadius`, tandis que la factory `AnalyticRRectDraw.of` conserve l'ordre `commandIndex`, `color`, `origin`, `deviceShape`, `rasterBounds`, `scissor`.

## Commandes fraîches

| Commande | Résultat frais |
| --- | --- |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest --rerun-tasks` | Succès ; normalisation et parité JVM/JS, y compris mapping X/Y/XY et saturation F32 bit-exacts. |
| `rtk ./gradlew :render-ir:test :gpu-plan:test --rerun-tasks` | Succès ; budget Uniform80 et compiler W4b vérifiés. |
| `rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --tests '*GPUCorePrimitiveAnalyticShapeUniformAbiTest*' --rerun-tasks` | Succès ; aucun failure/error XML. |
| `rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks` | 2 047 tests ; 45 failures `GPUAllApiBlendSurfaceTest :: DrawPoint` déjà connues, aucune failure W4b. |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test --rerun-tasks` | Succès ; 85 tâches exécutées. |
| `rtk ./gradlew :kanvas:test --rerun-tasks` | 3 631 tests ; baseline fraîche de 51 failures connues, 0 error. |

Les compilations transitives n'ont exécuté aucun test `font`; aucun test `codec` n'a été lancé.

## Ledger XML global exact

Le scan `rtk rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml`, horodaté 2026-09-04T23:38:29+02:00, retourne 51 matches dans 6 fichiers. L'inventaire XML totalise 120 suites, 3 631 tests, 51 failures et 0 error. Les 51 seuls noms sont :

- `ImageTest :: ColorType enum values()` ;
- `GPUAllApiBlendSurfaceTest :: DrawPoint/{PLUS, MULTIPLY, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY}/{UNCLIPPED, SCISSOR, ALPHA_MASK}` — les 45 combinaisons exactes du produit cartésien ;
- `GPUMaskBlurDispatchTest :: local path mask scales dash intervals and phase()` ;
- `GPUPreparedSurfaceFrameBuilderTest :: public non finite singular and perspective transforms refuse before frame task assembly()` ;
- `GPUPreparedSurfaceFrameBuilderTest :: prepared atlas expands to ordered sampled packets sharing one artifact with distinct uniforms()` ;
- `GPUPreparedTextStrokeTest :: prepared stroke path key seals exact geometry and verb count seals every contour()` ;
- `GPURefusalGuardsTest :: direct fill guard refuses radial and sweep non identity matrix facts before dispatch()`.

`rtk rg -n '<error' kanvas/build/test-results/test/TEST-*.xml` ne retourne aucune occurrence. Aucun nom nouveau et aucune failure W4b ne bloquent donc cette publication ; les failures listées sont hors périmètre et ne sont pas modifiées par W4b.

## Exclusions et dette SDF

W4b n'a exécuté ni `:integration-tests:skia`, ni GM/dashboard/baseline, ni `jpg-color-cube`, ni test `font` ou `codec`; `jpg-color-cube` demeure en quarantaine. Aucun shader, seuil de similarité, tolérance ou baseline n'a été modifié.

Pour les RRect non nuls, la SDF native n'est pas l'aire analytique Skia exacte. Cette dette est explicitement réservée à W7 : un nouveau shader ne pourra être envisagé qu'après une divergence matérielle constatée par l'intégration Skia. Il est interdit de la masquer par une tolérance, un seuil plus bas ou une rebaseline.

## Limites ouvertes

W4 reste ouverte : W4c couvre les fills de paths, W4d les strokes et hairlines, W4e les clips path, inverse et booléens. W5 (materials), W6 (layers/effets) et W7 (convergence GM, y compris la réévaluation SDF) ne font pas partie de W4b.
