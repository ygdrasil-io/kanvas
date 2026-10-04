# Common AA Path Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Consommer une source couleur AA PATH non filtrée commune au root W6b et dans un layer plain, avec preuve native et mesure des effets réels.

**Architecture:** Réemployer la préparation math et le resolve AA4 existants. Une gate root/layer/contrôle W6 avant produit discrimine la restriction de scope ; le contrat ordinary existant est factorisé/renommé, sans nouvelle lane. Le consommateur layer conserve localisation, restore et coûts.

**Tech Stack:** Kotlin, render IR, math, gpu-plan/gpu-renderer, Surface WebGPU native, Gradle/JUnit, Skia GM checkpoint.

**Spec:** refactor/waves/W07-gm-convergence/common-aa-path-source-design.md

**État courant:** Task1 complète, review Sol spec/quality Approved et
nettoyage M1/M2 re-reviewé C0/I0/M0. Produit W6a6ed673/W4d1fcb qualifié
focal9/9 et contexte119/120 (un FAIL historique), fixtures inchangées.
La globale antérieure f152/1fcb est interrompue690PASS/36FAIL/1skip,
non répétée après nettoyage ; aucun globalGREEN. Le census frais Task2
est terminé : 631 champs hors elapsedMs/renderMs identiques aux deux
snapshots précédents, zéro gain GM ou delta d’admission/pixels. Revue
documentaire Step2 et publication Step3 restent pending. Aucun globalGREEN.
Branche locale codex/w7-common-aa-layer-source, base5df3c9e8 figée pour
SolidRect. Publication en attente d'accord explicite.

## Global Constraints

- Fonts/codecs externes/jpg-color-cube exclus, aucune nouvelle exclusion.
- Pas de CPU renderer/fallback, mock/fake/injected callback, skip GPU, tests d'infrastructure/source-text/forwarding.
- Références, scores/seuils/tolérances, registre631/scope443, sample count, validators, caps et budgets inchangés.
- Géométrie dans math, nomenclature I/F32/64 ; aucune reconstruction renderer.
- Contrôleur seul pour runtime/Git, un runtime à la fois, borne240s inchangée ; terminal→audit intégral log/exit/events/XML/inventaires/ownPGID→postseal séparé avant toute écriture ou run suivant.
- Workspaces/raw receipts et inverse untracked SHA96cd8349 préservés ; aucun cleanup/merge, pas d'écriture code/docs/Git pendant native.
- Reviews indépendantes, suites globales RED/incomplètes explicites.
- Aucun filtre propre effacé, root corrélé et lanes FILL/deferred existantes conservés ; refus public encoded layer inchangé.

## Review Focus

- Faux RED de fixture/device : root et contrôle W6 réellement natifs/fullbuffer avant extension.
- Source colorée et filter coverage confondus : négatifs propres mask/image filters, provenance originale conservée.
- Double consommation/alpha/sample authority : demi-couverture, alpha128, doublon, restorealpha, ordre observables.
- Coordonnées appliquées deux fois ou layer bounds pris comme clip : translation et hard clip avec oracle device entier.
- Admission encodée/budget/recovery contournés : encoded négatif + root positif distinct, ancien B/B−1, sentinel/recovery même Surface.

---

### Task 1: Gate causale et source ordinary commune

**Files:**
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7CommonAaPathSourceSurfacePixelTest.kt`.
- Create Step3: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7CommonAaPathSourceDiscriminantsSurfacePixelTest.kt` ; gate précédente byteexact SHA b6206a00d47b37a8ebd85486741853798089bdbf18efdb8eea70e68bd85494c7.
- Modify après gate seulement : `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt`, `W4dGeneralPathPlanCompiler.kt`.
- Modify seulement si transport exact nécessaire : `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphValidation.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W6aLayerGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4dGeneralPreparedAuthority.kt`.
- Read only : anciens `W7W6OrdinaryAaPathSourceSurfacePixelTest`, `W7AaPathLayerSurfacePixelTest`, `W6aLayerBoundsSurfacePixelTest`, `W6aLayerRestoreSurfacePixelTest`, CompositionAdmissionV1.

**Interfaces:**
- Consumes public Surface.canvas/render/readPixels/discardRecordedOperations, Canvas.saveLayer/restore, Path, Paint ; original DrawNode et stripW6bPayload, math `preparePathStrokeGeometryF32`, source4x→resolve1x→AaResolvedColor.
- Produces named internal factory `W4dGeneralPathPlanCompiler.w6OrdinaryAaPathColorSource(runtimeCatalog)` and predicate `acceptsW6OrdinaryAaPathColorSourceScope(node: DrawNode): Boolean`, renaming the existing ordinary factory/predicate/flag rather than adding one. Keep factory parameters/capture transport otherwise unchanged.
- Main consumes READY_RED/READY_VALIDATION reports and exact touched-file hashes. Worker performs no build/native/test/Git/delegation; main is sole owner. Report path follows task brief in this plan's SDD workspace.

- [x] **Step 1: RED fixture only, no product edits.** Create three tests `rootStrokeHasIndependentFullBufferAndReplay`, `rootStrokeWithFilteredSiblingHasIndependentFullBufferAndReplay`, `plainLayerStrokeHasIndependentFullBufferAndReplay`. Use the spec's exact32×32 scenes, widths2/1, coordinates/color/clip and filtered sibling. Each loops two renders on one Surface and two freshSurfaces, without dispose/purge between. Pin RGBA8/4096bytes and fullbuffer oracle before native; use exact full/empty bytes, only half values ±1LSB. Add stdout `W7_COMMON_AA_PATH` with label/width/native completion/diagnostic. Assertions use public behavior, not planner names or private graph structure. @AfterAll GPUBackendRuntimeFactory.dispose only after class. Report READY_RED with full file hash and tests/expectations; no source changes until main supplies causal gate result.

- [x] **Step 2: Main authentic RED and audit.** Run bounded240s `:kanvas:test --tests '*W7CommonAaPathSourceSurfacePixelTest*' --rerun --no-daemon --console=plain` through frozen bounded-run.rb/init and private `-Pw7.validationDir`. Root+control must PASS fullbuffer/native/replay; layer must fail at w6a.layer.unsupported_child, not compile/device/budget. Every test/event accounted, terminal fullaudit then separate69/artifact/ref+newfixture postseal. If gate fails, report precise falsifier and stop product extension; never tune expectations to observed GPU or fabricate RED.

- [x] **Step 3: Complete discriminants before source.** Resume original writer to add a separate companion class with tests `layerStrokePreservesSourceAlphaAndDistinctOccurrences`, `layerRestorePreservesAlphaAndOrder`, `layerSourcePreservesDeviceCoordinatesAndHardClip`, `foreignAxesRefuseWithoutPublishingAndRecover`, `encodedLayerRefusesWhileEncodedRootRemainsNative`, `layerStrokeBudgetBoundaryRefusesAndRecovers`. Keep the original RED gate byteexact, with no extraction/change to its helpers. Same coreline; opaque width1 isolated duplicate expects [225,0,0,191/192] on x8..23/y15..16 (±1); alpha128 width2 expects [188,0,0,128], alpha128 width1 expects [137,0,0,64] (±1), independent empty bytes exact. Layer restorealpha128 multiplies source premul/alpha once. Opaque blue Rect[12,14,20,18] sibling before/after layer makes order observable; fullbuffer SrcOver expected derived before run. Translation(3,2) installed before saveLayer shifts line to DEVICE(11,18)→(27,18); hint LOCAL[12,15,20,17] maps to DEVICE[15,17,23,19], with visible x11..14 and x23..26 outside hint. Without clip, fullbuffer keeps x11..26/y17..18. Independent hardclip DEVICE[12,0,20,32] installed before translation truncates x12..19. Negative own mask/imagefilter expects existing unsupported_spatial_filter; shader/PLUS/AAclip/fractionalclip exact current child refusal, no blanket rejection of an admitted lane. Sentinel0x5a4096bytes unchanged, discard then fullblue recovery+native/replay sameSurface. Encoded layer expects unsupported.surface.composition.layer; encoded root width1 expects[128,0,0,128]±1 with transparent exterior. New budget fixture2×2, line(0,1)→(2,1), width1/plainlayer/nohint/nosibling : B25312 derived in spec before GPU, all4pixels[188,0,0,128]±1/completion/replay atB; B−1 exact budget.w5g.composed-uniform, sentinel16bytes0x5a intact, discard then fullblue native/replay sameSurface. Account StencilCover AA4D24S8, W6 uniform plus Solid16/tail-alpha16, no cache/lifetime discount; source provisional25104≤B. This derivation requires scoped independent review before execution; old FILL B26980 remains separate. Main runs/audits RED expanded fixture before product, preserving positive/negative statuses separately.

- [x] **Step 4: Factor/reuse ordinary source after causal gate.** Rename existing ordinary factory/predicate/flag consistently; predicate retains PATH/AA/FILL-or-STROKE, solidSrcOver/noeffects plus current transform/clip/geometry guards. W6 selection requires originalDraw==unfilteredDraw and admitted scope; keep existing root ownership condition for root, add compatible child scope without extra flag/family. Root in frame plain-layer-owned without ownsW6b is explicitly DEFERRED, not covered by standalone correlated root control. Preserve priority of historical FILL/deferred positives where possible; original DrawNode identity/order/clip/material immutable. Reuse AA4 geometry/resolve consumer and resource/lifetime/seal authorities. No renderer geometry construction or guard clearing. If new consumer needed beyond the established AA4 color contract, report NEEDS_CONTEXT, not speculative broad rewrite. Report READY_VALIDATION with exact sources/test hashes, self-review and behavior facts.

- [x] **Step 5: Main GREEN and review.** Both focused classes unchanged must all PASS (`--tests '*W7CommonAaPathSource*SurfacePixelTest*'`) with fullaudit/postseal. Context run old ordinary root test, AA FILLlayer including B/B−1, bounds/restore classes, once each or one controlled single-fork task; retain legacy exact capability-refusal branches as such, not proof those positives ran. New class cannot skip or accept capability refusal. If scope exported proof missing, report actual adapter issue rather than fabricate markers. Main commits only qualified files, generates range review package from task BASE, Sol spec+quality gate; original writer fixes C/I with scoped re-review. No globalGREEN claim.

### Task 2: Corpus delta, qualified delivery and stacked draft

**Files:**
- Create controller report `refactor/waves/W07-gm-convergence/common-aa-path-source-qualification.md` and exact fresh snapshot `common-aa-path-source-corpus.json` only if product retained.
- Modify controller `refactor/waves/W07-gm-convergence/pilotage.md`.
- Read only Task1 sources/tests, frozen631registry and refs, census2485snapshot, existing `summarize-parity.mjs`.

**Interfaces:** Consumes Task1 reviewed source contract and native proof; produces honest admission/pixel deltas and publication metadata. Existing checkpoint accepts only contiguous slices (`gm.parityFrom`/`gm.parityTo`), so reuse five fixed census slices rather than inventing a selector.

- [x] **Step 1: Main frozen fresh corpus.** Snapshot `common-aa-path-source-corpus.json` créé : 740716 bytes, SHA-256 `e8528b81c9167ac693f83de4348262434573ff71351e0ad2921f7a9a8af732ad`. Cinq slices [0,200), [200,400), [400,607), [607,608), [608,631), renderer `bf38d08be20edaa487ae2fddab43dda03fd4f268`, registry631/SHA `4ca8eea61451b1143fd3d15634d2c34e0c9ec31b74fd351ca30a69ee36f565d7`, perGM30s/outer240s, images=false. Tous les champs/présences des631 lignes hors elapsedMs/renderMs sont identiques à SolidRect et transversal2485cfb : zéro gain GM/delta d’admission/pixels. Scope443 ; 393 entrés (392 attempted=true + vertices607 timeout sans flag), 220 rendus, 197 comparés ; 172 render_failed, 50 setup_failed, 15 rendered_uncompared, 8 dimension_mismatch, 1 timeout ; 65≥95%/49≥99% à ±2, médiane77.91666666666667%. Timeout vertices607 conservé, exit wrapper1. Les17 refus initiaux PATH/AA restent14root/3child,12filteredroot/5unfiltered ; aucun gain candidat déduit. Détails, hashes d’archives et limites dans `common-aa-path-source-qualification.md`.

- [ ] **Step 2: Evidence review and delivery.** Qualification et pilotage documentent hashes des archives, preuves natives, delta corpus nul et limites. Revue documentaire/quality gate Sol puis revue indépendante de branche complète sur base locale `5df3c9e8c22f5b5e4de9e23739113bb4a678b026` pending ; la revue SolidRect existante et ses limites restent explicites. Revue Astra uniquement si disponible et utile pour un diagnostic non résolu. Une éventuelle vague de correction/revue reste à décider après findings. Archives/workspaces conservés.

- [ ] **Step 3: Draft stack.** Pending explicit publication authorization. Deux branches locales en attente (`codex/w7-common-aa-path-source` à SolidRect et `codex/w7-common-aa-layer-source` empilée dessus). Parent draft #2447 : https://github.com/ygdrasil-io/kanvas/pull/2447. Push public rejeté avant création ; aucun retry ni PR nouvelle. Aucun head/base/body distant vérifié, publication non revendiquée. Pas de merge/globalGREEN/W7complete.
