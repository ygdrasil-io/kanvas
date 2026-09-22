# W6b Task 3 — Image blur X/Y et Picture streams

Statut proposé : `DONE_WITH_CONCERNS`.

Base de travail : `9d4c35185487ff17a724a2f2854c70318ed3f818`.

## Portée livrée

- R12 : les cinq erreurs de compilation test-source W3/W4d qui empêchaient les
  graph-contract tests de s'exécuter ont été corrigées. La validation sépare
  l'ordre des commandes racines de l'ordre interne synthétique des occurrences
  `Picture`; `draw A → Picture P → draw B` ne peut plus publier `B` avant le
  terminal de `P`.
- Les passes W6b déjà gelées `IMAGE_BLUR_X` puis `IMAGE_BLUR_Y` sont admises et
  matérialisées nativement, avec leurs bornes, origins target-locales,
  générations de source, terminaux et leases existants. Aucun pass n'est créé,
  reclassé ou replanifié par le renderer.
- Le snippet WGSL matérialise explicitement `CLAMP`, `REPEAT`, `MIRROR` et
  `DECAL`; il ne possède pas de branche « default clamp ».
- R13 : `GraphTextureSourceOperandV1` conserve le vrai binding W5 du paint
  parent (ressource uniforme, offset et capacité). Le carrier RGBA est neutre,
  l'alpha et le color filter parent sont appliqués une fois, et le blend l'est
  au terminal unique.
- R14 : `knownContent` d'un aggregate provient seulement de
  `SourceBinding.producedOutputDeviceI32`, intersecté avec la cible du terminal
  effectivement initialisé, puis converti par le mapping/origin gelé. Les
  domaines de demande, allocations physiques et halos transparents ne sont pas
  promus en contenu connu.
- R15 : `LayerFramePlanV1.frozenPassSchedule` publie l'ordre total des pass IDs
  et chaque `PictureStreamAggregateV1` publie son slice contigu. Le lowerer le
  parcourt tel quel. La validation impose child slice, seal enfant avant seal
  parent, puis `seal parent → X → Y → terminal` pour le blur parent.
- Les tests publics couvrent impulse, les quatre tile modes, origins négatives,
  clip/cull différé sous transform non commutative, inline `DST_OUT`, isolation
  de Picture peinte, alpha/color-filter/blend parent une fois, Pictures filtrées
  imbriquées, et replay mémoire/wire en occurrences distinctes.

## RED → GREEN observés

| Sujet | RED causal | GREEN |
| --- | --- | --- |
| R12 ordre racine | Le contrat synthétique place `draw B` avant le terminal Picture et est refusé sans comparer les indices locaux de l'occurrence. | `RenderGraphContractTest` passe après les validations racine/interne distinctes. |
| Materializer W6b | Les scénarios publics commencent par la refusal `w6b.native_execution_unimplemented` des opérations gelées sans materializer. | Les pixels sont produits par les X/Y gelées, sans replanning renderer. |
| R14 painted-child CLAMP | En rétablissant temporairement l'ancien calcul, le test nested échoue avec `Frozen known content bounds RectI32(left=0, top=-3, right=19, bottom=16) escape SizeI32(width=19, height=13) at Point2I32(x=-6, y=-3)`. | Après restauration de l'intersection du `producedOutput` avec la cible terminale, l'oracle public nested donne `tests=1 failures=0 errors=0`. |
| R15 schedule | La compilation du nouveau contrat échoue d'abord : paramètres `executionPassIds` et `frozenPassSchedule` absents. | Le test `frozen Picture execution schedule rejects an omitted pass` passe; l'ordre est désormais publié et validé par le plan. |
| Fixture latente R12 | Une fois les cinq erreurs de compilation supprimées, `w6bFilterPublicationGraph()` échoue car sa Readback 1×1 mappe `null` au lieu des 4 octets RGBA exigés par la validation existante. | Correction test-source bornée, autorisée : `mappedBytesI64 = 4`, sans toucher à `bytesPerRow = 256` ni à la validation. |

## Commandes et résultats

Les commandes ont été lancées en série avec `rtk`.

| Commande | Résultat observé |
| --- | --- |
| `rtk ./gradlew --console=plain -q :gpu-plan:compileTestKotlin` | GREEN, exit 0. |
| `rtk ./gradlew --console=plain -q :gpu-plan:test --tests 'org.graphiks.kanvas.gpu.plan.RenderGraphContractTest'` | GREEN, 94/94, exit 0. |
| `rtk ./gradlew --console=plain -q :gpu-plan:compileKotlin` | GREEN, exit 0. |
| `rtk ./gradlew --console=plain -q :gpu-renderer:compileKotlin` | GREEN, exit 0. |
| `rtk ./gradlew --console=plain -q :kanvas:compileKotlin` | GREEN, exit 0. |
| `rtk ./gradlew --console=plain -q :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'` | XML JUnit : 6 tests, 0 failure, 0 error. Le processus se termine 133 : gate **UNKNOWN**. |
| `rtk ./gradlew --console=plain -q :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'` | XML JUnit : 8 tests, 0 failure, 0 error. Exit 133 : **UNKNOWN**. |
| `rtk ./gradlew --console=plain -q :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest'` | XML JUnit : 10 tests, 0 failure, 0 error. Exit 133 : **UNKNOWN**. |
| `rtk ./gradlew --console=plain -q :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'` | XML JUnit : 19 tests, 0 failure, 0 error, dont les deux cas nested filtered. Exit 133 : **UNKNOWN**. |
| `rtk ./gradlew --console=plain -q :kanvas:test` | GREEN, exit 0. |
| `rtk git diff --check` | GREEN, aucune erreur de whitespace. |

Un exit natif 133 n'est jamais interprété ci-dessus comme succès ou comme
régression. Les compteurs JUnit sont une preuve indépendante sur les tests qui
ont fini avant l'exit, mais le gate Gradle correspondant demeure `UNKNOWN`.

## Fichiers Task 3

Production :

- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/LayerScopePlanV1.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PictureStreamAggregateV1.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphValidation.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW6aEncoderScopesV1.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUW6aLayerFramePlan.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/W6bSeparableBlurSnippet.kt`

Tests :

- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompilerTest.kt`
- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4dPathStrokePlanCompilerTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W6bFilterPictureTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bFilterAdmissionRecoverySurfaceTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bImageBlurCpuOracle.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bImageBlurSurfacePixelTest.kt`

## Self-review

- Le renderer consomme les IDs déjà publiés par `frozenPassSchedule`; il ne
  parcourt pas un `SceneSnapshot`, ne choisit pas de tile mode, ne calcule pas
  les bounds et ne partitionne pas de passes.
- Les validations couvrent inventory/budget de l'uniform W5 parent, générations
  de source scellée, liens terminal/source, origins, targets directs, terminal
  unique et ordre de dépendance.
- Le calcul R14 utilise la sortie gelée réellement publiée au terminal, jamais
  `requiredInput`, `desiredOutput`, cull ou rectangle d'allocation.
- Les oracles d'image calculent l'attendu avant de créer la `Surface`; aucun
  mock, fake device, reflection, compteur ou hook test-only n'a été ajouté.
- Aucun nouveau wire, type géométrique privé, changement Skia/GM/dashboard,
  render/baseline/score ou global jpg-color-cube n'est inclus.

## Concerns

1. `:gpu-plan:test` complet échoue actuellement (268 tests, 28 failures).
   Investigation : une dette de tests W3/W4 hors scope devient visible dès que
   les cinq erreurs R12 permettent la compilation. Plusieurs fixtures W4
   construisent déjà en base une `ReadbackPass` avec `mappedBytesI64 = null`,
   alors que la validation existante exige la plage mappée exacte; d'autres
   attentes W3 supposent une ancienne admission de blur. Le contrat Task 3
   ciblé est GREEN 94/94. Cette implémentation ne modifie pas ces suites au-delà
   des corrections R12 autorisées.
2. Les quatre selectors publics W6b/W6a atteignent l'exit natif 133 après leurs
   JUnit verts. Ils restent `UNKNOWN`, comme requis.
3. Step 10 (review Sol) n'est pas dispatché : l'instruction de cette tâche
   interdit explicitement les sous-agents. Une review indépendante doit être
   orchestrée par le contrôleur si elle reste requise.
