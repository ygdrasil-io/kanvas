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

- [x] Add a public RED `Surface` test with Solid/Opacity Rect draws covering one non-`SRC_OVER` fixed-function mode and `DST`. The test observes nontrivial alpha and true reversed-order replay through `Picture`, with an independent bounded pixel oracle.
- [x] Run `rtk ./gradlew :kanvas:test --tests '*W5bBlendSurfacePixelTest*' --no-parallel`; verify RED is a W5a legacy-routing outcome, not a fixture error.
- [x] Replace the `BlendPlan { SrcOver }` enum with a sealed handle-free plan carrying backend-neutral factors/operations, coverage encoding and destination formula identity.
- [x] Make `EffectiveMaterialPlanner` normalize material and final blend together, preserve Solid/Opacity normalization, reject custom blender state and keep final draw blend outside the source DAG.
- [x] Extend `PlanCapabilitySnapshot` with nullable authentic binding-size/count facts and add `StorageBuffer`, `StorageRead`, `DestinationSnapshot` vocabulary without backend handles.
- [x] Add the independent public-test W5b oracle through `WgslFloatEnvelopeV1Oracle`, without renderer formula imports or empirical tolerance.
- [x] Lower fixed-function and `NoOp` through the W5a execution boundary; destination-read remained typed-refused until Task 2.
- [x] Verify two W5b public tests, W5a public regressions and compiles; commits `e1a05bafd`, `effefbabe`, `3038e46e2`, `c6252ba05`, `39a6f9177`.

### Task 2: Complete destination-read lowering without a second semantic planner

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/FinalBlendPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bDestinationGraph.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bBlendPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt`
- Reuse unchanged: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendFormulaLibrary.kt`
- Reuse unchanged: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/GPUBlendFormulaProgramLibrary.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitivePipelineDescriptor.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/W5bPreparedFrameWitnessV3.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt`
- Reuse unchanged: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationSnapshotGrouping.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/WgslFloatEnvelopeV1Oracle.kt`

**Interfaces:**
- Consumes: sealed `org.graphiks.kanvas.gpu.plan.BlendPlan` plus the existing W5a source-stage program/bindings.
- Produces: one renderer execution descriptor containing either exact native blend state, no color write, or one registered destination formula/layout. `GPUBlendPlanner` becomes a compatibility adapter for legacy routes and may not re-plan W5b draws.

- [x] Add RED public integral Rect tests for full/scissor destination-read and retain Task 1 fixed-function/`DST` regressions. Scalar coverage is deferred to Task 3's Point × `ALPHA_MASK`; fractional Rect and RRect remain in Task 4 with their geometry compilers.
- [x] Verify RED through `Surface.render()` and exact public pixels; do not assert shader text, pipeline keys, bindings, counters or route scopes.
- [x] Implement `W5bBlendPlanLowerer` as an exhaustive mapping from the sealed plan to existing native state/formula registries. Validate plan/formula version and source/coverage ABI before allocation; never call `GPUBlendPlanner.plan()` for a W5b draw.
- [x] Generalize W5a fragment composition so the source DAG remains unchanged while the authenticated target tail comes from the W5b blend plan. Destination-read adds its texture/sampler group without renumbering geometry group 0 or raw material group 1; ABI v3 is explicit.
- [x] Keep fixed-function and destination-read color math sourced from one formula registry. Preserve legacy adapters needed by non-migrated branches.
- [x] Run eight W5b public tests, 45 selected W5a public pixel tests (44 pass, one authentic AA4 skip), compiles and targeted public regressions; commits `57bdd4ae3`, `4b4c091b0`, `7aab83979`.

### Task 3: Close the 45 historical DrawPoint cells

**Files:**
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5bBlendCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/WgslFloatEnvelopeV1Oracle.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUCorePrimitiveSemanticBuilder.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bDestinationGraph.kt`
- Create or modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bCorePrimitiveGraph.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bBlendPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4eClipGraphLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/W5bPreparedFrameWitnessV3.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify as required inside the existing W5b direct pipeline/cache cone: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitivePipelineDescriptor.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`

**Interfaces:**
- Consumes: three ordered public `drawPoint` commands and the W5b blend/source plans from Tasks 1–2.
- Produces: exact `Render(version n) -> Copy(version n) -> RenderConsumer(version n+1)` sequences for visible destination-read points without replacing core point geometry or its capacity contract.

- [x] Add a filterable public method `GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix` that executes exactly the 45 cells: `{PLUS, MULTIPLY, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY}` × `{UNCLIPPED, SCISSOR, ALPHA_MASK}`. Each cell keeps the existing three point commands and compares observable pixels to the independent oracle.
- [x] Change the generic matrix expectation for those same DrawPoint cells from terminal refusal to prepared rendering. Keep all unrelated API expectations unchanged.
- [x] Run only the filterable DrawPoint gate and verify all 45 cells RED on the current direct-geometry/preflight refusal or wrong pixel; no font or image fixture may execute in this command.
- [x] Preserve the core point fan/hairline authority from W5a. Seal shared V/I/U resources once for the ordered multi-render frame, and issue a distinct destination version for every visible target write. Do not convert points to paths and do not weaken the 64-point capacity boundary.
- [x] Keep W4e as the producer/owner of alpha-mask coverage and resources. The W5b scalar consumer ABI receives that sealed coverage, evaluates `blend(source,destination)`, then returns `destination + coverage * (blended - destination)`; it must not synthesize a scissor, premultiply only the source, or reuse clip handles as destination snapshots.
- [x] Materialize each required GPU copy/formula consumer with conservative bounds and exact row-pitch/budget accounting. A culled point produces neither a write version nor a snapshot.
- [x] Prove ordering, versions, scalar coverage and culling only through the filterable 45-cell public pixel gate and W5a public regressions. Do not add structural/order/witness tests or inspect internal tasks, counters, scopes, bind groups or shader text.
- [x] Verify the 45 cells GREEN with singleton/two-adjacent oracle sets, then run W5a Point/Points regressions and the 512/513 W5a resource-bound tests. Commit `feat(gpu-renderer): close W5b DrawPoint blend matrix`.

Task 3 est close par `66dcceffc`, `f7a37d704`, puis les corrections de review `3573d080b` et `3bf122590`; le gate public est GREEN45. La vérification finale couvre 20 méthodes JUnit sans échec, erreur ni skip, incluant les régressions W5b, W5a Point/Points et les bornes exactes 64/512/513. La re-review indépendante finale est `READY`, sans finding.

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

- [x] Add RED public cells for Rect, fractional Rect, RRect, direct Path fill, stencil Path fill, stroke and hairline. Each family covers one fixed-function mode, `DST`, and one destination-read mode with nontrivial alpha; at least one Picture mutation witness must remain visible for each mutable geometry family.
- [x] Verify RED as missing W5b ownership/refusal, not by accepting legacy pixels.
- [x] Thread the sealed blend through each compiler and draw snapshot. Producer-only stencil/mask passes write no material and have no final blend; only the color consumer carries the W5b authority.
- [x] Extend the composite plan interning/remapping to preserve blend refs/order without merging distinct destination versions. Geometry, clip and W4 budgets remain byte-for-byte authoritative except for explicit destination snapshot resources.
- [x] Run all W5b geometry cells plus W3/W4/W5a public pixel regressions; commit `feat(gpu-plan): promote W5b geometry blend lanes`.

Final verification (2026-09-11): 107 exact public JUnit methods selected, 105 passed, two authentic AA4 capability skips, zero failures/errors; `W5bBlendSurfacePixelTest` 31/31 and the historical DrawPoint GREEN45 gate pass. Targeted `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas` and test compiles pass. Rect/RRect, narrow and General Paths, ordered native composites, and retained W4e hard mask/inverse consumers carry the sealed final blend; producer-only passes remain source-free. The W4e scalar witness preserves its original 2×2 producer and R8 mask quantization. W4a/W5a analytic Rect lanes compose with W5b destinations, W4e culls `DST/NoOp` before clip/inverse preparation, and General successor identities include the ordered pre-seal blend facts. Existing AA4 refusal and Picture clip-playback limitation remain unchanged. Independent review and three scoped re-reviews are `READY`, with no Critical, Important or Minor finding, through commit `9094996939b2cbc6bd1f485e63522abf4b5a4bfc`.

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

- [x] Add RED public tests using the existing already-resolved A8 glyph fixture, Vertices with/without colors, and Mesh without program. Each has fixed-function, `DST`, and destination-read cells; mutate glyph lists/vertex arrays after capture and require unchanged pixels.
- [x] Verify RED without invoking font generation, font suites, codec or image loading.
- [x] Carry the sealed W5b plan through prepared text/vertices payloads and reuse the common lowerer. Vertex colors modulate the W5 source before the final draw blend; A8 coverage multiplies the source before the final blend equation.
- [x] Preserve existing typed refusal precedence for color glyphs, invalid clips/transforms and `MeshProgram`; those paths do not acquire W5b ownership.
- [x] Run W5b text/vertices tests plus W5a A8/Vertices/Picture mutation regressions; commit `feat(gpu-renderer): consume W5b blends in prepared families`.

Task 5 est close par `8c2ba473d`, `ececa76c2`, `2344686c6`, puis la correction de review `c4a93234d`. Les preuves publiques finales couvrent 50 méthodes JUnit sans échec, erreur ni skip : W5b 39/39, dix régressions W5a A8/Vertices/Mesh et le gate historique DrawPoint GREEN45. Quatre compilations main forcées passent. Le clear d'une frame préparée commençant par un lecteur de destination dépend désormais du `BlendPlan.DestinationReadV1` scellé, sans mirror set de modes; les trois régressions retained-session `CLEAR`, `DST_IN` et `MODULATE` sont strictement transparentes. L'échec préexistant de `:gpu-renderer:compileTestKotlin` reste documenté dans le rapport Task 5 et n'est pas aggravé. La re-review indépendante finale est `READY`, sans nouveau finding.

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

- [x] Add RED mixed frames interleaving fixed-function, destination-read and `DST` across Rect → Point → RRect → Path → A8 → Vertices. Observe at least one pixel per middle command, one order counterfactual and every mutable captured source.
- [x] Add a public budget/capability refusal reachable from valid W5b input, followed by a valid W5b render on the same Surface when the public API permits it; otherwise use the same runtime/backend with distinct Surfaces and state that exact limitation. The refusal diagnostic and recovery pixels are the only assertions.
- [x] Seal destination versions and snapshot lifetimes before `Ready`; reject missing binding/texture/sampler/storage capability and I64 overflow before any native allocation. Preserve affine destination correlation in the oracle.
- [x] Ensure rollback owns every created texture/view/sampler/buffer/pipeline until completion and quarantines failed closes through existing pool journals. Do not add failure injection or lifecycle counters.
- [x] Run the complete W5b public suite and W5a/W3/W4 public regressions; commit `test(w5b): prove mixed blend ordering and recovery`.

Task 6 closed after three independent-review fix rounds. The mixed sibling now owns the ordered Core/A8/Vertices timeline, exact destination-copy contract, pre-allocation physical inventory, authentic `DST` elision and a Surface-confined zero-survivor proof that lowers to the existing transparent initialization clear. Final allowed regression: 146 selected public methods, 144 passed, 2 authentic AA4 skips, 0 failures/errors. Recovery uses distinct Surfaces on the same uninterrupted runtime/backend because the public display list and configuration are immutable; capability replacement and the aggregate mixed budget remain non-injectable through the public prepared API, so their typed branches have static review evidence rather than fabricated tests. The admitted ABI uses uniform buffers and sampled textures/samplers, not storage buffers. Independent scoped re-review verdict: READY.

### Task 7: Remove migrated blend fallbacks and update durable status

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`
- Modify: `refactor/waves/W05-material-graph/status.md`
- Modify: `refactor/README.md`

**Interfaces:**
- Consumes: compiler-authenticated W5b ownership and complete public gate evidence.
- Produces: terminal typed W5b refusal after ownership, legacy continuation only before ownership, and the authoritative W5b status/next-W5c scope.

- [x] Audit every production final-blend classification call site. W5b-promoted paths must consume the sealed plan; legacy-only paths are documented in the status and may keep the compatibility adapter until their `H` deadline.
- [x] Remove only fallbacks now owned by W5b. A `GapNotMigrated` before ownership stays legacy; an error after authenticated W5b ownership is terminal and cannot rescan public paint or substitute `SRC_OVER`/transparent/source-only behavior.
- [x] Update the 45-cell ledger entry from historical failure to closed only after the filterable public gate passes all 15 modes × 3 contexts. Record the exact command and results.
- [x] Update W05 status and README with every promoted family, exact AA4 skips, numerical `Unbounded` non-gates, capability/budget gaps, the pre-existing CoreAnalytics warning if still emitted, and W5c as the next stacked slice.
- [x] Run `rtk git diff --check`, targeted compiles and the complete W5b/W5a/W3/W4 public gates; commit `refactor(blend): remove W5b final-blend fallbacks`.

Task 7 closed by `b0ef1cbcf`: the exhaustive production audit is recorded in the ignored Task 7 report and summarized in the durable W05 status. Post-ownership W5b gaps are terminal; pre-admission/deferred legacy adapters remain explicitly scoped through W5c–W5h, W6 or W8. The forced before/after public regression stayed at 146 selected methods, 144 passed and 2 authentic AA4 skips; the standalone GREEN45 gate passed all 15 modes × 3 contexts. Independent Sol review verdict: READY with no finding.

### Task 8: Independent reviews, final verification and stacked PR

**Files:**
- Modify only files required by verified review findings.

**Interfaces:**
- Consumes: the full `codex/w5a-solid-opacity..codex/w5b-blends` range, plan, spec, task ledger and public verification evidence.
- Produces: two independent `READY` reviews, a clean verified branch, and a W5b PR targeting `codex/w5a-solid-opacity`.

- [x] Ask a fresh Sol agent for a global specification review against this plan and the W5 spec. Route every Critical/Important finding through one responsible non-Sol implementer and a scoped Sol re-review.
- [x] Ask a different fresh Sol agent for final code quality review. Iterate the single permitted final fix wave until no Critical/Important finding remains; record minors explicitly.
- [x] Run fresh, non-parallel JVM compiles and the complete public W5b/W5a plus bounded W3/W4 gates. Force test execution when Gradle reports `UP-TO-DATE`.
- [x] Confirm `:kanvas` target inventory; run JS only if an authentic JS target exists. Do not invent an infrastructure substitute.
- [x] Do not run font, codec, GM/dashboard/render/baseline, Skia integration or `jpg-color-cube` suites.
- [x] Run `rtk git diff --check`, verify a clean worktree and inspect the full commit range from `codex/w5a-solid-opacity`.
- [x] Push `codex/w5b-blends` and create/update its PR with base `codex/w5a-solid-opacity`, exact public test counts, authentic skips, review verdicts, and deferred W5c–W5h scope (`#2396`).

Task 8 ferme les findings de spécification F1–F3 et les findings qualité F4–F7 aux commits `7382123bf`, `900f1dea7` et `e470bcee8`. Les deux re-reviews Sol sont `READY`. La vérification contrôleur finale du 11 septembre 2026 sélectionne 151 méthodes publiques : 149 réussites, 2 skips AA4 authentiques, 0 failure/error; GREEN45 passe aussi seul sur ses 45 cellules. Les Minors `SetSat`/oracle, cast legacy `GeneralPathDraw.withBlend` et assertion de primer du test budget Task 6 restent explicitement différés dans le status W05.

## Verification Commands

```bash
rtk ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --tests '*GPUPlanSurfacePixelTest*' --no-parallel --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix' --no-parallel --rerun-tasks
rtk git diff --check
```

The final test names may be narrowed only to the exact public classes/methods created above. No infrastructure suite may replace a missing public behavior gate.
