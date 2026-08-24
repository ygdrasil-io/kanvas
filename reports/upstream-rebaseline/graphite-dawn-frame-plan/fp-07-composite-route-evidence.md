# FP-07 composite route — Task 9 cutover evidence

**Date:** 2026-08-02
**Task:** Plan Task 9 — Flip `GPUPreparedSurfaceFrameGate` for composites (conditional on Phases 1–3 green)
**Branch:** `codex/graphite-dawn-frame-plan-design` @ `7efc9c43e` (task start)
**Status of the flip:** **WITHHELD** — composites stay legacy-routed until the executor consumes `compositeCommands`. Full evidence below.

---

## 1. Precondition verification (Step 1) — PASSED

All Phase 1–3 tests green before the flip:

```bash
rtk proxy ./gradlew -p ... :gpu-renderer:test \
  --tests "*GPUPreparedCompositeLowererTest" --tests "*GPUPreparedCompositePreflightTest" \
  --tests "*GPUSaveLayerNativeExecutorTest" --tests "*GPUFilterOracleTest" \
  --tests "*GPUPreparedFilterDAGPlannerTest" --tests "*GPUBlendOracleTest" \
  --tests "*GPUPreparedSaveLayerFrameHandlingTest" --no-parallel
# BUILD SUCCESSFUL (35 actionable tasks)

rtk proxy ./gradlew -p ... :kanvas:test \
  --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --no-parallel
# BUILD SUCCESSFUL (39 actionable tasks)
```

The gate condition in the historical FP-07 design (§ Méthodologie: "le cutover du frame gate n'est activé qu'une fois tous les tests des phases 1-3 verts"; recover from Git history) is satisfied after Tasks 1–8.

## 2. The flip applied (Step 2)

Exactly per plan Step 2 on top of the green precondition:

- `GPUPreparedSurfaceFrameGate.kt`: `DrawPicture`/`BeginLayer`/`EndLayer` moved from the `Legacy(Composites)` branch into the `hasVisual = true` branch; `LegacyDisplayOpFamily`/`preparedSurfaceCode()` removed from the gate.
- `GPULegacyImmediatePathAdapter.kt`: `LegacyDisplayOpFamily` reduced to an empty enum; `allowedFamilies = emptySet()`; `familyOrNull = null` for every op (no display family remains on the temporary immediate renderer).
- Legacy-pinning test updates (compile-driven, no pixel expectations touched): `GPUPreparedSurfaceFrameGateTest` (composite fixtures → `Candidate`, `family` field asserts removed), `GPUFramePathApiInventoryTest` (`allowedFamilies == emptySet`), `GPUPreparedTextNoFallbackTest` (`entries == emptyList`), `GPUPreparedSurfaceProductRouterTest` (flush-snapshot replaced the composites legacy-gate case), `GPUPreparedSurfaceProductEntryTest` (flush-snapshot replaced the `BeginLayer` gate-legacy case).
- Flip-attempt diff/stat: 7 files, +135 −93 across main + test (all reverted to HEAD before this evidence file was written).

All five targeted test classes passed with the flip applied.

## 3. Full-suite run (Step 3) — 298 failures, 100 % composite frames

`rtk proxy ./gradlew -p ... :kanvas:test :gpu-renderer:test --no-parallel 2>&1 | tee /tmp/fp09_cutover.log`

- `:gpu-renderer:test` — **all green**.
- `:kanvas:test` — **298 test failures** across exactly 4 classes:

| Test class | Failures | Failure kind |
|---|---|---|
| `GPUAllApiBlendSurfaceTest` | 288 | 203 × `GPUPreparedSurfaceTerminalException` (`unsupported.composite.operation`) + 85 × pixel `AssertionFailedError` |
| `GPUSaveLayerCompositeRegressionTest` | 8 | 7 × pixel `AssertionFailedError` (layer bounds/alpha lost) + 1 × refusal-count regression (`fatalCount` 0 instead of 1) |
| `GPUClipCoverageSurfaceTest` | 1 | `clippedPictureChildUsesColorDodgeComposer` — painted-DrawPicture pixels |
| `GPUClipAdvancedBlendSurfaceTest` | 1 | `remaining high level GPU routes render through their S/G adapters` — `unsupported.composite.operation` Terminal |

**Zero non-composite failures.** Every geometry / text / image / vertices / clip / blend non-composite test stayed green, so the prepared route itself (Tasks 4–8) is not regressed. The `nested_vertices` FP-06 boundary pin (`GPUPreparedSurfaceProductRouterTest.vertices and mesh reaching the legacy route carry the exact composite refusal`) stayed **green** — the cutover did not relax the FP-06 boundary.

## 4. Root cause — the executor does not consume `compositeCommands`

The single root cause for all 298 failures is the documented deferred gap:

- `RecordingContracts.kt` `GPUTaskList.withCompositeCommands(...)` — `// TODO(Task 8/9): compositeCommands is scheduling evidence only until the executor consumes it; nothing renders it yet.`
- `GPUPreparedWindowOutput.attachToFrame` — `// TODO(Task 9): must carry compositeCommands forward or the merged layer commands are dropped here.` (the rebuilt task list drops them).
- Verified by inspection: `GPUPassCommand` (incl. `PrepareLayerTarget`/`RenderLayerChildren`/`CompositeLayer`) has **no consumption path** in `GPUFrameCoordinator`, `GPUBackendRuntimeNative`, or `GPUPreparedSurfaceFrameExecutor` — the executor renders `GPUTask.Render` tasks only. The materialized saveLayer command stream is scheduling evidence, never executed.

Consequence for composite frames admitted as `Candidate` after the flip:

1. **DrawPicture frames render wrong pixels — picture content is dropped.** The prepared flat mapper (`GPUOpMapper.mapCoreOperation`) returns `null` for `DrawPicture` (it is not a core visual), and the emptied legacy allowlist no longer records it — the op is silently skipped. The composite commands that would render the picture are scheduled but never executed. Verified pixel signature: a `[DrawRect(dest), DrawPicture(pic, paint)]` frame renders only the destination rect (e.g. `actual=[31,96,169,160]` = dest premul-linear store) — the picture's rect is absent. 85 pixel failures (`DrawPicture/*` in all contexts + `paintedPictureRestoresThroughItsOuterAlphaClipExactlyOnce`).
2. **saveLayer frames render wrong pixels — layer bounds/alpha/clip semantics are lost.** Flat path renders layer children directly on the surface target. `GPUSaveLayerCompositeRegressionTest` bound tests: `expected=255, actual=187` outside the layer bounds (child painted beyond the layer's device bounds — the `CompositeLayer` command that would apply the bounds is never executed). 7 pixel failures.
3. **saveLayer frames with non-core children terminally refuse.** The composite capture (`GPUPreparedCompositeCapturer.appendOperation`) only lowers `DrawRect`/`DrawRRect`/`DrawPath`/state ops; image/text/atlas/vertices/mesh children refuse `unsupported.composite.operation` (`GPUPreparedCompositeRefusalCodes.OPERATION`). 203 failures — previously-working frames that rendered correctly through the legacy route now terminate.
4. **A loud refusal became a silent drop.** `GPUSaveLayerCompositeRegressionTest.translated DrawPicture with captured clip and bounded saveLayer refuses before encoding` expected `fatalCount == 1` with `unsupported.picture.transformed_layer`; after the flip the picture is silently dropped by the prepared flat path → `fatalCount == 0`. This violates the documented fallback policy ("explicit refusal, never silent").

## 5. Decision (Step 3 requirement)

**The gate flip must NOT ship while the executor ignores `compositeCommands`.**

Reasoning per the task's decision rule ("if composite frames render wrong pixels because the executor ignores compositeCommands, that's a REAL regression — you must wire the executor consumption or keep composites legacy-routed until it's done"):

- Wrong pixels on previously-working composite frames are **confirmed** (categories 1–2 above), and a silent content drop replaces a previously loud refusal (category 4). These are real regressions; updating pixel expectations to accept them would be masking, which the task forbids.
- Wiring the executor consumption is **out of scope**: it requires the native layer-target execution path (texture allocation for `PrepareLayerTarget`, child render pass into the layer target for `RenderLayerChildren`, composite draw with the real blend plan + alpha for `CompositeLayer`, plus carrying the commands through `GPUPreparedWindowOutput.attachToFrame`). None of that machinery exists; Tasks 1–8 deliberately built only the command-stream generation and marked execution as a TODO.

**Action taken:** the flip was reverted; composites remain `LegacyDisplayOpFamily.Composites` in the gate and adapter; all legacy-pinning expectations (gate, inventory, text no-fallback, router, entry tests) are restored to their pre-flip state. The working tree is clean; the full suite is green again (re-run below).

### What must land before the flip can ship

1. Executor consumption of `compositeCommands` (or equivalent task-level execution): `PrepareLayerTarget` → layer texture allocation, `RenderLayerChildren` → child render tasks targeted at the layer texture, `CompositeLayer` → composite draw with the real `GPUBlendPlan` + layer alpha; `GPUPreparedWindowOutput.attachToFrame` must carry the commands forward (TODO at `GPUPreparedWindowOutput.kt:97`).
2. Flat-render elision for composite frames (builder TODO at `GPUPreparedSurfaceFrameBuilder.kt:118`: "for composite-only frames the flat child render must be elided when composite commands are scheduled; mixed composite+visual frames need explicit topology handling").
3. Decision + tests for frames the capture cannot lower (non-core children, transforms, filters): keep the explicit terminal refusal path and re-point the blend-matrix SAVE_LAYER expectations from `Legacy` to the observed terminal codes — only after 1–2 land and the pixel comparisons are reference-verified.

## 6. Reference comparison — fp-07 cutover branch (34d64799f)

The reference cutover (fp-07 branch, flipped early, 561 failures) treated `BeginLayer`/`EndLayer` as neutral ops and `DrawPicture` as `hasVisual`, and added `DrawPicture`/`BeginLayer`/`EndLayer` to `hasTerminalPreparedFamily()`. The 298 failures observed here reproduce the same root cause (executor gap) at a smaller scale because Tasks 1–8 completed the command-stream wiring; the pixel regressions are identical in kind. This branch's approach (flip with evidence-only commands) is therefore not a viable reference for shipping the cutover.

## 7. Verification after revert

Full suites re-run after the revert (flip removed, tree == HEAD + this evidence file):

```bash
rtk proxy ./gradlew -p ... :kanvas:test :gpu-renderer:test --no-parallel
```

- `:kanvas:test` — green except one occurrence of the known order-dependent flake
  `PipelineTypesTest > RuntimeEffect compile fails validation` (wgsl4k hook-order JVM flake,
  documented in `fp-06-prepared-vertices-mesh-route.md` § Known unrelated flakes; passes in
  isolation). **Zero composite-related failures** — all 298 flip failures are gone.
- `:gpu-renderer:test` — green except the historical pre-existing baseline
  `GPURendererPackageBoundaryTest > gpu renderer production source satisfies package boundary rules`
  (the 20 historical package cycles / 0 rule violations around `WgslReflection.kt`'s
  `org.graphiks.wgsl.proc` import — documented in `2026-06-29-gpu-renderer-pre-existing-test-failures.md`
  and unchanged in fp-06's evidence). Not composite-related.

## 8. Evidence inventory

- Full failure log (flip applied): `/tmp/fp09_cutover.log` (299 FAILED lines = 298 tests + 1 gradle task line).
- Failure XMLs (flip applied, regenerated during the run): `kanvas/build/test-results/test/TEST-...GPUAllApiBlendSurfaceTest.xml`, `...GPUSaveLayerCompositeRegressionTest.xml`, `...GPUClipCoverageSurfaceTest.xml`, `...GPUClipAdvancedBlendSurfaceTest.xml`.
- Precondition runs: both `BUILD SUCCESSFUL` (see §1).
- Reference cutover: `rtk git show 34d64799f` (fp-07 branch).

## 9. Task 17 re-flip (fp-07 cutover applied with evidence-based expectations)

Task 17 re-applied the composite cutover now that Task 15's executor materialization landed:
the gate routes DrawPicture/BeginLayer/EndLayer to `hasVisual`, the legacy adapter family is
empty, and the router treats the three ops as terminal-family members. The full-suite triage
was repeated with the Task 15/16 machinery present; every bounded-saveLayer pixel test now
renders through the prepared route, and every unsupported topology refuses loudly with a
documented code.

### 9.1 Precondition (Tasks 1–16 green)

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel
```
- `:gpu-renderer:test` — green except the pre-existing baseline
  `GPURendererPackageBoundaryTest` (historical package cycles; unchanged on a clean base).
- `:kanvas:test` — green.

### 9.2 Real regressions fixed in Task 15/16 code (not remasked)

Post-flip, bounded saveLayer pixel tests refused with `invalid.preflight.resource_undeclared`
(the composite commands declared a raw scope label as the composite parent) and then
`invalid.prepared-surface.layer-target` (no layer-targeted children render pass existed in
the surface path). Root causes and fixes:

1. **Composite parent label** — `GPUPreparedCompositeLowerer` now takes `sceneTargetLabel`
   (the real surface target ref) and maps saveLayer parents to `layer-target:<scopeId>`;
   `GPUPreparedSurfaceFrameTaskListBuilder.handleSaveLayer` passes the frame's targetId.
2. **Layer children renders** — the surface path previously elided every covered operation
   from the flat pipeline, so the executor never received a `RenderPassStep` targeting each
   `PrepareLayerTarget` label. The frame builder now maps covered core children as ordinary
   visuals (`GPUOpMapper` records `commandIdsByOperationIndex`), then
   `GPUPreparedSurfaceFrameTaskListBuilder.splitCompositeChildrenRenders` partitions the
   flat render into a scene render plus one `GPUTask.Render(target = layer-target:<scopeId>)`
   per layer, retargeted to the RGBA8Unorm layer ABI (structural pipeline key, target-state
   hash, `PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION`) with `layer-composite-order`
   dependencies; the readback is re-pointed after the final layer render.
3. **Premultiplied layer composite** — the prepared-image composite shader converts sampled
   textures to straight alpha (`sampled.rgb * sampled.a`), which double-multiplies premul
   layer children. The layer composite now uses a new `premultipliedSource` ABI flag
   (`flags.x == 2u`) so the layer texture is sampled as premultiplied; the image path is
   unchanged.
4. **Layer-bounds clipping** — bounded saveLayers clip their children to the layer's device
   bounds: each layer child's scissor is intersected with the layer bounds (mapped from the
   captured local bounds through the layer transform at BeginLayer) via a `ScissorOnly` clip
   plan, with the semantic scissor + `clipExecutionPlanIdentity` updated consistently.
   Fully-offscreen children are dropped from the layer render.
5. **Empty layers** — layers with no children or fully offscreen device bounds elide their
   covered children from the flat mapper (the frame's uniform slab must exactly cover the
   accepted packets) and drop their composite command triplets, so the parent is untouched.
6. **Nested layers** — the frame builder refuses nested saveLayer scopes with the preflight's
   documented `unsupported.prepared-surface.layer-nesting` code at the builder boundary (the
   capture's expanded-operation indices are sublist-relative, so the split range mapping is
   only exact for flat topologies).

Executor evidence: `GPUWgpu4kLayerTargetCompositeSmokeTest` (Task 15) stays green with the
shader change, and the bounded saveLayer pixel tests below now compare CPU vs GPU pixels with
exact tolerances.

### 9.3 Re-pointed documented refusals (observed terminal codes)

- `GPUSaveLayerCompositeRegressionTest` (27 tests): 10 pass with real pixels
  (bounded/translated/scaled/partially-offscreen/empty layers + legacy recorder unit tests);
  17 re-pointed to loud refusals — `unsupported.layer.bounds_unbounded` (unbounded
  saveLayers), `unsupported.composite.clip` (clips inside layer scopes),
  `unsupported.composite.operation` (DrawColor children, non-finite transforms),
  `unsupported.surface.prepared.mixed-composite-topology` (picture topologies the composite
  route cannot cover), `unsupported.prepared-surface.layer-nesting` (nested layers).
- `GPUAllApiBlendSurfaceTest` (1864 tests): the SAVE_LAYER blend matrix re-pointed from
  `Legacy`/`LegacyRefused` to the observed terminal codes — `bounds_unbounded` for
  DrawRect/DrawRRect/DrawPath/DrawPicture (unbounded fixture layers),
  `composite.operation` for the remaining APIs (DrawImage/DrawText/DrawColor/Clear/points/
  DRRect/atlas/vertices inside layer scopes), `mixed-composite-topology` for the painted
  DrawPicture cases in every clip context; the painted-picture named test re-pointed to the
  same terminal.
- `GPUClipCoverageSurfaceTest` (4) + `GPUClipAdvancedBlendSurfaceTest` (2): re-pointed to
  `composite.clip` / `composite.operation` / `mixed-composite-topology` terminal assertions.

**Task 17 follow-up (covered DrawPicture in saveLayer scopes):** an unpainted DrawPicture
inside a bounded saveLayer scope is refused at the capture boundary with
`unsupported.composite.operation` (like every other non-core child; FP-06 pattern) instead
of being expanded into the layer. The expansion silently dropped the picture content — the
flat mapper never maps picture-expanded children (`commandIdsByOperationIndex` records only
top-level mapped ops), so the covered children rode no commands — and the picture-only-layer
case died on the internal `invalid.prepared-surface.layer-target` invariant instead of a
documented refusal. Re-pointed: `GPUPreparedCompositeFrameRouteIntegrationTest`
`draw picture in composite frame is not silently dropped` (Ready → `composite.operation`
refusal) and added the mixed rect+picture route case; `GPUPreparedCompositeCaptureSemanticTest`
gained picture-only-layer and mixed-layer refusal cases (19 tests). Root-level and painted
pictures are unchanged (still `mixed-composite-topology` at the builder boundary), so
`GPUAllApiBlendSurfaceTest` / `GPUClipCoverageSurfaceTest` /
`GPUSaveLayerCompositeRegressionTest` needed no re-pointing.

### 9.4 Final verification

```bash
rtk proxy ./gradlew :kanvas:test :gpu-renderer:test --no-parallel
```
- `:kanvas:test` — **green, 3228 tests, 0 failures** (all composite, blend, clip, saveLayer,
  gate, router, entry, inventory, text-no-fallback, and integration suites).
- `:gpu-renderer:test` — green except the pre-existing baseline
  `GPURendererPackageBoundaryTest` (documented in
  `2026-06-29-gpu-renderer-pre-existing-test-failures.md`; reproduces on a clean base and is
  not composite-related).
- Guards: `GPUPreparedSurfaceProductRouterTest` (nested_vertices pin), FP-06 guards,
  Task 16 elision (`GPUPreparedCompositeFrameRouteIntegrationTest`), Task 15 smoke
  (`GPUWgpu4kLayerTargetCompositeSmokeTest`) — all green.

### 9.5 Evidence inventory (Task 17)

- Full-suite failure log at flip time: `/tmp/fp17_cutover.log` (581 failures, all loud
  `GPUPreparedSurfaceTerminalException` refusals; zero silent drops in that log).
- **Task 17 follow-up correction:** the flip log's "zero silent drops" claim did not cover
  the covered-unpainted-`DrawPicture`-in-saveLayer shape. That shape rendered fatal=0 with
  zero diagnostics while the picture content was absent (pixel evidence: pure red, no blue;
  legacy route rendered it correctly), and the picture-only-layer variant died on the
  internal `invalid.prepared-surface.layer-target` invariant. Fixed by refusing the shape at
  the capture boundary (`unsupported.composite.operation`) — it now refuses loudly with a
  documented code, and the picture-in-layer shape no longer has any silent or
  internal-invariant path.
- Post-fix full-suite run: `:kanvas:test` 3228 tests green (including the re-pointed route
  expectations and the new capture refusal cases); `:gpu-renderer:test` 3256 tests with the
  single pre-existing boundary baseline.
- Task 15 executor evidence (unchanged, still green): `GPUWgpu4kLayerTargetCompositeSmokeTest`.
