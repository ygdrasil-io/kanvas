# W7 AA Mask Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre les Paths root AA solid SrcOver avec MaskFilter.Blur NORMAL, couverture indépendante de la peinture.

**Architecture:** Binding de couverture frère du binding couleur ; mécanique W4d MSAA4/resolve1 partagée et autorité blanche canonique. W6b filtre la couverture, W5 shade le domaine complet et multiplie la couverture une fois, puis composite dans le parent.

**Tech Stack:** Kotlin, Scene IR/gpu-plan, WebGPU natif, tests publics JUnit Surface.

**Spec:** [aa-mask-design.md](aa-mask-design.md)

## Global Constraints

- Fonts, codecs, external decoding et `jpg-color-cube` restent hors périmètre.
- Ne modifier ni fixture GM, adaptateur GM, référence, seuil, exclusion, budget cap, enveloppe numérique ou contrôle d'autorité pour faire passer un cas.
- Aucun nouveau test d'infrastructure : pixels publics Surface, Render/Readback, second rendu, refus/sentinel et récupération uniquement.
- Les types géométriques appartiennent à math avec nomenclature I/F32/64 ; aucun nouveau type géométrique n'est prévu.
- Un seul processus Gradle/GPU à la fois, terminal réel avant handoff ; tous les échecs et warnings restent rapportés.
- Mesurer les mêmes 631 identités avec timeout 30 s ; ne pas présenter un diagnostic déplacé comme un gain de rendu.
- Suite globale connue rouge/incomplète : publication draft empilée sur #2418 seulement, sans merge ni clôture W7.

## Review Focus

1. AA remplacé par HARD_EDGE malgré un halo plausible : Task 1, sigma0,1.
2. PaintAlpha appliqué deux fois ou remplacé : Task 1, alpha0/128/255 et fond noir.
3. Halo recoupé par la géométrie ou les bounds root/clip : Task 1 halo, Task 2 frontière.
4. Stencil/resolve partagé entre occurrences ou mauvais ordre : Task 2 anneau et siblings.
5. Ressources MSAA/resolve non facturées ou publication avant refus : Task 2 B/B−1/récupération.

## Cadre d'exécution

Worktree `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`, branche
`codex/w7-aa-mask-coverage`, base `a21bb6472ac7561aa288c79ff19969b7cfca10f4`.
Scratch `/private/tmp/kanvas-w7-aa-mask.9OewPc`. Aucun changement dans cbf6.
Le diagnostic Terra et la revue stratégique Astra sont dans ce scratch.
Les tâches sont séquentielles ; la première livre une verticale directe,
la seconde étend ses phases sans redéfinir le handoff. Pas de sous-agent
créé par les workers, ni push : le contrôleur gère revue et publication.

### Task 1: Verticale de couverture AA directe vers NORMAL

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanW4dAaCoverageSourceBindingV1.kt` — contrat fermé de couverture, native recipe sœur et commun interne de faits AA si nécessaire.
- Reuse unchanged: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanW4dAaSourceBindingV1.kt` — conserver l'autorité couleur ; les primitives W4d communes existent déjà au lowerer.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt` — source deferred blanche distincte, préparation commune.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/SourceDeferredRenderConstructionV4.kt` — topology `AaResolvedCoverage` et remapping exhaustif.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt` — sélectionner le nouveau contrat après validation de l'occurrence originale.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt` — domaine raw, binding coverage, blur et carrier W5 plein domaine.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt` — variante exclusive `aaCoverageBinding` de FilterCoverageSourcePass.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`, `W6aLayerGraphValidation.kt`, `NativeSiteRecipeV1.kt`, `PlanPhysicalLayoutV1.kt` — inventaire, usages, canonical identity, catalog, layout et budget.
- Reuse unchanged: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/FrameSourceLayoutV4.kt` — son remapping générique couvre la nouvelle topology.
- Reuse unchanged: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUW4dAaSourcePreparedAuthority.kt`, `GPUPlanW4dGeneralPreparedAuthority.kt` — autorités couleur intactes ; créer le frère `GPUW4dAaCoveragePreparedAuthority.kt` et réemployer les helpers W4d existants de facts/seal/operands.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt` — blanc canonique sous binding coverage et référence physique au nouveau bundle.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUW6aLayerFramePlan.kt` — lowering du bundle coverage et de ses uses exacts.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `GPUW6aEncoderScopesV1.kt`, `GPUW6aNativePathValidation.kt` — preflight, resolve, operands et validation native de la couverture.
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7AaMaskBlurSurfacePixelTest.kt`.

**Interfaces:**
- Consomme la préparation W4d et `FilterInputSamplingV1`, le W5 solid material original et les recipes W6b NORMAL/MaterializedSource existantes.
- Produit `PlanW4dAaCoverageSourceBindingV1` avec command identity, source capability, phases W4d, remapping/resources, extent/origin snapshots, output resolve et native recipe. Même famille de signatures que le binding couleur, mais type et validation distincts.
- Produit `W4dGeneralPathPlanCompiler.w6AaCoverageSource(catalog: RuntimeEffectSemanticCatalogSnapshot): W4dGeneralPathPlanCompiler`, capability `w6-aa-resolved-coverage-source-v1`, topology `AaResolvedCoverage`.
- Produit `PlanPass.FilterCoverageSourcePass.aaCoverageBinding: PlanW4dAaCoverageSourceBindingV1?`, exclusif de rasterBinding et sealedAlphaSource. Aucun changement de sémantique des variantes existantes.
- Task 2 consommera ce même binding avec deux phases stencil, sans changer son équation couleur/couverture ni son contrat d'origine.

- [x] **Step 1: Écrire les positifs publics et contrôles avant production.**

Dans le nouveau test, créer des triangles root via Path : base `(16,16)`,
`(80,16)`, `(16,80)` sur Surface96×96. Peinture noire opaque sur transparent
sauf indication contraire. Fixer tous les points et assertions avant Surface.

1. `tiny normal blur preserves fractional aa coverage` : phases0,125/0,375/0,625,
   translation identique x/y, sigma0,1. Intérieur(24,24) alpha255,
   extérieur(90,90) alpha0 ; dans la bande diagonale prédéfinie
   x,y∈[20,76], x+y∈[93,99], au moins un alpha∈1..254 sur l'ensemble
   des trois fixtures (sans imposer une répartition particulière des samples).
   RGB noirs. Le même témoin avec antiAlias=false n'a aucun alpha intermédiaire.
2. `normal aa blur preserves halo and integer translation` : sigma1,5,
   intérieur(24,24) opaque, extérieur immédiat(15,32) alpha∈1..254,
   lointain(9,32) alpha0. Le fixture translaté de(3,2) a les mêmes pixels
   aux coordonnées translatées dans le domaine commun, loin des bords Surface.
3. `normal aa blur applies paint alpha once` : sigma1,5, sur transparent, peinture noire
   alpha0/128/255, pixel(24,24) alpha respectif0/128/255 (tolérance1), RGB0.
   Blanc alpha128 sur fond noir opaque : (188,188,188,255), tolérance1 sur RGB.
   Alpha0 sur fond opaque coloré conserve tous les pixels du fond, halo compris.
4. `direct aa mask scope remains closed` : triangle avec layer, imageFilter,
   blend PLUS, style OUTER, stroke et stencil concave. Refus exact observé sur
   base, sentinel0x5a inchangé ; discard et rectangle hard récupèrent. Le
   stencil est provisoirement négatif et deviendra positif en Task 2.

Chaque positif exige Render/Readback et un second render identique. `@AfterAll`
dispose le GPU, suivant les tests W7. Les tests ne lisent ni planner ni recipe.

- [x] **Step 2: RED réel, archivé sans écrasement.**

Run: `rtk proxy ./gradlew :kanvas:test --offline --no-build-cache --tests org.graphiks.kanvas.surface.W7AaMaskBlurSurfacePixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-aa-mask.9OewPc/task1-red --console=plain`.
Les positifs doivent échouer par la non-admission, pas par un défaut d'oracle
ou de compilation. Les contrôles déjà verts restent identifiés comme tels.

- [x] **Step 3: Implémenter la verticale et ses contrats fermés.**

Valider le draw original (root Path fill solide AA SrcOver, seul NORMAL)
avant d'en construire une source géométrique blanche. Conserver séparément
l'autorité du matériau original. La factory coverage réemploie la préparation
W4d, émet une topology distincte sans root/readback autonome, puis W6 rebinde
les ressources au domaine raw exigé par W6b. Origine/extents explicites dès
cette étape ; pas de zéro implicite, clipping d'entrée au viewport, ou local-
translation appliquée deux fois. Si un domaine n'est pas encore constructible,
faire un refus explicite documenté, jamais produire des pixels tronqués.

Le FilterCoverageSourcePass possède une seule passe native MSAA4. Son output
CoverageSource1× est directement le resolve à la fin de cette passe, pas une
copie ni un deuxième producteur. Task 1 accepte seulement le groupe direct
issu de MultisampleDirectColor et refuse le stencil ; la liste des phases
W4d préparées porte géométrie/pipelines, pas une promesse de rejouer leurs
load/store couleur inchangés. Task 2 aura deux groupes de commandes dans cette
même passe native, pas deux passes ni RenderPassSegment.
Ne pas affaiblir l'adjacence/les invariants du binding couleur existant.

Après blur X/Y/NORMAL, réutiliser un carrier rectangulaire W5 plein domaine et
la recette MaterializedSource en mode multiplication. Aucune seconde
rasterisation du Path ; ni alpha remplacé par la couverture, ni produit double.
Tous les buffers/textures/uniforms et leurs usages/lifetimes/byte counts doivent
être déclarés et facturés avant native allocation. Recipe sœur = blanc,
samples4→1, usages, clear/store, scissor, géométrie et resolve exacts ; le
materializer ne relit pas la peinture ou SceneSnapshot. Factoriser les helpers
W4d internes nécessaires, sans deuxième renderer ni purpose public permissif.

- [x] **Step 4: GREEN ciblé puis contrôles proches.**

Même commande avec archive unique `task1-green`. Ensuite nouvelle archive
`task1-related` avec W7AaMaskBlurSurfacePixelTest, W6bMaskBlurAutoLayerSurfacePixelTest,
W7RootAaSurfacePixelTest et W7AaPathLayerSurfacePixelTest. Corriger la production
si un oracle échoue ; toute correction d'oracle exige cause indépendante
documentée, jamais alignement sur les pixels obtenus.

- [x] **Step 5: Self-review, commit et handoff au contrôleur.**

`rtk git diff --check`. Commit fichiers propres à la tâche ; aucun push.
Rapport : commandes, exits réels, XML, chaque échec/warning, détails du contrat
et liste des ressources/budgets, limites résiduelles, aucun worker runtime actif.
Task 1 est un jalon partiel : la tentative globale unique appartient à Task 2,
sur le contrat complet ; ne pas revendiquer une suite globale verte ici.

### Task 2: Stencil, frontières et preuve du coût complet

**Files:**
- Modify: les fichiers Task 1 portant le binding coverage, la préparation W4d,
  la construction W6, les recipes/layout et l'autorité/lowering/preflight natifs.
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7AaMaskBlurSurfacePixelTest.kt`.
- Create if needed: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7AaMaskBlurBoundarySurfacePixelTest.kt` pour garder les fixtures publiques lisibles.

**Interfaces:** Consomme et conserve `AaResolvedCoverage`,
`PlanW4dAaCoverageSourceBindingV1` et `FilterCoverageSourcePass.aaCoverageBinding`.
Étend les géométries/pipelines admis à MultisampleStencilProducer puis
MultisampleStencilColorCover : deux groupes de commandes dans UNE passe native
coverage avec depth-stencil4 distinct, clear0 initial et resolve terminal.

- [x] **Step 1: Nouveaux témoins RED publics avant extension stencil.**

1. `normal aa ring preserves hole and both halos` : Surface128², outer CW
   [16,16,112,112], inner CCW[40,40,88,88], noir opaque, sigma2,3.
   Alpha255(28,64), alpha0(64,64)/(0,64), alpha∈1..254(40,64)/(15,64).
   Second fixture phase(+0,25,0) : mêmes classes plein/vide/halo à ces points,
   pas d'égalité byte-à-byte sous translation fractionnaire. Ajouter version
   sigma0,1 et phase0,375 avec bande d'arête et alpha intermédiaire.
2. `aa mask retains offscreen input until terminal clip` : Surface32²,
   Path rectangle device `[-8, 4, -0.25, 28]`, sigma1,5, clip hard[0,8,8,24].
   Alpha(0,16)>0 malgré géométrie extérieure au viewport, alpha(7,16)=0,
   tous les pixels hors clip exactement transparents. Un fixture avec
   translate(-8,4) et rectangle local `[0, 0, 7.75, 24]` donne les mêmes pixels.
3. `aa mask occurrences preserve sibling chronology` : Surface256×128,
   deux anneaux du témoin1, second translaté de(128,0), couleurs rouge/bleue,
   entre eux un rectangle hard vert [0,60,256,68]. Rouge du premier recouvert
   par vert(28,64), bleu du second sur vert(156,64), vert visible dans les
   trous(64,64)/(192,64), pas de fuite aux bords ; second rendu identique.
4. `aa mask exact budget refuses before publication and recovers` : triangle
   Task1 sur96², noir alpha128, sigma1,5, puis anneau128² du témoin1, sigma2,3.
   Dériver un B propre à chacun explicitement dans le
   test des allocations contractuelles (dimensions raw/halo, AA4/resolve1,
   V/I/U floors, depth-stencil4 pour anneau, blur/style, W5/materialized, root,
   readback et uniforms),
   sans lire le peak du planner. B passe avec pixels/Render/Readback ; B−1
   refuse `w6b.filter.frame_budget_exceeded`, sentinel intact, discard puis
   rectangle hard noir réussi sur la même Surface. Envoyer la dérivation
   au contrôleur avant le premier run du test, aucun B découvert par sondage.

Conserver les négatifs hors scope de Task1 ; retirer seulement son négatif
stencil devenu couvert. Tous les positifs exigent second render et evidence.

- [x] **Step 2: Étendre seulement le même producer coverage.**

Créer/valider depth-stencil4, clear transparent/stencil0 à l'ouverture de la
passe native, commandes producer puis cover test/reset, target AA4 commune,
resolve1 seulement en fin de passe, géométrie/contours de la même occurrence.
Déclarer chaque use/byte/lifetime. La recipe sœur scelle cette topologie
UNE passe/deux groupes et le layout operands resolve+depth4. Réemployer les
pipelines/géométries W4d, pas les deux passes couleur à load/store séparés,
ni RenderPassSegment ; jamais deux writers du
resolve, ni stencil repris d'une autre occurrence. Préserver les inputs
hors viewport/clip par leurs mappings W6b, sans changer blur ou shading.

- [x] **Step 3: GREEN et témoins proches séquentiels.**

Commandes Task1 étendues au nouveau fichier éventuel ; archives uniques
`task2-red`, `task2-green`, `task2-related`. Ajouter les tests publics W6aLayer,
W6aNestedLayer, W6aLayerBudgetRecovery aux contrôles précédents.

- [x] **Step 4: Une seule tentative globale bornée, puis validation finale.**

`:kanvas:test --offline --no-build-cache` sans filtre, même init timeout240s,
archive `full-suite-240`. Comparer XML/events à
`/private/tmp/kanvas-w7-root-aa.8WK1ZR/full-suite-240` : assertions nouvelles,
identités communes, non atteintes/interrompues, warnings et exit réel.
Pas de répétition globale, ni relance W5f large. Puis shard AA-mask + proches
dans `final`, attendre chaque exit avant handoff.

- [x] **Step 5: Self-review, commit, rapport et runtime rendu au contrôleur.**

Rapport et commit selon Task1, avec dérivation B et toutes les limites. Le
contrôleur organise Sol spec+quality et les corrections/re-reviews bornées.

### Clôture contrôleur

- [x] Corpus631 sur commit exact, slices0–607/607–608/608–631, timeout30 ; tous diagnostics conservés.
- [x] Comparer à `root-aa-470f62e63.json` : gains/pertes individuels et empreintes RGBA, aucun seuil modifié.
- [x] Mettre à jour le suivi existant avec résultat réel et limites ; revue globale Sol.
- [x] Publier draft empilée sur #2418 et l'attacher ; aucune revendication de parité globale ou merge readiness.

## Self-review du plan

Les tâches partagent le binding et le backend : ordre strict, contrat d'origine
et budget dès Task1 ; Task2 ajoute uniquement phases et preuves de frontière.
Les cinq risques ont des témoins causaux. L'avis Astra corrige trois pièges :
alpha128 blanc sur noir=188, un halo ne prouve pas l'AA, phase0,25 n'implique
pas une égalité de pixels. Les décisions sont suivies dans le ledger du lot.
