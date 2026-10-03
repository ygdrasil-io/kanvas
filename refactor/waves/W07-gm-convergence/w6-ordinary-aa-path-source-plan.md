# W6 Ordinary AA Path Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Rendre les deux vraies scènes rrect_blurs/blurcircles2 via une source PATH AA ordinaire sous W6, sans nouvel algorithme AA.

**Architecture:** W6 sélectionne une source root PATH FILL/STROKE solide SrcOver sans filtre, avec autorité originale conservée. Le compiler et le transport existants isolent AA4→1x puis composent une seule fois ; une preuve causale précède le patch.

**Tech Stack:** Kotlin, render IR, gpu-plan/gpu-renderer, math, Surface WebGPU native, Skia GMs, JUnit/Gradle.

**Spec:** refactor/waves/W07-gm-convergence/w6-ordinary-aa-path-source-design.md

## Global Constraints

- Fonts/codecs externes/jpg-color-cube exclus.
- Pas de CPU renderer/fallback, mock, skip GPU, tests d'infrastructure/source-text/forwarding.
- Références, scores/seuils, registre631/scope443, sample count et budgets inchangés.
- Math suit I/F32/64 ; aucune nouvelle géométrie dans gpu-plan/renderer.
- Contrôleur seul pour runtime/Git, un runtime à la fois, watchdog240s inchangé et audit séparé complet après chaque terminal.
- Workspaces/raw receipts et inverse-filter untracked SHA96cd8349 préservés ; aucun cleanup/merge.
- Suites globales RED/incomplètes explicites, pas de W7 clos.
- Famille root PATH AA FILL/STROKE solide SrcOver réellement sans filtre/effet dans frame W6 ; neuf occurrences avec filterPayloadPresent=true hors lot.

## Review Focus

- Frère W6 provoquant le refus d'un path auparavant natif : reduced FILL/STROKE avec vrai filtre déjà admis, pas ownership forcée.
- STROKE accepté comme FILL et contour/provenance perdus : capturer vrais séparateurs, ancres indépendantes et même préparation math.
- Resolve source perdu, double couleur ou blend dans mauvais ordre : source colorée transparente, doublon demi-couvert et permutation à intersection.
- Vrai filtre dépouillé ou route hostile empruntée : négatifs comportementaux filtre/matériau/blend/clip/transform et récupération native.
- Admission de GM vide derrière threshold0 : deux scènes complètes, régions formes/séparateurs, métriques/crops, comparaison entière.

---

### Task 0: Sémantique drawLine avant la source W6

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/CanvasExtensions.kt` (drawLine seulement).
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt` (drawLine et helper partagé de l'enveloppe path si nécessaire).
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7DrawLineSurfacePixelTest.kt`.
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7GmDrawLineSurfacePixelTest.kt`.
- Read only: Paint, Canvas, math hairline preparation, GmCanvasPathCtmSurfacePixelTest, two original GM sources/capture receipts.

**Interfaces:** Consumes public drawLine/drawPath, immutable Paint.copy, existing clip/CTM wrapper and native Surface; produces implicit STROKE at drawLine only, preserving every other paint fact and original path/transform. No new IR/sourceOperation/geometry/filter/AA domain. Existing Task1 captured-open-FILL is evidence motivating this prerequisite, not expected GREEN semantics.

- [ ] **Step 1: Independent API RED.** Fresh Task0 writer creates two public native tests, no product edits. Actual visible/interior and transparent/exterior pixels fixed before run; compare defaultFILL and STROKE_AND_FILL drawLine to explicitSTROKE drawPath, width0 and2, positive control not empty, independent openFILL drawPath remains empty. GmCanvas uses translation and existing clip wrapper, literal device anchors. No skipGPU/infrastructure/source-text/assertion of capture plumbing. Reuse class @AfterAll dispose correctly, one companion only. Report READY_RED with bytes/commands/hashes; controller runs each class separately bounded240s/full separate audit.
- [ ] **Step 2: Small semantic fix after authentic RED.** Samewriter sets paint.copy(style=STROKE) in shared Canvas.drawLine. GmCanvas.drawLine delegates to shared helper, preserving exactly drawPath's current withClip+save/concat/restore handling (factor tiny helper if necessary, no unrelated API edits). No style conversion in planner, no GM/reference/budget/domain edit, no width0→1. Report READY_VALIDATION; controller runs both tests plus existing GmCanvasPathCtmSurfacePixelTest covering wrapper changes, each separately audited. Explicit commit then one Sol spec+quality gate, originalwriter fixes C/I.
- [ ] **Step 3: Refresh causal W6 RED.** On unchanged W6 source, controller reruns exact blurCircles2RendersCompleteOrdinarySeparators capture; confirm STROKEwidth0, samepath/CTM/solidSrcOver/noeffect and authentic W6 boundary. Resume original Task1 writer to add reduced width0 hairline under same native-admitted filtered sibling before source; independently pin coverage bytes and run RED. All retained reduced controls remain accounted. No source patch until this refreshed family gate is audited.

**Qualified fixture correction:** The GM empty-FILL lane at integer device y14
hits a separate candidate generic zero-height cover-bounds refusal, before
pixels. Move only that zero-area control to local y12.5; all clear-lane,
visible-STROKE, drawLine, clip/CTM and Paint expectations remain identical.
An open two-point FILL has zero area at either coordinate, independently of
renderer output. Controller first discriminates against unchanged corrected
helpers, then writer restores exact old helpers for re-RED, then reapplies
the exact reviewed semantic patch for unchanged-fixture GREEN. Each stage
has pinned bytes and separate full runtime audit. Never treat this adjustment
as a fix of integer empty-FILL behavior; preserve that native debt and old
receipts. No blanket bounds-contract relaxation in this prerequisite.
Native proof in this GM fixture uses positive actual drawCallCount and
pipelineCount with clean result/refused0, native Surface port and all literal
readback pixels. Render/Readback scope names are not a universal export:
the existing canonical prepared router exports only image Upload scopes;
its execution nevertheless validates native submit/readback before success.
Preserve that public evidence-export limitation, no fabricated markers.
Step3 reduced hairline uses Surface64x64 LINEAR, standalone positive then
same path under existing blue blurred Rect sibling: local(4,5.5)→(20,5.5),
save/scale(2,3)/restore, AA STROKEwidth0 BUTT. Device line(8,16.5)→(40,16.5)
must stay one pixel wide: opaque red(16,16), exact clear(16,15)/(16,17)/
(6,16)/(42,16). This discriminates width0 from localwidth1 scaled to3.
If standalone route refuses, record that boundary before source expansion.

### Task 1: Cause, témoins RED et source ordinaire authentifiée

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt`
- Modify if contract transport requires: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt`, `W6aLayerGraphValidation.kt` in same directory.
- Modify only touched consumer invariants: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt`, `passes/GPUPlanW4dGeneralPreparedAuthority.kt`, `planning/W6aLayerGraphLowerer.kt`.
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7W6OrdinaryAaPathSourceSurfacePixelTest.kt`
- Create causal/native witnesses first: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7W6OrdinaryAaPathSourceIntegrationTest.kt` (Task2 subsequently completes qualification).
- Read only: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/blur/RRectBlurGm.kt`, `BlurCircles2Gm.kt` in same directory ; existing GM capture/diagnostic interfaces.

**Interfaces:**
- Consumes: SceneSnapshot/SceneCommand.Draw/DrawNode original and existing stripW6bPayload; W4dGeneralPathPlanCompiler.w6AaColorSource/acceptsW6AaColorSourceScope; matrixF64.preparePathStrokeGeometryF32; PathFillGeometryF32/PathStrokeGeometryF32; AaResolvedColor; Surface.canvas/render and RenderConfig.compositionDomain.
- Produces: a named factory `W4dGeneralPathPlanCompiler.w7W6OrdinaryAaPathColorSource(runtimeCatalog)` and predicate `acceptsW7W6OrdinaryAaPathColorSourceScope(draw: DrawNode): Boolean`, keeping immutable prepared contour and full source4x→resolved1x authority. Names are internal-source contracts, no new public Surface API.
- Controller consumes: exact causal capture/RED commands, unchanged expected pixel specification and READY_RED report ; later qualified source/test hash list and READY_VALIDATION report.

- [ ] **Step 1: Establish first refused draw.** Read original GM/capture and compiler dataflow. Stage the two real-GM native behavioral tests specified in Task2 Step1 before product edit; print targeted original DrawNode fields via public Surface.snapshotScene/snapshotOps before rendering, not assertions of capture plumbing. Controller runs each test separately for authentic old-product RED and captures rrect_blurs draw10/blurcircles2 draw22: origin/style/coverage/material/blend/clip/CTM/filter/effects. Trace original→child through unchanged stripW6bPayload, distinguish measured originals from static child-dataflow proof. Use existing detailed refusal/capture, no new inventory infrastructure or raised limits. Report both separate guards (ownership and FILL), exact source/consumer invariants, and any observations still missing. Stop hypothesis before product edit if family does not match.
- [ ] **Step 2: Write independent native failing witnesses, no product edit yet.** Add named tests `ordinaryStrokeSurvivesFilteredSibling`, `ordinaryFillSurvivesFilteredSibling`, `isolatedSourcePreservesPremultipliedColorAndOrder`, `ordinarySourceDoesNotBorrowForeignAuthority`. Public Surface scenes use one native-admitted NORMAL-blurred solid Rect sibling away from and then at a selected intersection, plus ordinary PATH line/triangle. Use 64×64, line(8,16)->(40,16), BUTT width2 for full interior/clear exterior and width1 for half-coverage; FILL triangle(8,8),(24,8),(8,24), sample(10,10) full/(28,28) clear. Red alpha1 on transparent fixes alpha≈128 at the half cell ; encoded premultiplied red≈128, LINEAR storage follows the existing sRGB transfer contract (not an unconditional RGB=alpha byte equality). Repeated identical width1 line expects isolated SrcOver alpha≈191/192 rather than retained-root≈128. Fix exact sample coordinates/domain-dependent RGB from the documented physical readback contract and tolerance justified by UNORM before run in report. Place brother blur at Rect(48,48,56,56), sigma1, for non-overlap tests, and use independent SrcOver expected colors for overlap/permutation. Test LINEAR always; encoded only with an already-admitted filtered sibling, no filter-domain expansion. Négatifs d'admission ordinaire : genuine filter/non-solid/non-SrcOver retain their existing supported semantic behavior or exact refusal, never universally reject an already-supported foreign lane. Unsupported clip/transform retain their refusal and next valid frame recovery, not generic `throws`. No assertion via compiler text, layout/packet forwarding or mock ; existing sealed facts are runtime receipts, not infrastructure tests.
- [ ] **Step 3: Report READY_RED, wait controller qualification.** Worker runs no runtime/build/Git and spawns nobody. Controller uses bounded240s `:kanvas:test --tests '*W7W6OrdinaryAaPathSourceSurfacePixelTest*' --rerun`; audit full log/exit/events/XML separately before resuming writer. RED must be the intended source boundary, not a fixture/compilation failure; each pre-patch negative/control remains individually accounted. Do not adjust expected pixels to observed output.
- [ ] **Step 4: Promote the smallest source contract.** Resume same writer after RED. Select only original and child genuinely ordinary PATH AA solid SrcOver root when ownsW6b; include FILL/STROKE in the named factory/predicate. Retain unfiltered original identity and current transform/clip/resource guards; no Rect mode or filter-coverage mode borrow. Use existing AA4 stencil/direct source and resolve1x, source consumption once, sealed metadata/localized geometry and exact resource costs. No new shader/ABI/sampling/domain. Any necessary consumer change authenticates the new exact contract, never relaxes unrelated Ready checks. Preserve root correlated-AA tests unchanged. Report READY_VALIDATION with full touched-file and behavior facts.
- [ ] **Step 5: Controller GREEN and Sol task review.** Re-run the same bounded native test; add existing root/W6 source covering tests from touched contracts. Audit each separately, preserve warnings/failed receipts and all negative/control statuses. Commit explicit tested files excluding protected untracked; generate diff from task BASE and dispatch one Sol spec+quality review. Address C/I with original writer and scoped re-review. No GREEN global inferred from focused PASS.

### Task 2: Two real frames, measured delivery and final review

**Files:**
- Modify Task1 causal/native witnesses: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7W6OrdinaryAaPathSourceIntegrationTest.kt`
- Create controller report: `refactor/waves/W07-gm-convergence/w6-ordinary-aa-path-source-qualification.md`
- Modify controller: `refactor/waves/W07-gm-convergence/pilotage.md`
- Read only: Task1 product/source/tests and fixed GM/reference registry.

**Interfaces:**
- Consumes: Task1 native source contract, unchanged RRectBlurGm/BlurCircles2Gm, existing render/capture/reference/comparison pipeline.
- Produces: `rrectBlursRendersCompleteOrdinarySeparators`, `blurCircles2RendersCompleteOrdinarySeparators` native tests and stdout prefix `W7_W6_ORDINARY_AA_GM` with native counters, source/resolve/consumption facts, fixed dimensions/reference hash/domain, full comparison metrics, independent content samples and replay hash. No reference/score update.

- [ ] **Step 1: Complete full-scene witnesses before runtime.** Extend the two Task1 native tests without duplicating names/capture. Instantiate unchanged GM300×400 and730×1350 via actual GmCanvas/Surface route. Assert native>0/refused0, complete dimensions/opaque background, filtered shape interiors and all ordinary separators at expected positions, repeat identical bytes. RRect anchors left/right centers(50/250,50/150/250/350) white/yellow/orange/blue and clear center column(150,50) background68; derive literal separator samples from width1 and declared LINEAR composition before run, independently of image score. BlurCircles2 first circle centers(65,65)/(65,170), first separator y222.5 and subsequent row translations from untouched GM; fix fully empty/background and black interior anchors before run. Use fixed reference comparison/crops, minSimilarity0 never a fidelity oracle. Keep raw evidence of each channel/alpha and draw position.
- [ ] **Step 2: Controller bounded delivery.** Run each full GM test separately,240s; audit original terminal then full logs/exit/events/XML/stdout separately before next runtime. Inspect freshly rendered image/reference/diff regions and whole-frame metrics. If either frame has another substantial refusal, missing content, strong composition error or budget/time violation, record exact falsifier and re-prioritize without expanding this plan or claiming delivery.
- [ ] **Step 3: Qualify retained product.** Only after both frames satisfy delivery, measure the9 unfiltered subgroup and existing root/W6 controls then fixed631/443 corpus through existing runner, with isolated controller/watchdog/evidence. Modules touched run in proportion; inherited noncompiling/global RED suites remain explicit. Compare every affected admission/hash/metric, distinguish admission from fidelity, no scope/threshold/reference manipulation. Worker report READY_VALIDATION, controller alone runtime/Git.
- [ ] **Step 4: Review and publication.** One Sol Task2 gate, then one Astra whole-branch/strategy review using package from plan branch BASE, ledger and all raw qualification receipts. Preserve/trier deferred Minors. One final fix wave if required, original writer/scoped re-review. Draft stacked PR only for retained verified product, body states actual gains/regressions/open debt; attach and verify exact base/head/body. No merge or W7 completion claim.
