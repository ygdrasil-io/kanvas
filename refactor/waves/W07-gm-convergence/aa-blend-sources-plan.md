# W7 AA Blend Sources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose solid AA geometry with the 12 Porter-Duff modes and PLUS consistently at root, in plain layers and in Picture.

**Architecture:** Preserve geometric coverage independently from paint alpha. Reuse the W4d white-coverage producer, existing materials/formulas and one shared deferred occurrence emitter; retain a selected versioned coverage law through native execution.

**Tech Stack:** Kotlin/JVM, gpu-plan, WebGPU/WGSL, public Surface/Picture native readback.

**Spec:** `refactor/waves/W07-gm-convergence/aa-blend-sources-design.md`

## Global Constraints

- PLUS is `sat(C*S + D)`; other admitted modes are `D + C*(B(S,D)-D)` or a proved selected equivalent. Coverage is independent of paint alpha.
- Reuse `PlanW4dAaCoverageSourceBindingV1`; consume resolve alpha, not RGB. Preserve MSAA4/resolve1 and UNORM quantization.
- No relaxed proof, CompositionEnvelope, epsilon, cap, oracle tolerance, reference, exclusion or GM threshold; no artificial layer or GM-name selection.
- Preserve existing capabilities; new admission is the spec's closed 13-mode solid LINEAR path/rect matrix. Fonts, codecs/external decoding and jpg-color-cube remain excluded.
- No new infrastructure/mock/forwarding/source-text tests. Public native Surface/Picture pixels and refusal/sentinel/recovery only; geometry belongs in math with I/F32/64 names.
- All shell commands start with rtk, actual workdir `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`; edits use absolute apply_patch. No subagents from workers, no merge/publish by workers.
- Docs in refactor; draft stacked on #2425. Terra implementation, Sol reviews, Astra only necessary strategy/final review. Preserve unrelated cbf6 changes.

## Review Focus

1. Saturation at partial coverage distinguishes pre-scale from post-lerp (Task 1).
2. Transparent paint still changes destination for CLEAR/SRC (Task 3).
3. Coverage alpha channel, origin and occurrence identity remain distinct from material data (Task 2).
4. Ordered consumers read the immediately preceding destination; DST does not mutate it (Tasks 2/3).
5. Replayed Picture at distinct origins and under layer scissor does not reuse stale mapping/destination (Task 4).

## Validation commands and evidence

Archive root: `/private/tmp/kanvas-w7-aa-blend.rFtTrn`. Every run uses a fresh
subdirectory and `bounded-run.rb DIR 240 ./gradlew ... --offline --no-daemon
--no-build-cache --console=plain`. Public tests add `-I
/private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle
-Pw7.validationDir=DIR`. Never overwrite a run or count compilation/setup
failure as causal RED. Summarize XML identities/counts, failures/errors/skips,
native refusal outputs, process and wrapper exits. GPU is available here.

Baseline is 31/31 in `baseline`, 4 classes: W7AaPathLayerSurfacePixelTest,
W7RootAaSurfacePixelTest, W7AaMaskBlurSurfacePixelTest,
W7MixedRootAaRectSurfacePixelTest. Current checkpoint `gm-baseline` indices
[10,14) confirms unchanged refusals for PlusMergesAA and aarectmodes.

### Task 1: Correct the existing W5 covered PLUS law

**Files:**
- Modify `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/FinalBlendPlan.kt` and W5 destination graph/canonical identity validation where the new selected fact crosses a seal.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bBlendPlanLowerer.kt`, `passes/GPUCorePrimitivePreparedAuthority.kt`, `execution/GPUW5aSourceStageNativeV2.kt` and covered formula consumer/key sites required by that selected law. Audit `passes/GPUBlendPlanning.kt` and `pipelines/GPUBlendFormulaProgramLibrary.kt`; modify only a reachable non-font covered-PLUS execution contract, not legacy pre-admission/validation or unrelated font consumers. Record the route/seal proof if no modification is required. Paths after the first renderer path share its package root.
- Modify independent `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/WgslFloatEnvelopeV1Oracle.kt`, `W5bBlendCpuOracle.kt` and covered-PLUS oracle consumers only as needed for explicit V2 migration.
- Create public `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7CoveredPlusSurfacePixelTest.kt`.

**Interfaces:**
- Add `BlendCoverageLawV1 { DestinationInterpolation, SourcePreScale }` in FinalBlendPlan.kt. `BlendPlan.DestinationReadV1.coverageLaw: BlendCoverageLawV1` retains the selected law; default is DestinationInterpolation for compatibility of unchanged callers.
- FinalBlendPlanner selects SourcePreScale for PLUS + ScalarCoverageInShader. Full PLUS kernel remains `plus_exact@v1`; no new fixed-function selection in Task 1.
- Add plan authority `selectedCoverageLawV1(mode: BlendMode, coverage: BlendCoverageEncodingV1): BlendCoverageLawV1` and use it for selection/validation, never backend mode classification.
- Versioned canonical composition identity includes the law when SourcePreScale; prepared equality, shader/native cache keys and validation consume it. Full/unrelated modes retain their identity where possible. Binding-layout ABI3/4 unchanged.
- Add independent test-oracle entry `coveredPlusPrescaleV2(src: Array<Interval>, destination: AttachmentState, coverageF32: Float, scalarMask: Boolean = false): DrawResult` in the existing oracle object. It uses unchanged outward-rounding/storage machinery, not production formulas. Covered PLUS consumers call it explicitly; V1 post-lerp is not an accepted alternative.

- [x] Write public saturation-sensitive fixtures for analytic AA Rect and existing W4e scalar-mask path. Both cover a nontransparent destination with a same-channel green source, so green and alpha distinguish the laws while R/B are exact zero; also assert full interior and untouched exterior. Use the authentic all-analytic background and explicit nontrivial source opacity for the first analytic witness; retain opaque/mixed-hard-AA coverage as an unresolved end-series matrix requirement if that distinct route refuses. Construct expectations before Surface from the new independent V2 law and independently prove disjointness from V1 post-lerp. Preserve coverage storage quantization (.5 inline versus encoded mask coverage); never infer it from native output. The green fixture replaces white solely because the independent white/.75 oracle spans three nonsaturating R/B codes and refuses its unchanged envelope.
- [x] Run only the new class to causal RED on current product, archive actual pixel mismatch. Correct fixture/compiler errors before counting RED.
- [x] Select and carry the law. Native ABI3 inline and ABI4 texture coverage call the full kernel on `C*S` for SourcePreScale; DestinationInterpolation retains old post-lerp. The unscaled full kernel must not be applied first. Audit shared material formula consumers; do not patch the helper while leaving the actual source-stage tail inconsistent. Reject incoherent mode/law/coverage combinations before native allocation. Keep destination topology/version/snapshot budgets unchanged.
- [x] Migrate covered-PLUS independent oracle call sites explicitly; preserve old-law calculation only as exclusion. Keep unrelated numerical schedules/caps unchanged. Add no infrastructure tests.
- [x] GREEN new class, then affected W5bBlendSurfacePixelTest and W5gComposedMaterialSurfacePixelTest with all identities accounted for. Include nonsaturating/zero-source/zero-coverage/full-coverage controls, repeated actual native render, and same-Surface recovery after an existing low-budget refusal. New tests require full expected buffers or independently justified witness+untouched-region assertions, Render/Readback, dispatch, zero refusal/diagnostic/skip.
  Review qualification exception: one isolated `W5gComposedMaterialSurfacePixelTest.eachChildModeHasADisjointSemanticWitness` run may use a 600-second wrapper and `review-long-evidence.init.gradle`. Its first 24 cases took almost the 240-second window; invocation-name filtering is unavailable on this runner. This changes only validation wall time, never test assertions or renderer/proof/oracle/GM limits. Do not extend again automatically; preserve any remaining incomplete evidence.
- [x] Run one bounded full `:kanvas:test` attempt after final product edits. Report every emitted failure by method and incomplete/unreached identities; inherited red/incomplete is not green. Do not repeatedly run an already-known global timeout while iterating.
- [x] Self-review, commit scoped code/tests, full report with RED/GREEN commands and exit evidence. Controller task review then fixes/re-review precede Task 2.

### Task 2: Deferred AA PLUS/SRC_OVER at root and in layers

**Files:**
- Create focused `gpu-plan/.../PlanAaDeferredCompositeV1.kt` and `W6AaDeferredOccurrenceEmitterV1.kt` in `org/graphiks/kanvas/gpu/plan`.
- Modify W4dGeneralPathPlanCompiler, W6aLayerPlanCompiler, PlanPasses, W6aLayerGraphConstruction/Validation and physical/recipe/native-site integration required for a new typed consumer.
- Create focused `gpu-renderer/.../execution/GPUAaDeferredCompositeNativeV1.kt`; integrate W6a materializer/recording/prepared validation without copying the entire materializer.
- Create `kanvas/.../surface/W7AaDeferredBlendSurfacePixelTest.kt`.

**Interfaces:**
- Task 1 coverage law and existing sealed `BlendPlan` are the final composition authority.
- `PlanAaDeferredCompositeV1` captures occurrence, coverage binding, solid material, target/mapping, BlendPlan and destination versions/snapshot. It is not a second material evaluator.
- `W6AaDeferredOccurrenceEmitterV1` owns resource rebinding and producer/snapshot/consumer ordering, receiving selected facts and an explicit target/mapping; root and layer call the same emitter. Task 4 reuses this interface, with final concrete signature documented in Task 2 report.

- [x] RED: public PLUS/SRC_OVER × path/rect × root/plain-layer, no fake layer at root. Include direct triangle, concave/even-odd stencil, alpha0/fractional/1, saturation edge, complementary and overlapping triangles. Rect uses successful analytic lanes where available, with provenance unchanged.
- [x] Introduce generic closed admission before no-layer ownership refusal. Retain old resolved-color route; new source uses canonical-white coverage producer with original consumer blend separate. Only supported Task 2 cells enter the new route.
- [x] Implement typed consumer and common emitter. Read C from resolve alpha once, evaluate solid material once, select existing blend lowering with authenticated clamp. Carry law/coverage-channel/identity through validation/native keys. Snapshot actual target version when required. No alias/readback target as source.
- [x] GREEN: independent pixel assertions for translation/scissor/nonzero layer origin, two sequential consumers plus intervening fixed draw, reversed order, stencil hole and repeat bytes. Hand-derive budget B from all declared physical rows before rendering; B passes, B−1 preserves sentinel and recovers. No capability-refusal substitutes for positive witnesses on this host.
- [x] Replace only baseline permanent-refusal assertions whose exact scenario becomes an admitted PLUS/SRC_OVER cell with positive native pixel witnesses (not deletion). Keep filter/domain/other-mode refusal/recovery coverage; document superseded baseline identities. Re-run the four baseline classes and Task 1 tests, accounting for all 31 original identities and their intentional replacements. Measure PlusMergesAA with unchanged port/config/reference, archive actual/diff and report gain separately from fidelity. Self-review/commit; task review gate.

### Task 3: Complete the Porter-Duff family through the same consumer

**Files:** Deferred selection/consumer/validation from Task 2 and `W7AaDeferredBlendSurfacePixelTest.kt`; existing blend formula authority only where a missing selected contract requires it.

**Interfaces:** Reuse the Task 2 emitter/consumer and Task 1 law. No per-mode pass family. Full kernels stay shared; source scaling only for proved equivalent lowering, destination-read for the others.

- [x] RED missing 11 Porter-Duff cells (CLEAR,SRC,DST,DST_OVER,SRC_IN,DST_IN,SRC_OUT,DST_OUT,SRC_ATOP,DST_ATOP,XOR), path/rect root/layer. Each mode needs a positive nontrivial independent witness; DST specifically preserves nontransparent destination without a write.
- [x] Add closed mode selection and safe composition preserving C. CLEAR/SRC with alpha0 must not cull; fractional destination alpha distinguishes SRC_IN/DST_IN. Retain unsupported advanced/filter/domain guards.
- [x] GREEN small full-buffer fixtures across all13 modes, partial/interior/exterior coverage; test fixed→destination-read→AA sequence and reverse with independent sequential expectations. Re-run Task 1/2 suites and targeted aarectmodes measurement; record distinct remaining blockers without extending geometry arbitrarily.
- [x] Self-review/commit, task review gate. Update matrix status, not global W7 completion.

### Task 4: Picture integration and series convergence evidence

**Files:** W6aLayerGraphConstruction `preparePictureDrawLane`/`appendPlannedDraw` and shared emitter; Picture capture/mapping only if needed for the spec's existing admitted affine/scissor contract; new `kanvas/.../surface/W7AaDeferredPictureSurfacePixelTest.kt`; refactor status/pilotage/README/snapshot.

**Interfaces:** Consume Task 2 sealed occurrence/emitter and Task 3 closed family. Picture paint/restore stays separate from child final blend.

- [ ] RED all13 modes × path/rect × Picture-root/Picture-layer using independent expected pixels, not direct/Picture equality alone. Include same serialized Picture at two translations, different destination contents, nonzero layer origin and integral scissor, both fill rules across fixtures.
- [ ] Add real coverage compiler selection and assembly through shared emitter. Preserve nested occurrence IDs, child order, target-local mapping/culls and fresh destination snapshots. Replace historical in-scope permanent Picture refusal tests with their positive contract; retain out-of-scope refusal/recovery witnesses.
- [ ] GREEN complete advertised104 cells and prior suites; identity accounting, no skip/refusal/capability shortcuts. Run one bounded full suite after final product edits and compare all observed identities to Task 1 run.
- [ ] Measure unchanged631/443 corpus with30s/GM using existing aggregator; inspect target actual/diff, every old image delta and invariant. No reference/threshold/exclusion/domain changes. Preserve failures, timeouts and incomplete evidence.
- [ ] Update refactor durable result/remaining limitations, self-review/commit. Controller final Astra review over complete branch then one fix-wave/scoped re-review, publish draft stacked on #2425 and attach it. W7 remains active.

## Plan self-review

All spec axes map to Tasks2–4; W5 law and oracle migration are Task1.
No task activates downstream admission early. Resource/identity validation
belongs with its executable consumer, not a test-only scaffolding task.
The emitter's concrete internal signature is chosen in Task2 and recorded
before Task4 dispatch; Task4 consumes that interface rather than duplicating it.
User carte blanche supplies delegated design/plan decisions; SDD method remains
the explicit user choice. No further approval loop is introduced.
