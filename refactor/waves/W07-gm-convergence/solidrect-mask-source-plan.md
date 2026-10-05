# SolidRect Mask Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Préserver la source RectI32 authentifiée avant clip/root pour le mask blur W6, sans changer les pixels ordinaires ni les budgets.

**Architecture:** Snapshot W3 complet distinct du visible/scissor ; access contextuel W6 direct MaskBlur et localisation au reverse-demand existant. Aucun nouveau compiler/lane ni géométrie renderer.

**Tech Stack:** Kotlin, math I/F32/64, gpu-plan, Surface WebGPU native, Gradle/JUnit, Skia GM checkpoint.

**Spec:** refactor/waves/W07-gm-convergence/solidrect-mask-source-design.md

## Global Constraints

- Fonts/codecs externes/jpg-color-cube exclus, aucune nouvelle exclusion.
- Pas de CPU renderer/fallback, mock/fake/injected callback, skip GPU, tests d'infrastructure/source-text/forwarding.
- Références, scores/seuils/tolérances, registre631/scope443, sample count, validators, caps et budgets inchangés.
- Géométrie dans math, nomenclature I/F32/64 ; aucune reconstruction renderer.
- Contrôleur seul pour runtime/Git, un runtime à la fois, borne240s inchangée ; terminal→audit intégral log/exit/events/XML/inventaires/ownPGID→postseal séparé avant toute écriture ou run suivant.
- Workspaces/raw receipts et inverse untracked SHA96cd8349 préservés ; aucun cleanup/merge, pas d'écriture code/docs/Git pendant native.
- Reviews indépendantes, suites globales RED/incomplètes explicites.
- Ancienne gate SHA b6206a00 inchangée ; common AA PATH Step3/4 toujours conditionnelles, pas de rootguard/fillpriority/domainextension dans ce sous-lot.

## Review Focus

- Default/clone perd l'autorité source : same-Surface replay+deux fresh surfaces, W3→W6→material/localization/canonical recipe audit.
- Source fullbounds pris globalement comme visible : test nofiltergrandRect aux clips bord et intérieur, contexte bounds/restore.
- Root target coupe le halo négatif : ancienne gate bord inchangée, nouveau clip intérieur indépendant.
- Padding opaque/clamp masque le vrai bord : vrai geometryedgeblur composite sur fond rouge, oracle math sept taps fixé avant GPU.
- Coûts source élargis sous-chargés : charges physiques dérivées avant GREEN, budget refus/sentinel/recovery public et contexte existant, aucun budget/cap/validator relâché.

---

### Task 1: Autorité raw SolidRect et preuve native

**Files:**
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7SolidRectMaskBlurSourceSurfacePixelTest.kt`.
- Modify après RED seulement: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`, `W3SolidRectPlanCompiler.kt`, `W6aLayerGraphConstruction.kt`.
- Modify après audit des rebinds: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphConstruction.kt`, `W5bGeometryLanePlanV3.kt`, `W5bDestinationGraph.kt`. Ces sites reconstruisent réellement SolidRect lors des remaps matériel/coordonnées/blend ; conserver le nouveau fait source dans le même repère. Aucune extension de leurs admissions.
- Modify si clone/canonical fact l'exige seulement: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphValidation.kt`, `NativeSiteRecipeV1.kt`, et fichier exact du clone découvert (signaler avant élargissement).
- Read only: ancien W7CommonAaPathSourceSurfacePixelTest, CompositionAdmissionV1, W6bFilterGraphConstruction/Demands, W6a native materializer/canonical encodings, tests W6a bounds/restore/mask/composition.

**Interfaces:** Consumes W3 `resolveTransformed` result, public Surface.canvas/render/readPixels/discardRecordedOperations; produces `SolidRectDraw.copySourceRasterBoundsI32(): RectI32` immutable. `of`/`ofMaterial` final parameter `sourceRasterBoundsI32: RectI32 = visibleBounds`, compatible default. New contextual helper in W6a `directMaskSourceRasterBoundsI32(draw: PlanDraw): RectI32` uses SolidRect fact or existing raster accessor; only direct mask-blur consumers call it. Reporting READY_RED/READY_VALIDATION with exact hashes to SDD task brief path. Workers no build/test/native/Git/delegation; main sole owner.

**Budget proof pinned after independent Astra diagnosis:** add public `enlargedSourceBudgetAcceptsExactBoundaryAndRecoversAfterRefusal` to the same new fixture. Minimal scene/formula/diagnostic in spec: no AA sibling/background, literal checked sum B17456/B−1=17455, six texture total5136, root4096/staging8192/W6row16/BLUErow16/W6dleases0. Positive exact B four renders full oracle; refused readPixels preserves4096byte0x5a sentinel, discard then fullblue ordinary rectangle sameSurface/samebudget and native replay. Current source fails opaque oracle before correction; current14,144 graph would also admit at B−1, not a qualified negative. Never alter a boundary in response to GPU results; report topology mismatch NEEDS_CONTEXT. Existing W3 recovery pool accounting gap tracked separately.

- [x] **Step 1: Fixture RED seulement.** Add `interiorClipPreservesOpaqueBlurSourceAndReplay`, `ordinaryClippedRectKeepsIndependentFullBufferAndReplay`, `trueGeometryEdgeBlurRetainsFalloffOverOpaqueBackground`. Exact32×32 scenes/formulas in spec; interior has same red AAwidth2 trait; nofilter loops clipbord/intérieur; geometryedge clipintérieur + opaqueRED drawColor. FullRGBA4096/native/no-refusals/completion/draw/pipelines, deuxrendersmêmeSurface+deuxfresh, AfterAll disposeonce only. Print W7_SOLID_RECT_MASK with scene/replay/native/diagnostics and firstclipcorner bytes beforeoracle; don'tcalibrateexpected. No originalgate changes. Derive physical enlargedsource charges/readback/uniform/pool floors from existing code in report, not a static source/graph test; explicit unknown if unprovable. Main pins a reviewed publicbudgetrefusal before GREEN, no injected cap/provider. READY_RED only.

- [x] **Step 2: Main RED/audit.** Frozen wrapper/init/privatearchive240 `:kanvas:test --tests '*W7SolidRectMaskBlurSourceSurfacePixelTest*' --rerun --no-daemon --console=plain -Pw7.validationDir=...`. Nofilter mustPASS fullbuffer/native/replay; interior predicteddeficit at8,8≈alpha125/blue186, not a device/build failure. Trueedge oracle independentlychecked. All events/XML/log/stdoutstderr/inventory/ownPGID fullyread terminal, separateproduct/fixture/artifact/ref postseal before writes. If centerclipPASS or unrelatedfailure, no source fix until newdiagnosis. Do not replace oldgate/controloracle.

- [x] **Step 3: Source après RED causal.** Capture geometry RectI32 in W3 beforevisible/clip, copydefensively, preserve every applicable clone/material/blend/clip/commandindex/origin rebind. Ordinaryrootvisible/scissor unchanged. W6 uses contextual fullrawbound for MaskBlur inverse-demand/sourceallocation and rawcoverage clip removal; localizes onlytoauthenticallocation, which is shapebounds∩requiredInput. Existingnativefullscreenrecipe allowed onlywithauthenticated containedsource; retain/seal matchingfacts. Never blanketchange w6aRasterBoundsI32 or material/domain/filterguards. If a missingconsumer/validator needs relaxation or an unseen clone outside listedfiles, report NEEDS_CONTEXT with exactfile/reason beforeimplementing. Record exactsources/testhashes +charges/selfreview READY_VALIDATION; no native/Git.

- [x] **Step 4: Main GREEN/context/review.** Same focusedfixture all5PASS after fix1; run unchangedoldgate rootandfilteredcontrol methods, bothPASS atwidths2/1 allreplays. Plainlayer oldmethod remainsRED until separatecommonAAfix, no fullclassGREENclaim. Exercise existingW6abounds/restore/composition/mask contexts +publicbudgetrefusal/sentinel/discard/nativeblue recovery samSurface with budget unchanged, explicitdiagnosticpinnedbefore run. Fullaudit/separatepostseal each. Commit qualified files only; rangepackagefromTask1BASE, independent spec+qualityreview, fix C/I then scopedre-review. No globalGREEN. Solfreshseat ifavailable; Astra exceptionalreview of thisarchitecturaldiagnosis/fix ifSolcapacitystillunavailable, no selfreviewcountedasindependent.

**Review fix1 (C0/I1/M1, head1759669bf):** source∩inverse-demand vide atteignable
dans un saveLayer au clip parent non vide. Cinquième fixture RED seulement,
scène/prefix/sentinel/recovery exacts dans la spec ; quatre témoins et budgets
figés. Après audit/postseal RED, guard seulement direct MaskBlur + SolidRect
déballé : intersection vide → ConstructionFailure InvalidBounds existant,
avant evaluation/allocation ; autres familles/fallbacks inchangés. Supprimer
le cast redondant NativeSiteRecipeV1. Main qualifie cinq tests + contexte,
commit range1759669bf→fixhead puis re-review indépendante I1/M1. Aucun corpus
avant C/I résolus. Agent original/fresh replacement réellement bloqués par
la limite du harnais : reprise Luna existante bornée, rapport fix1 séparé,
pas d'implémentation produit/test dans le contrôleur.

### Task 2: Corpus et draft stackée

**Files:** Create `refactor/waves/W07-gm-convergence/solidrect-mask-source-qualification.md`, fresh `solidrect-mask-source-corpus.json` seulement si produit retenu ; modify `pilotage.md`.

**Interfaces:** Consumes reviewedqualifiedTask1/head/fullnativeproof ; produces frozen631delta and publication metadata. Existing checkpoint/summarize-parity unchanged.

- [x] **Step 1: Main corpus.** Same five slices [0,200),[200,400),[400,607),[607,608),[608,631), images=false/perGM30s/outer240/no-daemon/frozeninit; serialfullaudit/postsealbefore next. Aggregate existing unmodifiedscript; compare all631fields excludingtimes to2485snapshot, 443denominator fixed, actualrendered/compared/admission/pixels/hash changes and regressions. No assumedgain/noPNG/score/refwrites.
- [x] **Step 2: Evidence/finalreview.** Qualification honest baselineRED/newGREEN/context/charges/nativecontrols/corpus deltas andlimits, rawhashes and allreviewfindings/rulings. Main pilotage current state, no fakeW7completion. Independent wholebranchreview from2447base; fix C/I and re-review, retain receipts/workspaces.
- [ ] **Step 3: Draft stack.** Ownedbranchpush normal only afterqualifiedretainedsource/review ; draftbasecodex/w7-transversal-corpus-census2447, attach+verifyremotehead/base/bodybyteexact. Actualtitle reflectsSolidRectsourcefix/commonAAconditionalstate. No merge/globalGREEN/W7complete. Fully-clipped offconsumerinput trackednot silentlyopened.
