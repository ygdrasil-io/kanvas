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

---

## Fix round 1 — cinq findings importants acceptés

Base de correction : `375255fec` (`7164e48` ne portait que le ledger).

### Correctifs livrés

1. L'admission native Task 3 ne ré-explore plus `SceneSnapshot`, les tables de
   filtres ou les clips capturés. `W6aLayerPlanCompiler` publie et valide d'abord
   le graph Task 2, puis admet seulement le sous-ensemble gelé : kinds
   `IMAGE_BLUR_X/Y`, schedule total, terminaux et operands de terminal. Les
   opérations absentes du sous-ensemble gardent le refus stable
   `w6b.native_execution_unimplemented`.
2. L'adressage `MIRROR` WGSL et l'oracle indépendant suivent exactement le
   contrat W5e : période `2 * dimension`, repli `min(p, 2 * dimension - 1 - p)`.
   La fixture publique utilise désormais trois texels RGB asymétriques :
   `CLAMP`, `REPEAT`, `MIRROR` et `DECAL` ont quatre sorties distinctes.
3. Les `FilterCompositeOperationV1.Layer` et les terminaux Picture abaissent
   désormais les bindings W5 gelés : alpha, color filter, `BlendPlan` et,
   lorsqu'il existe, le snapshot de destination. Le carrier graph-texture reste
   neutre. Les sources Picture internes qui n'ont pas de
   `GraphTextureSourceOperandV1` restent abaissées depuis leur terminal gelé
   (blend/snapshot), sans chercher un operand parent inexistant. Cela ferme aussi
   le RED trouvé sur les Pictures filtrées imbriquées.
4. L'admission des clips `DeviceRect` différés est plan-owned et bornée aux
   rectangles non-AA, non vides et à mapping axis-aligned; les formes
   non-représentables refusent de manière terminale et un rectangle vide est un
   terminal no-op. Le materializer n'élargit plus silencieusement un clip vide
   en AABB.
5. Le contrat public de `W6bFilterPictureTest` capture deux relectures de la
   même `Picture`, compile le vrai graph de production, et prouve deux paires
   distinctes `(sealedSourceId, sealedSourceGenerationI64)` ainsi que le lien de
   chaque paire vers son `PictureSourcePass` puis son `FilterComposite` exact.
   Le replay pixel mémoire/wire existant reste exécuté.

R13/R14/R15 sont conservés : aucune nouvelle ressource uniforme, aucun wire,
aucun replanning renderer, et aucune promotion de demand/cull/halo vers
`knownContent`.

### RED → GREEN round 1

| Finding | RED observé | GREEN observé |
| --- | --- | --- |
| Admission gelée | L'ancien admission helper re-traversait les occurrences et `CapturedFilterNodeV1` avant publication. | Le helper est supprimé; les recovery contracts W6b observent les refus/récupérations depuis le graph publié. |
| MIRROR | L'ancien shader/oracle employait le repli `2 * extent - 2`; l'oracle public signalait le mismatch du texel miroir (137 contre 199 attendu). | Le test public quatre modes passe avec source multi-texel asymétrique et oracle qui ne réemploie pas le shader. |
| Layer/Picture parent terminal | Les REDs publics donnaient une Picture destination-read rouge au lieu de noir, un saveLayer color-filter sans filtre et `Invalid destination snapshot consumer`. | Les trois pixels publics (Picture destination-read, restore destination-read et color-filter) passent; le snapshot est validé par son binding gelé. |
| Picture filtrées imbriquées | Après l'abaissement des operands parent, le test obligatoire échouait avec `W6b Picture terminal is missing its frozen graph-texture operand for FilterSource:2`; cette source interne n'est pas un aggregate parent. | Le terminal interne est abaissé depuis son blend/snapshot gelé, le terminal aggregate consomme l'operand W5 gelé; le scénario nested passe. |
| DeviceRect différé | Les REDs publics de clip AA/non-axis-aligned exposaient l'absence de coverage exacte derrière l'ancien AABB scissor. | Refus public stable + recovery pour AA/non-axis, et pixel transparent/no-op pour empty, passent. |
| Occurrences répétées | Le nouveau contrat a d'abord reçu `unsupported.material.gradient.storage-capability` car son snapshot de capacités de production était incomplet. | Avec l'inventory de capacités requis, la capture publique produit le graph et prouve les deux paires/consumers distincts. Aucune correction de production n'était nécessaire pour cet invariant déjà publié. |

### Gates round 1

Les commandes suivantes ont été lancées séquentiellement via `rtk`.

| Commande | Preuve |
| --- | --- |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'` | JUnit XML : 9 tests, 0 failure, 0 error. Gradle exit 133 : **UNKNOWN**. |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'` | JUnit XML : 9 tests, 0 failure, 0 error, incluant replay mémoire/wire et contrat source-generation. Exit 133 : **UNKNOWN**. |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'` | JUnit XML : 22 tests, 0 failure, 0 error. Exit 133 : **UNKNOWN**. |
| `rtk ./gradlew :gpu-plan:test --tests 'org.graphiks.kanvas.gpu.plan.RenderGraphContractTest'` | BUILD SUCCESSFUL, exit 0; les contrats graph restent verts. |
| `rtk ./gradlew :gpu-plan:test` | 268 tests, 28 failures W3/W4 préexistantes/hors scope; `RenderGraphContractTest` reste vert. |
| `rtk ./gradlew :kanvas:test` | Le run complet atteint l'exit natif 133; statut du gate **UNKNOWN**. Les sorties de tests ne reclassifient ni ce 133 ni les suites non terminées en succès ou régression. |
| `rtk git diff --check` | GREEN, aucune erreur whitespace. |

### Fichiers round 1

Production :

- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/OccurrenceSourceInputV1.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphValidation.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/W6bSeparableBlurSnippet.kt`

Tests :

- `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W6bFilterPictureTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bFilterAdmissionRecoverySurfaceTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bImageBlurCpuOracle.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bImageBlurSurfacePixelTest.kt`

### Self-review round 1

- Le renderer lit uniquement les operands, schedules, bounds, generations et
  snapshots publiés. Il ne parcourt ni `SceneSnapshot` ni filtres capturés et
  ne crée aucun pass, buffer ad hoc ou wire.
- Les quatre tile modes ont un dispatch exhaustif; l'oracle pixel est
  indépendant de la formule WGSL.
- Chaque destination-read est lié à la ressource snapshot et à la version
  gelées; la validation accepte le consumer Layer filtré qui peut être séparé
  de la `TextureCopy` par coverage/source/X/Y publiés.
- Les tests ajoutés sont publics et pixel/graph contractuels; aucun mock, fake
  device, reflection, hook, compteur ou test d'infrastructure n'a été ajouté.

### Concerns round 1

1. Les exits 133 des suites `:kanvas:test` restent **UNKNOWN** conformément à
   la règle explicite; les XML JUnit ne changent pas le statut du processus.
2. `:gpu-plan:test` complet conserve les 28 échecs W3/W4, hors scope de Task 3;
   le gate ciblé `RenderGraphContractTest` est vert.
3. Le finding Minor de nomenclature WGSL est volontairement inchangé, comme
   demandé; il reste réservé à la final review.

---

## Fix round 2 — deux findings importants acceptés

Base de correction : `39491652d` (`f48f3c6` ne portait que le ledger).

### Correctifs livrés

1. `PlanPass.PictureComposite` consulte l'operand graph-texture gelé par sa
   source. Lorsque l'operand existe, le terminal unique applique son alpha et
   son color filter W5 une seule fois, puis utilise le snapshot de destination
   du `BlendPlan.DestinationReadV1`. Le carrier demeure une copie RGBA neutre;
   le chemin sans operand conserve son composite normal. Aucun état Scene ou
   paint n'est relu par le renderer.
2. Un `INTERSECT` vide à matrice finie devient un `DeviceRect` vide, sans
   dépendre de l'identité. Le constructeur de graph reconnaît ce terminal
   no-op avant de demander un lane W4/W5 pour un enfant devenu inobservable;
   `knownContent` ne retient alors aucune sortie absente de
   `SourceBinding.producedOutputDeviceI32`.
3. L'admission plan-owned d'un `DeviceRect` hard-edge exige désormais une
   matrice affine axis-aligned sans perspective et quatre bords device F64
   intégralement représentables en I32. Le materializer consomme cette preuve
   gelée comme scissor exact et ne fait plus de `roundOut`/AABB élargi.

R12--R15, les trois findings déjà fermés et les 94 graph contracts sont
conservés. Aucun wire, buffer ad hoc, objet géométrique privé, test
d'infrastructure ou replanning renderer n'a été ajouté.

### RED → GREEN round 2

| Finding | RED observé | GREEN observé |
| --- | --- | --- |
| PictureComposite non filtré peint | Le pixel public nested produit R=255 là où l'oracle indépendant fixe attend R=0 : le terminal avait perdu alpha, color filter et snapshot du parent. | Le cas public alpha + swap R/B + `MULTIPLY` destination-read donne son RGBA attendu; 10/10 assertions de `W6bImageBlurSurfacePixelTest`. |
| Empty transformé / hard-edge fractionnaire | Le clip empty sous rotation provoque `w6a.layer.unsupported_child`; le clip fractionnaire externe s'exécute au lieu d'émettre le sentinel terminal. | Le empty rotationnel donne transparent, et le hard-edge fractionnaire refuse avec sentinel inchangé puis la même `Surface` récupère; 24/24 assertions de `W6bFilterAdmissionRecoverySurfaceTest`. |

### Gates round 2

Les commandes ont été exécutées séquentiellement via `rtk`.

| Commande | Résultat observé |
| --- | --- |
| `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest` (RED) | 10 tests, 1 failure : oracle R=0 / actual R=255 au nouveau scénario. Exit 133 : le gate est **UNKNOWN**. |
| `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest` (RED) | 24 tests, 2 failures : empty transformé et refusal hard-edge fractionnaire absente. Exit 133 : **UNKNOWN**. |
| `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest` (GREEN final) | XML JUnit 10 tests, 0 failure, 0 error; exit 133 : **UNKNOWN**. |
| `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest` (GREEN) | XML JUnit 24 tests, 0 failure, 0 error; exit 133 : **UNKNOWN**. |
| `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.picture.W6bFilterPictureTest` | XML JUnit 9 tests, 0 failure, 0 error; exit 133 : **UNKNOWN**. |
| `rtk ./gradlew :gpu-plan:test --tests org.graphiks.kanvas.gpu.plan.RenderGraphContractTest` | BUILD SUCCESSFUL, 94 assertions/contrats verts, exit 0. |
| `rtk ./gradlew :gpu-plan:compileKotlin` | BUILD SUCCESSFUL, exit 0. |
| `rtk ./gradlew :gpu-renderer:compileKotlin` | BUILD SUCCESSFUL, exit 0. |
| `rtk ./gradlew :kanvas:compileTestKotlin` | BUILD SUCCESSFUL, exit 0. |
| `rtk ./gradlew :gpu-plan:test` | 268 tests, 28 failures W3/W4 préexistantes/hors scope; `RenderGraphContractTest` reste vert. |
| `rtk ./gradlew :kanvas:test` | La suite complète atteint à nouveau l'exit natif 133. Sans preuve indépendante exhaustive après cet arrêt, ce gate reste **UNKNOWN**; il n'est ni succès ni régression. |
| `rtk git diff --check` | GREEN, aucune erreur de whitespace. |

### Fichiers round 2

Production :

- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/OccurrenceSourceInputV1.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`

Tests :

- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bImageBlurSurfacePixelTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bFilterAdmissionRecoverySurfaceTest.kt`

### Self-review round 2

- L'alpha, le color filter et le snapshot viennent exclusivement de l'operand
  et du terminal publiés; la copie carrier est toujours neutre et le terminal
  ne double pas les effets.
- Le test pixel emploie un attendu fixe (teal + demi-alpha blue après swap),
  calculé avant la `Surface`; ses mutations réalistes couvrent omission de
  l'alpha, du filter ou du snapshot.
- Le scissor n'est admis qu'après preuve plan-owned des quatre bords entiers;
  l'abaisseur vérifie la même représentation exacte au lieu d'arrondir.
- Empty sous toute matrice finie est un no-op observé publiquement, sans
  fallback W4/W5; une sortie non initialisée n'est jamais promue en
  `knownContent`, conformément à R14.
- Les tests sont des pixels/sentinels publics sur la vraie `Surface`; aucun
  mock, fake-device, reflection, compteur ou hook test-only n'est présent.

### Concerns round 2

1. Chaque selector natif Kanvas se termine en exit 133 après ses assertions
   JUnit vertes : ils restent **UNKNOWN**, conformément à la règle explicite.
2. `:gpu-plan:test` complet conserve les 28 failures W3/W4 historiques hors
   scope; le gate contractuel Task 3 est vert (94/94).
3. La suite Kanvas complète termine également 133; faute de preuve exhaustive
   indépendante après l'arrêt, elle est documentée comme **UNKNOWN**, pas
   comme une régression introduite par ce correctif.
