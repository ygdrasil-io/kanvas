# W7 Mixed Direct-Inverse Scene Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax.

**Goal:** Repair the measured standalone inverse operation and mixed scene resource inventory while retaining compiler-authenticated native contracts.
**Architecture:** Tasks1–2 established the failures without changing product semantics. Task3 retains the authenticated producer/cover pair and shares its native recipe; Task4 carries full graph resource inventory. Public pixels and native evidence gate each stage.
**Tech Stack:** Kotlin/JVM, Surface, native wgpu/Metal.
**Spec:** [inverse-native-authority-design.md](inverse-native-authority-design.md), selected after Tasks1–2 under the user's W7 carte blanche.

## Global Constraints

- Worktree /Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas, branch codex/w7-inverse-scene-inventory, base0dce69805c0b441f71279ad520455b4163be9c82 (draft#2430).
- Only public real Surface pixels/native evidence. No mocks, infrastructure/source-text/forwarding tests, GPU-availability skips, CPU fallback, fake Picture or GM-name routing.
- Geometry uses org.graphiks.math.geometry.RectF32; no new numeric geometry outside math, I/F32/64 nomenclature retained.
- Preserve proofs, CompositionEnvelope, admission, epsilon, budgets/caps, references/thresholds,631registry/443eligible, scopes/domains/exclusions. Fonts, codecs externally decoded and jpg-color-cube remain excluded.
- Controller alone runs Gradle/native, serial private bounded240s process groups, actual handle retained to terminal. Workers have no runtime/build/subagent authority.
- Worker owns test/product edits; controller owns plan/evidence/publication. RTK shell and apply_patch edits. Do not commit failing test changes or alter product until controller has causal evidence and an approved repair design.
- Existing baseline: source unchanged from measured207rendered/184compared and41freshnative; global be813afd7 remains red/incomplete, not requalified. No merge/cleanup/W7 closure.

## Diagnostic question

The materializer's directInverseDomain requires no mask inventory and a direct-inverse consumer condition, then otherwise requires paired mask accumulators and resolved resources. Source suggests ordinary siblings can break that classification; no runtime value has yet identified the failing conjunct. Merely changing all to any would not prove authentic target/depth/resolve/scissor/sample/V/I/U authority.

Three 8x8 scenes use an integer-aligned inverse-winding rectangle (2,2)-(6,6). For each, compare antiAlias=false and true on the inverse geometry/clip while ordinary rectangles stay explicitly non-AA. Expected pixels are hand-derived, never from product helpers. A transparent interior in the inverse-only case follows the existing fresh Surface transparent clear contract; the other scenes explicitly draw their backgrounds.

## Review Focus

- Direct inverse is not silently replaced by ordinary fill: inside/outside literal pixels differ.
- Adding an ordinary sibling cannot erase/reverse the inverse or fail an otherwise valid frame.
- AA route effects are separated from a scene-inventory effect by independent hard/AA tests.
- No hidden baseline white: inverse-only interior is transparent, mixed scene interior blue, inverse clip interior white.
- Native submission and repeat remain observable; an exception is a real failed positive, not converted to a successful refusal assertion.

### Task 1: Add public causal pixel witnesses, tests only

**Create:** kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7InverseSceneInventorySurfacePixelTest.kt.
**Read for interfaces:** W7ClipProducerScissorSurfacePixelTest.kt and GPUPlanSurfacePixelTest.kt in the same directory. Do not copy their capability-skip helpers.
**Consumes:** Surface(8,8), canvas, Path/addRect/FillType.INVERSE_WINDING, Paint, ClipOp.INTERSECT, RenderResult pixels/nativeEvidenceScopeKinds/stats/diagnostics; @AfterEach GPUBackendRuntimeFactory.dispose.
**Produces:** six independent @Test methods, each rendering its own fixture. No product file change.

- [x] Before bodies, name the bug caught in concise comments: wrong inverse polarity, lost ordinary sibling, or missing native inventory; expected pixels independent of implementation.
- [x] Add inverseOnlyHard and inverseOnlyAA: draw inverse rect in white, no other draw/clip. Assert every one of64pixels: for x,y in2..5, literal[0,0,0,0]; elsewhere[255,255,255,255]. No implicit white initialization.
- [x] Add ordinaryThenInverseHard and ordinaryThenInverseAA: full8x8blue rectangle with antiAlias=false, then same inverse white path. Assert interior[0,0,255,255], exterior[255,255,255,255].
- [x] Add inverseClipHard and inverseClipAA: full8x8white rectangle antiAlias=false, clipPath same inverse rect with explicit INTERSECT and variant antiAlias, then full8x8blue rectangle antiAlias=false. Assert interior[255,255,255,255], exterior[0,0,255,255].
- [x] All six assert Render+Readback, dispatched>0, refused=0, emptydiagnostics; render same retained Surface a second time, assert clean evidence and byte-identical pixels. Do not catch expected positive render exceptions or change expectations on failure.
- [x] Self-review imports/API/expected masks. Freeze tests only and report READY_FOR_DIAGNOSIS. No commit, build or product change. Controller executes :kanvas:test --tests '*W7InverseSceneInventorySurfacePixelTest' with private runner/init, examines all six terminal outcomes. Build/import failure is invalid evidence, not causal RED.
- [x] After results, report exactly what is confirmed and unconfirmed; controller reviews the test evidence and defines a repair separately if needed. Do not infer the precise materializer conjunct from its shared error string.

## Controller continuation

Observed first run: compilation succeeds,2SUCCESS inverseClipHard/AA and4FAILURE.
Mixed scenes refuse W4e resource inventory. Inverse-only scenes fail native
materialization with10typed operands but5keys atscope1. These are two causal
positive failures, not passing refusal tests. Exact failed mixed conjunct
remains unobserved; no product repair yet. See current status for qualification.

- [x] Audit fresh run and independent test review; capture exact boundary values if refusal reproduces.
- [x] If all six pass, preserve the falsification and reproduce the curved original case before alleging an inventory bug. If some fail, isolate the differing condition and seek Astra architecture support before modifying admission.
- [x] Write measured repair design/implementation tasks only once the cause is established. Keep RED tests local until a qualified solution or explicitly reviewed diagnostic artifact is ready; never publish a green claim for this preliminary phase.

### Task 2: Observe native decisions with temporary traces

**Files:** GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt and GPUW4eNativeOperandKeysV6.kt in gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/.
**Interfaces:** existing local immutable values at directInverseDomain guard and native operand key branch, no signature/authority changes. The six Task1tests remain frozen.
**Purpose:** Astra's initial source trace identifies disagreement between encoder and key-builder stencil-producer classification; verify runtime phase/consumer/commonSource plus exact mixed-inventory conjuncts.

- [x] Add temporary println records prefixed W7_TEMP_INVENTORY immediately before the inventory refusal guard: w4eFinal presence, entries/renderSteps sizes, passes/maskIds/scratchIds/aaProducerDepthIds/hardProducerDepthIds/preparedPaths/accumulators/resolvedIds counts, directInverseDomain, each entry passId/consumer type/path phase/sample, and whether scratchIds contains an ID outside maskIds. No native handles or pixel dumps.
- [x] Add temporary W7_TEMP_KEYS trace in the existing preparedPath branch after its local predicates are known: passId, phase, sample, commonSource, inverse consumer/interior type, directPath, stencilProducer, hardMaskProducer, stencilCover, scan-span presence/count. Preserve the original branches, all checks and returned keys exactly.
- [x] Add W7_TEMP_COMMANDS before native payload construction: each already-built render's sourceStepIndex/passId, ordered command classes and operand kind/ownership if exposed, existing scope keys role/kind/ownership/bindingKey, and actual commonSource. No handles/buffers. This observes the later cover without bypassing the scope1 failure.
- [x] Self-review and freeze; controller runs the same six tests in a new private archive, expecting unchanged2SUCCESS/4FAILURE plus diagnostic values. No runtime/build/commit by worker.
- [x] After controller reads terminal output, remove only your temporary trace statements with apply_patch; preserve tests and all other edits. Confirm product diff versus73be2c3 is empty, report observed values supplied by controller and any limitation. No repair in this task.

Task2 reviewed by Astra after trace removal: accepted, no remaining actionable
finding. The [selected repair design](inverse-native-authority-design.md)
keeps an authentic producer/cover pair, then fixes graph-issued inventory.
The stages below implement that design. No product repair existed when they
were written; the diagnostic evidence is not a GREEN claim.

## Repair review focus and custody

- Alpha128 exposes duplicate cover application: Task3 checks transparent hole and exactly128 exterior alpha, never approximately192.
- A hard sibling in an AA scene requires a real path mask without clip folds: Task4 retains ordinaryThenInverseAA and checks the reverse order.
- Terminal ownership can change with a suffix draw: Task4 tests both hard/AA inverse followed by a hard red sibling.
- Picture bounds are not an implicit clip: Task4 uses a real Picture with explicit clip and transform, memory and serialized forms.
- Refusal must not poison resource reuse: Task4 preserves a sentinel, discards, then renders the admitted scene twice on the same Surface.

Task3 is an independently reviewable operation repair but not a green whole
class: two mixed positive tests deliberately remain RED until Task4. Preserve
all eight tests unchanged in meaning. Archive Task3's exact working diff and
native evidence for Sol review; defer its product/test commit until Task4
passes. Task4 reviews its own delta against that frozen diff plus shared
contracts. The controller then commits both reviewed stages together. Never
disable, delete, catch, or reclassify the mixed positive failures to get green.

### Task 3: Seal the standalone inverse producer/cover operation

**Files:**
- Modify `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7InverseSceneInventorySurfacePixelTest.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUW4ePreparedInversePairOperation.kt` for the narrow immutable operation contract, not numeric geometry.
- Modify `GPUPlanW4ePreparedAuthority.kt` in that same passes directory, and `GPUW4eNativeOperandKeysV6.kt` / `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` in the sibling execution directory.

**Interfaces:** Existing `GPUW4ePreparedFrameAuthority.issueRoot(authority, graph, refs, frameId, capabilitySealHash, renders)` is the only root issuance boundary. Add `internal fun inversePairOperationFor(packet: GPUDrawPacket): GPUW4ePreparedInversePairOperation?` on the frame authority. It returns a graph-sealed per-pass operation only for the exact attached packet/path/consumer and frame witness. The operation exposes producer versus cover and the ordered command-operand recipe; the key builder and encoder both consume it. No caller may issue it from an enum phase or nullable consumer alone.

**Constraints:** All Global Constraints apply. No Gradle/native/build/helper agents or commits by worker. Do not change mixed inventory admission, payload capacity/packing, commonSource, scan-span/Zero/InverseMask/direct/W5b/W6 contracts, or strict native operand equality. Read the selected spec completely. Controller runs the tests and supplies terminal evidence; worker freezes edits until that result.

- [x] Add independent `inverseOnlyAlphaHard` and `inverseOnlyAlphaAA` to the existing public test class. Surface8×8, inverse-winding rectangle(2,2)-(6,6), white alpha128, no background. All hole pixels RGBA0; exterior alpha exactly128 and RGB matching the existing public premultiplied readback convention. Use literal arithmetic expectations, not renderer helpers. Retain Render/Readback, zero refusals, empty diagnostics and byte-identical second retained render. Do not weaken the six original tests.
- [x] Freeze tests first. Controller runs `:kanvas:test --tests '*W7InverseSceneInventorySurfacePixelTest'` through the existing serial240s private runner/init. Expected causal RED: alpha and opaque inverse-only fail operand materialization, mixed fail inventory, clips pass; build failure is not RED. Wait for controller authorization before product edits.
- [x] Issue the immutable pair in `issueRoot` for authenticated `InverseDomain.Geometry` producer/cover neighbors, before the existing single-sample early return. Bind ordered pass IDs, command identity, atomic group, finite domain/interior fill rule, target/depth/sample, V/I/U slices and identities, load/store, and final resolve owner. Verify both packet prepared path/consumer identities against the authenticated authority; preserve root resolve checks. Clip-only/layered issuance does not gain a standalone pair.
- [x] Use the shared operation recipe for keys and encoding. Producer: stencil clear0, finite interior only, no color write, existing replace-one direct or winding/parity fan, `INVERSE_DOMAIN_INTERIOR` slices. Cover: load same stencil, test zero, apply existing blend exactly once in the finite domain using existing inverse uniform, no geometry replay. AA remains4× and only the graph-designated cover resolves. Derive attachment keys normally; no count constants/padding. Paired Geometry without a valid witness must refuse, never fall through to the historical full-domain operation.
- [x] Freeze and request controller run of the whole eight-test class. Stage acceptance: six PASS (inverse-only2, alpha2, clip2) and exactly two unchanged mixed inventory failures. Any other failure requires repair; this stage is not overall GREEN.
- [x] Self-review and write report with exact controller-supplied RED/GREEN-stage evidence, files and residuals. Controller archives working diff including untracked test/new file and obtains Sol spec+quality review. No commit yet; Task4 consumes the frozen approved operation contract.
- [x] Review amendment: require an explicit fail-closed lookup for a graph-required pair whose witness is missing/non-owning, without changing non-root contracts. Also preserve optional resolve for an intermediate AA cover: the graph's final color pass, not every inverse pair, owns the resolve. Before that resolve correction, add `successiveInverseAAColorsPreserveEarlierComplement`: Surface8×8, whiteAA inverse rectangle(2,2)-(6,6), then blueAA inverse rectangle(0,0)-(4,8), no ordinary sibling/clip. All64pixels: right columns x4..7 blue; left x2..3,y2..5 transparent; remaining left white. Same native/repeat checks. Controller establishes causal RED, then runs corrected nine-test class plus three existing W6 inverse scan-span controls (mirror edge, reversed winding, upper-right edge distinction). Final Task3 stage criterion becomes7PASS/2unchangedmixedRED; this addition does not weaken any prior witness.

### Task 4: Carry authenticated resource inventory and prove composition

**Files:** Task3 frame authority/materializer and public test class; create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUW4ePreparedResourceInventory.kt`; modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4eClipGraphLowerer.kt` only to pass already-available graph/ref/preparation facts. No unrelated lowerer migrations.

**Interfaces:** Preserve Task3 `inversePairOperationFor`, `requiredRootInversePairOperationFor` and authenticated `rootIssued` routing. Add a root-only immutable `GPUW4ePreparedResourceInventory` held by frame authority and validated against actual preparations, ordered render owners and readback before checkout. Consume compiler-authenticated resources and exact `Map<String, GPUFrameResourceRef>` from `issueRoot`; use the existing preparation descriptor/use/lifetime types without new geometry. Expose `internal fun validatesResourceInventory(preparations: List<GPUResourcePreparationRequest>, renders: List<GPUFrameStep.RenderPassStep>, readback: GPUFrameStep.ReadbackCopyStep?): Boolean`.

- [x] Use Task1's mixedHard/mixedAA causal RED and Task3's still-failing mixed results as tests-first evidence; do not rerun the unchanged RED merely for ritual.
- [x] Seal exact resource ID/ref bijection, kind/descriptor/role/sample/bytes/usages/lifetime and ordered owner/continuation facts from the root graph. Validate no missing/extra/aliased resources and exact owner mappings before native checkout. Preserve frame/seal, V/I/U payload identity and bytes, actual fold pairing, readback/resolve ownership and cleanup.
- [x] Replace the standalone global all-inverse-or-full-clip heuristic with validation of that root inventory. Allocate scene/depth/mask resources by their authenticated roles, including hard-edge masks in AA scenes with no fake accumulators/resolved clip masks. Existing non-root/W5b/W6 admission retains its own authority; never broaden it through a boolean shortcut.
- [x] Controller runs all nine tests (including Task3 review addition); require9PASS, no skips/refusals. Fix causally if not. Then add `inverseThenOrdinaryHard` / `inverseThenOrdinaryAA`: white inverse followed by opaque red hard rectangle x0..3,y0..7. Expected left four columns red; right hole x4..5,y2..5 transparent; remaining right pixels white. Check all pixels/native/repeat.
- [x] Add memory/serialized real Picture controls for hard and AA: record explicit clipRect(0,0,8,8), then the blue-background/white-inverse scene; replay translated(+2,+1) into transparent12×10Surface. Within translated8×8 expect blue hole and white exterior, outside transparent. Use PictureRecorder and actual serialize/deserialize APIs demonstrated by adjacent W7HardPictureSurfacePixelTest; do not infer clipping from recording bounds. Assert all pixels/native/repeat independently, not only equality between variants.
- [x] Add public refusal/recovery using the existing unsupported composite paint fixture from `W7HardPictureSurfacePixelTest.opacitySolidShaderPictureStaysOutsideHardSeed`, retaining its `unsupported.composite.paint` diagnostic. Sentinel0x5a unchanged after readPixels refusal; discardRecordedOperations; record the admitted mixed inverse scene on the same Surface, assert all pixels and two clean native renders. Keep the existing perspective/shader fixture rather than inventing a new rejection contract.
- [x] Freeze, controller runs the expanded class and affected existing inverse/clip/Picture/continuation suites serially. Self-review, report, then Sol reviews Task4's frozen delta and evidence. Newly discovered positive failures remain local until corrected.

**Measured stage amendment:** Initial gate9/9 and expanded14/16 pass; the two AA Picture positives reveal a separate missing W4e→W6 occurrence-source contract. Their implementation moves to the explicit extension described in the spec addendum, with a dedicated implementation plan before product changes. Task4's independent review covers exact root inventory and preserved non-root admission, plus the14positive results and all16unchanged oracles. It cannot certify Picture AA support or native crash recovery. Covering reaches70PASS/1historicalW5bFAIL before a distinct W6 inverse-AA masked native abort, then224/224PASS in the five unreached classes. Secure that abort in Task5. All product/test commits stay deferred until the two Picture positives and the new capability's witnesses pass; stage acceptance is never full qualification.

### Task 5: Refuse unsupported masked inverse AA before native submission

**Files:** `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4ePreparedAuthority.kt` for authenticated prepared-path/consumer classification and, only if required to surface the typed refusal before checkout, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`. Public test `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6InverseScanSpanSurfacePixelTest.kt`.

**Interfaces:** Add `internal fun hasUnsupportedMaskedInverseAaDirectOperation(packet: GPUDrawPacket): Boolean` on the graph-issued frame authority. It must validate the attached frame/path/consumer identities and classify the actual Multisample4 direct-color + InverseMask.Geometry combination lacking a native complement recipe. Require its sealed depth/target facts; do not infer ownership from the packet enum alone. Surface typed diagnostic `unsupported.native-core-primitive.w4e-inverse-mask-aa` at pre-materialization before checkout/submit. All other routes preserve their previous contracts. If the exact prepared consumer representation differs, report the named types before implementation rather than guessing a broader predicate.

Root issuance must capture these facts from the already authenticated `authority.pathFor(passId)` and `authority.consumerFor(passId)` of the root graph, keyed by the owning pass ID and retaining exact prepared object identities. The observed fixture is a standalone root Surface despite its W6-named test class. Do not migrate non-root authorities to this witness. Match `PathRenderPhase.MultisampleDirectColor`, `SamplePlan.Multisample4` and the geometry interior of `GPUW4ePreparedClipConsumerAuthority.InverseMask`; preserve exact target/depth identities. Missing or mismatched required authority must fail closed, not silently become an ordinary supported path.

- [x] Use affected-1's exact executor134/pipeline-attachment mismatch as causal RED. Update only the existing `AA inverse direct over scan span limit keeps its multisample route` public witness: same1×4097Surface, hard clip and inverse AA triangle. Require the chosen typed refusal (not scan-span-draw-limit), unchanged0x5asentinel. Discard, draw an opaque blue fullSurface hard rectangle, verify every pixel `[0,0,255,255]`, Render/Readback, zero refusals/diagnostics, and a byte-identical second render on the same Surface. Do not lower sample count, remove attachment, catch native crash, skip or create infrastructure tests.
- [x] Implement the narrow graph-authorized refusal. Root inverse-domain AA, existing hard inverse-mask and scan-span budgeting must remain unchanged. Source/test edits only by worker; controller executes.
- [x] Freeze; controller runs the entire W6InverseScanSpanSurfacePixelTest and the sixteen-test W7 inventory class. Require all W6 tests executed/passing without process abort and the same14W7PASS/2knownAAPictureRED; any other failure is investigated causally. Then focused Sol review of this task's exact delta and evidence.
- [x] Report this as runtime safety, not added rendering capability; retain positive inverse-maskAA support as a separate gap. No product/test commit while Picture positives are still RED. After review, write the dedicated bounded W4e inverse-AA Picture capability plan from the spec addendum and execute it before Task6 qualification.

### Task 6: Qualify and publish the stacked lot

**Files:** `refactor/waves/W07-gm-convergence/status.md`, current plan and existing corpus evidence/dashboard artifacts only as produced by the established scripts. No product edits by controller; any finding returns to its worker.

- [x] Run fresh affected native suites on final product SHA. Run the full kanvas suite once in a bounded private group; attribute every failure, timeout and unreached test to this exact SHA. Do not relabel the historical global as a new result or claim full pass from focused suites. Corrected38c75ab12:343=342PASS/1knownNoOpFAIL; global725=687PASS37FAIL1SKIP,240s124/143,298knownunreachedlowerbound,272affectedPASSseparately; math788eventsPASS/XMLmatrix310only.
- [x] Run existing631/443corpus protocol serially with unchanged registry/domain/exclusions/caps. Compare strict previous snapshot `inverseclip-port-e9da0ebd6.json`, preserving18invariants and old render hashes/metrics. Inspect new/changed images; report gains, losses and unresolved mismatches without inventing success thresholds. Fresh38c75ab12:207rendered184compared,zero gains/losses,all207hashmetrics/all631nontimeunions/all18invariantvaluesandpresenceidentical;deltaimagesempty,vertices30sretained.
- [x] Archive full branch review package against0dce69805; request targeted Astra final architecture/spec/quality review using fresh native/corpus evidence and ledger minors. Address material findings through one worker fix wave and scoped review. BroadAstra atc61786ab5:0Critical1Important3Minor; finalworkerwave/scopedSol I1/M1/M2/M3allADDRESSED,no newC/I/M. Correctedsource38c75ab12treea66c1d8a4exactreviewed,fullfinalbranchpackage381404charsretained;merge/W7notqualified.
- [ ] Commit qualification/status; create/update draft PR stacked on codex/w7-inverseclip-port (#2430), attach it to this chat, verify remote HEAD and PR base. No merge; W7 remains open beyond this lot unless its global exit conditions are actually met.

## Repair plan self-review

The design's two causal defects map to Tasks3/4; alpha, sample, resolve,
ordered siblings, Picture boundaries and refusal/recovery map to explicit
public checks. Task3 cannot be committed green alone, so commit custody is
deferred rather than weakening tests. Inventory and pair witnesses are
separate immutable graph-owned responsibilities; numeric geometry and payload
packing stay unchanged. Qualification and publication are Task6, after the
dedicated inverse-AA Picture plan; neither is inferred from an earlier local gate.
