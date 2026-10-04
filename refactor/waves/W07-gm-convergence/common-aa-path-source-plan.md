# Common AA Path Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Consommer une source couleur AA PATH non filtrée commune au root W6b et dans un layer plain, avec preuve native et mesure des effets réels.

**Architecture:** Réemployer la préparation math et le resolve AA4 existants. Une gate root/layer/contrôle W6 avant produit discrimine la restriction de scope ; le contrat ordinary existant est factorisé/renommé, sans nouvelle lane. Le consommateur layer conserve localisation, restore et coûts.

**Tech Stack:** Kotlin, render IR, math, gpu-plan/gpu-renderer, Surface WebGPU native, Gradle/JUnit, Skia GM checkpoint.

**Spec:** refactor/waves/W07-gm-convergence/common-aa-path-source-design.md

**État courant:** Steps1/2 faites ; gate fraîche après SolidRect : root et
contrôle filtré PASS, layer RED attendu, audit/postseal complets. I1/I2 et
M1/M2 re-reviewés C0/I0/M0. Step3 autorisée ; Step4 reste conditionnée au
RED des discriminants. Branche locale codex/w7-common-aa-layer-source,
base5df3c9e8 figée pour le lot SolidRect. Publication en attente d'accord
explicite ; aucune task complète ni nouveau code source common AA.

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

- [ ] **Step 3: Complete discriminants before source.** Resume original writer to add a separate companion class with tests `layerStrokePreservesSourceAlphaAndDistinctOccurrences`, `layerRestorePreservesAlphaAndOrder`, `layerSourcePreservesDeviceCoordinatesAndHardClip`, `foreignAxesRefuseWithoutPublishingAndRecover`, `encodedLayerRefusesWhileEncodedRootRemainsNative`, `layerStrokeBudgetBoundaryRefusesAndRecovers`. Keep the original RED gate byteexact, with no extraction/change to its helpers. Same coreline; opaque width1 isolated duplicate expects [225,0,0,191/192] on x8..23/y15..16 (±1); alpha128 width2 expects [188,0,0,128], alpha128 width1 expects [137,0,0,64] (±1), independent empty bytes exact. Layer restorealpha128 multiplies source premul/alpha once. Opaque blue Rect[12,14,20,18] sibling before/after layer makes order observable; fullbuffer SrcOver expected derived before run. Translation(3,2) installed before saveLayer shifts line to DEVICE(11,18)→(27,18); hint LOCAL[12,15,20,17] maps to DEVICE[15,17,23,19], with visible x11..14 and x23..26 outside hint. Without clip, fullbuffer keeps x11..26/y17..18. Independent hardclip DEVICE[12,0,20,32] installed before translation truncates x12..19. Negative own mask/imagefilter expects existing unsupported_spatial_filter; shader/PLUS/AAclip/fractionalclip exact current child refusal, no blanket rejection of an admitted lane. Sentinel0x5a4096bytes unchanged, discard then fullblue recovery+native/replay sameSurface. Encoded layer expects unsupported.surface.composition.layer; encoded root width1 expects[128,0,0,128]±1 with transparent exterior. New budget fixture2×2, line(0,1)→(2,1), width1/plainlayer/nohint/nosibling : B25296 derived in spec before GPU, all4pixels[188,0,0,128]±1/completion/replay atB; B−1 exact w6a.layer.frame_budget_exceeded, sentinel16bytes0x5a intact, discard then fullblue native/replay sameSurface. Account StencilCover AA4D24S8, both uniforms, no cache/lifetime discount; source provisional25104≤B. This derivation requires scoped independent review before execution; old FILL B26980 remains separate. Main runs/audits RED expanded fixture before product, preserving positive/negative statuses separately.

- [ ] **Step 4: Factor/reuse ordinary source after causal gate.** Rename existing ordinary factory/predicate/flag consistently; predicate retains PATH/AA/FILL-or-STROKE, solidSrcOver/noeffects plus current transform/clip/geometry guards. W6 selection requires originalDraw==unfilteredDraw and admitted scope; keep existing root ownership condition for root, add compatible child scope without extra flag/family. Root in frame plain-layer-owned without ownsW6b is explicitly DEFERRED, not covered by standalone correlated root control. Preserve priority of historical FILL/deferred positives where possible; original DrawNode identity/order/clip/material immutable. Reuse AA4 geometry/resolve consumer and resource/lifetime/seal authorities. No renderer geometry construction or guard clearing. If new consumer needed beyond the established AA4 color contract, report NEEDS_CONTEXT, not speculative broad rewrite. Report READY_VALIDATION with exact sources/test hashes, self-review and behavior facts.

- [ ] **Step 5: Main GREEN and review.** Both focused classes unchanged must all PASS (`--tests '*W7CommonAaPathSource*SurfacePixelTest*'`) with fullaudit/postseal. Context run old ordinary root test, AA FILLlayer including B/B−1, bounds/restore classes, once each or one controlled single-fork task; retain legacy exact capability-refusal branches as such, not proof those positives ran. New class cannot skip or accept capability refusal. If scope exported proof missing, report actual adapter issue rather than fabricate markers. Main commits only qualified files, generates range review package from task BASE, Sol spec+quality gate; original writer fixes C/I with scoped re-review. No globalGREEN claim.

### Task 2: Corpus delta, qualified delivery and stacked draft

**Files:**
- Create controller report `refactor/waves/W07-gm-convergence/common-aa-path-source-qualification.md` and exact fresh snapshot `common-aa-path-source-corpus.json` only if product retained.
- Modify controller `refactor/waves/W07-gm-convergence/pilotage.md`.
- Read only Task1 sources/tests, frozen631registry and refs, census2485snapshot, existing `summarize-parity.mjs`.

**Interfaces:** Consumes Task1 reviewed source contract and native proof; produces honest admission/pixel deltas and publication metadata. Existing checkpoint accepts only contiguous slices (`gm.parityFrom`/`gm.parityTo`), so reuse five fixed census slices rather than inventing a selector.

- [ ] **Step 1: Main frozen fresh corpus.** Seal product HEAD/tests/metadata/protectedinverse and all existingPNG/scores/refs. Run existing measureSkiaParity images=false on [0,200),[200,400),[400,607),[607,608),[608,631), perGM30s/outer240s/--no-daemon/frozeninit, one at a time. Terminal→FULLaudit→separatepostseal each, timeout607 kept, no writes while native. Aggregate with existing unmodified script to fresh path. Compare all631 rowfields excluding times, surveyed/flagsattempted/renderstagetimeout/entered/rendered/compared, outcomes/metrics/hashes, sans imposer les anciens compteurs au nouveau produit. Highlight previous17 first w6a.layer.unsupported_child PATH/AA diagnostics (14root/3child,12filteredroot/5unfiltered), not17 candidate child gains, without dropping other cases. No generator/score/ref writes. Record zero gains if measured; no inferred completion from removed diagnostic. Foundation value needs main's explicit postgate ruling, not presumed corpus ROI.

- [ ] **Step 2: Evidence review and delivery.** Main writes qualification/pilotage with raw archive hashes, native/control/negative statuses, matched scopes/pixel deltas/regressions and limits. Sol documentary/task-quality gate plus final whole-branch review over local branchBASE5df3c9e8 (SolidRect déjà relu ; limites héritées explicites), independent from implementer, with all ledger findings/rulings. Astra only if available/meaningful unresolved diagnosis. One final fix wave/re-review, retain archives/workspaces.

- [ ] **Step 3: Draft stack.** Only retained qualified product/evidence, push owned branch normally and create draft basecodex/w7-common-aa-path-source after its publication is explicitly authorized and verified. Attach PR, verify remote head/base/body byteexact and parentHEAD. Update actual publication link, no merge/globalGREEN/W7complete. If Task1 falsified, publish diagnostic disposition only if useful, no misleading product claim.
