# Graphite/Dawn-inspired WebGPU Frame Plan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the immediate, CPU-snapshot-capable GPU path with one Kanvas-owned WebGPU frame planner and executor that routes every drawing API through the canonical 29-mode blend/coverage decision, preserves MSAA contents across pass breaks, and records one encoder, one command buffer, and one submission per scene.

**Architecture:** `NormalizedDrawCommand` and `GPUTaskList` remain semantic authorities. A pure `GPUFramePlanner` linearizes their accepted work into an immutable handle-free `GPUFramePlan`; `GPUFramePreflighter` materializes all resources transactionally; `GPUFrameExecutor` encodes the prepared frame once. Offscreen and window paths share a canonical `GPUSceneTarget`. The implementation borrows Graphite/Dawn's separation between recording, resource preparation, encoding, submission, presentation, and completion, without porting Graphite or introducing a second backend.

**Tech Stack:** Kotlin/JVM, Gradle, wgpu4k/WebGPU, WGSL, JUnit 5, Skia GM integration tests, shell evidence commands prefixed with `rtk`.

## Global Constraints

- The approved design is `docs/superpowers/specs/2026-07-13-graphite-dawn-inspired-webgpu-frame-plan-design.md`; if code pressure contradicts it, stop and amend the design before changing semantics.
- Read `.upstream/specs/gpu-renderer/README.md` and the authority files changed by Task 1 before each implementation slice. The synchronized active specs win over historical tickets and Git-history plans.
- Do not port Ganesh or Graphite, add a SkSL compiler, revive `KanvasPipelineIR`, add a general render DAG, or add a second GPU backend.
- Keep `passes.GPUBlendMode` as the only 29-mode identity. Remove the smaller `state.GPUBlendMode` and all route-driving blend booleans.
- No product destination-read route may call `readRgba()`, `mapAsync`, upload CPU-produced destination pixels, or invoke a hidden CPU renderer before later GPU drawing.
- Use one scene encoder, one command buffer, and one `queue.submit()` for a successfully prepared scene. Surface `present()` remains a post-submit host action and never means GPU completion.
- The in-progress wgpu4k correction is expected to preserve `GPUQueue.onSubmittedWorkDone()`. Implement only a thin Kanvas lifecycle/evidence adapter around that API. Do not add a private native callback workaround.
- If corrected wgpu4k behavior is ambiguous or fails the native conformance suite, stop activation and report a minimized wgpu4k issue with exact dependency revisions, callback mode, reproduction, and observed order. The stable Kanvas refusal is `dependency.resource.queue_completion_unavailable`.
- Preserve the user's unrelated Skia GM registry edits in the worktree. Every commit must stage explicit paths only.
- Follow test-driven development: add the focused failing test, run it and observe the expected failure, implement the smallest coherent slice, rerun focused tests, then run the affected module suite.
- Keep dumps deterministic and free of raw native handles, object addresses, texture identities in pipeline keys, and timing-dependent ordering.
- Commit each task independently. Do not leave compatibility adapters beyond the task that removes their last consumer.

---

## Task 1: Synchronize authority and restore the package DAG

**Files:**

- Modify: `.upstream/specs/gpu-renderer/02-gpu-recording-task-graph.md`
- Modify: `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`
- Modify: `.upstream/specs/gpu-renderer/12-blend-color-target-state.md`
- Modify: `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`
- Modify: `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- Modify: `.upstream/specs/gpu-renderer/24-clip-stencil-mask-pipeline.md`
- Modify: `.upstream/specs/gpu-renderer/32-target-authority-taxonomy-diagnostics.md`
- Modify: `.upstream/specs/gpu-renderer/34-analysis-materialization-recording.md`
- Modify: `.upstream/specs/gpu-renderer/35-package-class-layout.md`
- Modify: `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`
- Modify: `.upstream/specs/gpu-renderer/README.md`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskBlurPlan.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskBlurPlanTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`

**Interfaces:**

- Consumes: the approved design and the listed active GPU-renderer authority files.
- Produces: one normative ownership/dependency vocabulary consumed by Tasks 2–14 and an acyclic current package baseline; no runtime API changes.

- [ ] **Step 1: Add an authority-coherence test**

Extend `GPURendererPackageBoundaryTest` so it reads the active spec files and asserts the synchronized vocabulary is present exactly once as authority: `GPUBlendPlan`, `GPUFramePlan`, `GPUFramePreflighter`, `PreparedGPUFrame`, `GPUSceneTarget`, `GPUQueueCompletionTicket`, `LCDCoverage`, and `RefusedCompositeCommand`. Assert that active specs do not authorize CPU destination snapshots, presentation-as-completion, a second blend-mode enum, or materialization decisions before the final `GPUTaskList`/`GPUFramePlan` order is known.

- [ ] **Step 2: Run the test and confirm the authority is currently inconsistent**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest'
```

Expected: FAIL because the existing active specifications still contain the old LCD refusal, immediate submission/completion wording, and narrower blend authority. On the current baseline, the same test also reports the pre-existing `commands -> filters -> commands` package cycle.

- [ ] **Step 3: Apply the approved amendments to the active specs**

Make these ownership statements normative:

- `GPUTaskList` is dependency authority; `GPUFramePlan` is its deterministic linear execution schedule.
- analysis and recording remain handle-free; `GPUResourceMaterializationDecision`, pass command streams, and concrete handles are produced only in preflight after frame order is finalized.
- `GPUBlendPlan` owns all 29 modes, fixed-function state, shader formula identity, coverage encoding, opacity specialization, and destination-read requirement.
- the canonical mode, blend planner, and semantic `GPUBlendDestinationReadRequirement` live together in `passes`; foundation `state` does not import late-planning packages. `destination` consumes that semantic requirement and alone chooses its concrete strategy, so `passes` never imports `destination`. Handle-free device generation lives in `capabilities`, while color format/interpretation live in `color`.
- `GPUDestinationReadStrategyPlanner` chooses materialization only; it cannot reinterpret blend semantics.
- LCD coverage is vector RGB coverage with channel-wise interpolation and maximum channel alpha; exact MSAA uncertainty refuses.
- the canonical scene texture, persistent MSAA continuation, bounded snapshot grouping, scratch budget, preflight rollback, one-submit executor, late surface acquisition, post-submit present, and real queue completion are required.
- wgpu4k completion uses the unchanged facade API after its corrected revision passes native conformance; no Kanvas native workaround is authorized.

- [ ] **Step 4: Remove the existing commands/filters package cycle**

`MaskBlurPlan.kt` needs only the scalar bounds transport already owned by `clips`. Replace its import of the compatibility alias `commands.GPUBounds` with `clips.GPUBounds`, and update `MaskBlurPlanTest.kt` to import the same owner. Do not move or duplicate the type. Confirm `filters` no longer imports `commands`; `commands` may continue consuming filter descriptors without a reverse edge.

- [ ] **Step 5: Run the focused test and spec conflict scan**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest'
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanTest'
rtk proxy rg -n '^import org\.graphiks\.kanvas\.gpu\.renderer\.commands\.' gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters
rtk proxy rg -n 'present.*completion|CPU destination|state\.GPUBlendMode|LCD.*unconditional.*refus|materialization.*before.*task' .upstream/specs/gpu-renderer/{02-gpu-recording-task-graph.md,10-gpu-execution-context-submission.md,12-blend-color-target-state.md,20-destination-read-strategy.md,21-text-glyph-pipeline.md,24-clip-stencil-mask-pipeline.md,32-target-authority-taxonomy-diagnostics.md,34-analysis-materialization-recording.md,35-package-class-layout.md,37-draw-packet-command-stream.md}
```

Expected: both tests pass; both scans return no matches. Historical context explicitly labeled as superseded is acceptable only when the test proves the new authority is unambiguous.

- [ ] **Step 6: Commit the authority synchronization**

```bash
rtk git add .upstream/specs/gpu-renderer/README.md .upstream/specs/gpu-renderer/02-gpu-recording-task-graph.md .upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md .upstream/specs/gpu-renderer/12-blend-color-target-state.md .upstream/specs/gpu-renderer/20-destination-read-strategy.md .upstream/specs/gpu-renderer/21-text-glyph-pipeline.md .upstream/specs/gpu-renderer/24-clip-stencil-mask-pipeline.md .upstream/specs/gpu-renderer/32-target-authority-taxonomy-diagnostics.md .upstream/specs/gpu-renderer/34-analysis-materialization-recording.md .upstream/specs/gpu-renderer/35-package-class-layout.md .upstream/specs/gpu-renderer/37-draw-packet-command-stream.md gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskBlurPlan.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskBlurPlanTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt
rtk git commit -m 'docs: align GPU renderer frame planning authority'
```

## Task 2: Converge on one exhaustive 29-mode blend plan

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendMode.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaa.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/paintblend/PaintBlendExecutionContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/VerticesContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapter.kt`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapterTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendCoveragePlannerTest.kt`
- Delete: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/state/BlendAllowlistGateTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendAllowlistPlannerTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererLayoutSurfaceTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadLiveMaterializationTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadMaterializationPreimageTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadStrategyGateTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlannerTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/paintblend/PaintBlendExecutionBoundaryTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/VerticesRouteDecisionTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendPlanTest.kt`

**Interfaces:**

- Consumes: `passes.GPUBlendMode`, `GPUCoverageConsumption`, `GPUSourceAlphaClassification`, target facts, and the minimal `GPUSamplePlan`.
- Produces: `GPUBlendPlanner.plan(request: GPUBlendSpecializationRequest): GPUBlendPlan` and `GPUBlendPlan.destinationReadRequirement: GPUBlendDestinationReadRequirement` for destination, intermediate, pipeline, and executor consumers.

- [ ] **Step 1: Write the exhaustive planner contract test**

Create a parameterized test over `GPUBlendMode.entries` and assert there are exactly 29 unique labels. Cover `FullOrScissor`, `ScalarCoverage`, `StencilCoverage1x`, `MultisampleAttachmentCoverage`, and `LCDCoverage`; translucent and proven-opaque sources; normalized clamping and non-clamping targets; sample counts 1 and 4. Assert the complete normative route matrix from the approved design, including `Screen` as fixed function, `Multiply` as destination shader, scalar `Plus` as `plus_exact@v1`, five opaque scalar upgrades, `Dst` as no-op, advanced-MSAA refusal, and LCD destination routing.

The production contract introduced by the test is:

```kotlin
sealed interface GPUBlendPlan {
    val mode: GPUBlendMode
    val sourceCoverageEncoding: GPUSourceCoverageEncoding
        get() = GPUSourceCoverageEncoding.None
    val destinationReadRequirement: GPUBlendDestinationReadRequirement
        get() = GPUBlendDestinationReadRequirement.None

    data class FixedFunctionBlend(
        override val mode: GPUBlendMode,
        val state: GPUFixedFunctionBlendState,
        override val sourceCoverageEncoding: GPUSourceCoverageEncoding,
    ) : GPUBlendPlan

    data class ShaderBlendNoDstRead(
        override val mode: GPUBlendMode,
        val formulaId: String,
        override val sourceCoverageEncoding: GPUSourceCoverageEncoding,
    ) : GPUBlendPlan

    data class ShaderBlendWithDstRead(
        override val mode: GPUBlendMode,
        val formulaId: String,
        override val sourceCoverageEncoding: GPUSourceCoverageEncoding,
        override val destinationReadRequirement: GPUBlendDestinationReadRequirement,
    ) : GPUBlendPlan

    data class LayerCompositeBlend(
        override val mode: GPUBlendMode,
        val child: GPUBlendPlan,
        val layerOrderingToken: String,
    ) : GPUBlendPlan {
        override val sourceCoverageEncoding: GPUSourceCoverageEncoding
            get() = child.sourceCoverageEncoding
        override val destinationReadRequirement: GPUBlendDestinationReadRequirement
            get() = child.destinationReadRequirement
    }

    data class NoOp(override val mode: GPUBlendMode, val reason: String) : GPUBlendPlan
    data class UnsupportedBlend(
        override val mode: GPUBlendMode,
        val diagnostic: GPUBlendDiagnostic,
        val refusalScope: GPURefusalScope,
    ) : GPUBlendPlan
}

enum class GPUBlendDestinationReadRequirement {
    None,
    FixedFunctionBlend,
    TargetCopy,
    ExistingIntermediate,
    LayerIsolation,
    Refused,
}

data class GPUBlendSpecializationRequest(
    val mode: GPUBlendMode,
    val coverage: GPUCoverageConsumption,
    val sourceAlpha: GPUSourceAlphaClassification,
    val target: GPUTargetBlendFacts,
    val samplePlan: GPUSamplePlan,
    val layerOrderingToken: String? = null,
)

class GPUBlendPlanner {
    fun plan(request: GPUBlendSpecializationRequest): GPUBlendPlan
}

sealed interface GPUSamplePlan {
    val sampleCount: Int

    data object SingleSampleFrame : GPUSamplePlan {
        override val sampleCount: Int = 1
    }

    data class MultisampleFrame(override val sampleCount: Int) : GPUSamplePlan
}
```

Use the shared `GPUBlendDestinationReadRequirement.None` default so every plan variant exposes the same property without nullable routing. This semantic enum is owned by `passes`; delete the older `destination.GPUDestinationReadRequirement`. `destination` already depends on pass products, so its strategy planner consumes this enum and maps it to destination actions without creating a `passes` → `destination` import or a package cycle. Task 2 introduces only the minimal handle-free single-sample/multisample distinction required by blend specialization; Task 4 adds continuation identity and transition semantics without changing this ownership.

- [ ] **Step 2: Run the new test and observe the duplicate/narrow authority failure**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUBlendCoveragePlannerTest'
```

Expected: FAIL to compile because the exhaustive plan and coverage types do not exist.

- [ ] **Step 3: Make `passes.GPUBlendMode` identity-only and implement the pure planner**

Keep the 29 enum constants and `gpuLabel`; remove `colorSrcFactor`, `colorDstFactor`, alpha factors, and `requiresDestinationRead`. Move exact attachment operations, factors, fragment-output encoding, target-format gates, formula IDs, and refusal codes into `GPUBlendPlanning.kt`. Model color and alpha operations independently. Include formula identity and coverage topology in the pipeline-key input; exclude concrete texture identity, snapshot origin, and logical bounds. Change native fixed-function state conversion to consume `GPUBlendPlan.FixedFunctionBlend.state`; it must never infer attachment state from the enum.

- [ ] **Step 4: Remove the smaller enum and route-driving booleans**

Delete `state.GPUBlendMode`, `destination.GPUDestinationReadRequirement`, and every blend-specific allowlist/plan contract from foundation `StateContracts.kt`. Move blend semantics and their evidence adapter into `passes/GPUBlendPlanning.kt` beside the canonical mode identity; `state` keeps only backend-neutral target/alpha/fixed-state components that do not import `passes`. Change every destination/intermediate/scene import to the canonical `passes` types. Replace `GPUBlendFacts(kind, modeLabel, requiresDestinationRead, blendMode)` with identity plus non-routing source facts:

```kotlin
data class GPUBlendFacts(
    val mode: GPUBlendMode,
    val sourceAlpha: GPUSourceAlphaClassification,
)
```

Migrate every production consumer in `analysis`, `paintblend`, `vertices`, native state conversion, and the current Kanvas mapper/clip/renderer bridge in this task. `GPUPrimitiveBlendPlan` stores the canonical primitive blend plan rather than its own boolean. Kanvas may keep an explicit temporary function that asks the canonical plan for `destinationReadRequirement`, but it may not recreate a mode table or cache a boolean. Keep compatibility constructors only inside this commit and delete them before committing so downstream modules compile at every task boundary.

- [ ] **Step 5: Update allowlist/intermediate evidence to consume `GPUBlendPlan`**

`GPUBlendAllowlistPlanner` becomes an evidence adapter over `GPUBlendPlanner`, not a second decision table. `GPUIntermediatePlanner` and `GPUDestinationReadStrategyPlanner` consume `plan.destinationReadRequirement`; only the latter maps that semantic requirement to destination materialization actions. Preserve stable diagnostics and hashes where semantics are unchanged; intentionally update snapshots where `Screen`, `Plus`, or coverage routing changes. Run `GPURendererPackageBoundaryTest` in this task and require zero package-cycle violations.

- [ ] **Step 6: Run focused and module tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUBlendCoveragePlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUBlendAllowlistPlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.destination.DestinationReadStrategyGateTest' --tests 'org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest' --tests 'org.graphiks.kanvas.gpu.renderer.vertices.VerticesRouteDecisionTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUBlendPlanTest'
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test :kanvas:test
rtk proxy rg -n 'blend\.requiresDestinationRead|mode\.requiresDestinationRead|plan\.requiresDestinationRead|colorSrcFactor|colorDstFactor|alphaSrcFactor|alphaDstFactor' gpu-renderer/src/main/kotlin gpu-renderer-scenes/src/main/kotlin kanvas/src/main/kotlin
rtk proxy rg -n 'enum class GPUBlendMode' gpu-renderer/src/main/kotlin gpu-renderer-scenes/src/main/kotlin kanvas/src/main/kotlin
```

Expected: tests pass. The first scan has no matches. The second scan shows exactly one enum declaration in `passes/GPUBlendMode.kt`; unrelated layer-isolation facts named `requiresDestinationRead` are not blend-routing authorities and may remain until layer contracts are typed in Task 12.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendMode.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaa.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/paintblend/PaintBlendExecutionContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/VerticesContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapter.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapterTest.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererLayoutSurfaceTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadLiveMaterializationTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadMaterializationPreimageTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadStrategyGateTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendCoveragePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/state/BlendAllowlistGateTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendAllowlistPlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/paintblend/PaintBlendExecutionBoundaryTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/VerticesRouteDecisionTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendPlanTest.kt
rtk git commit -m 'refactor: make blend planning exhaustive and canonical'
```

## Task 3: Centralize scalar and LCD blend formulas

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendFormulaLibrary.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendFormulaLibraryTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendCpuOracle.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BlendWgslBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUSubpixelLcd.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUSubpixelLcdTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLParserBackedReflectionTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLModuleAbiTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeWgslValidationTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendFormulaSurfaceTest.kt`

**Interfaces:**

- Consumes: the stable `formulaId` and scalar/vector coverage topology selected by `GPUBlendPlan`.
- Produces: one production formula registry consumed by `BlendWgslBuilder`, plus the independent test-only `GPUBlendCpuOracle` used for RGBA acceptance.

- [ ] **Step 1: Add CPU-oracle and generated-WGSL tests for every formula ID**

For all 29 modes, compare premultiplied CPU reference results at coverage `0f`, `0.25f`, `0.5f`, and `1f` for transparent, translucent, and opaque destinations. Add unequal LCD coverage vectors such as `(0.15, 0.55, 0.9)` and assert per-channel `D + F * (Blend(S,D) - D)` plus alpha equal to the maximum of the three interpolated alpha values. Assert `Dst` emits no shader route and LCD never accepts scalar encoding.

Implement `GPUBlendCpuOracle` only in test source, independently of production formula IDs, route tables, and WGSL string assembly. Port the already independent advanced-mode reference formulas from `GPUBlendFormulaSurfaceTest`/`GPUAllApiBlendSurfaceTest`, add fixed golden edge cases for zero alpha, division boundaries, saturation, and hue/luminosity transfer, and compare native shader pixels to that oracle. A registry-owned evaluator may aid diagnostics but can never be an acceptance oracle.

For each fixed-function/shader/coverage topology, assemble the complete production WGSL module, parse and reflect it through wgsl4k, compare reflected bindings/entry points/layout to the declared ABI, and create the native shader module/pipeline in the existing validation harness. Snippet-only string assertions do not count as acceptance.

- [ ] **Step 2: Run tests and confirm formula ownership is still split**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibraryTest' --tests 'org.graphiks.kanvas.gpu.renderer.text.GPUSubpixelLcdTest'
```

Expected: FAIL because `GPUBlendFormulaLibrary` does not exist and LCD is not connected to the canonical plan.

- [ ] **Step 3: Implement one formula registry**

Expose stable formula IDs, WGSL function bodies, binding topology, and scalar/vector coverage kind from one production registry. Make `BlendWgslBuilder` assemble the selected formula rather than switch on blend mode. Move the destination formula body out of `kanvas/GPUWgsl.kt`; keep only complete shader programs assembled from gpu-renderer-provided snippets until Task 12 removes that compatibility host. Do not expose production CPU expected-color logic from this registry.

- [ ] **Step 4: Bind `GPUSubpixelLCDPlan` to canonical vector coverage**

Translate accepted LCD text facts into `GPUCoverageConsumption.LCDCoverage`; keep the existing refusal until the target is single-sample and the plan has a destination strategy. Do not normalize RGB coverage to one scalar.

- [ ] **Step 5: Run focused and cross-module tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibraryTest' --tests 'org.graphiks.kanvas.gpu.renderer.text.GPUSubpixelLcdTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUBlendFormulaSurfaceTest'
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.wgsl.WGSLParserBackedReflectionTest' --tests 'org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleAbiTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeWgslValidationTest'
rtk ./gradlew :gpu-renderer:test :kanvas:test
```

Expected: all pass with all RGBA channels checked.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendFormulaLibrary.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BlendWgslBuilder.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUSubpixelLcd.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendFormulaLibraryTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendCpuOracle.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUSubpixelLcdTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLParserBackedReflectionTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLModuleAbiTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeWgslValidationTest.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendFormulaSurfaceTest.kt
rtk git commit -m 'feat: centralize exact GPU blend formulas'
```

## Task 4: Add persistent MSAA continuation and complete frame memory accounting

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaa.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuation.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUFrameMemoryBudget.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/color/ColorContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuationTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUFrameMemoryBudgetTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/color/SDRColorBoundaryTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendCoveragePlannerTest.kt`

**Interfaces:**

- Consumes: the minimal `GPUSamplePlan`, typed target/device generation, color format/interpretation, and blend destination-read facts.
- Produces: `GPUSampleContinuationKey`, explicit load/resolve/discard transitions, and `GPUFrameMemoryBudgetPlan` consumed by destination grouping, resources, preflight, and telemetry.

- [ ] **Step 1: Add continuation and budget failures first**

Test that a pass break can preserve untouched pixels only when the same stored MSAA color/depth-stencil attachment and target generation continue. Reject a fresh transient attachment with preserve-load. Test several pass breaks, layer sample-plan isolation, target generation mismatch, and advanced blend refusal when exact single-sample lowering is not proven. Extend the blend planner matrix tests so `SingleSampleFrame` accepts its scalar exact route while every multisample continuation either uses an exact attachment route or returns `unsupported.blend.msaa_destination_read_exactness`.

Budget tests must account separately for canonical target bytes, retained and frame-local MSAA color/depth-stencil, layer/filter targets, snapshots, readback staging, and reusable scratch. Verify a 4K 4x attachment set cannot bypass the peak budget by being target-owned.

- [ ] **Step 2: Run focused tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaContinuationTest' --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetTest'
```

Expected: FAIL because continuation and aggregate budget types do not exist.

- [ ] **Step 3: Implement explicit continuation**

Evolve the minimal `GPUSamplePlan` from Task 2 with fresh-clear, retained-load, resolve, and discard transitions in a pure plan. Put target identity and generation in a typed continuation key. Define handle-free `GPUDeviceGenerationID` in `capabilities/CapabilityContracts.kt`, plus `GPUColorFormat` and `GPUColorInterpretation` in `color/ColorContracts.kt`; semantic planning packages use these foundation types and never import `execution.GPUDeviceGeneration`. Make ordinary single-sample plans explicit so `GPUBlendPlanner` and `GPUIntermediatePlanner` distinguish an exact single-sample frame from a local resolve approximation; neither planner may inspect sample-count integers independently.

- [ ] **Step 4: Implement checked aggregate accounting**

Return `peakFrameTransientBytes`, `targetResidentBytes`, category totals, device-limit facts, and a stable diagnostic. Remove the fixed 16 MiB per-copy ceiling as a feature-support decision; retain only validated device limits and configured aggregate budget.

- [ ] **Step 5: Run affected suites**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaContinuationTest' --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetTest' --tests 'org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest'
rtk ./gradlew :gpu-renderer:test
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaa.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuation.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUFrameMemoryBudget.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/color/ColorContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuationTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUFrameMemoryBudgetTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/color/SDRColorBoundaryTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendCoveragePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateMsaaPlanTest.kt
rtk git commit -m 'feat: preserve MSAA state across pass breaks'
```

## Task 5: Plan bounded destination snapshots and deterministic grouping

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationContracts.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationSnapshotGrouping.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationSnapshotGroupingTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadStrategyGateTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadMaterializationPreimageTest.kt`

**Interfaces:**

- Consumes: ordered target accesses, `GPUBlendDestinationReadRequirement`, `GPUSampleContinuationKey`, typed target/color/generation facts, and Task 4's budget.
- Produces: `GPUDestinationSnapshotGrouper.group(orderedAccesses: List<GPUTargetAccess>): GPUDestinationSnapshotGroupingResult` and destination materialization actions consumed by frame planning.

- [ ] **Step 1: Write grouping, hazard, bounds, sample-state, and cost tests**

Cover exact group-key equality over target identity/generation, device generation, format/color interpretation, the typed MSAA continuation key from Task 4, and source-intermediate identity. Cover intersecting writes, direct intervening draws, target/layer/filter/generation changes, AA/filter expansion, floor/ceil alignment, clip/target intersection, empty bounds, distant-rectangle union rejection, and accepted local union. Default policy before calibrated constants must produce one snapshot per destination-reading draw.

The core result must be explicit:

```kotlin
data class GPUDestinationSnapshotGroup(
    val key: GPUDestinationSnapshotGroupKey,
    val logicalBounds: GPUPixelBounds,
    val members: List<GPUDestinationReadMember>,
    val copiedBytes: Long,
    val decisionDump: List<String>,
)

data class GPUDestinationSnapshotGroupKey(
    val target: GPUTargetIdentity,
    val targetGeneration: Long,
    val deviceGeneration: GPUDeviceGenerationID,
    val format: GPUColorFormat,
    val colorInterpretation: GPUColorInterpretation,
    val sampleContinuation: GPUSampleContinuationKey,
    val sourceIntermediate: GPUIntermediateIdentity?,
)

class GPUDestinationSnapshotGrouper(
    private val costModel: SnapshotGroupingCostModel,
) {
    fun group(orderedAccesses: List<GPUTargetAccess>): GPUDestinationSnapshotGroupingResult
}
```

- [ ] **Step 2: Run the new test and see missing grouping support**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupingTest'
```

Expected: FAIL to compile because grouping contracts do not exist.

- [ ] **Step 3: Separate semantic requirement, strategy, and materialization**

Keep `None`, fixed-function, target snapshot, existing intermediate, layer isolation, and refusal as semantic/strategy outcomes. Model `CopyAsDrawMaterialization` only as a materialization of a target snapshot when a future real source is texturable but lacks `CopySrc`; do not expose it as a seventh blend strategy. Require canonical scene/layer targets to include `CopySrc`.

- [ ] **Step 4: Implement deterministic grouping and dumps**

Use checked arithmetic for pixel area and aligned byte cost. Reject union inflation above `2.0`, aggregate frame-budget overflow, or missing calibration. Store logical copy origin/extent independently of later backing allocation. Never use wall time or allocation identity in a decision.

- [ ] **Step 5: Run destination and module tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.destination.*'
rtk ./gradlew :gpu-renderer:test
```

Expected: all destination and module tests pass and no accepted plan cites CPU readback.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationSnapshotGrouping.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationSnapshotGroupingTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadStrategyGateTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadMaterializationPreimageTest.kt
rtk git commit -m 'feat: plan bounded destination snapshot groups'
```

## Task 6: Finalize recordings into one immutable linear frame plan

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcher.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcherTest.kt`

**Interfaces:**

- Consumes: the finalized `GPUTaskList`, canonical `GPUBlendPlan`, destination grouping results, typed provenance, and resource preparation requests.
- Produces: `GPUFramePlanner.plan(taskList: GPUTaskList): GPUFramePlan`, whose closed ordered `GPUFrameStep` list is the sole semantic input to preflight.

- [ ] **Step 1: Write deterministic frame-plan tests**

Cover compatible recording insertion, preserved task IDs/dependencies/phase order, cycle rejection, replay-key mismatch, pass/destination-copy/pass sequencing, layer transitions, readback/output steps, direct draw batching, target hazards, and repeatable dump/hash output. Add refusal cases for isolated leaf, whole composite command with child provenance consumed, and atomic escalation when order cannot be preserved. Assert `CopyAsDrawMaterializationStep` is emitted only when the capability record says the implementation is present; canonical `CopySrc` targets never need it, and an otherwise requested route refuses with `unsupported.destination_read.copy_unavailable` before preflight.

Use this closed step algebra:

```kotlin
sealed interface GPUFrameStep {
    val sourceTaskIds: List<GPUTaskID>
    val executionKind: GPUFrameStepExecutionKind

    data class RenderPassStep(
        val target: GPUFrameTargetRef,
        val loadStore: GPULoadStorePlan,
        val samplePlan: GPUSamplePlan,
        val drawPackets: List<GPUDrawPacket>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    data class ComputePassStep(
        val target: GPUFrameTargetRef,
        val resourceUses: List<GPUFrameResourceUse>,
        val dispatches: List<GPUComputeDispatch>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    data class PrepareResourcesStep(
        val requests: List<GPUResourcePreparationRequest>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Preflight
    }

    data class UploadResourceStep(
        val staging: GPUFrameBufferRef,
        val destination: GPUFrameResourceRef,
        val layout: GPUUploadLayout,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    data class CopyResourceStep(
        val source: GPUFrameResourceRef,
        val destination: GPUFrameResourceRef,
        val regions: List<GPUResourceCopyRegion>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    data class DependencyBarrierStep(
        val orderedUseTokens: List<GPUTaskUseToken>,
        val reasonCode: String,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly
    }

    data class CopyDestinationStep(
        val source: GPUFrameTargetRef,
        val snapshot: GPUFrameTextureRef,
        val logicalBounds: GPUPixelBounds,
        val copyLayout: GPUTextureCopyLayout,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    data class CopyAsDrawMaterializationStep(
        val source: GPUFrameTargetRef,
        val snapshot: GPUFrameTextureRef,
        val logicalBounds: GPUPixelBounds,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    data class TargetTransitionStep(
        val parent: GPUFrameTargetRef,
        val child: GPUFrameTargetRef,
        val transitionKind: GPUTargetTransitionKind,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly
    }

    data class ReadbackCopyStep(
        val source: GPUFrameTargetRef,
        val staging: GPUFrameBufferRef,
        val request: GPUFrameReadbackRequest,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    data class AcquireSurfaceOutput(
        val descriptor: GPUSurfaceOutputDescriptor,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Preflight
    }

    data class SurfaceBlitRenderPassStep(
        val scene: GPUFrameTargetRef,
        val output: GPUSurfaceOutputRef,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    data class PostSubmitPresentAction(
        val output: GPUSurfaceOutputRef,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.PostSubmitHost
    }

    data class RefusedLeafDrawStep(
        val commandId: GPUDrawCommandID,
        val diagnostic: GPUFrameDiagnostic,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.RefusalEvidence
    }

    data class RefusedCompositeCommandStep(
        val commandId: GPUDrawCommandID,
        val provenanceTokens: List<GPUCompositeProvenanceToken>,
        val diagnostic: GPUFrameDiagnostic,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.RefusalEvidence
    }
}

enum class GPUFrameStepExecutionKind {
    Preflight,
    Encoder,
    DependencyOnly,
    PostSubmitHost,
    RefusalEvidence,
}

data class GPUFramePlan(
    val frameId: GPUFrameID,
    val recordingSeals: List<GPURecordingSeal>,
    val steps: List<GPUFrameStep>,
    val memoryBudget: GPUFrameMemoryBudgetPlan,
    val diagnostics: List<GPUFrameDiagnostic>,
)

data class GPUFrameReadbackRequest(
    val requestId: GPUReadbackRequestID,
    val sourceBounds: GPUPixelBounds,
    val pixelFormat: GPUReadbackPixelFormat,
    val outputColorInterpretation: GPUColorInterpretation,
)

@JvmInline value class GPUFrameID(val value: Long)
@JvmInline value class GPUTaskID(val value: String)
@JvmInline value class GPUReadbackRequestID(val value: String)
@JvmInline value class GPUTaskUseToken(val value: String)
@JvmInline value class GPUCompositeProvenanceToken(val value: String)

enum class GPUReadbackPixelFormat { Rgba8Unorm }

data class GPUResourcePreparationRequest(
    val resource: GPUFrameResourceRef,
    val role: GPUFrameResourceRole,
    val usage: List<GPUFrameResourceUsage>,
    val lifetime: GPUFrameResourceLifetime,
)
```

- [ ] **Step 2: Run the planner test**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlannerTest'
```

Expected: FAIL because the finalizer does not exist.

- [ ] **Step 3: Evolve task payloads without adding a second route decision**

Add typed target, access, pass, destination, output, refusal-scope, and provenance facts to the existing `GPUTask` variants. Replace task-ID strings and resource-plan labels at this boundary with validated `GPUTaskID` and `GPUResourcePreparationRequest` values. Map `PrepareResources` to preflight work, `Upload` and general `Copy` to encoder steps using prepared staging resources, and `Barrier` to a dependency-only step that produces no fake WebGPU barrier command. Preserve each source task ID in canonical source order in the resulting step even when the step is not encodable; do not use unordered sets in deterministic dumps. `GPUFramePlanner` may validate and linearize; it must consume the already chosen `GPUBlendPlan`, preserve refused tasks, and reject cycles/incompatible seals atomically.

- [ ] **Step 4: Reuse pass-local batching**

Keep `GPUPassBatcher` responsible only for adjacent compatible `GPUDrawPacket` values inside a provisional render-pass segment. The frame planner owns copies, compute scopes, target transitions, output, and lifetime ordering. The handle-free plan carries `GPUFrameReadbackRequest`, never `execution.GPUReadbackLayout`. Preflight computes padding and lowers packets to the post-materialization `GPUPassCommandStream` in Task 8.

- [ ] **Step 5: Run recording/pass tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.recording.*' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketCommandStreamTest'
rtk ./gradlew :gpu-renderer:test
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanner.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcher.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcherTest.kt
rtk git commit -m 'feat: finalize recordings into a linear GPU frame plan'
```

## Task 7: Add pooled scratch textures and exact readback layout

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePool.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUReadbackLayout.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePoolTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUReadbackLayoutTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`

**Interfaces:**

- Consumes: `GPUFrameMemoryBudgetPlan`, typed scratch descriptors, completion ownership, `GPUReadbackRequest`, and `GPUCapabilities`.
- Produces: completion-safe opaque scratch leases and `GPUReadbackLayoutPlanner.plan(request: GPUReadbackRequest, capabilities: GPUCapabilities): GPUReadbackLayout`.

- [ ] **Step 1: Test logical/backing separation and completion-safe reuse**

Request non-power-of-two logical textures, verify deterministic size classes, independent logical origin/extent and backing extent, format/usage/sample/device-generation keys, no alias between overlapping lifetimes, reuse only after accepted GPU completion, completed-resource eviction, budget refusal details, and device-generation invalidation.

- [ ] **Step 2: Test WebGPU readback layout arithmetic**

Cover widths whose unpadded rows are and are not multiples of the facade-provided `copyBytesPerRowAlignment`, non-zero offsets, rows-per-image, checked overflow, total padded size, and row depadding back to tightly packed RGBA. Test the observed WebGPU value `256` and a fake capability value `512` so the implementation cannot hard-code 256. Use `Long` for intermediate arithmetic and reject non-positive/non-power-of-two capability values and results that cannot fit facade buffer sizes.

```kotlin
data class GPUReadbackLayout(
    val width: Int,
    val height: Int,
    val bytesPerPixel: Int,
    val copyBytesPerRowAlignment: Int,
    val unpaddedBytesPerRow: Long,
    val paddedBytesPerRow: Long,
    val rowsPerImage: Int,
    val bufferOffset: Long,
    val totalBufferBytes: Long,
)

class GPUReadbackLayoutPlanner {
    fun plan(request: GPUReadbackRequest, capabilities: GPUCapabilities): GPUReadbackLayout
}
```

- [ ] **Step 3: Run new tests and confirm missing implementations**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUScratchTexturePoolTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackLayoutTest'
```

Expected: FAIL to compile.

- [ ] **Step 4: Implement pool ownership through `GPUResourceProvider`**

Return opaque resource references and lease metadata; never expose raw wgpu handles to the semantic plan. Make the pool consume the aggregate frame budget from Task 4 and emit category-complete diagnostics.

- [ ] **Step 5: Implement readback packing/depacking**

Keep map/copy completion in the output path only. Readback may observe a completed scene but cannot provide destination pixels for subsequent GPU drawing.

- [ ] **Step 6: Run resource/execution tests and commit**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.resources.*' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackLayoutTest'
rtk ./gradlew :gpu-renderer:test
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePool.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUReadbackLayout.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePoolTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUReadbackLayoutTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt
rtk git commit -m 'feat: pool frame scratch and validate readback layout'
```

## Task 8: Add transactional frame preflight and a one-to-one mixed encoder plan

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/ExecutionContracts.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapter.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUExecutionContextTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUExecutionCacheContractsTest.kt`

**Interfaces:**

- Consumes: immutable `GPUFramePlan`, resource providers, generation seals, surface acquisition, and a deterministic `GPUQueueCompletionTicketProvider` fake/adapter contract.
- Produces: `GPUFramePreflighter.preflight(framePlan: GPUFramePlan): GPUFramePreflightResult`, with `Ready(PreparedGPUFrame)` or typed refusal/failure and full rollback.

- [ ] **Step 1: Write transactional preflight tests**

Assert full success, pipeline failure, bind-group failure, scratch-budget failure, stale target/device/resource generation, readback-layout failure, and surface acquisition statuses. Every failure before encoding must run rollback in reverse acquisition order, leave no durable native references, create no encoder, and submit nothing. Missing queue-completion proof must refuse before surface acquisition.

- [ ] **Step 2: Define prepared-frame ownership**

```kotlin
data class PreparedGPUFrame(
    val semanticPlan: GPUFramePlan,
    val encoderPlan: GPUCommandEncoderPlan,
    val resources: GPUPreparedResourceSet,
    val generationSeal: GPUPreparedGenerationSeal,
    val completionTicket: GPUQueueCompletionTicket,
    val acquiredSurfaceOutput: GPUAcquiredSurfaceOutput?,
    val rollback: GPUFrameRollback,
)

sealed interface GPUFramePreflightResult {
    data class Prepared(val frame: PreparedGPUFrame) : GPUFramePreflightResult
    data class Refused(val diagnostic: GPUFrameDiagnostic) : GPUFramePreflightResult
}
```

Define the handle-free `GPUQueueCompletionTicket` and `GPUQueueCompletionProvider.reserveTicket()` interfaces here so preflight can depend on them; tests use a deterministic fake provider. The corrected wgpu4k-backed implementation arrives in Task 9.

Change `GPUCommandEncoderPlan` from one pass-local scope to an ordered list that maps one-to-one to every GPU-encodable `GPUFrameStep`. During preflight, lower each `GPUDrawPacket` to a materialized `GPUPassCommandStream`, compute `GPUReadbackLayout` from the logical request and facade capabilities, and retain the one-to-one source-task mapping. Keep surface acquisition and post-submit presentation as explicit host-action evidence outside encoder scopes. Include operation class, source task IDs, target/resource generation labels, and evidence counts. Do not include native handles.

- [ ] **Step 3: Run the failing tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest'
```

Expected: FAIL because preflight and mixed encoder planning do not exist.

- [ ] **Step 4: Implement the only materialization boundary**

Materialize reusable resources first, reserve the completion ticket, and acquire the surface output as the final ephemeral operation before encoder creation. Replace the execution-local `GPUDeviceGeneration` wrapper with the foundation `GPUDeviceGenerationID` introduced in Task 4 so semantic plans, capabilities, resource providers, and execution share one handle-free generation identity without an import cycle. Normalize `lost`/`outdated` to reconfiguration, genuine timeout to retry without submit, and out-of-memory/device-loss to terminal failure. Seal target, device, and resource generations.

- [ ] **Step 5: Run execution tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionContextTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionCacheContractsTest'
rtk ./gradlew :gpu-renderer:test
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/ExecutionContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUExecutionContextTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUExecutionCacheContractsTest.kt
rtk git commit -m 'feat: preflight GPU frames transactionally'
```

## Task 9: Separate submission, presentation, and real GPU completion

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`
- Modify: `gradle/libs.versions.toml`
- Create: `gradle/verification-metadata.xml`
- Modify: `gpu-renderer/build.gradle.kts`
- Modify: `gpu-renderer-scenes/build.gradle.kts`
- Modify: `integration-tests/test-utils/build.gradle.kts`
- Modify: `integration-tests/skia/build.gradle.kts`
- Modify: `integration-tests/svg/build.gradle.kts`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/wgpu4k-completion-conformance.json`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapterTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

**Interfaces:**

- Consumes: corrected `GPUQueue.onSubmittedWorkDone()`, exact resolved dependency evidence, and preflight's reserved ticket.
- Produces: `GPUQueueCompletionAdapter.reserveTicket(): GPUQueueCompletionTicket` with exactly-once post-submit completion, quarantine on failure, and no native callback implementation in Kanvas.

- [ ] **Step 1: Replace presentation-based completion expectations**

Tests must assert the independent state machines:

```text
execution: Planned -> Prepared -> Encoded -> Submitted
           -> GPUCompleted | FailedPreSubmit | FailedAfterSubmit
output:    NotApplicable | Acquired | Presented | PresentFailed
```

`Presented` and `PresentFailed` retain all submission resources. Only accepted completion success releases reusable leases. Completion failure quarantines them. Target close and readback do not fabricate general completion. Unknown and duplicate callbacks are deterministic and exactly-once.

- [ ] **Step 2: Run queue tests and observe the old semantics fail**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionAdapterTest'
```

Expected: FAIL because current tests and constants treat `presented`, `present-failed`, and target close as completion reasons.

- [ ] **Step 3: Implement the thin facade adapter**

`GPUQueueCompletionAdapter.reserveTicket()` must prove revision/capability before preflight. The ticket is armed immediately after `queue.submit()` and before any fallible present action. It delegates to corrected `GPUQueue.onSubmittedWorkDone()` and owns only coroutine/lifecycle normalization, exactly-once delivery, revision evidence, cancellation, close, and diagnostics. It must not allocate native callbacks or poll native handles itself.

- [ ] **Step 4: Pin the exact resolved wgpu4k artifact**

Move the shared `0.2.0-SNAPSHOT` coordinate to one `libs.versions.toml` version/library alias and use it from all five current consumers. Generate Gradle SHA-256 dependency verification metadata for the resolved corrected artifact:

```bash
rtk ./gradlew --write-verification-metadata sha256 :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionAdapterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest'
```

Inspect `gradle/verification-metadata.xml` and require checksums for the resolved `wgpu4k-toolkit` artifact plus every platform/native runtime variant loaded by the conformance test. The conformance JSON records the declared coordinate, resolved timestamped snapshot/module metadata, every relevant artifact SHA-256, wgpu-native revision, callback mode, host/architecture, and test command. A mutable coordinate without verification checksums and resolved metadata is not accepted.

- [ ] **Step 5: Add native conformance evidence for corrected wgpu4k**

Delay callbacks beyond the registering call and allocation scope; churn memory and GC; submit ordered tickets; test success, failure, device loss, adapter close, cancellation, and many in-flight submissions. Record exact wgpu4k and wgpu-native revisions and observed callback order. If any behavior is surprising, keep the product gate closed and create a minimized wgpu4k report instead of adding a Kanvas workaround.

- [ ] **Step 6: Run queue, native, and module tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionAdapterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest'
rtk ./gradlew :gpu-renderer:test
```

Expected: all pass with the corrected dependency and `wgpu4k-completion-conformance.json` records `accepted=true`. The unavailable/refusal unit branch remains tested, but an actual conformance run that produces `accepted=false`, cannot identify the native revision, hangs, reorders callbacks, or lacks exact checksums is a hard stop for this implementation sequence.

- [ ] **Step 7: Apply the stop/go gate**

Proceed to Tasks 10–14 only when the exact pinned artifact passes the native conformance suite and the report says `accepted=true`. Otherwise commit only the explicit dependency gate/report if useful, report the minimized evidence to the wgpu4k project, and stop without a Kanvas native workaround or product route activation.

- [ ] **Step 8: Commit the accepted completion boundary**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt gradle/libs.versions.toml gradle/verification-metadata.xml gpu-renderer/build.gradle.kts gpu-renderer-scenes/build.gradle.kts integration-tests/test-utils/build.gradle.kts integration-tests/skia/build.gradle.kts integration-tests/svg/build.gradle.kts reports/upstream-rebaseline/graphite-dawn-frame-plan/wgpu4k-completion-conformance.json
rtk git commit -m 'fix: release GPU resources on real completion'
```

## Task 10: Execute offscreen frames with one encoder and one submission

**Precondition:** Task 9's pinned native completion conformance report is accepted. Do not execute this task on the unavailable/refusal-only branch.

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUSceneTarget.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutorTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

**Interfaces:**

- Consumes: accepted `PreparedGPUFrame`, `GPUSceneTarget`, prepared resources, and reserved completion ticket.
- Produces: `GPUFrameExecutor.execute(preparedFrame: PreparedGPUFrame): GPUFrameExecutionResult` with one encoder, one command buffer, one submit, then guarded host output actions.

- [ ] **Step 1: Add an instrumented executor test**

Prepare a frame containing render, bounded destination copy, render, compute/filter, target transition, and optional readback. Assert exactly one encoder creation, ordered GPU scopes matching `GPUCommandEncoderPlan`, separately ordered host actions, one finish, one command buffer, one submit, exact retained resource registration, completion arm immediately after submit, and no intermediate submission. Add stale-seal, synchronous encode failure, post-submit completion-arm failure, and callback failure paths.

- [ ] **Step 2: Add native pixel tests for copy and MSAA pass breaks**

Verify bounded copy extents and origin math, no CPU upload between passes, untouched old pixels after a partial MSAA draw and pass break, several breaks on retained attachments, logical snapshots backed by larger pooled textures, and aligned readback output.

- [ ] **Step 3: Run failing executor tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest'
```

Expected: FAIL because the current runtime exposes immediate per-operation encoding/submission and CPU-backed `snapshotTargetToOffscreenTexture()`.

- [ ] **Step 4: Implement `GPUSceneTarget` and the ordered encoder**

Own the canonical resolved texture, optional retained MSAA attachments, dimensions, format/color facts, usage, sample plan, and generation. `GPUFrameExecutor.execute(preparedFrame)` validates the seal, creates one encoder, visits each planned scope, finishes once, submits once, registers resources, arms completion, then performs guarded output actions. It cannot allocate or choose new routes.

- [ ] **Step 5: Make destination snapshots native texture-to-texture copies**

Replace the product use of `snapshotTargetToOffscreenTexture()` with planned `copyTextureToTexture` on the active encoder. Encode destination shader draws against the snapshot texture and logical-origin uniforms. Keep output readback as a final planned buffer copy.

- [ ] **Step 6: Run native and module suites**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaContinuationTest'
rtk ./gradlew :gpu-renderer:test
```

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUSceneTarget.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutorTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt
rtk git commit -m 'feat: execute offscreen scenes in one GPU submission'
```

## Task 11: Route window output through the canonical scene target

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWindowFrameLifecycleTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

**Interfaces:**

- Consumes: the canonical `GPUSceneTarget`, preflight's late surface acquisition, executor output, and independent completion state.
- Produces: a final surface-blit encoder scope plus `PostSubmitPresentAction`; presentation never completes resource lifetime.

- [ ] **Step 1: Test late acquire, surface blit, present, and completion independently**

Instrument success, lost, outdated, genuine timeout, out-of-memory, device loss, resize/generation change, throwing present, and completion failure. Assert surface acquisition is the final preflight resource action, scene rendering never targets the surface texture directly, the surface blit is the final encoded render pass, completion is armed before present, and present failure cannot release or cancel completion.

- [ ] **Step 2: Run the test against current window behavior**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWindowFrameLifecycleTest'
```

Expected: FAIL because `WgpuWindowSurface.encodeAndPresent` combines responsibilities and treats presentation as completion evidence.

- [ ] **Step 3: Split window lifecycle operations**

Expose acquire/reconfigure normalization to preflight, surface-blit encoding to `GPUFrameExecutor`, and a non-throwing captured `PostSubmitPresentAction`. On present failure, complete required surface discard/cleanup handling while leaving submitted resources retained or quarantined until real completion/device teardown.

- [ ] **Step 4: Run window/native tests and commit**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWindowFrameLifecycleTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest'
rtk ./gradlew :gpu-renderer:test
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWindowFrameLifecycleTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt
rtk git commit -m 'feat: present canonical GPU scene targets'
```

## Task 12: Migrate every Kanvas drawing API in bounded family slices

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchVertices.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendPlanTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageDispatchTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPathClipRegressionTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUImagePixelsTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAlphaImageMaterialTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUTextAtlasGeometryTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUColorGlyphPaintAlphaTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatchTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatchTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt`

**Interfaces:**

- Consumes: Canvas `DisplayOp` families, `GPUOpMapper`, canonical blend/coverage plans, recording, preflight, and `GPUFrameExecutor`.
- Produces: one normalized task/frame route for every drawing API; the temporary `GPULegacyImmediatePathAdapter` allowlist shrinks to empty and is deleted in Slice 12E.

- [ ] **Step 1: Introduce the migration boundary without changing pixels**

Make `GPUOpMapper` the only Canvas-state translator. Add `GPULegacyImmediatePathAdapter` as an explicit temporary boundary for families not yet moved: it may call the existing renderer entry points, but cannot classify blend/coverage/destination routing, create CPU snapshots, or be reachable from a migrated family. Give it a closed `LegacyDisplayOpFamily` allowlist and a dump counter so each slice proves its allowlist shrinks. Delete it in Slice 12E in the same commit that removes its final consumer.

Translate Canvas state exactly once into normalized draw/composite commands. Map all 29 blend identities without a destination-read boolean. Consume active geometry and clip planners' coverage results. Resolve save/restore into composite command tokens before renderer-core entry; do not add `save`, `restore`, matrix, or clip-stack mutation APIs to `GPUFramePlanner`.

### Slice 12A: Core primitives, paths, and clips

- [ ] **Step 2: Add failing primitive/clip inventory cases**

Create `GPUFramePathApiInventoryTest` with color/clear, points/lines, rect/rrect/DRRect/path, transform metadata, scissor, analytic/mask/stencil clip coverage, and flush/snapshot ordering. For each visual operation, assert normalized provenance, target-space bounds, geometry/clip coverage, canonical `GPUBlendPlan`, frame-step provenance, and one executor submission. Assert transforms, clips, and annotations create state/metadata only. Exercise all 29 modes through the shared matrix on these core families.

- [ ] **Step 3: Run the focused red test**

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUClipCoverageDispatchTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPathClipRegressionTest'
rtk ./gradlew :kanvas:test
```

Expected: FAIL because primitive and clip commands still dispatch through immediate renderer branches.

- [ ] **Step 4: Migrate primitives and clips, then commit**

Route these families through recording, finalization, preflight, and `GPUFrameExecutor`. Remove them from the legacy adapter allowlist. Ordinary scalar-AA `SrcOver` must remain a direct fixed-function draw without destination snapshot or fullscreen coverage-combine pass.

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUClipCoverageDispatchTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPathClipRegressionTest'
rtk ./gradlew :kanvas:test
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendPlanTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageDispatchTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPathClipRegressionTest.kt
rtk git commit -m 'refactor: route primitives and clips through frame plans'
```

### Slice 12B: Images, image-nine, lattice, and atlas

- [ ] **Step 5: Add failing image-family cases, migrate, and commit**

Extend the inventory with image, image-nine, lattice, and atlas entries. Assert normalized source/destination rectangles or per-patch transforms, sampling/material facts, per-patch conservative bounds, primitive-color composition before the final target blend, shared pipeline keys, and one frame submission. Prove mode identity reaches the same planner with representative `Src`, `SrcOver`, `Screen`, `Multiply`, `SoftLight`, and `Dst` cases; the exhaustive 29-mode formula matrix remains owned by Tasks 2–3.

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUImagePixelsTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUAlphaImageMaterialTest'
```

Expected before implementation: FAIL on the new image-family route assertions. Move those families to the common task/frame path, remove their legacy allowlist entries, rerun the focused command and `rtk ./gradlew :kanvas:test` to green, then commit:

```bash
rtk ./gradlew :kanvas:test
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUImagePixelsTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAlphaImageMaterialTest.kt
rtk git commit -m 'refactor: route image families through frame plans'
```

### Slice 12C: Text and glyphs

- [ ] **Step 6: Add failing text cases, migrate, and commit**

Extend the inventory with A8 atlas, LCD vector coverage, color glyph layers, prepared text runs, and stable dependency refusals for unavailable shaping/font assets. Assert the final target blend is unchanged by glyph preparation with representative `SrcOver`, `Multiply`, `SoftLight`, and `Dst` handoffs. Unequal LCD RGB coverage, all 29 formulas, and exact single-sample/refusal behavior remain exhaustively tested in Tasks 2–3 rather than repeated for each API family.

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUTextAtlasGeometryTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUColorGlyphPaintAlphaTest'
```

Expected before implementation: FAIL on the new text-family route assertions. Move text/glyph task payloads to the common frame path, remove their legacy allowlist entries, rerun the focused command and `rtk ./gradlew :kanvas:test` to green, then commit:

```bash
rtk ./gradlew :kanvas:test
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUTextAtlasGeometryTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUColorGlyphPaintAlphaTest.kt
rtk git commit -m 'refactor: route text and glyphs through frame plans'
```

### Slice 12D: Vertices and meshes

- [ ] **Step 7: Add failing vertices/mesh cases, migrate, and commit**

Extend the inventory with indexed/non-indexed vertices, mesh buffers, texture coordinates, optional primitive colors, primitive blend, and final target blend. Assert primitive blending happens before the canonical target `GPUBlendPlan`; no vertices-specific destination switch is allowed.

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest'
```

Expected before implementation: FAIL on the new vertices/mesh route assertions. Use representative primitive/final target blends covering fixed-function, advanced shader, and no-op handoff; do not duplicate the exhaustive formula matrix. Migrate the family, remove its legacy allowlist entries, rerun the focused command and `rtk ./gradlew :kanvas:test` to green, then commit:

```bash
rtk ./gradlew :kanvas:test
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchVertices.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt
rtk git commit -m 'refactor: route vertices and meshes through frame plans'
```

### Slice 12E: Layers, filters, masks, pictures, and backdrop composites

- [ ] **Step 8: Add failing composite-family and refusal tests**

Extend the inventory with begin/end layer and restore, filters/backdrop, masks, pictures, and child recordings. Accepted work must use explicit source/intermediate/target transitions and expanded bounds. Unsupported layer, picture, or filter work must produce one `RefusedCompositeCommandStep` containing child provenance and ordering tokens; child tasks must not leak to the parent target. Safe unsupported leaves may produce `RefusedLeafDrawStep`. Unisolatable state/order errors fail the frame atomically.

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUImageFilterDispatchTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUMaskBlurDispatchTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest'
```

Expected: FAIL because composite families still contain immediate target and refusal behavior.

- [ ] **Step 9: Migrate composites and prove the legacy allowlist is empty**

Move layer/filter/mask/picture task payloads to the common frame path. Resolve save/restore entirely in the mapper. Assert the temporary adapter's family allowlist and invocation count are both empty across the complete inventory, then delete `GPULegacyImmediatePathAdapter.kt` before the slice commit.

- [ ] **Step 10: Run the complete Kanvas GPU surface suite and commit**

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.*'
rtk ./gradlew :kanvas:test
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatchTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatchTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt
rtk git commit -m 'refactor: route GPU composites through frame plans'
```

Expected: all Kanvas GPU surface tests pass, including interiors, exteriors, AA edges, clip edges, all RGBA channels, all inventory families, and the exhaustive canonical 29-mode tests. No legacy-adapter source or consumer remains.

## Task 13: Remove superseded immediate and CPU-snapshot paths

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt`
- Delete: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationReadExecutor.kt`
- Delete: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationReadExecutorTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`

**Interfaces:**

- Consumes: the fully migrated frame route from Task 12 and the terminal output readback API.
- Produces: no new runtime interface; removes all superseded immediate, CPU destination-snapshot, duplicate blend, and evidence-only execution contracts.

- [ ] **Step 1: Add negative architecture assertions**

Assert production source contains no `snapshotTargetToOffscreenTexture`, destination-read call to `readRgba`, CPU snapshot upload, immediate per-operation submit, `destinationReadBlendModeIndex`, `clipCoverageBlendModeIndex`, `GPUDestinationReadExecutor`, legacy destination composer, coverage route boolean, or second blend planner. Permit `readRgba` only as the explicit terminal readback/output API until it is renamed to make that boundary obvious.

- [ ] **Step 2: Run the boundary test and observe legacy symbols**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest'
```

Expected: FAIL while legacy contracts and immediate helpers remain.

- [ ] **Step 3: Delete superseded contracts and implementations**

Remove `snapshotTargetToOffscreenTexture()` and its CPU read/upload implementation, `GPUDestinationReadExecutor` and its evidence-only test, immediate destination composer/executor branches in `GPUClipExecution`/`GPURenderer`, duplicate blend index switches, common-path fullscreen coverage combine shader, presentation-completion constants, evidence-only encoder plans, and adapters whose last consumers moved in Task 12. Rename terminal readback APIs if needed so production search cannot confuse output readback with a continuation path.

- [ ] **Step 4: Run architecture scan and module tests**

```bash
rtk proxy rg -n 'snapshotTargetToOffscreenTexture|destinationReadBlendModeIndex|clipCoverageBlendModeIndex|GPUDestinationReadExecutor|DestinationReadComposer|GPU_QUEUE_COMPLETION_PRESENTED|GPU_QUEUE_COMPLETION_PRESENT_FAILED|GPU_QUEUE_COMPLETION_TARGET_CLOSE' gpu-renderer/src/main/kotlin kanvas/src/main/kotlin
rtk ./gradlew :gpu-renderer:test :kanvas:test
```

Expected: scan has no matches; both module suites pass.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationReadExecutor.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationReadExecutorTest.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt
rtk git commit -m 'refactor: remove immediate GPU destination snapshots'
```

## Task 14: Validate pixels, regenerate GM evidence, and measure performance

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/TelemetryContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/PerformanceBudgetTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/FrameGateM23BaselineTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/GPUFrameStructuralTelemetryTest.kt`
- Modify: `integration-tests/skia/build.gradle.kts`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkSceneSelection.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunner.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunnerTest.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/FramePlanWindowBenchmarkOptions.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/FramePlanWindowBenchmarkOptionsTest.kt`
- Create: `integration-tests/skia/src/kadreBenchmark/kotlin/org/graphiks/kanvas/skia/KadreFramePlanBenchmark.kt`
- Create: `tools/performance/run-frame-plan-benchmarks.sh`
- Create: `tools/performance/frame-plan-benchmark.schema.json`
- Create: `tools/performance/skia-nanobench-fixed-loop-multi-sample.patch`
- Modify: `integration-tests/skia/test-similarity-scores.properties`
- Modify: `integration-tests/skia/src/test/resources/generated-renders/`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/benchmark-manifest.json`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/raw/` sample artifacts
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/report.md`
- Verify locally: `integration-tests/skia/build/reports/skia-gm-dashboard/index.html`

**Interfaces:**

- Consumes: completed frame telemetry, stable `SkiaGmRenderer.render()` readback completion, the shared GM registry/draw implementation, and pinned Graphite/Dawn source plus the one-line driver patch.
- Produces: immutable structural telemetry snapshots, schema-validated raw samples, current generated renders/scores, benchmark manifest, hashes, p50/p95 report, and promotion verdicts.

- [ ] **Step 1: Add failing structural telemetry assertions**

Count direct draws, shader draws, refusals, pass breaks, snapshot groups, copied pixels/bytes, scratch allocation/reuse/eviction, encoders, command buffers, submissions, presentation, completion, waits, `peakFrameTransientBytes`, and `targetResidentBytes`. Assert per-scene one encoder/command-buffer/submission, zero CPU destination snapshots, zero AA-only `SrcOver` copies in `hairmodes`, bounded copy area, and completion-based resource release.

- [ ] **Step 2: Run the telemetry test red**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralTelemetryTest'
```

Expected: FAIL because the executor does not yet publish the full structural counter record.

- [ ] **Step 3: Implement, verify, and commit telemetry before benchmarking**

Publish one immutable counter snapshot per completed/failed frame from `GPUFrameExecutor`; do not reconstruct counters from logs. Keep presentation and completion counters independent and include refusal/error status. Run focused plus affected suites:

```bash
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test :kanvas:test
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/TelemetryContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/GPUFrameStructuralTelemetryTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/PerformanceBudgetTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/FrameGateM23BaselineTest.kt
rtk git commit -m 'feat: record structural GPU frame telemetry'
```

Expected: BUILD SUCCESSFUL with no quarantined unexpected resource, hidden fallback, or unstable evidence dump.

- [ ] **Step 4: Add the benchmark runner test before the runner**

Test command parsing, the fixed GM set `aaxfermodes,hairmodes,xfermodes`, the offscreen/readback runner, the separate opt-in Kadre window options, 120 warmups, exactly 300 retained measured samples, 30 cold process records, completion-boundary enforcement, nanosecond monotonic durations, p50/p95 calculation over all samples, manifest revision matching, and NDJSON rows validated against `frame-plan-benchmark.schema.json`. Add a scene-identity test proving both lanes select the same `SkiaGm` objects from `SkiaGmRegistry`, invoke the same `onOnceBeforeDraw`/`draw` implementation through `GmCanvas`, and carry the same GM class name and scene fingerprint; a same-name reimplementation must fail.

```bash
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.SkiaGmBenchmarkRunnerTest'
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.FramePlanWindowBenchmarkOptionsTest'
```

Expected: FAIL because the runner, Gradle task, script, and schema do not exist.

- [ ] **Step 5: Implement and commit the fixed benchmark harness**

Add `benchmarkSkiaGms` as a `JavaExec` task whose properties are:

```text
kanvasGmBenchNames=aaxfermodes,hairmodes,xfermodes
kanvasGmBenchLane=offscreen-readback
kanvasGmBenchCacheState=warm|cold
kanvasGmBenchProcessIndex=0..29
kanvasGmBenchWarmup=120
kanvasGmBenchSamples=300
kanvasGmBenchOutput=/Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw
```

The offscreen runner emits one schema-validated NDJSON row per measured frame. For compatibility with the exact pre-implementation baseline, it starts timing immediately before the stable `SkiaGmRenderer.render()` call and stops only after that call returns its completed `SkiaRenderResult.rgba` readback. The test makes this boundary explicit: returned pixels are unavailable until GPU copy/map completion, while the candidate's `GPUQueueCompletionTicket` remains an internal implementation detail of `Surface.render()` and is never referenced by the cherry-pickable harness. The shell script, not the runner, launches 30 independent Gradle processes per GM with exactly one name (`aaxfermodes`, `hairmodes`, or `xfermodes`), `cacheState=cold`, `warmup=0`, `samples=1`, and distinct process indices. Each cold process creates its own device and caches before its only completed frame.

Keep window/present measurement separate in an opt-in `kadreBenchmark` source set and `runFramePlanKadreNativeBenchmark` task inside `integration-tests/skia`. Configure that source set to consume the exact compiled output of `sourceSets["test"]`, including `SkiaGmRegistry`, `SkiaGmBenchmarkSceneSelection`, `GmCanvas`, and every selected GM class; do not copy or recreate scenes in `kadreBenchmark`. Both runners must call the shared selector, receive the same registry-owned GM implementation, and execute its `onOnceBeforeDraw`/`draw` methods. The script self-test rejects GM class declarations or alternate scene tables under `src/kadreBenchmark`. The lane may resolve Kadre only when `-PenableKadreFrameBenchmark=true` and `external/poc-koreos` is initialized. The task uses `-XstartOnFirstThread` and `--enable-native-access=ALL-UNNAMED`; ordinary headless tests, `benchmarkSkiaGms`, and CI validation never resolve Kadre or require the submodule. If the submodule/native window is unavailable, the manifest records the window lane as informational with an explicit deviation rather than substituting offscreen results.

Check in this exact driver-only patch against Skia revision `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`:

```diff
diff --git a/bench/nanobench.cpp b/bench/nanobench.cpp
--- a/bench/nanobench.cpp
+++ b/bench/nanobench.cpp
@@ -1410,7 +1410,6 @@ int main(int argc, char** argv) {
     grContextOpts.fShaderErrorHandler = &errorHandler;
 
     if (kAutoTuneLoops != FLAGS_loops) {
-        FLAGS_samples     = 1;
         FLAGS_gpuFrameLag = 0;
     }
```

The shell script validates clean revisions, host/adapter/driver/JDK/power metadata, exact dimensions/format/color/sample plan, Graphite source and derived commits, the patch SHA-256 and one-line allowed diff, sample counts, hashes, and lane separation. Its `--self-test` uses temporary fixture output and performs no GPU benchmark or Kadre resolution. The self-test rejects a patch touching anything except the one `FLAGS_samples` assignment in `bench/nanobench.cpp`.

```bash
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.SkiaGmBenchmarkRunnerTest'
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.FramePlanWindowBenchmarkOptionsTest'
rtk tools/performance/run-frame-plan-benchmarks.sh --self-test
rtk ./gradlew :integration-tests:skia:test
rtk git add integration-tests/skia/build.gradle.kts integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkSceneSelection.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunner.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunnerTest.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/FramePlanWindowBenchmarkOptions.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/FramePlanWindowBenchmarkOptionsTest.kt integration-tests/skia/src/kadreBenchmark/kotlin/org/graphiks/kanvas/skia/KadreFramePlanBenchmark.kt tools/performance/run-frame-plan-benchmarks.sh tools/performance/frame-plan-benchmark.schema.json tools/performance/skia-nanobench-fixed-loop-multi-sample.patch
rtk git commit -m 'test: add reproducible GPU frame benchmark harness'
rtk git tag kanvas-frame-plan-benchmark-harness-2026-07-13 HEAD
```

Keep this commit source-compatible with the pre-implementation GM runner: it may depend only on the stable Skia GM registry/render result/readback surface, not new frame-plan, queue-ticket, or telemetry types. The fixed tag lets the exact harness patch be applied to a baseline-derived worktree without moving renderer changes backward.

- [ ] **Step 6: Run Skia GM tests before rebaseline**

```bash
rtk ./gradlew :integration-tests:skia:test
```

Expected: only intentional render changes attributable to the new exact frame path. Diagnose every unexpected low-similarity GM before changing a score.

- [ ] **Step 7: Regenerate renders, dashboard, and similarity scores**

Invoke the repository `regenerate-skia-dashboard` skill and follow its commands exactly:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaDashboard
rtk ./gradlew :integration-tests:skia:test
```

This writes tracked render evidence under `integration-tests/skia/src/test/resources/generated-renders/`, updates `integration-tests/skia/test-similarity-scores.properties`, and writes the local self-contained dashboard under `integration-tests/skia/build/reports/skia-gm-dashboard/`. Regenerate from the current pipeline snapshot, not from an old CPU or pre-change artifact. Confirm every score corresponds to a generated current render and no missing reference is silently accepted. The ignored `build/` dashboard is local inspection evidence and is not presented as a committed artifact; the tracked render PNGs, scores, and reports are the durable evidence.

- [ ] **Step 8: Inspect primary regression GMs**

Inspect `aaxfermodes`, `hairmodes`, and `xfermodes` at interiors, exteriors, AA edges, and clip edges. Require all RGBA channels to match the CPU oracle within the established tolerance. Record any legitimate refusal and route diagnostic; do not raise a similarity budget to hide a formula or coverage bug.

- [ ] **Step 9: Create isolated benchmark worktrees after both code commits**

On one recorded host/adapter/driver/JDK/power mode, run the same scene, dimensions, format, color space, sample plan, and completion boundary for pre-change Kanvas, candidate Kanvas, and Graphite+Dawn revision `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`. Use isolated checkouts and independent build directories. The Graphite checkout is derived from the pinned source solely by the checked-in nanobench-driver patch:

```bash
rtk git worktree add -b codex/kanvas-frame-benchmark-baseline /Users/chaos/.codex/worktrees/kanvas-frame-baseline bdbab8292a45cc44521d1642714a87feb5a8e8d2
rtk git -C /Users/chaos/.codex/worktrees/kanvas-frame-baseline cherry-pick kanvas-frame-plan-benchmark-harness-2026-07-13
rtk git worktree add --detach /Users/chaos/.codex/worktrees/kanvas-frame-candidate HEAD
rtk git -C /Users/chaos/workspace/kanvas-forge/skia-main worktree add -b codex/kanvas-frame-nanobench-driver /Users/chaos/.codex/worktrees/skia-frame-benchmark defc3a5a92966c32cb2a6a901e2fa3036a13bb8a
rtk git -C /Users/chaos/.codex/worktrees/skia-frame-benchmark apply --check /Users/chaos/.codex/worktrees/da7e/kanvas/tools/performance/skia-nanobench-fixed-loop-multi-sample.patch
rtk git -C /Users/chaos/.codex/worktrees/skia-frame-benchmark apply /Users/chaos/.codex/worktrees/da7e/kanvas/tools/performance/skia-nanobench-fixed-loop-multi-sample.patch
rtk git -C /Users/chaos/.codex/worktrees/skia-frame-benchmark add bench/nanobench.cpp
rtk git -C /Users/chaos/.codex/worktrees/skia-frame-benchmark commit -m 'bench: allow fixed-loop multi-sample runs'
```

The baseline-derived branch starts from the exact pre-implementation renderer and adds only the tagged benchmark harness commit. Before measuring, the script asserts that its diff from `bdbab8292a45cc44521d1642714a87feb5a8e8d2` contains only the harness/build-script/schema/patch paths listed in Step 5; any renderer or production dependency change refuses the baseline. The manifest records the original renderer commit, derived baseline commit, harness tag/commit, and candidate commit independently. The candidate worktree is created only after the telemetry and harness commits and its resolved commit ID is written to the manifest. The Graphite manifest records the pinned parent commit, derived local commit, exact one-file diff, and patch SHA-256; any other Skia diff refuses measurement. Never checkout, reset, or benchmark another revision inside the user's current worktree or the existing Skia checkout. Keep build directories inside their respective worktrees. Separate offscreen/readback from window/present. For each warm case, collect 120 untimed warmups and 300 measured frames; for cold diagnostics, collect 30 independent process launches. Check in all raw samples and SHA-256 hashes. Compute p50 and p95 over all 300 warm samples without deletion.

- [ ] **Step 10: Build and run the exact Kanvas and Graphite/Dawn commands**

The checked-in script runs these Kanvas commands in both isolated worktrees for each lane, with absolute output directories under the current worktree's report directory:

```bash
rtk ./gradlew :integration-tests:skia:benchmarkSkiaGms -PkanvasGmBenchNames=aaxfermodes,hairmodes,xfermodes -PkanvasGmBenchLane=offscreen-readback -PkanvasGmBenchCacheState=warm -PkanvasGmBenchProcessIndex=0 -PkanvasGmBenchWarmup=120 -PkanvasGmBenchSamples=300 -PkanvasGmBenchOutput=/Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw
rtk ./gradlew :integration-tests:skia:runFramePlanKadreNativeBenchmark -PenableKadreFrameBenchmark=true -PkadreFrameBenchNames=aaxfermodes,hairmodes,xfermodes -PkadreFrameBenchWarmup=120 -PkadreFrameBenchSamples=300 -PkadreFrameBenchOutput=/Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw
```

For Graphite/Dawn on macOS Metal, the script verifies the pinned source revision and builds nanobench. One 420-sample process preserves the required immediate sequence: samples 0–119 are labeled warmup and excluded from percentiles, while samples 120–419 are the 300 measured samples. The script also launches 30 separate one-sample nanobench processes for cold informational records:

```bash
rtk bin/gn gen out/KanvasFramePlanRelease --args='is_debug=false skia_enable_graphite=true skia_use_dawn=true skia_use_metal=true'
rtk ninja -C out/KanvasFramePlanRelease nanobench
rtk out/KanvasFramePlanRelease/nanobench --config grdawn_mtl --match '^GM_aaxfermodes$' '^GM_hairmodes$' '^GM_xfermodes$' --loops 1 --samples 420 --outResultsFile /Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw/graphite-warm-and-measured.json
rtk out/KanvasFramePlanRelease/nanobench --config grdawn_mtl --match '^GM_aaxfermodes$' --loops 1 --samples 1 --outResultsFile /Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw/graphite-aaxfermodes-cold-process-00.json
```

Execute the orchestrator from the current worktree:

```bash
rtk tools/performance/run-frame-plan-benchmarks.sh --baseline-root /Users/chaos/.codex/worktrees/kanvas-frame-baseline --candidate-root /Users/chaos/.codex/worktrees/kanvas-frame-candidate --skia-root /Users/chaos/.codex/worktrees/skia-frame-benchmark --output-root /Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan
```

Graphite's `GMBench.getUniqueName()` prefixes every selected GM with `GM_`, and `--match` consumes separate arguments; the script asserts exactly those three benchmark names were emitted. Stock nanobench forces `FLAGS_samples = 1` for explicit loops, so the versioned driver-only patch removes exactly that assignment. `--loops 1` is then required for warm and cold runs so nanobench neither auto-calibrates nor batches multiple `drawContent()` calls into one reported sample; the script requires exactly 420 samples per warm GM before splitting indices 0–119 as untimed warmup and 120–419 as measured. The displayed cold command is `aaxfermodes`, process index 00. The script repeats it with one exact prefixed `--match` at a time for `GM_aaxfermodes`, `GM_hairmodes`, and `GM_xfermodes`, each in 30 new nanobench processes with zero-padded indices 00 through 29. Each cold process reports its first and only sample. The script validates exactly 30 cold records per GM. It similarly launches 30 independent Gradle/Java processes per GM for baseline and candidate cold results. The script refuses dirty benchmark worktrees, wrong revisions, an unexpected Skia patch, missing GPU completion, sample-count mismatch, mixed lanes, unsupported window claims, or schema-invalid output. It writes raw hashes, exact commands, resolved candidate/Graphite commits, environment, p50/p95, and deviations to the manifest/report. Graphite nanobench is an offscreen comparison only; window-present results stay in their separate opt-in Kanvas/Kadre lane and are never compared to it.

- [ ] **Step 11: Apply promotion gates**

Require each primary GM to improve at least 2x at p50 and improve p95 against the fresh pre-change run. Direct-draw GMs may regress by at most 10% p50 and 15% p95. Publish candidate/Graphite ratios and target at most 2x Graphite p50 for direct-draw-dominated scenes. A failed performance gate keeps structural correctness intact but blocks performance promotion and requires a profile naming the next dominant cost.

- [ ] **Step 12: Run final full verification**

```bash
rtk ./gradlew test
rtk git diff --check
rtk git status --short
```

Expected: all tests pass; `git diff --check` is empty; status contains only explicitly intended evidence changes plus the user's pre-existing unrelated GM registry edits.

- [ ] **Step 13: Commit generated evidence explicitly**

Stage only the tracked render directory, score file, and performance report paths; telemetry and benchmark harness code were committed before candidate worktree creation. Inspect `rtk git diff --cached --stat` before committing. The dashboard under `build/` is intentionally not staged.

```bash
rtk git add integration-tests/skia/test-similarity-scores.properties integration-tests/skia/src/test/resources/generated-renders reports/upstream-rebaseline/graphite-dawn-frame-plan
rtk git diff --cached --stat
rtk git commit -m 'test: publish WebGPU frame plan evidence'
rtk git status --short
```

Expected after commit: only the user's pre-existing unrelated GM registry/service edits remain; no intended generated render, score, or report is untracked or unstaged.

---

## Final Review Checklist

- [ ] Every active authority file agrees with the approved design.
- [ ] Exactly one 29-mode blend identity and one exhaustive `GPUBlendPlan` remain.
- [ ] All scalar, stencil, MSAA, and LCD coverage routes are tested against the premultiplied CPU oracle.
- [ ] Every drawing API reaches the same planner and frame executor.
- [ ] Composite refusals cannot leak child work.
- [ ] All destination reads are bounded GPU resources or stable refusals.
- [ ] Persistent MSAA continuation preserves untouched pixels across pass breaks.
- [ ] Preflight is transactional and owns all materialization.
- [ ] One prepared scene creates one encoder, one command buffer, and one submit.
- [ ] Present and GPU completion have independent state and resource lifetime.
- [ ] The corrected unchanged wgpu4k completion API has native conformance evidence; otherwise product activation is dependency-gated with no workaround.
- [ ] CPU continuation snapshots and immediate submission helpers are absent from production source.
- [ ] GM renders and similarity scores come from the current pipeline snapshot.
- [ ] Performance evidence uses the exact common protocol and recorded revisions.
- [ ] No unrelated user-owned worktree changes are staged or modified.
