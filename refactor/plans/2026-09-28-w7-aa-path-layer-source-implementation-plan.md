# W7 AA Path Layer Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre un path AA à couleur solide et `SrcOver` comme enfant d'une `saveLayer` W6, avec source couleur résolue isolée et composition ordonnée.

**Architecture:** W4d.2 publie un fragment de passes path 4× MSAA sans readback ; W6 lui attribue des ressources et IDs propres, résout dans une texture single-sample échantillonnable, puis compose cette texture une fois dans la cible du scope. La validation et le renderer consomment les mêmes passes/recettes gelées. `ResolvedCoverage` pour W6b reste un sous-projet séparé.

**Tech Stack:** Kotlin Multiplatform, Gradle, `:gpu-plan`, `:gpu-renderer`, API publique `Surface`, backend WebGPU/Metal.

**Spec:** `refactor/specs/2026-09-28-w7-aa-path-layer-source-design.md`.

## Global Constraints

- Première tranche : `saveLayer` explicite, path AA, matériau couleur solide, blend `SrcOver`, sans image/mask filter ni clip complexe ; `DirectTriangle` et `StencilCover` admis.
- Identité et translation, y compris fractionnaire et origine de layer non nulle ; aucune extension implicite de la route W4d autonome aux transforms étroites.
- Source `ResolvedColor` premultiplied, transparente hors path, indépendante de la cible parent ; aucun `ReadbackPass` enfant ni resolve vers le `LogicalTarget` du scope.
- Source `ResolvedCoverage` distincte et non implémentée ; ne jamais dériver la couverture d'un alpha de couleur, ni accepter un mask filter par cette route.
- Chaque ressource/passe/recette native et son coût sont publiés avant allocation ; checked-I64, budget W6 conservateur, refus B−1 avant `device.create*`, aucun aliasing présumé.
- Tout nouvel objet ou calcul géométrique relève exclusivement de `:math` et suit la nomenclature I/F32/F64. Aucun test d'infrastructure du code : tests de pixels/diagnostics via `Surface` uniquement.
- Pas de fonts, codecs, décodage externe, `jpg-color-cube`, régénération de GM/références/scores/dashboard, ni revendication ISO ou clôture W6 2A1/2B.
- Shell préfixé `rtk` ; Gradle `--no-daemon --no-parallel --no-build-cache`, un run à la fois. Reporter séparément XML JUnit, exit Gradle et éventuel exit natif macOS 133/134.
- Exécution après approbation du plan : Subagent-Driven Development demandé par l'utilisateur ; Sol pour les reviews, Astra uniquement si blocage architectural persistant. Commits ciblés ; préserver les changements d'autrui et la branche W7.

## Review Focus

1. Parent déjà peint : la source AA ne peut pas résoudre sur lui ni modifier les pixels hors path (Task 1, test `aa direct triangle preserves painted parent`).
2. Matériau alpha partiel : le `SrcOver` ne doit être appliqué ni zéro ni deux fois (Task 3, test `aa source alpha is composed exactly once`).
3. Deux enfants consécutifs : le stencil, le resolve et les versions de destination ne fuient pas entre occurrences (Tasks 2/3, tests `aa stencil children are isolated` et `aa children preserve order`).
4. Origine de layer et translation fractionnaire : le rebase et l'échantillonnage portent la même origine gelée (Task 3, test `aa translated layer keeps source alignment`).
5. MSAA/resolve absent ou budget B−1 : refus typé pré-allocation et `Surface` réutilisable (Task 4, préflight natif et test public `aa layer B minus one refuses` ; capacité absente vérifiée seulement si le device réel la présente).

---

## File map

- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt` : extraction du fragment AA source, admission W6 étroite, préservation du graphe autonome.
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/SourceDeferredRenderConstructionV4.kt`, `RenderGraph.kt`, `PlanResources.kt` : topologie source `AaResolvedColor`, rôle `PathAaResolvedColor`, validation AA sans readback terminal.
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt`, `W6aLayerGraphConstruction.kt`, `W6aLayerGraphValidation.kt`, `W6aLayerPlanBudget.kt` : sélection ciblée, IDs/ressources/passes W6, composition, refus et coût agrégé.
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`, `PlanPhysicalLayoutV1.kt`, `NativeSiteRecipeV1.kt` : pass `PathAaColorComposite`, slots physiques et recettes gelées ; `RenderGraph.kt` vérifie son ordre `RenderChildren` dans le scope.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt`, `passes/GPUPlanW4dGeneralPreparedAuthority.kt` : extraire la préparation des phases AA sans imposer le readback du graphe autonome.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUW6aLayerFramePlan.kt`, `execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `execution/GPUFramePreflighter.kt`, `execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` : liaison native des passes AA, resolve, composition et préflight de leurs ressources/recettes.
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7AaPathLayerSurfacePixelTest.kt` (nouveau) : seuls nouveaux tests, via l'API publique ; `refactor/waves/W07-gm-convergence/status.md` : bilan après vérification.

### Task 1: Fragment `ResolvedColor` et triangle AA bout en bout

**Files:** Modify `W4dGeneralPathPlanCompiler.kt`, `SourceDeferredRenderConstructionV4.kt`, `RenderGraph.kt`, `PlanResources.kt`, `W6aLayerPlanCompiler.kt`, `W6aLayerGraphConstruction.kt`, `W6aLayerGraphValidation.kt`, `PlanPasses.kt`, `PlanPhysicalLayoutV1.kt`, `NativeSiteRecipeV1.kt`, les six fichiers renderer de la file map ; Create `W7AaPathLayerSurfacePixelTest.kt`.

**Interfaces:** Ajouter `DeferredLaneTopologyV4.AaResolvedColor`, `PlanResourceRole.PathAaResolvedColor`, `PlanPassRole.PathAaColorComposite` et un `W4dGeneralPathPlanCompiler.W6_AA_COLOR_SOURCE_CAPABILITY_ID` distinct de l'ID AA autonome. Le constructeur W4d reçoit `allowAaColorSource: Boolean = false`, conservé par ses méthodes de copie ; seul le compilateur enfant W6 active ce flag avec `acceptsNarrowTransforms=true`. Son `constructSources(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<SourceDeferredRenderConstructionV4>` rend alors une lane sans readback : `MultisampleColorTarget` 4×, `DepthStencil` 4× si le fragment l'exige, `PathAaResolvedColor` 1× (`RenderAttachment`, `Sampled`), V/I/U et un `PathRenderPass` coloré dont `resolveTarget` désigne la source isolée. `PlanPass.PathAaColorComposite(ordinal: Int, source: PlanResourceId, destination: PlanResourceId, sourceBoundsLayerI32: RectI32, destinationOriginLayerI32: Point2I32, destinationVersionAfter: DestinationVersionI64)` applique `SrcOver` fixe. Le validateur de lane AA est distinct du validateur autonome W4d, qui garde son readback terminal et son refus de construction en source hors W6.

- [ ] **Step 1: Écrire le RED public.** `aa direct triangle preserves painted parent` : cible 7×7, parent rouge opaque, `saveLayer`, triangle bleu AA `(1,1),(5,1),(1,5)`, restore. Fixer avant `Surface` les pixels pleinement intérieur `(1,1)` et extérieur `(0,0),(5,5)` ; vérifier un pixel de diagonale AA par alpha strictement intermédiaire dans une variante sur fond transparent, et `Render`/`Readback`. Le test doit échouer sur `w6a.layer.unsupported_child` avant correction.
- [ ] **Step 2: Exécuter le RED isolé.** Run `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W7AaPathLayerSurfacePixelTest.aa direct triangle preserves painted parent' --no-daemon --no-parallel --no-build-cache`. Expected : échec de sélection W6, non un oracle dérivé du renderer ; noter XML et code natif séparément.
- [ ] **Step 3: Publier la lane AA sans readback.** Le W6-only child selector choisit la variante W4d.2 `allowAaColorSource=true` avec `acceptsNarrowTransforms=true` **seulement** pour le draw AA solide `SrcOver` sans filtre/clip complexe ; les autres sélecteurs gardent leur ordre. Factoriser `aaGraph` pour partager les phases/ressources/identités sans copier sa cible racine ou son `ReadbackPass`. Dans `RenderGraph.validateConstructionTopology`, valider la capability source distincte par ses propres règles (4×/1×, resolve terminal de la lane, usages, lifetimes, dépendances, matériau et aucune passe readback) avant la branche autonome qui exige un readback.
- [ ] **Step 4: Construire la source W6 et sa composition.** `W6aLayerGraphConstruction` remappe le rôle `PathAaResolvedColor` vers un ID unique distinct du scope, charge les ressources physiques sans aliasing, émet les passes AA adjacentes puis `PathAaColorComposite` à l'index du draw. `W6aLayerGraphValidation` et le contrôle `RenderChildren` de `RenderGraph` imposent source initialisée/résolue avant lecture, `SrcOver` unique, version destination +1 et aucun effacement du parent.
- [ ] **Step 5: Extraire la préparation native AA.** `W4dGeneralPathGraphLowerer`/`GPUPlanW4dGeneralPreparedAuthority` exposent la préparation des phases W4d AA à W6 sans exiger le readback du graphe autonome ; les deux consommateurs authentifient le même draw et les mêmes ressources scellées.
- [ ] **Step 6: Brancher l'exécution W6.** `GPUW6aLayerFramePlan`, le matérialiseur W6 et `GPUFramePreflighter` lient MSAA/depth/resolve puis composent la texture 1×, sans choix géométrique ou pass/slot tardif ; `NativeSiteRecipeV1` et `PlanPhysicalLayoutV1` inventorient chaque site avant allocation et refusent un pipeline/cible manquant.
- [ ] **Step 7: Vérifier et commit.** Run séparément `rtk ./gradlew :gpu-plan:compileKotlin --no-daemon --no-parallel --no-build-cache`, `:gpu-renderer:compileKotlin`, `:kanvas:compileTestKotlin`, puis le sélecteur public de Step 2. Expected : compiles exit 0 et pixels/scopes verts ; sinon classifier capacité GPU vs défaut de route sans remplacer l'oracle. Commit des seuls fichiers Task 1 ; revue Sol des IDs, resolve isolé, recette et absence de régression W4d autonome avant Task 2.

### Task 2: Paire stencil AA et fill rules

**Files:** Modify `W4dGeneralPathPlanCompiler.kt`, `RenderGraph.kt`, `W6aLayerGraphConstruction.kt`, `W6aLayerGraphValidation.kt`, `NativeSiteRecipeV1.kt`, `GPUW6aLayerFramePlan.kt`, `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `GPUFramePreflighter.kt`, `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`, `W7AaPathLayerSurfacePixelTest.kt`.

**Interfaces:** La lane `AaResolvedColor` accepte la paire `MultisampleStencilProducer` / `MultisampleStencilColorCover` avec même `canonicalGeneralPathAtomicGroup`, même D24S8 4×, `ClearZeroStore` puis `LoadStoreTestReset`. Seul le cover coloré résout vers `PathAaResolvedColor`; le composite reste celui de Task 1.

- [ ] **Step 1: Écrire trois RED publics.** `aa concave stencil path preserves notch` utilise un L concave sur 7×7, winding, avec pixels pleinement dedans/dehors fixés avant `Surface`. `aa even odd stencil path preserves hole` utilise deux contours concentriques even-odd et contrôle le trou ; `aa stencil children are isolated` enchaîne deux paths de couleurs distinctes et contrôle le second plus un extérieur transparent. Chaque succès exige `Render`/`Readback`.
- [ ] **Step 2: Exécuter les trois sélecteurs isolément.** Même commande Gradle que Task 1 avec chaque nom de test. Expected : RED de stratégie stencil, pas changement d'oracle ni fallback hard-edge.
- [ ] **Step 3: Étendre la construction et le seal.** Importer la paire W4d sous IDs W6, depth/stencil 4× et groupe atomique vérifiés ; clear/test/reset et resolve sur le cover final seulement. Le renderer consomme les deux recettes natives W4d sans créer une seconde géométrie ou une seconde application de couleur.
- [ ] **Step 4: Vérifier et commit.** Compiles ciblées `:gpu-plan`, `:gpu-renderer`, `:kanvas:compileTestKotlin`, puis les trois sélecteurs et le triangle de Task 1, séparément. Expected : XML `failures=0, errors=0, skipped=0` pour les succès et aucun écart de parent/child. Commit ciblé ; revue Sol de l'atomicité et du stencil reset avant Task 3.

### Task 3: Ordre, alpha et mapping de layer

**Files:** Modify `W6aLayerGraphConstruction.kt`, `W6aLayerGraphValidation.kt`, `PlanPasses.kt`, `GPUW6aLayerFramePlan.kt`, `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `GPUFramePreflighter.kt`, `W7AaPathLayerSurfacePixelTest.kt`.

**Interfaces:** `PathAaColorComposite` conserve le rectangle source et l'origine destination en coordonnées I32 locales, dérivés une seule fois du mapping device W6 ; sa version destination est checked-I64. Le `PathRenderPass` AA conserve la transformation F32/F64 déjà préparée par W4d ; aucun re-raster ou biais n'est ajouté au renderer.

- [ ] **Step 1: Écrire les RED publics.** `aa children preserve order` peint deux triangles AA qui se chevauchent dans une même layer et vérifie les pixels de chevauchement et d'extérieur ; `aa source alpha is composed exactly once` utilise une couleur source alpha 128 sur parent opaque et un pixel pleinement couvert, avec oracle `SrcOver` fixé avant `Surface` ; `aa translated layer keeps source alignment` compare la frame avec origine non nulle/translation fractionnaire à son oracle translaté, y compris un bord AA et un extérieur.
- [ ] **Step 2: Exécuter les sélecteurs isolément.** Expected : RED ciblé si une version, un mapping ou un alpha est encore incorrect ; un test déjà vert est conservé sans assouplir ses pixels.
- [ ] **Step 3: Corriger au propriétaire exact.** Sceller ordre des passes et versions W6 par occurrence ; rebinder domaine, origine et scissor une seule fois en I32 checked. La source transparente est composée une fois avec `SrcOver` avant l'enfant suivant ; le restore externe reste inchangé. Le renderer consomme les coordonnées publiées sans échantillonnage implicite du parent.
- [ ] **Step 4: Vérifier et commit.** Rejouer séparément les trois sélecteurs, puis ceux des Tasks 1–2 et les témoins W6 hard-edge adjacents de `W6aLayerW4W5SurfacePixelTest`. Expected : pixels et scopes verts sans élargissement de tolérance. Commit ciblé ; revue Sol des versions, coordonnées et alpha avant Task 4.

### Task 4: Refus, budget conservateur et bilan W7

**Files:** Modify `W4dGeneralPathPlanCompiler.kt`, `RenderGraph.kt`, `W6aLayerPlanCompiler.kt`, `W6aLayerGraphConstruction.kt`, `W6aLayerGraphValidation.kt`, `W6aLayerPlanBudget.kt`, `NativeSiteRecipeV1.kt`, `PlanPhysicalLayoutV1.kt`, `GPUW6aLayerFramePlan.kt`, `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `W7AaPathLayerSurfacePixelTest.kt`, `refactor/waves/W07-gm-convergence/status.md`.

**Interfaces:** L'admission W6 compte par allocation déclarée la couleur 4×, D24S8 4×, `PathAaResolvedColor` 1×, V/I/U, layer/root/staging et autres ressources W6, en I64 vérifié, sans rabais de lifetime ou cache. Le sélecteur AA reconnu transmet son refus de capacité/ressource avec owner W4d ou W6, jamais `w6a.layer.unsupported_child` générique ; `Surface` peut être réutilisée après refus.

- [ ] **Step 1: Écrire les RED publics de refus.** `aa layer B minus one refuses` fixe B à l'empreinte W6 finale déclarée de la fixture positive, calculée indépendamment du planner comme les tests publics W6a existants, et vérifie B admis, B−1 refus `w6a.layer.frame_budget_exceeded` via `Surface.readPixels(..., sentinel)` sans mutation du sentinel, puis récupération sur la même `Surface` après `discardRecordedOperations()`. `aa filtered path stays outside color source` contrôle que mask et image filter ne deviennent pas des succès trompeurs. Il n'existe pas de commutateur public pour supprimer la capacité 4×/resolve : ne pas créer de test d'infrastructure pour simuler ce device.
- [ ] **Step 2: Exécuter les deux sélecteurs isolément.** Expected : RED sur le mauvais gate ou la récupération ; le témoin de préservation filtrée peut être déjà vert. Si le GPU réel n'expose pas 4×/resolve, vérifier son refus exact sur un témoin `Surface` et arrêter la revendication de témoin positif ; ne pas inventer une capacité ni basculer sur CPU.
- [ ] **Step 3: Geler l'empreinte et les diagnostics.** Sceller les slots et recettes AA avant native, inclure la source 1× `Sampled` et les programmes nouveaux dans l'inventaire annoncé, puis rejeter overflow, format/extent/mapping incohérent, limite de commandes, capacité absente et B−1 avant allocation. Retenir les refus hors tranche avec leur provenance ; ne pas assimiler ces refus à une implémentation de `ResolvedCoverage`.
- [ ] **Step 4: Vérifier le lot public.** Exécuter les compiles ciblées et chaque sélecteur des quatre Tasks séparément, puis un inventaire GM frais vers `/private/tmp` sans génération de PNG, score ou dashboard. Comparer les 44 premiers refus AA et noter les nouveaux diagnostics sans promettre leur disparition ; reporter XML, exit Gradle et exit natif indépendamment.
- [ ] **Step 5: Documenter, commit, relire.** Mettre à jour `W07-gm-convergence/status.md` avec commits, preuves, refus restants, W6 2A1/2B ouverts et étapes image-filter/coverage différées. Commit des seuls fichiers Task 4 ; Sol relit le diff et les preuves, corriger ses remarques importantes, puis une revue finale de la branche avant toute mise à jour de PR.

## Handoff

Après review-clean, présenter les preuves et la migration de diagnostics sans qualifier W7 d'ISO. Une éventuelle PR W7 mise à jour reste empilée sur sa base W6 ; aucun merge, baseline GM ou fermeture de gate W6 n'est impliqué par cette seule tranche. Les sous-projets image filter puis `ResolvedCoverage`/mask filter nécessitent leurs propres specs et plans.
