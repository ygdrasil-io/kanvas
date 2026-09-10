# W5b Common Final Blend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make final draw blend a single sealed plan-first authority shared by every W5a-promoted geometry family, including exact destination-read ordering, and close the 45 historical `DrawPoint` cells.

**Architecture:** `:gpu-plan` owns a handle-free `BlendPlan` and produces it together with the W5 material reference before `RenderGraph.Ready`. `:gpu-renderer` lowers that plan to the existing fixed-function, `NoOp`, or GPU destination-copy/formula machinery without reclassifying the public blend mode. Every behavioral gate is a public `Surface`/`Canvas`/`Picture` pixel or refusal/recovery test; production review, not infrastructure assertions, proves the absence of a parallel blend authority.

**Tech Stack:** Kotlin/JVM, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGSL, JUnit 5.

**Spec:** `refactor/specs/2026-09-09-w5-material-graph-design.md`, especially sections 5.3–5.6, 6, 10, 13–18.

## Global Constraints

- Branch `codex/w5b-blends` is stacked on `codex/w5a-solid-opacity`; W5b gets its own PR targeting the W5a branch.
- W5b changes no W4 geometry, coverage, clip, tessellation, point capacity or AA topology.
- Geometry values remain in `:math:geometry`; transforms remain in `:math:matrix`; every new public numeric name uses I32/I64/F32/F64.
- The effective source remains linear premultiplied RGBA. The final result is `destination + coverage * (blend(source, destination) - destination)`.
- `GPUBlendPlanner` semantics are moved behind the sealed plan-first boundary; no draw family may independently reconstruct or reclassify the final blend.
- `DST` is `NoOp`. Fixed-function is used only when algebraically exact for the coverage form. All other admitted modes use a GPU destination snapshot and formula; no CPU readback or fallback is allowed.
- `PLUS` is fixed-function on clamped single-sample full/scissor coverage and destination-read on scalar coverage. This is the canonical W5b classification.
- Destination snapshots are keyed by target identity, device generation and monotonically increasing `DestinationVersionI64`; a snapshot never survives an intervening target write.
- Snapshot extents are conservative draw/clip intersections aligned to the physical copy-row requirement. Unknown bounds copy the full target.
- Missing capability, overflow or budget failure is typed, terminal after W5 ownership and transactional before native allocation.
- `WgslFloatEnvelopeV1` remains the numerical authority. A pixel gate accepts only a singleton or two adjacent RGBA8 codes derived analytically; wider/non-adjacent results are `Unbounded`, never widened by empirical tolerance.
- AA4 stays an authentic capability skip/refusal when unavailable. No capability, sample count or success is fabricated.
- No new source-shape, reflection, private/internal, call-count, scope, counter, bind-group-byte, uniform-byte or other infrastructure test may serve as proof.
- Fonts, codecs, GM/dashboard/render/baseline, `:integration-tests:skia` and `jpg-color-cube` are outside all W5b gates. Text uses only the existing already-resolved A8 fixture; images remain deferred.
- The only durable tracking files are this plan, `refactor/README.md`, and `refactor/waves/W05-material-graph/status.md`; agent briefs/reports remain under the ignored `.superpowers/sdd` workspace.

---

### Task 1: Seal the plan-first final blend authority

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/FinalBlendPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bBlendPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendSurfacePixelTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendCpuOracle.kt`

**Interfaces:**
- Consumes: `DrawNode.blend`, `CoveragePlan`, `SamplePlan`, `PlanCapabilitySnapshot`, `MaterialPlanTable`, `MaterialPlanRef`.
- Produces: sealed `BlendPlan` variants `LegacySrcOverV1`, `FixedFunctionV1`, `DestinationReadV1`, and `NoOpV1`; refusals remain `EffectiveMaterialPlanner.Result.Refused` and can never appear inside a `Ready` plan. Also produces `EffectiveMaterialPlanner.Result.Ready(table, root, blend)`, `DestinationVersionI64`, `PlanResourceRole.DestinationSnapshot`, `PlanResourceUsage.StorageRead`, `PlanOperationCapability.StorageBuffer`, and versioned capability limits named with I32/I64 suffixes.

- [ ] Add a public RED `Surface` test with Solid/Opacity Rect draws covering one non-`SRC_OVER` fixed-function mode and `DST`. The test must observe nontrivial alpha and paint order through `Picture`; add mutation counterfactuals only for mutable captured inputs. Use an independent bounded pixel oracle.
- [ ] Run `rtk ./gradlew :kanvas:test --tests '*W5bBlendSurfacePixelTest*' --no-parallel`; verify RED is a W5a `unsupported.material.w5a.draw-state`/legacy-routing outcome, not a fixture error.
- [ ] Replace the `BlendPlan { SrcOver }` enum with a sealed handle-free plan. The fixed-function variant stores backend-neutral factors/operations and coverage encoding; the destination-read variant stores the exact formula identity and required source/coverage contract; `LegacySrcOverV1` exists only for unchanged W3/W4 witnesses.
- [ ] Make `EffectiveMaterialPlanner` return material and final blend together. It must keep Solid/Opacity normalization unchanged, accept every standard `BlendNode.Mode`/non-custom `BlendNode.Paint`, reject custom blender state, and never fold final draw blend into the source material DAG.
- [ ] Extend `PlanCapabilitySnapshot` with the exact binding-size/count facts from spec section 5.4. Every newly introduced device limit is nullable/absent until a production adapter supplies an authentic value; absence means capability unavailable and no synthetic default is permitted. Add `StorageBuffer`, `StorageRead`, `DestinationSnapshot`, and frame-local destination texture planning without adding backend handles.
- [ ] Add `W5bBlendCpuOracle` as a public-test-only independent implementation of section 10.1 over linear premultiplied colors. Reuse `WgslFloatEnvelopeV1Oracle` for interval/attachment closure; no renderer formula helper may be imported.
- [ ] Lower the fixed-function and `NoOp` variants far enough through the existing W5a execution boundary for the new public cells to turn GREEN. A `DestinationReadV1` may be planned but remains a terminal typed refusal until Task 2; no public destination-read test is committed as passing evidence in Task 1.
- [ ] Run the fixed-function/`DST` RED test to GREEN plus `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin`, and the existing W5a public suite. Commit `feat(gpu-plan): seal W5b final blend authority`.

### Task 2: Complete destination-read lowering without a second semantic planner

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/FinalBlendPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bBlendPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendFormulaLibrary.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/GPUBlendFormulaProgramLibrary.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationSnapshotGrouping.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendSurfacePixelTest.kt`

**Interfaces:**
- Consumes: sealed `org.graphiks.kanvas.gpu.plan.BlendPlan` plus the existing W5a source-stage program/bindings.
- Produces: one renderer execution descriptor containing either exact native blend state, no color write, or one registered destination formula/layout. `GPUBlendPlanner` becomes a compatibility adapter for legacy routes and may not re-plan W5b draws.

- [ ] Add RED public Rect and fractional Rect tests proving that the same captured source executes under destination-read modes, including scalar coverage on a fractional edge. Retain the fixed-function and `DST/NoOp` cells from Task 1 as regressions. RRect remains in Task 4, where its compiler acquires W5b ownership.
- [ ] Verify RED through `Surface.render()` and exact public pixels; do not assert shader text, pipeline keys, bindings, counters or route scopes.
- [ ] Implement `W5bBlendPlanLowerer` as an exhaustive mapping from the sealed plan to existing native state/formula registries. Validate plan/formula version and source/coverage ABI before allocation; never call `GPUBlendPlanner.plan()` for a W5b draw.
- [ ] Generalize W5a fragment composition so the source DAG remains unchanged while the authenticated target tail comes from the W5b blend plan. Destination-read adds its texture/sampler group without renumbering geometry group 0 or raw material group 1; the new ABI version must be explicit.
- [ ] Keep fixed-function and destination-read color math sourced from one formula registry. Remove only duplicate W5b classification tables; preserve legacy adapters needed by non-migrated branches.
- [ ] Run the W5b public tests, W5a public tests and targeted W3/W4 public pixel tests; commit `feat(gpu-renderer): lower sealed W5b blend plans`.

### Task 3: Close the 45 historical DrawPoint cells

**Files:**
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendCpuOracle.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUCorePrimitiveSemanticBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`

**Interfaces:**
- Consumes: three ordered public `drawPoint` commands and the W5b blend/source plans from Tasks 1–2.
- Produces: exact `Render(version n) -> Copy(version n) -> RenderConsumer(version n+1)` sequences for visible destination-read points without replacing core point geometry or its capacity contract.

- [ ] Add a filterable public method `GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix` that executes exactly the 45 cells: `{PLUS, MULTIPLY, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY}` × `{UNCLIPPED, SCISSOR, ALPHA_MASK}`. Each cell keeps the existing three point commands and compares observable pixels to the independent oracle.
- [ ] Change the generic matrix expectation for those same DrawPoint cells from terminal refusal to prepared rendering. Keep all unrelated API expectations unchanged.
- [ ] Run only the filterable DrawPoint gate and verify all 45 cells RED on the current direct-geometry/preflight refusal or wrong pixel; no font or image fixture may execute in this command.
- [ ] Preserve the core point fan/hairline authority from W5a. Seal shared V/I/U resources once for the ordered multi-render frame, and issue a distinct destination version for every visible target write. Do not convert points to paths and do not weaken the 64-point capacity boundary.
- [ ] Materialize each required GPU copy/formula consumer with conservative bounds and exact row-pitch/budget accounting. A culled point produces neither a write version nor a snapshot.
- [ ] Verify the 45 cells GREEN with singleton/two-adjacent oracle sets, then run W5a Point/Points regressions and the 512/513 W5a resource-bound tests. Commit `feat(gpu-renderer): close W5b DrawPoint blend matrix`.

### Task 4: Promote Rect, RRect, Path fill, stroke and hairline blends

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4cPathFillPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dPathStrokePlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4eClipPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aCompositePlanCompiler.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendSurfacePixelTest.kt`

**Interfaces:**
- Consumes: existing W3/W4 geometry/coverage plans and sealed W5b final blend.
- Produces: W5b successor capabilities carrying both `MaterialV1` and non-legacy `BlendPlan`, including ordered composite lanes and atomic stencil producer/cover pairs.

- [ ] Add RED public cells for Rect, fractional Rect, RRect, direct Path fill, stencil Path fill, stroke and hairline. Each family covers one fixed-function mode, `DST`, and one destination-read mode with nontrivial alpha; at least one Picture mutation witness must remain visible for each mutable geometry family.
- [ ] Verify RED as missing W5b ownership/refusal, not by accepting legacy pixels.
- [ ] Thread the sealed blend through each compiler and draw snapshot. Producer-only stencil/mask passes write no material and have no final blend; only the color consumer carries the W5b authority.
- [ ] Extend the composite plan interning/remapping to preserve blend refs/order without merging distinct destination versions. Geometry, clip and W4 budgets remain byte-for-byte authoritative except for explicit destination snapshot resources.
- [ ] Run all W5b geometry cells plus W3/W4/W5a public pixel regressions; commit `feat(gpu-plan): promote W5b geometry blend lanes`.

### Task 5: Promote already-resolved A8 text and Vertices/Mesh

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextLowerer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedTextMaterialBridge.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextSessionCache.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedVerticesRenderRunMaterializer.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendSurfacePixelTest.kt`

**Interfaces:**
- Consumes: admitted W5a A8 text and prepared vertices payloads plus the sealed W5b blend.
- Produces: the same source material/coverage composition followed by fixed-function, `NoOp`, or destination-read final blend; Mesh without program remains the public vertices path.

- [ ] Add RED public tests using the existing already-resolved A8 glyph fixture, Vertices with/without colors, and Mesh without program. Each has fixed-function, `DST`, and destination-read cells; mutate glyph lists/vertex arrays after capture and require unchanged pixels.
- [ ] Verify RED without invoking font generation, font suites, codec or image loading.
- [ ] Carry the sealed W5b plan through prepared text/vertices payloads and reuse the common lowerer. Vertex colors modulate the W5 source before the final draw blend; A8 coverage multiplies the source before the final blend equation.
- [ ] Preserve existing typed refusal precedence for color glyphs, invalid clips/transforms and `MeshProgram`; those paths do not acquire W5b ownership.
- [ ] Run W5b text/vertices tests plus W5a A8/Vertices/Picture mutation regressions; commit `feat(gpu-renderer): consume W5b blends in prepared families`.

### Task 6: Prove mixed-frame ordering, budgets and recovery

**Files:**
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendCpuOracle.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bBlendPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationSnapshotGrouping.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`

**Interfaces:**
- Consumes: every W5b-promoted family and destination version/resource plans.
- Produces: one frame-wide ordered material/blend execution graph with transactional refusal and recovery on the same public Surface/runtime/backend.

- [ ] Add RED mixed frames interleaving fixed-function, destination-read and `DST` across Rect → Point → RRect → Path → A8 → Vertices. Observe at least one pixel per middle command, one order counterfactual and every mutable captured source.
- [ ] Add a public budget/capability refusal reachable from valid W5b input, followed by a valid W5b render on the same Surface when the public API permits it; otherwise use the same runtime/backend with distinct Surfaces and state that exact limitation. The refusal diagnostic and recovery pixels are the only assertions.
- [ ] Seal destination versions and snapshot lifetimes before `Ready`; reject missing binding/texture/sampler/storage capability and I64 overflow before any native allocation. Preserve affine destination correlation in the oracle.
- [ ] Ensure rollback owns every created texture/view/sampler/buffer/pipeline until completion and quarantines failed closes through existing pool journals. Do not add failure injection or lifecycle counters.
- [ ] Run the complete W5b public suite and W5a/W3/W4 public regressions; commit `test(w5b): prove mixed blend ordering and recovery`.

### Task 7: Remove migrated blend fallbacks and update durable status

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`
- Modify: `refactor/waves/W05-material-graph/status.md`
- Modify: `refactor/README.md`

**Interfaces:**
- Consumes: compiler-authenticated W5b ownership and complete public gate evidence.
- Produces: terminal typed W5b refusal after ownership, legacy continuation only before ownership, and the authoritative W5b status/next-W5c scope.

- [ ] Audit every production final-blend classification call site. W5b-promoted paths must consume the sealed plan; legacy-only paths are documented in the status and may keep the compatibility adapter until their `H` deadline.
- [ ] Remove only fallbacks now owned by W5b. A `GapNotMigrated` before ownership stays legacy; an error after authenticated W5b ownership is terminal and cannot rescan public paint or substitute `SRC_OVER`/transparent/source-only behavior.
- [ ] Update the 45-cell ledger entry from historical failure to closed only after the filterable public gate passes all 15 modes × 3 contexts. Record the exact command and results.
- [ ] Update W05 status and README with every promoted family, exact AA4 skips, numerical `Unbounded` non-gates, capability/budget gaps, the pre-existing CoreAnalytics warning if still emitted, and W5c as the next stacked slice.
- [ ] Run `rtk git diff --check`, targeted compiles and the complete W5b/W5a/W3/W4 public gates; commit `refactor(blend): remove W5b final-blend fallbacks`.

### Task 8: Independent reviews, final verification and stacked PR

**Files:**
- Modify only files required by verified review findings.

**Interfaces:**
- Consumes: the full `codex/w5a-solid-opacity..codex/w5b-blends` range, plan, spec, task ledger and public verification evidence.
- Produces: two independent `READY` reviews, a clean verified branch, and a W5b PR targeting `codex/w5a-solid-opacity`.

- [ ] Ask a fresh Sol agent for a global specification review against this plan and the W5 spec. Route every Critical/Important finding through one responsible non-Sol implementer and a scoped Sol re-review.
- [ ] Ask a different fresh Sol agent for final code quality review. Iterate the single permitted final fix wave until no Critical/Important finding remains; record minors explicitly.
- [ ] Run fresh, non-parallel JVM compiles and the complete public W5b/W5a plus bounded W3/W4 gates. Force test execution when Gradle reports `UP-TO-DATE`.
- [ ] Confirm `:kanvas` target inventory; run JS only if an authentic JS target exists. Do not invent an infrastructure substitute.
- [ ] Do not run font, codec, GM/dashboard/render/baseline, Skia integration or `jpg-color-cube` suites.
- [ ] Run `rtk git diff --check`, verify a clean worktree and inspect the full commit range from `codex/w5a-solid-opacity`.
- [ ] Push `codex/w5b-blends` and create/update its PR with base `codex/w5a-solid-opacity`, exact public test counts, authentic skips, review verdicts, and deferred W5c–W5h scope.

## Verification Commands

```bash
rtk ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --tests '*GPUPlanSurfacePixelTest*' --no-parallel --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix' --no-parallel --rerun-tasks
rtk git diff --check
```

The final test names may be narrowed only to the exact public classes/methods created above. No infrastructure suite may replace a missing public behavior gate.
