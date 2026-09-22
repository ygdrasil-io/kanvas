# Task 2 — W6b filter authority, bounds, targets and terminal admission

## Révision

- Base imposée : `ee84ea48d1fedae9ba3e429460e1cb676e27a12e`
- Head Task 2 : `de40ebdbce1094e2ea342724732c0e1e48dd964e`
- Commit : `feat(gpu-plan): freeze w6b filter authority`

## RED → GREEN → refactor

1. RED public : ajout de `W6bFilterAdmissionRecoverySurfaceTest`, puis exécution sur
   la production inchangée avec
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'`.
   Exit Gradle `1` (l'exécuteur natif a ensuite quitté `133`), mais le résultat JUnit
   causal a été `tests=2, failures=1, errors=0` :
   `w6c filter refuses terminally and same surface recovers()` attendait la
   terminaison, alors que `readPixels` avait complété. C'était le fallback legacy
   W6a de l'`ImageFilter.Offset` (W6c), non une erreur de harness. Le contrôle
   W6a sans filtre passait déjà.
2. GREEN public : après l'autorité terminale, le même shard a observé le refus
   `w6b.filter.unsupported_family`, aucune mutation du tampon sentinelle, puis le
   recovery sur la même `Surface` avec `ColorARGB.of(255, 17, 61, 211)`.
3. Refactor : les diagnostics sont centralisés dans `W6bFilterDiagnostics`; les
   payloads/passes sont des types scellés gelés, sans objet filtre public. Les
   gates finaux et les XML JUnit ci-dessous ont été relancés après ce refactor.

## Contrats implémentés

- Une seule sélection W6 : le candidate gate reconnaît les image/mask/backdrop
  filters; `CapabilityCompilerChain` délègue à `W6aLayerPlanCompiler`; celui-ci
  appelle `W6bFilterGraphConstruction` avant toute construction de child lane ou
  allocation physique. Le routeur ne peut jamais revenir au legacy après cette
  ownership.
- `W6bFilterGraphConstruction` ne lit que `CapturedFilterTableV1`,
  `CapturedFilterRootV1` et `CapturedFilterInputV1`; le payload typé
  `DropShadowComposite` porte `CapturedDropShadowModeV1`.
  Une recherche dédiée dans `gpu-plan/src/main/kotlin` des objets publics
  `ImageFilter`/`MaskFilter` ne retourne aucun match (exit `1` de `rg`).
- Diagnostics stables : `w6b.filter.unsupported_family`,
  `w6b.filter.unsupported_backdrop`, `w6b.filter.filtered_previous`,
  `w6b.filter.unsupported_target_format`,
  `w6b.filter.native_execution_unimplemented`, plus les diagnostics gelés de
  bounds, budget et capability. Les branches positives W6b restent refusées par
  `native_execution_unimplemented` jusqu'à Task 3.
- `:math` possède l'expansion blur F64, la projection checked I32, le rebasing
  target-local et le mapping `mapDeviceRectToTargetI32OrNull`; aucun convertisseur
  de bounds n'est ajouté au renderer.
- `FilterBoundsPlanV1`, `FilterEvaluationKeyV1`, `FilterPassOperationV1`,
  `FilterAxisV1` et les neuf `FilterImplementationKindV1` figent l'identité
  `(capturedNodeId, boundSourceId, mappingF64, desiredOutputI32)`, les snapshots
  de bounds et les payloads blur/mask/shadow sans objet API public.
- `PlanResourceRole.FilterTarget` est limité au target RGBA8 premultiplied,
  single-sample, frame-local, RenderAttachment+Sampled. Les validations W6a
  vérifient les rectangles target-local et les usages à la publication.
- Le budget W6a/W6b prend le maximum du peak logique et de la somme physique
  I64 checked. Chaque slot physique retient `reservedBytesI64`; un cache warm ne
  réduit donc jamais l'admission.

## Fichiers modifiés/créés

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RectProjectionF64.kt`
- `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/LayerMappingF64.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/{CapabilityCompilerChain,PlanPasses,PlanPhysicalLayoutV1,PlanResources,RenderGraph,W6aLayerGraphValidation,W6aLayerPlanBudget,W6aLayerPlanCompiler}.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/{W6bFilterDiagnostics,W6bFilterGraphConstruction,W6bFilterPlanV1}.kt`
- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
  (adaptation du constructeur `FilterPass` existant au contrat typé)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/{GPUPlanSurfaceCandidateGate,GPUPlanSurfaceRouter}.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/{W6aLayerSurfacePixelTest,W6bFilterAdmissionRecoverySurfaceTest}.kt`
  (le premier met à jour les owners diagnostiques W6b; le second est le témoin
  public RED/GREEN et le contrôle W6a sans filtre)

## Gates finales (sérielles)

| Commande | Exit | Garde de preuve |
|---|---:|---|
| `rtk proxy ./gradlew :math:geometry:compileKotlinJvm` | 0 | `BUILD SUCCESSFUL` |
| `rtk proxy ./gradlew :math:matrix:compileKotlinJvm` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :render-ir:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :gpu-plan:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :kanvas:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'` | 1 / native 133 | XML JUnit : `tests=2, failures=0, errors=0` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'` | 1 / native 133 | XML JUnit : `tests=16, failures=0, errors=0` |

Custodie JUnit :

- `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest.xml`
  (timestamp `2026-09-22T09:51:22.518Z`, les 2 méthodes sont présentes et vertes)
- `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest.xml`
  (timestamp `2026-09-22T09:51:42.311Z`, les 16 méthodes sont présentes et vertes)

Les deux JVMs de tests ont ensuite terminé à `133` (`Gradle Test Executor 37`
et `38`). Conformément aux règles W6, ces sorties natives sont `UNKNOWN`, pas
des succès ni des échecs comportementaux; les XML sont la garde de custody.

## Vérification et revue personnelle

- `rtk git diff --check` : exit `0`.
- `rtk git diff --cached --check` : exit `0` immédiatement avant le commit.
- Revue manuelle des routes `GPUPlanSurfaceRouter`,
  `GPUPlanSurfaceCandidateGate`, `CapabilityCompilerChain`,
  `W6aLayerPlanCompiler`, `W6bFilterGraphConstruction` et des validations de
  graph : aucune route/planner parallèle ni fallback après l'ownership W6b.
- La source conserve le comportement W6a sans filtre (témoin direct public) et
  les comportements non-W6 restent hors du nouveau gate.

## Risques et limites restantes

- L'exécution positive image-blur/mask/drop-shadow est expressément gelée pour
  Task 3; Task 2 refuse donc uniformément ces bras avant toute allocation native.
- `:gpu-plan:compileTestKotlin` a exit `1` à cause de cinq erreurs de tests
  préexistantes de migration Task 1, hors périmètre W6b :
  `W3SolidRectPlanCompilerTest` lignes 214 et 406 (ancien `ImageFilterNode` et
  branche `MaterialOnlyRefusal`), et `W4dPathStrokePlanCompilerTest` lignes 157
  et 703 (ancien `ImageFilterNode`, exhaustivité `ImageV1` et return type). La
  rupture provenant du nouveau constructeur `FilterPass` a été corrigée dans
  `RenderGraphContractTest`; aucune erreur W6b ne reste dans cette compilation.
- Le format public GPU actuel n'expose que les variantes RGBA8/BGRA8; l'admission
  W6b refuse terminalement toute valeur non `RGBA8_UNORM_SRGB`. Une future surface
  F16/HDR devra garder ce diagnostic avant allocation et recevoir son témoin
  public dédié.
- Les exits 133/134 n'ont pas de preuve indépendante ici et restent `UNKNOWN`.

## Fix round 1 — revue `de40ebdbc..working tree` (2026-09-22)

### Base et head

- Base originelle Task 2 : `ee84ea48d1fedae9ba3e429460e1cb676e27a12e`.
- Base de cette correction : `de40ebdbce1094e2ea342724732c0e1e48dd964e`
  (`feat(gpu-plan): freeze w6b filter authority`).
- Head : `d7b8ffb` — `fix(gpu-plan): complete w6b filter admission graph`.

### Disposition des cinq findings

1. **Fallback capture après ownership W6b — corrigé.**
   `GPUPlanSurfaceRouter` interdit maintenant le fallback `CAPTURE_LIMIT_CODES`
   aussi lorsque `w6bOwned`. Le témoin public `filtered capture limit` construit une
   Picture filtrée sous `GraphLimits(maxNodes = 1)`, vérifie `graph-node-limit:`,
   l'absence de mutation de la sentinelle et le recovery sur la même `Surface`.
2. **Graphe W6b réellement gelé — corrigé.**
   `W6bFilterGraphConstruction` lie les occurrences capturées Blur/Mask/DropShadow à
   une `SourceBinding` immutable, produit les `FilterTarget` RGBA8, les `FilterPass`,
   `FilterBoundsPlanV1` et `FilterEvaluationKeyV1`, puis les ajoute au seul
   `W6aLayerGraphConstruction`. Celui-ci publie le graph et son budget I64 physique
   pessimiste avant que `W6aLayerPlanCompiler` ne retourne
   `w6b.filter.native_execution_unimplemented`; aucune allocation native ni exécution
   renderer n'est introduite. Les effets capturés dans `EffectStack` sont extraits une
   seule fois (le paint les duplique), donc le W5 lane ne replannifie pas de filtre.
3. **Clé d'évaluation X/Y — corrigé.**
   `FilterPass` valide les inputs immédiats sans exiger que chacun contienne la source
   d'occurrence. Le test de contrat de production ajoute X/Y : Y lit le target X et
   conserve exactement la même clé/source liée. Le selector `:gpu-plan:test` ne peut
   pas atteindre ce test car la compilation de test échoue auparavant sur les cinq
   migrations W3/W4d préexistantes listées plus bas.
4. **Mapping target-local — corrigé.**
   `mapDeviceRectToTargetI32OrNull` reçoit maintenant l'origine explicite du
   `FilterTarget` et délègue à la soustraction checked I32 de `:math`; il n'utilise
   plus l'origine de layer. Le test public math démontre un target origin distinct.
5. **Pictures bornées et preuves publiques — corrigé.**
   Le gate de surface traverse les `Picture.ops` avec identité, borne et fail-closed;
   la frontière IR traverse les `GeometryNode.Picture.scene` avec profondeur, nombre
   de commandes et ancêtres bornés. Les témoins publics couvrent Picture imbriquée,
   filtered-previous, capture-limit, format BGRA constructible et recovery. Le
   diagnostic backdrop stale est maintenant `w6b.filter.unsupported_backdrop`.

### RED → GREEN → refactor

- RED public initial :
  `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'`
  a donné JUnit `tests=6, failures=2, errors=0` (Picture imbriquée routée vers
  `unsupported.composite.paint`; capture-limit non terminal), avant correction;
  le process Gradle était également `1`/executor `133`.
- RED public additionnel : le gate
  `W6aLayerSurfacePixelTest.unsupportedSpatialFilterRefusesTerminallyAndRecovers`
  révélait que les roots pouvaient être représentés par `EffectStack`; il recevait
  `w6a.layer.unsupported_spatial_filter`. Après extraction capturée et strip du
  payload W6b, le même test est vert. La vérification du chemin ColorFilter existant
  a révélé une garde de restore trop large; elle a été retirée, puis le gate W6a
  entier a été relancé vert.
- GREEN/refactor : les targets W6b restent frame-local pendant tout le frame, ce qui
  respecte l'invariant existant de `PlanPhysicalLayoutV1` et rend l'admission I64
  strictement pessimiste. Aucun mécanisme de cache ne réduit ce coût.

### Fichiers modifiés dans la correction

- `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/LayerMappingF64.kt`
  et `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix3x3F64Test.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/{PlanPasses,W6aLayerGraphConstruction,W6aLayerPlanCompiler,W6bFilterGraphConstruction,W6bFilterPlanV1}.kt`
- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/{GPUPlanSurfaceCandidateGate,GPUPlanSurfaceRouter}.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/{W6aLayerBoundsSurfacePixelTest,W6bFilterAdmissionRecoverySurfaceTest}.kt`

### Commandes et custody JUnit

| Commande exacte | Exit process | Preuve/custody |
|---|---:|---|
| `rtk ./gradlew :math:geometry:compileKotlinJvm` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :math:matrix:compileKotlinJvm` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :render-ir:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :gpu-plan:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :kanvas:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :math:matrix:jvmTest --tests 'org.graphiks.math.matrix.Matrix3x3F64Test.target mapping rebases at its explicit origin rather than the layer origin'` | 0 | JUnit `1/0/0`, `math/matrix/build/test-results/jvmTest/TEST-org.graphiks.math.matrix.Matrix3x3F64Test.xml` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'` | 1, executor 53 = 133 | JUnit `6/0/0`, `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest.xml` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'` | 1, executor 52 = 133 | JUnit `16/0/0`, `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest.xml` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest'` | 1, executor 54 = 133 | JUnit `10/0/0`, `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest.xml` |
| `rtk ./gradlew :gpu-plan:test --tests 'org.graphiks.kanvas.gpu.plan.RenderGraphContractTest.separable filter passes retain one bound occurrence while y reads x output'` | 1 | bloqué à `:gpu-plan:compileTestKotlin` par les cinq erreurs préexistantes W3/W4d, aucune erreur nouvelle W6b |

Les executors 133 restent **UNKNOWN** : les XML JUnit ci-dessus, pas l'exit du
processus natif, sont la preuve comportementale. Aucun élément indépendant ne
permet de classer 133 ou 134 en succès/échec natif.

### Self-review

- `rtk git diff --check de40ebdbc..HEAD` et `rtk git diff --check` : exit 0 avant
  staging; revue manuelle de la route capture/router, traversal candidate/IR,
  construction W6a/W6b, usages/lifetimes/budget et mapping math.
- Vérifié : aucun objet public `ImageFilter`/`MaskFilter` dans `:gpu-plan` main;
  les seuls filtres qui y entrent sont les valeurs capturées `:render-ir`.
- Vérifié : aucun planner parallèle, aucune fallback legacy après `w6bOwned`, aucune
  allocation native/exécution positive Task 2, et les diagnostics 133/134 ne sont
  pas interprétés.

### Risques restants

- Task 3 doit consommer les passes W6b gelés pour leur matérialisation native; Task
  2 les refuse volontairement avant toute exécution.
- L'API publique présente permet de construire BGRA (témoin ci-dessus), mais n'expose
  pas de `GPUColorFormat` F16/HDR constructible. La frontière générique non-RGBA est
  couverte; F16/HDR spécifique reste à tester lorsqu'un format public existera.
- `:gpu-plan:compileTestKotlin` reste bloqué hors périmètre par
  `W3SolidRectPlanCompilerTest.kt:214,406` et
  `W4dPathStrokePlanCompilerTest.kt:157,703` (ancien `ImageFilterNode`, branches
  exhaustives `MaterialOnlyRefusal`/`ImageV1`).

## Fix round 2 — lier les occurrences W6b au graphe W6a (2026-09-22)

### Base, portée et disposition de la re-review

- Base de la correction : `d7b8ffb9d6d6cae8d4fc94fac3eddc337cd46b23`.
- Head de la correction : `265972e` —
  `fix(gpu-plan): bind w6b filter occurrences`.
- La correction reste strictement Task 2 : elle ne crée aucune allocation native ni
  exécution image-blur/mask/drop-shadow. Les bras positifs publient le graphe gelé,
  puis refusent uniformément `w6b.filter.native_execution_unimplemented`.

Les trois findings Important de `task-2-rereview.md` sont corrigés ainsi :

1. **Occurrence/source/bounds/composite réels — corrigé.** Chaque occurrence directe
   rend d'abord dans son propre `FilterSource`; chaque Picture a un
   `PictureSourcePass` typé avec son `sourceSceneCanonicalId`/command index; et une
   occurrence de layer copie le `LayerTarget` scellé dans un `FilterSource` immutable
   avant le premier filtre. Les chaînes `FilterPass` sont insérées à l'index capturé
   et leur `FilterComposite` typé est émis avant tout sibling ultérieur. Un layer ne
   sert donc plus jamais de source W6b mutable. Les `FilterTarget`, pass IDs,
   dépendances sérielles, slots et durées de vie restent dans le seul graphe W6a et
   dans son peak I64 pessimiste.

   Les quatre régions restent des `FilterBoundsPlanV1` F64→I32 checked avec une
   origine explicite par target : blur conserve son expansion hors source, le shadow
   incorpore `dx/dy`, `MaskBlurStyle` porte le style capturé, et TransparentBlack est
   un `FilterTransparentBlack` produit par `FilterSourceClear` qui retient aussi le
   `boundSourceId` immutable de l'occurrence. `MaskShader` ne
   fabrique aucun `MaterialPlanRef(0)`/offset zéro : il porte une référence W5
   `Planned` réelle ou l'identité `CapturedDeferred` disjointe.
2. **Continuité X→Y et source liée — corrigé.**
   `W6bFilterGraphWitnessV1` est gelé à la publication. Il vérifie que toute
   `boundSourceId` existe, est une source d'occurrence produite avant le filtre,
   vérifie l'arité immédiate selon l'opération, la continuité producteur→consommateur
   d'une même clé (dont X→Y), l'unicité/immédiateté du composite terminal et son
   ordering avant le parent. Les fixtures de contrat construisent un vrai
   `RenderGraph`: X→Y est accepté; source sans rapport, producteur cassé et terminal
   non consommé sont refusés à la publication.
3. **Lane Picture non filtrée — corrigé.** L'ownership reste borné/cycle-safe pour
   découvrir les filtres, mais `W6aLayerGraphConstruction` garde une lane
   `PictureSourcePass` pour chaque Picture non filtrée et une source Picture typée
   exacte pour une occurrence filtrée imbriquée. Aucune source Picture manquante ne
   tombe sur le root partagé. Le témoin public mélange un sibling Picture non filtré
   et un Picture filtré, puis vérifie diagnostic terminal, sentinelle intacte et
   recovery sur la même Surface.

Les findings déjà fermés restent préservés : `CAPTURE_LIMIT_CODES` ne rétablit pas le
fallback après `w6bOwned`; le mapping target-local utilise l'origine du
`FilterBoundsPlanV1`; backdrop, filtered-previous, format non-RGBA et traversal
bornée refusent avant l'exécution native.

### RED, diagnostic systématique, puis GREEN

Un nouveau RED est apparu pendant le contrôle de non-régression W6a :

```text
rtk ./gradlew :kanvas:test --tests
'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest.unsupportedSpatialFilterRefusesTerminallyAndRecovers'
```

Avant la correction, JUnit échouait avec `w6a.layer.unsupported_child: Failed
requirement.`; le process Gradle terminait ensuite avec l'exit natif `133`. Selon le
protocole `systematic-debugging`, le selector a été reproduit, le stack complet a été
capturé dans le XML, et l'entrée a été suivie depuis le `FrameSourceLayoutV4` construit
jusqu'à `RenderGraph.publishW6a` :

- dernier boundary confirmé : `FrameSourceLayoutV4.layeredFrame(frame)` était
  `Built`;
- cause exacte : `RenderGraph.kt:274`, invariant
  `LayerExecutionStepV1.RenderChildren`; il recevait le RenderPass qui alimente le
  `FilterSource`, alors que l'invariant historique n'acceptait que le `LayerTarget`;
- contrôle de comparaison : les 14 autres lanes W6a, y compris les layers sans
  filtre, restaient verts;
- hypothèse testée minimalement : un enfant filtré doit être représenté par son
  composite terminal typé, pas par son pass source intermédiaire.

La correction publie donc le `FilterComposite` direct comme `RenderChildren`, le
valide contre le target du scope, et accepte le `FilterComposite.Layer` comme restore
du scope après sa copie `LayerTarget → FilterSource`. Les deux selectors auparavant
en échec sont ensuite verts :

```text
rtk ./gradlew :kanvas:test --tests
'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest.unsupportedSpatialFilterRefusesTerminallyAndRecovers'
--tests
'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest.restoreMaskFilterRefusesTerminallyAndRecovers'
```

JUnit : `2/0/0`; l'exécuteur a ensuite quitté `133`, qui reste `UNKNOWN`. Les sondes
temporaires de stack/diagnostic ont été retirées avant les gates finales.

### Gates finaux, exits et custody JUnit

| Commande exacte | Exit process | Preuve |
|---|---:|---|
| `rtk proxy ./gradlew :math:geometry:compileKotlinJvm` | 0 | `BUILD SUCCESSFUL` |
| `rtk proxy ./gradlew :math:matrix:compileKotlinJvm` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :render-ir:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :gpu-plan:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :kanvas:compileKotlin` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'` | 1, executor 73 = 133 | JUnit `16/0/0` (les deux regressions filtrées sont vertes) |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'` | 1, executor 74 = 133 | JUnit `8/0/0` |
| `rtk ./gradlew :gpu-plan:test --tests 'org.graphiks.kanvas.gpu.plan.RenderGraphContractTest'` | 1 | bloqué à `:gpu-plan:compileTestKotlin`, sans erreur W6b nouvelle |

Custody de la dernière gate W6b :
`kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest.xml`
enregistre `tests=8, failures=0, errors=0` (timestamp `2026-09-22T11:45:57.015Z`,
executor 74). La gate W6a précédente a affiché ses 16 méthodes vertes
dans la sortie Gradle; son XML est remplacé par la gate W6b suivante, selon le
nettoyage standard de `:kanvas:test`.

Le selector `gpu-plan` est empêché exclusivement par les cinq erreurs préexistantes :
`W3SolidRectPlanCompilerTest.kt:214,406` et
`W4dPathStrokePlanCompilerTest.kt:157,703` (anciens `ImageFilterNode`, branches
exhaustives `MaterialOnlyRefusal`/`ImageV1` et return type). La production
`:gpu-plan:compileKotlin` est verte et la fixture W6b ne produit aucune erreur de
compilation additionnelle.

### Self-review et risques restants

- `rtk git diff --check d7b8ffb9d..HEAD` et `rtk git diff --check` sont verts.
- Relecture des ressources : les FilterSource/FilterTarget sont RGBA8,
  single-sample, frame-local, et les copies de layer ajoutent explicitement
  `CopyDestination`; les durées de vie restent pessimistes jusqu'à la fin de frame.
- Relecture des routes : un W6b owned frame ne repasse ni par legacy ni par le
  renderer positif; le chemin W6b ne fabrique aucun `MaterialPlanRef(0)` ni uniform
  offset inventé.
- Aucun fait indépendant ne classe les exits natifs 133/134 : ils restent
  **UNKNOWN**.

Risque résiduel délibéré : Task 3 doit matérialiser les opérations natives à partir
de ce graphe d'occurrences déjà ordonné; la présente tâche refuse encore ce chemin
avant allocation native. Les formats publics F16/HDR ne sont toujours pas
constructibles; la frontière générique non-RGBA est couverte sans inventer de fixture.

## Fix round 3 — comparaison des lanes avant modification (2026-09-22)

Cette cartographie a été faite avant toute modification round 3, en comparant le
chemin W5 non filtré, le binding W4e, les quatre régions W6a, et leur équivalent
filtré. Les écarts exacts constatés sont les suivants.

| Autorité / étape | Lane W5/W4e/W6a qui fonctionne | Voie W6b filtrée actuelle | Écart à corriger |
|---|---|---|---|
| Source et material W5 | `W6aLayerPlanCompiler.select` crée une `Segment` W5 par Draw; `constructSourceLanes` produit une `SourceDeferredRenderConstructionV4`; `FrameSourceLayoutV4.layeredFrame` intercale exactement `frame.lanes[*].sourceTable()` puis `prepareAndFinish` publie l'unique `MaterialPlanTable` et les roots. | Le payload W6b est retiré, puis la lane W5 rend déjà le source colorisé dans `FilterSource`; le filtre image est figé avant le mask. Le mask shader est réduit à `material.canonicalId`, sans lane/row W5 supplémentaire dans `frame.lanes`. | Geler explicitement raw coverage, coverage masquée, source W5 shaded et entrée/sortie image, dans l'ordre coverage/mask → W5 material → image, et rattacher le mask shader à la seule autorité `FrameSourceLayoutV4`/`MaterialPlanTable`. |
| W4e et path général | `localNative` rebind les IDs lane, mapping de layer, clips/masks, V/I/U et `PlanW4eGeometryBindingV1`; `FrameSourceLayoutV4` remet les material roots dans les passes, puis `PlanPhysicalLayoutV1` vérifie payload, extent, pass IDs et native target. | Le RenderPass/stencil sémantique peut viser `directFilterSource`, mais `localNative` reçoit encore le mapping/les IDs du parent; la construction finale fait `PlanW4eGeometryBindingV1(targetFor(scope), ...)`. La validation ne compare pas le target de chaque pass natif avec le pass sémantique lié. | Rebind target, mapping/origine, masks et ressources target-sized vers la source d'occurrence; publier la binding avec ce même target et vérifier l'égalité native↔sémantique par graph pass ID. |
| Quatre régions W6a | `W6aLayerGraphConstruction` calcule distinctement `knownContentByScope`, `desiredOutputByScope`, `requiredInput`, `producedOutput` et `compositeDomain` avant d'allouer le layer; ces valeurs deviennent `LayerBoundsPlanV1`. | `filterSource(target)` initialise `knownContent` avec le rectangle complet du target. Au EndLayer filtré, `allocateOccurrenceSource(targetDeviceBounds(target), ...)` copie l'étendue entière et perd les régions W6a scellées. | Donner à la source copiée du layer les quatre régions W6a réelles, avec source extent/origin du `compositeDomain`, sans promouvoir l'extent physique au contenu connu. |
| Occurrence, mask, composite | Une lane W5 non filtrée conserve ordre Draw/clip/restore et le blend final; une layer step identifie ses enfants et son restore. | `freezeOccurrence` matérialise `image` puis `mask`; `MaskBlurStyle` ne reçoit que blur; les composites sont à temps mais le witness infère des contexts au lieu d'une chaîne; le Picture est supprimé par la garde globale de `W6aLayerPlanCompiler`. | Une chaîne d'occurrence explicite doit conserver raw/original/blurred coverage quand le style le requiert, chaque input/clé/terminal/composite, le Picture capturé complet et sa participation `LayerExecutionStepV1`. |

Racine commune : la voie W6b a été ajoutée comme une transformation après la lane W5
colorisée, alors que Task 2 doit geler la frontière entre coverage, material et image
filter sans créer une seconde autorité de publication. Le travail round 3 portera donc
sur une métadonnée d'occurrence typée et publiable par le seul frame layout, pas sur un
assouplissement local des validations.

### Fix round 3 — fermeture des quatre findings Important (2026-09-22)

- Base : `265972efa`; head : `HEAD` (ce commit de correction final) —
  `fix(gpu-plan): seal w6b occurrence semantics`.
- Portée : uniquement le graphe/admission/publication Task 2. Aucune allocation native,
  aucun pixel blur/mask/shadow, et aucun fallback renderer n'ont été ajoutés.

1. **Ordre coverage/material/image et quatre régions — corrigé.**
   `CoverageSource` capture la coverage brute à l'occurrence; `CoverageOriginal` la
   retient pour SOLID/OUTER/INNER; les `FilterPass` mask précèdent le RenderPass W5
   colorisé; un `FilterSource` immutable reçoit ensuite le résultat W5; l'image-filter
   est enfin suivi de son unique `FilterComposite` parent. Les `SourceBinding` retiennent
   `knownContent`, `desiredOutput`, `requiredInput`, `producedOutput` et l'origine du
   target. Les layers utilisent donc les quatre régions W6a scellées plutôt que l'extent
   complet. Blur ne reclipse jamais son expansion et drop-shadow incorpore `dx/dy`.
2. **MaskShader W5 réel — corrigé.** `W6aLayerGraphConstruction` construit une
   `MaterialSourceConstructionV4` par occurrence Shader, avec le `MaterialNode` capturé,
   transform, clip et bounds/cull exacts. `FrameSourceLayoutV4` l'ajoute à son unique
   publication W5, puis publie le vrai `MaterialPlanRef` et la vraie ressource uniform
   `SourceUniformData` dans `MaskShaderMaterialBindingV1.Planned`. Aucun canonical-id
   substitut, `MaterialPlanRef(0)` ni uniform zéro n'est produit.
3. **Witness exact d'occurrence — corrigé.** `W6bFilterGraphWitnessV1` remonte depuis
   chaque composite terminal et impose source immutable, premier input, continuité
   producteur→consommateur et identité de clé, X avant Y, arité par opération, shadow
   original/`SHADOW_ONLY`, terminal consommé une fois et composite immédiatement placé
   avant le parent. `RenderGraphContractTest` couvre la chaîne X→Y valide et les sources
   sans rapport, Y seul/clé différente, output terminal non consommé, source LayerTarget
   mutable, composite intermédiaire et formes drop-shadow invalides.
4. **Picture/W4e occurrence cibles — corrigé.** La suppression frame-globale Picture a
   disparu. Chaque Picture devient une `FilterOccurrenceSourceV1` immutable qui porte
   `SceneSnapshot`, draw, cull/transform/clip et nesting; une Picture non filtrée passe
   par `PictureSourcePass` puis `PictureComposite` avant ses siblings. Pour W4e/path,
   `physicalTargetByLane` rebind mapping/origine, target-sized resources et binding
   natif vers le même `FilterSource` que le RenderPass sémantique; `PlanPhysicalLayoutV1`
   exige l'égalité target native↔graph.

#### RED, diagnostic systématique et GREEN

Le RED public ciblé suivant recevait initialement `Failed requirement.` au lieu du
diagnostic terminal W6b, avant que l'exécution native ne soit atteinte :

```text
rtk gradlew :kanvas:test --tests
'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest.image root followed by mask occurrence remains one terminal filtered source' --no-daemon
```

Le stack complet plaçait le dernier boundary dans
`W6aLayerGraphValidation.validateW6aLayerTopology` (ligne 70) : la `RenderPass` W5
consommait le `FilterTarget` de mask, mais `FilterPass` ne marquait pas son output
initialisé. Comparaison avec les lanes W5 et raw coverage : leurs producteurs
enregistrent tous la version `0` avant consommation. Hypothèse minimale vérifiée :
initialiser chaque output `FilterPass` immutable, sans modifier les opérations ni les
routes. Après cette correction, le selector a JUnit `1/0/0` avec le diagnostic attendu
`w6b.filter.native_execution_unimplemented` (l'exécuteur Gradle sort encore 133).
Les diagnostics temporaires de stack ont été supprimés avant la revue finale.

#### Gates finals et custody

| Commande exacte | Exit process | Custody / attribution |
|---|---:|---|
| `rtk gradlew :math:geometry:compileKotlinJvm --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk gradlew :render-ir:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk gradlew :gpu-plan:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk gradlew :gpu-renderer:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk gradlew :kanvas:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest' --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest' --no-daemon` | 1, executor 133 | XML JUnit frais : Surface `8/0/0`, Picture `7/0/0` |
| `rtk gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest' --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest' --no-daemon` | 1, executor 133 | XML JUnit : `16/0/0` et `10/0/0` |
| `rtk gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.GPUPlanSurfacePixelTest.W4e hard ordered rect RRect and path clips match the independent public Surface oracle' --no-daemon` | 0 | XML JUnit frais : `1/0/0` |
| `rtk gradlew :gpu-plan:compileTestKotlin --no-daemon` | 1 | blocage baseline uniquement : W3 `214,406`; W4d `157,703`, aucune erreur W6b/fixture nouvelle |

Les XML frais W6b sont
`kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest.xml`
et `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.picture.W6bFilterPictureTest.xml`;
ils enregistrent respectivement `8/0/0` et `7/0/0`. Le log de la dernière invocation
(`1790081119_gradlew_test.log`) confirme ensuite l'exit `133`. Sans élément indépendant,
les statuts natifs 133/134 restent **UNKNOWN**, pas un succès ni une régression attribuée.

#### Fichiers changés et self-review

- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/{FilterOccurrenceSourceV1,FrameSourceLayoutV4,PlanPasses,PlanPhysicalLayoutV1,PlanResources,RenderGraph,RenderGraphConstruction,W6aLayerGraphConstruction,W6aLayerGraphValidation,W6aLayerPlanCompiler,W6bFilterGraphConstruction,W6bFilterGraphWitnessV1,W6bFilterPlanV1}.kt`
- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
- ce rapport de tâche.

`rtk git diff --check` est vert. La relecture manuelle du diff a vérifié notamment :
pas de public `ImageFilter`/`MaskFilter` dans `:gpu-plan`, pas de material référence
fabriquée, pas de lane Picture abandonnée, la consommation terminale unique, le budget
I64 pessimiste existant, et l'absence d'allocation/exécution native Task 2.

Risques délibérément laissés à Task 3 : matérialiser les opérations natives à partir du
graphe d'occurrence déjà gelé. Les formats F16/HDR restent non constructibles par l'API
publique; la frontière générique non-RGBA continue d'être couverte sans infrastructure
artificielle.

## Fix round 4 — graphe sémantique W6b complet (2026-09-22)

### Base, portée et causes vérifiées

- Base : `820e76c0a`.
- Head : le commit final `fix(gpu-plan): complete w6b semantic graph`.
- Portée : Task 2 uniquement. Le résultat reste un graphe W6a/W6b scellé puis le
  refus terminal `w6b.filter.native_execution_unimplemented`; aucune allocation,
  shader natif, pixel blur/mask/shadow, ni seconde autorité de planification n'a
  été introduit.

Les trois findings Important de `task-2-rereview3.md` ont été vérifiés dans le
code avant modification puis fermés ainsi.

1. **Mask coverage/material/image — corrigé.** La source colorisée directe était
   allouée depuis le rectangle clipé brut avant `freezeMaskOccurrence`; les halos
   de blur étaient donc perdus. `StencilCover` ne portait aucune entrée coverage,
   et le EndLayer n'émettait ni capture coverage ni mask avant la hand-off W5.
   La construction alloue désormais chaque `FilterSource` colorisé depuis le
   domaine post-mask, y compris pour Picture et layer; le RenderPass général et
   le StencilCover consomment explicitement `coverageSource`. Les occurrences de
   layer scellent `CoverageSource → mask → PictureSourcePass(layerInput)` avant
   l'image filter, avec les quatre régions W6a réelles conservées et un seul
   composite/blend final. Les sources W5 de MaskShader sont filtrées après
   construction des passes : une ligne est publiée si, et seulement si, une
   passe MaskShader capturée correspondante la consomme. Le RED layer-MaskShader
   a aussi révélé un invariant de binding périmé : `Planned` cherchait le préfixe
   historique `source-uniform-data:` alors que `PlanResourceRole.SourceUniformData`
   produit `SourceUniformData:`; il est maintenant dérivé de l'enum, sans ID
   synthétique.
2. **Witness occurrence/prédécesseur — corrigé.** Le witness ne comptait comme
   consommateur matériel que RenderPass; il classait donc à tort un mask target
   utilisé par PictureSourcePass comme terminal. `PictureSourcePass` (et le cover
   stencil typé) utilisent maintenant le même contrôle producteur/consommateur.
   `DropShadowColorize` exige maintenant son producteur immédiat de même clé :
   un `SeparableBlur` Y de kind `IMAGE_BLUR_Y`; les garanties préexistantes de
   source immutable, clé, X→Y, arité par mode, bijection terminal/composite et
   ordre immédiat restent intactes. Les nouvelles fixtures de contrat négatives
   refusent la coverage Picture sans producteur et la colorize sans Y blur.
3. **Stream Picture ordonné — corrigé.** Le chemin choisissait autrefois soit le
   SceneSnapshot entier soit les seules occurrences filtrées, en supprimant les
   siblings non filtrés; sa pile LIFO inversait les siblings imbriqués. Une
   récursion DFS ordonnée produit maintenant des `PictureCommandEntry` explicites
   et la construction émet dans cet ordre des PictureSource/PictureComposite ou
   coverage→mask→W5→filter→composite selon chaque entrée. Les transforms, clips
   et paints des Pictures externes sont gelés dans `PictureW5CoordinatesV1` pour
   la source W5 et composés dans le carrier de coordonnées MaskShader; les passes
   Picture participent aussi aux `LayerExecutionStepV1` du scope parent.

### RED, GREEN et fixtures

Le RED ciblé après la mise en place des nouveaux chemins était :

```text
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest --no-daemon
```

La nouvelle méthode `layer mask shader reaches w6b terminal admission and same
surface recovers` échouait à la publication avec `Failed requirement`. La trace
plaçait exactement la cause dans
`FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned` : le test de préfixe
de `uniformResource` était en minuscule avec tirets, tandis que `planResourceId`
utilise le nom enum `SourceUniformData`. Après la correction enum-dirigée, les
traces temporaires ont été supprimées et le selector cible est vert (JUnit), puis
l'exécuteur natif retourne encore 133, classé **UNKNOWN**.

Les témoins ajoutés sont tous à frontière publique ou contrat de graphe :

- Surface : un Picture unique mélange les children non filtrés, un sibling Picture
  imbriqué filtré, puis un sibling final; un second témoin exécute un child
  MaskShader sous transform, clip et paint externes. Tous vérifient le diagnostic
  terminal, sentinelle non mutée et recovery sur la même Surface.
- Contrat : une chaîne coverage Picture X→Y valide est reconnue comme consommée;
  les cas sans producteur et shadow colorize sans vertical blur sont refusés.
  Aucun fake device, reflection, mock d'infrastructure ou test source statique
  n'a été ajouté.

### Gates, exits et custody JUnit

| Commande exacte | Exit process | Preuve / attribution |
|---|---:|---|
| `rtk proxy ./gradlew :math:geometry:compileKotlinJvm --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk proxy ./gradlew :math:matrix:compileKotlinJvm --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :render-ir:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest' --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest' --no-daemon` | 1, executor 133 | XML frais : Surface `11/0/0`, Picture `7/0/0`; les méthodes JUnit sont toutes vertes avant l'exit natif UNKNOWN |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest' --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest' --no-daemon` | 1, executor 133 | XML : W6a `16/0/0`, bounds `10/0/0`; UNKNOWN natif après JUnit vert |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.GPUPlanSurfacePixelTest.W4e hard ordered rect RRect and path clips match the independent public Surface oracle' --no-daemon` | 0 | JUnit `1/0/0`, `BUILD SUCCESSFUL` |
| `rtk ./gradlew :gpu-plan:compileTestKotlin --no-daemon` | 1 | seulement les cinq erreurs baseline : W3 `214,406`; W4d `157,703`; aucune erreur dans `RenderGraphContractTest` ni source W6b nouvelle |

Custody W6b :
`kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest.xml`
contient `tests=11, failures=0, errors=0`, et
`kanvas/build/test-results/test/TEST-org.graphiks.kanvas.picture.W6bFilterPictureTest.xml`
contient `tests=7, failures=0, errors=0`. Les XML W6a de la gate précédente
enregistrent `16/0/0` et `10/0/0`; la gate W4e ultérieure peut remplacer les
résultats Gradle standard mais son log confirme sa méthode verte et l'exit 0.

### Fichiers modifiés, self-review et risques

- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/{FilterOccurrenceSourceV1,PlanPasses,RenderGraph,RenderGraphConstruction,W6aLayerGraphConstruction,W6aLayerGraphValidation,W6bFilterGraphConstruction,W6bFilterGraphWitnessV1,W6bFilterPlanV1}.kt`
- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bFilterAdmissionRecoverySurfaceTest.kt`
- ce rapport.

La self-review finale de `820e76c..HEAD` et `rtk git diff --check` a vérifié :
pas de réouverture des findings routing/origin/capture/W4e/diagnostic/budget déjà
clos; pas de public filter object dans `:gpu-plan`; pas de géométrie nouvelle hors
`:math`; lifetimes frame-local pessimistes et calculs I64 checked conservés; aucun
fallback ou travail natif positif; et aucune trace de debug restante.

Risques délibérément restants : Task 3 doit matérialiser le graphe coverage/W5/image
et le stream Picture déjà ordonné, sans le replanifier. Les cinq erreurs de compilation
de test W3/W4d et les exits natifs 133/134 restent hors périmètre et **UNKNOWN**;
ils ne sont ni reclassés en succès, ni attribués à cette correction.

## Fix round 4 — fermeture du stream Picture amendé (2026-09-22)

### Causes vérifiées et fermeture apportée

La revue de l'amendement a confirmé que le descripteur `LayerEntry` précédent ne
référençait pas le sous-scope W6a réel : aucune cible enfant, séquence d'étapes,
ni terminal/composite unique n'étaient publiés dans l'ordre du stream. Une telle
description seule n'était pas exécutable par Task 3. La construction W6a réutilise
donc maintenant son autorité existante pour créer une cible dynamique de Picture,
un `LayerScopePlanV1` réel relié par `parentTargetResource`, ses étapes d'exécution
et son unique `LayerComposite` ou `FilterComposite(Layer)`. Le `LayerEntry` contient
les IDs de ce scope et de cette cible; le validateur de stream vérifie cette
fermeture, l'ordre des terminaux et le cycle de vie begin/accumulating/seal.

Les trois constats encore ouverts sont ainsi scellés dans la même fermeture :

- Les sources de filtre masquées partent du domaine coverage post-mask. Les chemins
  stencil/général consomment la coverage typée, et une occurrence layer fige
  coverage/mask avant W5, y compris Blur/Table/Shader. Une ligne MaskShader W5 est
  publiée seulement lorsqu'une passe correspondante la consomme.
- Le witness reconnaît de façon identique les consommateurs coverage `RenderPass`,
  `StencilCover` et `PictureSourcePass`; `DropShadowColorize` exige le blur vertical
  de même clé. La production de cible de `StencilCover` est aussi enregistrée pour
  que le chemin stencil filtré possède un prédécesseur réel.
- Le stream Picture porte des entrées source ordonnées, filtrées et non filtrées,
  y compris les siblings imbriqués. Il distingue inline sans paint de l'isolated
  peint/filtré, conserve mapping, inner clip, composite clip différé, cull et demand,
  et utilise une source RGBA prémultipliée `GraphTextureSourceOperandV1` peinte une
  seule fois. Une correction d'audit supplémentaire évite de recomposer les
  transforms de Pictures déjà absorbés par une cible enfant.

Le diagnostic de refus reste `w6b.picture_stream.invalid`. Les nouvelles preuves
ne rendent aucun pixel natif positif : elles exercent uniquement le contrat de
graphe et les sentinelles/recovery publics autorisés.

### Preuves finales

| Commande exacte | Exit process | Résultat |
|---|---:|---|
| `rtk ./gradlew :gpu-plan:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk proxy ./gradlew :math:geometry:compileKotlinJvm --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk proxy ./gradlew :math:matrix:compileKotlinJvm --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :render-ir:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :kanvas:compileKotlin --no-daemon` | 0 | `BUILD SUCCESSFUL` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest' --no-daemon` | 1, executor 133 | JUnit énumère `15/0/0`; exit natif **UNKNOWN** |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest' --no-daemon` | 1, executor 133 | JUnit énumère `16/0/0`; exit natif **UNKNOWN** |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.GPUPlanSurfacePixelTest.W4e hard ordered rect RRect and path clips match the independent public Surface oracle' --no-daemon` | 0 | JUnit `1/0/0`, `BUILD SUCCESSFUL` |
| `rtk ./gradlew :gpu-plan:compileTestKotlin --no-daemon` | 1 | uniquement les cinq erreurs baseline W3/W4d (`214`, `406`, `157`, `703` x2); aucune erreur nouvelle dans la source de contrat W6b |
| `rtk git diff --check 820e76c0a` | 0 | aucun défaut de whitespace |

Custody fraîche : le XML produit par le dernier gate W6b,
`kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest.xml`,
enregistre `tests=15`, `failures=0`, `errors=0` avant l'exit 133 du processus
natif. Le dernier gate W6a a également énuméré ses 16 méthodes vertes avant le
même exit 133; la gate W4e ultérieure, satisfaite depuis le cache Gradle, est
sortie 0.

Les fixtures ajoutées couvrent le `LayerEntry` sans vrai scope W6a (refus du
diagnostic stable), un layer `initWithPrevious`, un layer MaskShader et des
Pictures isolated imbriqués qui conservent chaque préfixe de transform une fois.
Le test de contrat ne peut pas s'exécuter séparément tant que la compilation de
tests `:gpu-plan` reste bloquée par les cinq erreurs hors périmètre ci-dessus; le
compilateur a néanmoins attribué zéro erreur aux sources ajoutées.

### Auto-revue et risque restant

L'auto-revue de `820e76c0a..HEAD` plus le diff non committé a vérifié l'absence
d'un second planner/material authority, de public filter object dans `:gpu-plan`,
de géométrie hors `:math`, et de nouvelle exécution native. Les IDs, budgets et
lifetimes restent frame-local, pessimistes et checked I64. Le seul risque assumé
reste Task 3 : matérialiser ce graph fermé sans le replanifier; les sorties natives
133/134 et la dette de compilation W3/W4d restent explicitement **UNKNOWN**.
