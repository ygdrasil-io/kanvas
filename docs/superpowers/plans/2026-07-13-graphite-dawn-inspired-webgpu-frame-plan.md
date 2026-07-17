# Graphite/Dawn-inspired WebGPU Frame Plan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the immediate, CPU-snapshot-capable GPU path with one Kanvas-owned WebGPU frame planner and executor that routes every drawing API through the canonical 29-mode blend/coverage decision, preserves MSAA contents across pass breaks, and records one encoder, one command buffer, and one submission per scene.

**Architecture:** `NormalizedDrawCommand` and `GPUTaskList` remain semantic authorities. A pure `GPUFramePlanner` linearizes their accepted work into an immutable handle-free `GPUFramePlan`; `GPUFramePreflighter` materializes all resources transactionally; `GPUFrameExecutor` encodes the prepared frame once. Offscreen and window paths share a canonical `GPUSceneTarget`. The implementation borrows Graphite/Dawn's separation between recording, resource preparation, encoding, submission, presentation, and completion, without porting Graphite or introducing a second backend.

**Tech Stack:** Kotlin/JVM, Gradle, wgpu4k/WebGPU, WGSL, JUnit 5, Skia GM integration tests, shell evidence commands prefixed with `rtk`.

## Global Constraints

- The approved design is `docs/superpowers/specs/2026-07-13-graphite-dawn-inspired-webgpu-frame-plan-design.md`; if code pressure contradicts it, stop and amend the design before changing semantics.
- Read `.upstream/specs/gpu-renderer/README.md` and the authority files changed by Task 1 before each implementation slice. The synchronized active specs win over historical tickets and Git-history plans.
- Before Tasks 2, 4, and 12 change geometry or coverage, read `.upstream/specs/geometry-coverage/README.md`. Before Task 3 changes WGSL parsing/reflection/generated shaders, read `.upstream/target/high-performance-wgsl-pipeline-target.md` and `.upstream/specs/wgsl-pipeline/README.md`. Before Tasks 11, 12, and 14 change real-time rendering, GMs, Kadre, performance tiers, or release evidence, read `.upstream/target/skia-like-realtime-renderer-target.md` and `.upstream/specs/skia-like-realtime/README.md`. Record these reads in the task ledger; if any active authority conflicts with this design/plan, stop and amend the design plus plan before code.
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

Extend `GPURendererPackageBoundaryTest` so it reads the active spec files and asserts the synchronized vocabulary is present exactly once as authority: `GPUBlendPlan`, `GPUFramePlan`, `GPUFrameCoordinator`, `GPUFramePreflighter`, `PreparedGPUFrame`, `GPUSceneTarget`, `GPUQueueCompletionTicket`, `LCDCoverage`, and `RefusedCompositeCommand`. Assert that active specs do not authorize CPU destination snapshots, presentation-as-completion, a second blend-mode enum, or materialization decisions before the final `GPUTaskList`/`GPUFramePlan` order is known.

- [ ] **Step 2: Run the test and confirm the authority is currently inconsistent**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest'
```

Expected: FAIL because the existing active specifications still contain the old LCD refusal, immediate submission/completion wording, and narrower blend authority. On the current baseline, the same test also reports the pre-existing `commands -> filters -> commands` package cycle.

- [ ] **Step 3: Apply the approved amendments to the active specs**

Make these ownership statements normative:

- `GPUTaskList` is dependency authority; `GPUFramePlan` is its deterministic linear execution schedule.
- `GPUFrameCoordinator` is the sole product entry across planner finalization, preflight, and execution. It performs no route decision and preserves planning/preflight refusals as terminal frame outcomes; no scene or surface product entry may bypass it.
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
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapter.kt`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapterTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchVertices.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatch.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatch.kt`
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
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendPlanTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAlphaImageMaterialTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageDispatchTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatchTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatchTest.kt`

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
    ) : GPUBlendPlan {
        override val destinationReadRequirement =
            GPUBlendDestinationReadRequirement.DestinationTextureRequired
    }

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
    ) : GPUBlendPlan {
        override val destinationReadRequirement = GPUBlendDestinationReadRequirement.Refused
    }
}

enum class GPUBlendDestinationReadRequirement {
    None,
    DestinationTextureRequired,
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

Keep the 29 enum constants and `gpuLabel`; remove `colorSrcFactor`, `colorDstFactor`, alpha factors, and `requiresDestinationRead`. Move exact attachment operations, factors, fragment-output encoding, target-format gates, formula IDs, and refusal codes into `GPUBlendPlanning.kt`. Model color and alpha operations independently. Include formula identity and coverage topology in the pipeline-key input; exclude concrete texture identity, snapshot origin, and logical bounds. Change every runtime recording contract that currently accepts `GPUBlendMode?` to accept the already prepared `GPUFixedFunctionBlendState?` from `GPUBlendPlan.FixedFunctionBlend.state`. Migrate all current callers in the same task: vertex position draws in `GPUDispatchVertices`, filter and blur composite passes in `GPUImageFilterDispatch`/`GPUMaskBlurDispatch`, and DST_IN/DST_OUT mask/stencil passes in `GPUClipCoverage`. Each caller receives the state selected by the canonical plan, or an explicitly prepared canonical fixed-state constant for an internal composite whose semantics are already fixed; none passes a `GPUBlendMode` to runtime. Shader and destination-read plans pass no attachment blend state. Native conversion may translate the supplied operations/factors to wgpu4k values, but it must delete `blendStateFor(GPUBlendMode?)` and never infer attachment state from the enum.

- [ ] **Step 4: Remove the smaller enum and route-driving booleans**

Delete `state.GPUBlendMode`, `destination.GPUDestinationReadRequirement`, and every blend-specific allowlist/plan contract from foundation `StateContracts.kt`. Move blend semantics and their evidence adapter into `passes/GPUBlendPlanning.kt` beside the canonical mode identity; `state` keeps only backend-neutral target/alpha/fixed-state components that do not import `passes`. Change every destination/intermediate/scene import to the canonical `passes` types. Replace `GPUBlendFacts(kind, modeLabel, requiresDestinationRead, blendMode)` with identity plus non-routing source facts:

```kotlin
data class GPUBlendFacts(
    val mode: GPUBlendMode,
    val sourceAlpha: GPUSourceAlphaClassification,
)
```

Migrate every production consumer in `analysis`, `paintblend`, `vertices`, `GPUBackendRuntimeContracts`, native state conversion, and the current Kanvas mapper/clip/renderer bridge in this task. `GPUPrimitiveBlendPlan` stores the canonical primitive blend plan rather than its own boolean. Kanvas may keep an explicit temporary function that asks the canonical plan for `destinationReadRequirement`, but it may not recreate a mode table or cache a boolean. Keep compatibility constructors only inside this commit and delete them before committing so downstream modules compile at every task boundary.

- [ ] **Step 5: Update allowlist/intermediate evidence to consume `GPUBlendPlan`**

`GPUBlendAllowlistPlanner` becomes an evidence adapter over `GPUBlendPlanner`, not a second decision table. Make `GPUIntermediatePlanner` independent of the `destination` package: it produces typed eligible-intermediate facts and identities from the canonical blend requirement but never imports a destination plan or chooses a destination strategy. `GPUDestinationReadStrategyPlanner` consumes those facts and is the only owner that chooses `TargetCopy`, `ExistingIntermediate`, `LayerIsolation`, or refusal. It may not reinterpret the formula or blend mode. This fixes the dependency direction to `destination -> intermediates` only, which is required before Task 5 imports `GPUIntermediateIdentity`. Preserve stable diagnostics and hashes where semantics are unchanged; intentionally update snapshots where `Screen`, `Plus`, or coverage routing changes. Run `GPURendererPackageBoundaryTest` in this task and require zero package-cycle violations.

- [ ] **Step 6: Run focused and module tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUBlendCoveragePlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUBlendAllowlistPlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.destination.DestinationReadStrategyGateTest' --tests 'org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest' --tests 'org.graphiks.kanvas.gpu.renderer.vertices.VerticesRouteDecisionTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUBlendPlanTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUAlphaImageMaterialTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUClipCoverageDispatchTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUImageFilterDispatchTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUMaskBlurDispatchTest'
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test :kanvas:test
rtk proxy rg -n 'blend\.requiresDestinationRead|mode\.requiresDestinationRead|plan\.requiresDestinationRead|colorSrcFactor|colorDstFactor|alphaSrcFactor|alphaDstFactor' gpu-renderer/src/main/kotlin gpu-renderer-scenes/src/main/kotlin kanvas/src/main/kotlin
rtk proxy rg -n 'enum class GPUBlendMode' gpu-renderer/src/main/kotlin gpu-renderer-scenes/src/main/kotlin kanvas/src/main/kotlin
rtk proxy rg -n '^import org\.graphiks\.kanvas\.gpu\.renderer\.destination\.' gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates
```

Expected: tests pass. The first and third scans have no matches. The second scan shows exactly one enum declaration in `passes/GPUBlendMode.kt`; unrelated layer-isolation facts named `requiresDestinationRead` are not blend-routing authorities and may remain until layer contracts are typed in Task 12.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendMode.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaa.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/paintblend/PaintBlendExecutionContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/VerticesContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapter.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapterTest.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchVertices.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatch.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatch.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererLayoutSurfaceTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadLiveMaterializationTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadMaterializationPreimageTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadStrategyGateTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendCoveragePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/state/BlendAllowlistGateTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendAllowlistPlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/paintblend/PaintBlendExecutionBoundaryTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/VerticesRouteDecisionTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendPlanTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAlphaImageMaterialTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageDispatchTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatchTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatchTest.kt
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
- Modify: `gradle/libs.versions.toml`
- Create: `gradle/verification-metadata.xml`
- Modify: `gpu-renderer/build.gradle.kts`
- Modify: `kanvas/build.gradle.kts`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUBlendFormulaSurfaceTest.kt`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/wgsl4k-conformance.json`

**Interfaces:**

- Consumes: the stable `formulaId` and scalar/vector coverage topology selected by `GPUBlendPlan`.
- Produces: one production formula registry consumed by `BlendWgslBuilder`, plus the independent test-only `GPUBlendCpuOracle` used for RGBA acceptance.

- [ ] **Step 1: Freeze and validate the exact wgsl4k input before implementation**

Move both hard-coded wgsl4k coordinates in `gpu-renderer` and `kanvas` to shared version-catalog aliases. Resolve the exact `wgsl-core-jvm` and `wgsl-parser-jvm` snapshot artifacts used by Gradle and record their versions, timestamped module metadata, repository provenance, checksums, and the exact corresponding wgsl4k source revision in `wgsl4k-conformance.json`. Correlation to a source revision is mandatory for mutable snapshots; if it cannot be proven from resolved metadata and published provenance, the stop/go gate stays closed and the minimized dependency report is the only output.

Create dependency verification metadata immediately, before any formula implementation, by resolving both affected module suites:

```bash
rtk ./gradlew --write-verification-metadata sha256 :gpu-renderer:test :kanvas:test
rtk ./gradlew :gpu-renderer:test :kanvas:test
```

Inspect the metadata diff and require the exact resolved wgsl4k artifacts/checksums. From this point Tasks 3–8 run under dependency verification, so a mutable snapshot replacement fails closed instead of silently changing the accepted parser. Run the existing parser/reflection ABI tests and native shader-module validation against those exact artifacts before changing formula generation. Task 9 extends this same global metadata file to the complete headless test graph; it does not recreate it.

Commit and tag this dependency-only gate separately so isolated baseline worktrees can reproduce the same verified build inputs without receiving formula or renderer changes:

```bash
rtk git add gradle/libs.versions.toml gradle/verification-metadata.xml gpu-renderer/build.gradle.kts kanvas/build.gradle.kts reports/upstream-rebaseline/graphite-dawn-frame-plan/wgsl4k-conformance.json
rtk git commit -m 'build: pin WGSL parser inputs for frame planning'
rtk git tag kanvas-frame-plan-wgsl4k-dependency-2026-07-13 HEAD
```

This is a stop/go gate symmetric with the wgpu4k gate in Task 9. If parsing, reflection, IR, or generation is ambiguous or surprising, stop Task 3 and every dependent task, preserve a minimized shader/module reproducer, and report it as a wgsl4k issue to the maintainer. Do not add a Kanvas-only parser workaround or silently infer behavior.

- [ ] **Step 2: Add CPU-oracle and generated-WGSL tests for every formula ID**

For all 29 modes, compare premultiplied CPU reference results at coverage `0f`, `0.25f`, `0.5f`, and `1f` for transparent, translucent, and opaque destinations. Add unequal LCD coverage vectors such as `(0.15, 0.55, 0.9)` and assert per-channel `D + F * (Blend(S,D) - D)` plus alpha equal to the maximum of the three interpolated alpha values. Assert `Dst` emits no shader route and LCD never accepts scalar encoding.

Implement `GPUBlendCpuOracle` only in test source, independently of production formula IDs, route tables, and WGSL string assembly. Port the already independent advanced-mode reference formulas from `GPUBlendFormulaSurfaceTest`/`GPUAllApiBlendSurfaceTest`, add fixed golden edge cases for zero alpha, division boundaries, saturation, and hue/luminosity transfer, and compare native shader pixels to that oracle. A registry-owned evaluator may aid diagnostics but can never be an acceptance oracle.

For each fixed-function/shader/coverage topology, assemble the complete production WGSL module, parse and reflect it through wgsl4k, compare reflected bindings/entry points/layout to the declared ABI, and create the native shader module/pipeline in the existing validation harness. Snippet-only string assertions do not count as acceptance.

- [ ] **Step 3: Run tests and confirm formula ownership is still split**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibraryTest' --tests 'org.graphiks.kanvas.gpu.renderer.text.GPUSubpixelLcdTest'
```

Expected: FAIL because `GPUBlendFormulaLibrary` does not exist and LCD is not connected to the canonical plan.

- [ ] **Step 4: Implement one formula registry**

Expose stable formula IDs, WGSL function bodies, binding topology, and scalar/vector coverage kind from one production registry. Make `BlendWgslBuilder` assemble the selected formula rather than switch on blend mode. Move the destination formula body out of `kanvas/GPUWgsl.kt`; keep only complete shader programs assembled from gpu-renderer-provided snippets until Task 12 removes that compatibility host. Do not expose production CPU expected-color logic from this registry.

- [ ] **Step 5: Bind `GPUSubpixelLCDPlan` to canonical vector coverage**

Translate accepted LCD text facts into `GPUCoverageConsumption.LCDCoverage`; keep the existing refusal until the target is single-sample and the plan has a destination strategy. Do not normalize RGB coverage to one scalar.

- [ ] **Step 6: Run focused and cross-module tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibraryTest' --tests 'org.graphiks.kanvas.gpu.renderer.text.GPUSubpixelLcdTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUBlendFormulaSurfaceTest'
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.wgsl.WGSLParserBackedReflectionTest' --tests 'org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleAbiTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeWgslValidationTest'
rtk ./gradlew :gpu-renderer:test :kanvas:test
```

Expected: all pass with all RGBA channels checked.

- [ ] **Step 7: Commit**

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
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/coordinates/CoordinateContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/IntermediateContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuationTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUFrameMemoryBudgetTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/color/SDRColorBoundaryTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanContractsTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendCoveragePlannerTest.kt`

**Interfaces:**

- Consumes: the minimal `GPUSamplePlan`, typed target/device generation, color format/interpretation, and blend destination-read facts.
- Produces: `GPUSampleContinuationKey`, an explicit load transition plus
  independent store and canonical-resolve actions, and
  `GPUFrameMemoryBudgetPlan` consumed by destination grouping, resources,
  preflight, and telemetry.

Canonical ownership is fixed before Tasks 5–8 so implementers do not invent duplicate identities or package cycles:

| Type family | Canonical owner | Creation task |
|---|---|---|
| `GPUPixelBounds` | `coordinates/CoordinateContracts.kt` | Task 4 |
| `GPUTargetIdentity` | `state/StateContracts.kt` | Task 4 |
| `GPUIntermediateIdentity` | `intermediates/IntermediateContracts.kt` | Task 4 |
| `GPUDeviceGenerationID` | `capabilities/CapabilityContracts.kt` | Task 4 |
| `GPUColorFormat`, `GPUColorInterpretation` | `color/ColorContracts.kt` | Task 4 |
| `GPUTargetAccess`, `GPUDestinationReadMember`, snapshot group/key/result, and `SnapshotGroupingCostModel` | `destination/GPUDestinationSnapshotGrouping.kt` | Task 5 |
| Frame resource/texture/buffer/target refs, resource roles/usages/lifetimes/uses, preparation requests, and upload/copy layouts and regions | `resources/ResourceContracts.kt` | Task 6 |
| Frame IDs, task/use/provenance tokens, recording seals, frame/output refs, compute dispatch descriptors, target-transition kinds, surface-output descriptors, and readback request IDs | `recording/GPUFramePlan.kt` | Task 6 |
| `GPUDrawCommandID`, `GPUDrawPacket`, `GPULoadStorePlan` | Existing `commands`, `passes`, and `state` owners | Reused; not redefined |
| `PreparedGPUFrame`, prepared generation/resource/rollback types, completion ticket/provider, and preflight result | `execution/PreparedGPUFrame.kt` plus `execution/GPUQueueCompletionAdapter.kt` | Task 8 |
| Diagnostics for frame refusal/preflight/execution | existing `diagnostics.GPUDiagnostic` | Reused; no new `GPUFrameDiagnostic` |

All identities and references are validated, handle-free value objects. Bounds and cross-frame target/intermediate identities live in earlier domain packages because destination grouping needs them before frame-plan finalization. The package edge is strictly `recording -> resources`: handle-free resource references/descriptors live in `resources/ResourceContracts.kt`, where the concrete provider can consume them without importing `recording`; frame-order and output concepts remain in `recording`. Neither package may leak native wgpu4k handles.

- [ ] **Step 1: Add continuation and budget failures first**

Test that a pass break can preserve untouched pixels only when the same stored MSAA color/depth-stencil attachment and target generation continue. Reject a fresh transient attachment with preserve-load. Test several pass breaks, layer sample-plan isolation, target generation mismatch, and advanced blend refusal when exact single-sample lowering is not proven. Extend the blend planner matrix tests so `SingleSampleFrame` accepts its scalar exact route while every multisample continuation either uses an exact attachment route or returns `unsupported.blend.msaa_destination_read_exactness`.

Budget tests must account separately for canonical target bytes, retained and frame-local MSAA color/depth-stencil, layer/filter targets, snapshots, readback staging, and reusable scratch. Verify a 4K 4x attachment set cannot bypass the peak budget by being target-owned.

- [ ] **Step 2: Run focused tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaContinuationTest' --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetTest'
```

Expected: FAIL because continuation and aggregate budget types do not exist.

- [ ] **Step 3: Implement explicit continuation**

Evolve the minimal `GPUSamplePlan` from Task 2 with fresh-clear and
retained-load transitions plus separate store/discard and
resolve-canonical/skip actions in a pure plan. A resolve must not erase the
stored-attachment continuation proof. Put target identity and generation in a
typed continuation key. Define handle-free `GPUDeviceGenerationID` in
`capabilities/CapabilityContracts.kt`, `GPUColorFormat` and
`GPUColorInterpretation` in `color/ColorContracts.kt`, `GPUPixelBounds` in
`coordinates/CoordinateContracts.kt`, `GPUTargetIdentity` in
`state/StateContracts.kt`, and `GPUIntermediateIdentity` in
`intermediates/IntermediateContracts.kt`. Their focused contract tests validate
non-empty/stable identities and checked bounds. Semantic planning packages use
these foundation types and never import `execution.GPUDeviceGeneration`. Make
ordinary single-sample plans explicit so `GPUBlendPlanner` and
`GPUIntermediatePlanner` distinguish an exact single-sample frame from a local
resolve approximation; neither planner may inspect sample-count integers
independently.

- [ ] **Step 4: Implement checked aggregate accounting**

Return `peakFrameTransientBytes`, `targetResidentBytes`, category totals, device-limit facts, and a stable diagnostic. Remove the fixed 16 MiB per-copy ceiling as a feature-support decision; retain only validated device limits and configured aggregate budget.

- [ ] **Step 5: Run affected suites**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaContinuationTest' --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetTest' --tests 'org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest'
rtk ./gradlew :gpu-renderer:test
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaa.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuation.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUFrameMemoryBudget.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/color/ColorContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/coordinates/CoordinateContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/IntermediateContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendPlanning.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuationTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUFrameMemoryBudgetTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/color/SDRColorBoundaryTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanContractsTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUBlendCoveragePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateMsaaPlanTest.kt
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

Keep the blend planner's semantic result limited to `None`, `DestinationTextureRequired`, or `Refused`. `GPUDestinationReadStrategyPlanner` is the sole owner that maps `DestinationTextureRequired` to a target snapshot, existing intermediate, layer isolation, or typed refusal using target/order/layer facts. Model `CopyAsDrawMaterialization` only as a materialization of a target snapshot when a future real source is texturable but lacks `CopySrc`; do not expose it as another blend strategy. Require canonical scene/layer targets to include `CopySrc`.

- [ ] **Step 4: Implement deterministic grouping and dumps**

Use checked arithmetic for pixel area and aligned byte cost. Reject union inflation above `2.0` and aggregate frame-budget overflow. Until a checked-in microbenchmark provides versioned copy/pass-break/scratch cost constants, select the conservative Graphite-like policy of one bounded copy per destination-reading draw and disable cross-draw snapshot sharing; missing calibration is not a draw refusal. Store logical copy origin/extent independently of later backing allocation. Never use wall time or allocation identity in a decision.

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
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcher.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcherTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`

**Interfaces:**

- Consumes: the finalized `GPUTaskList`, canonical `GPUBlendPlan`, destination grouping results, typed provenance, and resource preparation requests.
- Produces: `GPUFramePlanner.plan(taskList: GPUTaskList): GPUFramePlan`, whose closed ordered `GPUFrameStep` list is the sole semantic input to preflight.

- [ ] **Step 1: Write deterministic frame-plan tests**

Cover compatible recording insertion, preserved task IDs/dependencies/phase order, cycle rejection, replay-key mismatch, pass/destination-copy/pass sequencing, layer transitions, readback/output steps, direct draw batching, target hazards, and repeatable dump/hash output. Add refusal cases for isolated leaf, whole composite command with child provenance consumed, and atomic escalation when order cannot be preserved. Assert `CopyAsDrawMaterializationStep` is emitted only when the capability record says the implementation is present; canonical `CopySrc` targets never need it, and an otherwise requested route refuses with `unsupported.destination_read.copy_unavailable` before preflight. Extend `GPURendererPackageBoundaryTest` to prove `resources` imports neither `recording` nor `execution`, `recording` imports only handle-free resource contracts rather than the concrete provider/materialized handles, and the complete package graph remains acyclic.

The plan snapshots every nested collection into JVM-immutable storage. Its canonical hash and
human-readable dump preserve the frame capability seal, recording seals, `phaseOrder`, complete
`GPUTaskDependency` evidence, memory budget, internal render batches, exact Task 5 destination
group keys/consumers, intentionally elided `NoOp` draw evidence, steps, and diagnostics. The dump
includes the complete handle-free capability-seal facts, not only its hash. Structural boundaries,
nullable values, and typed resource-ref variants remain distinguishable in the hash preimage.
While a child target scope is active, every target-touching render, compute, copy, upload,
destination-snapshot, preparation, or readback task may touch only that active child; after
`CompositeChild`, only the exact `ReturnToParent` transition is legal. Invalid scopes refuse the
frame atomically. An atomically refused plan has no steps and carries a terminal diagnostic.

A Task 5 snapshot is materialized before the first exact packet consumer only when no non-consumer
packet or task can write its source or snapshot during the consumer lifetime. A destination
consumer absorbed by a refused composite is rejected atomically instead of losing its Task 5
evidence. Consumer bindings must target the exact Task 5 target and remain strictly ordered by the
Task 5 access order and the actual `(task, packet)` execution order. All packets inside one
`RenderPassStep` share one `targetStateHash`; a change cuts the render pass. After any physical pass
cut on an already opened target/sample/segment, the continuation uses `load` with no clear color;
subsequent semantic clears must be explicit clear packets rather than repeated attachment clears.

Use this closed step algebra:

```kotlin
sealed interface GPUFrameStep {
    val sourceTaskIds: List<GPUTaskID>
    val executionKind: GPUFrameStepExecutionKind

    class RenderPassStep(
        val target: GPUFrameTargetRef,
        val loadStore: GPULoadStorePlan,
        val samplePlan: GPUSamplePlan,
        val drawPackets: List<GPUDrawPacket>,
        val batches: List<GPUFrameRenderBatch>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class ComputePassStep(
        val target: GPUFrameTargetRef,
        val resourceUses: List<GPUFrameResourceUse>,
        val dispatches: List<GPUComputeDispatch>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class PrepareResourcesStep(
        val requests: List<GPUResourcePreparationRequest>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Preflight
    }

    class UploadResourceStep(
        val staging: GPUFrameBufferRef,
        val destination: GPUFrameResourceRef,
        val layout: GPUUploadLayout,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class CopyResourceStep(
        val source: GPUFrameResourceRef,
        val destination: GPUFrameResourceRef,
        val regions: List<GPUResourceCopyRegion>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class DependencyBarrierStep(
        val orderedUseTokens: List<GPUTaskUseToken>,
        val reasonCode: String,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly
    }

    class CopyDestinationStep(
        val source: GPUFrameTargetRef,
        val sourceKey: GPUDestinationSnapshotGroupKey,
        val snapshot: GPUFrameTextureRef,
        val logicalBounds: GPUPixelBounds,
        val copyLayout: GPUTextureCopyLayout,
        val consumers: List<GPUDestinationSnapshotConsumerRef>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class CopyAsDrawMaterializationStep(
        val source: GPUFrameTextureRef,
        val sourceKey: GPUDestinationSnapshotGroupKey,
        val sourceIntermediate: GPUIntermediateIdentity,
        val snapshot: GPUFrameTextureRef,
        val logicalBounds: GPUPixelBounds,
        val capabilitySealHash: String,
        val consumers: List<GPUDestinationSnapshotConsumerRef>,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class TargetTransitionStep(
        val parent: GPUFrameTargetRef,
        val child: GPUFrameTargetRef,
        val transitionKind: GPUTargetTransitionKind,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly
    }

    class ReadbackCopyStep(
        val source: GPUFrameTargetRef,
        val staging: GPUFrameBufferRef,
        val request: GPUFrameReadbackRequest,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class AcquireSurfaceOutput(
        val descriptor: GPUSurfaceOutputDescriptor,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Preflight
    }

    class SurfaceBlitRenderPassStep(
        val scene: GPUFrameTargetRef,
        val output: GPUSurfaceOutputRef,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class PostSubmitPresentAction(
        val output: GPUSurfaceOutputRef,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.PostSubmitHost
    }

    class RefusedLeafDrawStep(
        val commandId: GPUDrawCommandID,
        val diagnostic: GPUDiagnostic,
        override val sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val executionKind = GPUFrameStepExecutionKind.RefusalEvidence
    }

    class RefusedCompositeCommandStep(
        val commandId: GPUDrawCommandID,
        val provenanceTokens: List<GPUCompositeProvenanceToken>,
        val diagnostic: GPUDiagnostic,
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

class GPUFramePlan(
    val frameId: GPUFrameID,
    val capabilitySeal: GPUFrameCapabilitySeal,
    val recordingSeals: List<GPURecordingSeal>,
    val dependencies: List<GPUTaskDependency>,
    val phaseOrder: List<GPUTaskPhase>,
    val elidedNoOpDraws: List<GPUFrameElidedNoOpDraw>,
    val steps: List<GPUFrameStep>,
    val memoryBudget: GPUFrameMemoryBudgetPlan,
    val diagnostics: List<GPUDiagnostic>,
    val atomicallyRefused: Boolean,
)

data class GPUFrameElidedNoOpDraw(
    val taskId: GPUTaskID,
    val packetId: GPUDrawPacketID,
    val commandId: GPUDrawCommandID,
    val mode: GPUBlendMode,
    val reason: String,
)

data class GPUFrameReadbackRequest(
    val requestId: GPUReadbackRequestID,
    val sourceBounds: GPUPixelBounds,
    val pixelFormat: GPUReadbackPixelFormat,
    val outputColorInterpretation: GPUColorInterpretation,
    val bufferOffsetBytes: Long = 0L,
)

@JvmInline value class GPUFrameID(val value: Long)
@JvmInline value class GPUTaskID(val value: String)
@JvmInline value class GPUReadbackRequestID(val value: String)
@JvmInline value class GPUTaskUseToken(val value: String)
@JvmInline value class GPUCompositeProvenanceToken(val value: String)

enum class GPUReadbackPixelFormat { Rgba8Unorm }
```

Define the referenced handle-free resource algebra in
`resources/ResourceContracts.kt`, including `GPUFrameResourceRef` and its
texture/buffer/target refinements, `GPUFrameResourceRole`,
`GPUFrameResourceUsage`, `GPUFrameResourceLifetime`, `GPUFrameResourceUse`,
`GPUResourcePreparationRequest`, `GPUUploadLayout`, `GPUTextureCopyLayout`, and
`GPUResourceCopyRegion`. `GPUResourcePreparationRequest.resource` uses that
same resource-owned `GPUFrameResourceRef`. Do not define any of them in
`recording`, and do not let `resources` import frame steps or recording seals.
For texture and target preparation, carry a typed handle-free
`GPUFrameTextureDescriptor` with non-empty logical pixel bounds, typed color
format, and positive sample count. The request's typed usage set completes the
scratch-pool key; device generation remains a preflight context fact. Task 7
must not reconstruct format, sample count, or logical extent from labels.

- [ ] **Step 2: Run the planner test**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlannerTest'
```

Expected: FAIL because the finalizer does not exist.

- [ ] **Step 3: Evolve task payloads without adding a second route decision**

Add typed target, access, pass, destination, output, refusal-scope, and provenance facts to the existing `GPUTask` variants. Replace task-ID strings and resource-plan labels at this boundary with validated `GPUTaskID` and `GPUResourcePreparationRequest` values. Map `PrepareResources` to preflight work, `Upload` and general `Copy` to encoder steps using prepared staging resources, and `Barrier` to a dependency-only step that produces no fake WebGPU barrier command. Preserve each source task ID in canonical source order in the resulting step even when the step is not encodable. The sole exception is a canonical `GPUBlendPlan.NoOp` with a required non-blank audit reason: omit encoder work and preserve its task, packet, command, mode, and reason in ordered `GPUFrameElidedNoOpDraw` evidence included in dump/hash. Do not use unordered sets in deterministic evidence. `GPUFramePlanner` may validate and linearize; it must consume the already chosen `GPUBlendPlan`, preserve refused tasks, and reject cycles/incompatible seals atomically.

- [ ] **Step 4: Reuse pass-local batching**

Keep `GPUPassBatcher` responsible only for adjacent compatible `GPUDrawPacket` values inside a provisional render-pass segment. The frame planner owns copies, compute scopes, target transitions, output, and lifetime ordering. The handle-free plan carries `GPUFrameReadbackRequest`, never `execution.GPUReadbackLayout`. Preflight computes padding and lowers packets to the post-materialization `GPUPassCommandStream` in Task 8.

- [ ] **Step 5: Run recording/pass tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.recording.*' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketCommandStreamTest'
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest'
rtk proxy rg -n '^import org\.graphiks\.kanvas\.gpu\.renderer\.(recording|execution)\.' gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources
rtk proxy rg -n '^import org\.graphiks\.kanvas\.gpu\.renderer\.resources\.(GPUConcreteResourceProvider|GPUMaterialized|GPUPrepared)' gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording
rtk ./gradlew :gpu-renderer:test
```

Expected: both scans have no matches and the package-boundary test proves the
only frame-plan/resource edge is `recording -> resources` through handle-free
contracts.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanner.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcher.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPassBatcherTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt
rtk git commit -m 'feat: finalize recordings into a linear GPU frame plan'
```

## Task 7: Add pooled scratch textures and exact readback layout

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePool.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUReadbackLayout.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePoolTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUReadbackLayoutTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilitiesTest.kt`

**Interfaces:**

- Consumes: `GPUFrameMemoryBudgetPlan`, typed scratch descriptors, completion ownership, canonical handle-free `GPUFrameReadbackRequest`, and `GPUCapabilities`.
- Produces: completion-safe opaque scratch leases, a distinct output-owned readback staging lease, and `GPUReadbackLayoutPlanner.plan(request: GPUFrameReadbackRequest, capabilities: GPUCapabilities): GPUReadbackLayoutPlan`.

- [ ] **Step 1: Test logical/backing separation and completion-safe reuse**

Request non-power-of-two logical textures and use Graphite's small `GetApproxSize`
policy independently on width and height: minimum 16, next power of two through
1024, then alternating 1.5x-power-of-two and power-of-two classes. Verify
independent logical origin/extent and backing extent, exact normalized
backing/format/usage/sample/device-generation keys, no alias between overlapping
lifetimes, reuse only after accepted GPU completion, deterministic
completed-resource LRU eviction without wall time, budget refusal details, and
device-generation invalidation. Count normalized backing bytes, never logical
bytes, against the aggregate budget. Logical bounds and semantic resource role
do not participate in the reuse key.

- [ ] **Step 2: Test WebGPU readback layout arithmetic**

Cover widths whose unpadded rows are and are not multiples of the facade-provided `copyBytesPerRowAlignment`, non-zero offsets, rows-per-image, checked overflow, exact minimum buffer size, and row depadding back to tightly packed RGBA. Preserve the caller's `bufferOffsetBytes` exactly and reject an RGBA8 offset that is not a multiple of 4; never round it silently. Compute the minimum Dawn copy size as `offset + paddedBytesPerRow * (height - 1) + unpaddedBytesPerRow`; keep any larger pooled backing-buffer size as a separate fact. Map from offset zero and depad each row from `bufferOffset + y * paddedBytesPerRow`, avoiding an artificial map-offset multiple-of-8 constraint. Test the observed WebGPU value `256` and a fake capability value `512` so the implementation cannot hard-code 256. Use `Long` for intermediate arithmetic and reject non-positive/non-power-of-two alignment values, values that cannot fit WebGPU `UInt` row fields, values above `GPULimits.maxBufferSize`, and tightly packed or padded results that cannot fit current host `ByteArray` sizes. Model the staging lease lifecycle as `Reserved -> Submitted -> GPUCompletedMappingPending -> Mapped -> Depadded -> Releasable`, plus `MapFailed -> Releasable|Quarantined`. Delay mapping after accepted queue completion, request an identical staging buffer from another frame, and prove there is no reuse until map/unmap/depad terminates.

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

sealed interface GPUReadbackLayoutPlan {
    data class Planned(val layout: GPUReadbackLayout) : GPUReadbackLayoutPlan
    data class Refused(val diagnostic: GPUDiagnostic) : GPUReadbackLayoutPlan
}

class GPUReadbackLayoutPlanner {
    fun plan(request: GPUFrameReadbackRequest, capabilities: GPUCapabilities): GPUReadbackLayoutPlan
}
```

- [ ] **Step 3: Run new tests and confirm missing implementations**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUScratchTexturePoolTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackLayoutTest'
```

Expected: FAIL to compile.

- [ ] **Step 4: Implement pool ownership through `GPUResourceProvider`**

Return opaque resource references and lease metadata; add a concrete opaque
`GPUBufferResourceRef` rather than reusing the logical `GPUFrameBufferRef`, and
never expose raw wgpu handles to the semantic plan. Make the pool consume the
aggregate frame budget from Task 4 and emit category-complete diagnostics.
Only completion-accepted leases are reusable or evictable; submitted,
quarantined, device-invalidated, and output-owned readback leases are not.
Evict by a deterministic monotone-use token, never wall time.

- [ ] **Step 5: Implement readback packing/depacking**

Keep map/copy completion in the output path only. Readback may observe a completed scene but cannot provide destination pixels for subsequent GPU drawing. Queue completion releases ordinary submission resources but transfers the output-owned staging lease to `GPUCompletedMappingPending`; only terminal depadding/map failure plus unmap makes it reusable, and device-loss failure quarantines it when safe release cannot be proven.

Extend `GPULimits` with the facade-observed `maxBufferSize`. wgpu4k already
exposes this public limit; its current native `mapAsync`/queue-completion
progress behavior remains a Task 9 dependency concern and must not be hidden by
a Kanvas polling or blocking workaround.

- [ ] **Step 6: Run resource/execution tests and commit**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.resources.*' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackLayoutTest'
rtk ./gradlew :gpu-renderer:test
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePool.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUReadbackLayout.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePoolTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUReadbackLayoutTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilitiesTest.kt
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
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererLayoutSurfaceTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadLiveMaterializationTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/StencilCoverLiveMaterializationTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/layers/SaveLayerLiveMaterializationTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUTextureSamplerMaterializationProviderTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/validation/FirstRoutePMEvidenceBundleTest.kt`

**Interfaces:**

- Consumes: immutable `GPUFramePlan`, resource providers, generation seals, surface acquisition, and one deterministic `GPUQueueCompletionProvider` fake/adapter contract.
- Produces: `GPUFramePreflighter.preflight(framePlan: GPUFramePlan): GPUFramePreflightResult`, with `Prepared(PreparedGPUFrame)` or typed refusal/failure and full rollback.

- [ ] **Step 1: Write transactional preflight tests**

Assert full success, pipeline failure, bind-group failure, scratch-budget failure, stale target/device/resource generation, readback-layout failure, and surface acquisition statuses. Every failure before encoding must run rollback in reverse acquisition order, leave no durable native references, create no encoder, and submit nothing. Missing queue-completion proof must refuse before surface acquisition. For a readback frame, prepared ownership marks the staging lease as output-owned and transfers it to the completed-frame path; ordinary post-submit completion release cannot repool it. Re-run the Task 6 package-boundary assertions: `GPUConcreteResourceProvider` consumes resource-owned preparation requests directly, `resources` still imports neither `recording` nor `execution`, and preflight in `execution` is the only layer that joins a `GPUFramePlan` to materialized resource results.

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
    data class Refused(val diagnostic: GPUDiagnostic) : GPUFramePreflightResult
}
```

Delete the legacy string-based `execution.GPUReadbackRequest` and migrate every listed consumer to Task 6's canonical `GPUFrameReadbackRequest`; no adapter or parallel request type remains. Define the handle-free `GPUQueueCompletionTicket` and sole `GPUQueueCompletionProvider.reserveTicket()` interface here so preflight can depend on it; tests use a deterministic fake provider. The corrected wgpu4k-backed implementation arrives in Task 9.

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
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest'
rtk proxy rg -n '^import org\.graphiks\.kanvas\.gpu\.renderer\.(recording|execution)\.' gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources
rtk ./gradlew :gpu-renderer:test
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/ExecutionContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUExecutionContextTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUExecutionCacheContractsTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererLayoutSurfaceTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationReadLiveMaterializationTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/StencilCoverLiveMaterializationTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/layers/SaveLayerLiveMaterializationTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUTextureSamplerMaterializationProviderTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/validation/FirstRoutePMEvidenceBundleTest.kt
rtk git commit -m 'feat: preflight GPU frames transactionally'
```

## Task 9: Separate submission, presentation, and real GPU completion

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle/verification-metadata.xml`
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

Move the shared `0.2.0-SNAPSHOT` coordinate to one `libs.versions.toml` version/library alias and use it from all five current consumers. Generate Gradle SHA-256 dependency verification metadata over the complete ordinary headless test graph, not only `:gpu-renderer:test`:

```bash
rtk ./gradlew --write-verification-metadata sha256 test
```

Inspect the metadata diff and require coverage for every artifact resolved by the final headless `test` graph, including `wgpu4k-toolkit`, all platform/native runtime variants used by conformance, serialization/test dependencies in integration modules, and transitive plugins. Rerun `rtk ./gradlew test` under verification before committing. The conformance JSON records the declared coordinate, resolved timestamped snapshot/module metadata, every relevant artifact SHA-256, wgpu-native revision, callback mode, host/architecture, and test command. A mutable coordinate without verification checksums and resolved metadata is not accepted.

Commit this dependency-only, baseline-compatible pin separately and tag it so the later benchmark baseline can use the exact same wgpu4k/wgpu-native artifacts without receiving renderer changes:

```bash
rtk git add gradle/libs.versions.toml gradle/verification-metadata.xml gpu-renderer/build.gradle.kts gpu-renderer-scenes/build.gradle.kts integration-tests/test-utils/build.gradle.kts integration-tests/skia/build.gradle.kts integration-tests/svg/build.gradle.kts
rtk git commit -m 'build: pin WebGPU runtime for frame comparison'
rtk git tag kanvas-frame-plan-wgpu4k-dependency-2026-07-13 HEAD
```

- [ ] **Step 5: Add native conformance evidence for corrected wgpu4k**

Delay callbacks beyond the registering call and allocation scope; churn memory and GC; submit ordered tickets; test success, failure, device loss, adapter close, cancellation, and many in-flight submissions. Record exact wgpu4k and wgpu-native revisions and observed callback order. If any behavior is surprising, keep the product gate closed and create a minimized wgpu4k report instead of adding a Kanvas workaround.

Native callback failure is publicly triggerable and mandatory: submit on an isolated context, close its device, then register `onSubmittedWorkDone()` and prove that the public `Device is closed` failure is normalized exactly once. Registration before close may legitimately race with fast success and must be reported honestly. Native device-loss injection is mandatory only when the stable public facade exposes a deterministic trigger. When it does not, exhaustively test device-loss normalization and record `not-exercisable-public-api` in the report. This is an explicit device-loss-only acceptance amendment: it does not relax success, callback failure, ordering, lifetime, close, cancellation, revision, checksum, or exactly-once evidence, and it must not be implemented with private handle access, native message parsing, or a Kanvas workaround.

- [ ] **Step 6: Run queue, native, and module tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionAdapterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest'
rtk ./gradlew :gpu-renderer:test
```

Expected: all pass with the corrected dependency and `wgpu4k-completion-conformance.json` records `accepted=true`. The unavailable/refusal unit branch remains tested. An actual conformance run that produces `accepted=false`, cannot identify the native revision, hangs, reorders callbacks, or lacks exact checksums is a hard stop for this implementation sequence. `not-exercisable-public-api` is accepted only for native device-loss injection under the Step 5 amendment; every other gate remains strict.

- [ ] **Step 7: Apply the stop/go gate**

Proceed to Tasks 10–14 only when the exact pinned artifact passes the native conformance suite and the report says `accepted=true`. Otherwise commit only the explicit dependency gate/report if useful, report the minimized evidence to the wgpu4k project, and stop without a Kanvas native workaround or product route activation.

- [ ] **Step 8: Commit the accepted completion boundary**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueCompletionAdapterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt reports/upstream-rebaseline/graphite-dawn-frame-plan/wgpu4k-completion-conformance.json
rtk git commit -m 'fix: release GPU resources on real completion'
```

## Task 10: Execute offscreen frames with one encoder and one submission

**Precondition:** Task 9's pinned native completion conformance report is accepted. Do not execute this task on the unavailable/refusal-only branch.

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUSceneTarget.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinator.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeResourceAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/TelemetryContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanExecutor.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RectOnlyOffscreenRenderer.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenFrameSampler.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/PerFamilyBenchmark.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutorTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinatorTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUSolidPayloadGathererTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanIntegrityTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeResourceAdapterTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphRenderSmokeTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphReferenceParityTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuationTest.kt`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/M25ExecutorWiringTest.kt`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenScenePngParityTest.kt`
- Create: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenFrameSamplerTest.kt`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/PerFamilyBenchmarkTest.kt`

**Interfaces:**

- Consumes: accepted `PreparedGPUFrame`, `GPUSceneTarget`, prepared resources, and reserved completion ticket.
- Produces: `GPUFrameExecutor.execute(preparedFrame: PreparedGPUFrame): GPUFrameExecutionHandle` with one encoder, one command buffer, and one submit; `GPUFrameCoordinator.submit(taskList)` is the sole end-to-end planner → preflight → executor entry point and returns a two-phase `GPUPreparedSceneFrameHandle`. Its completion stage yields the only final result and immutable frame-scoped `GPUFrameStructuralTelemetrySnapshot`; early refusals use an already-completed stage. Preflight also produces one opaque handle-free native-payload token; `GPURuntimeResourceAdapter` privately maps it to typed native operands and the executor consumes that mapping exactly once. Before native encoding, Slice 10C preserves the exact solid `GPUDrawSemanticPayload` produced by the existing gatherer from packet through preflight; no label/hash reconstruction is permitted. Task 10A accepts completion-only output; it defines the closed `ReadbackRgba` request/result algebra but refuses readback execution fail-closed. Only Slice 10C may produce a successful terminal readback after queue completion, map, row depadding, and unmap.

- [ ] **Slice 10A — Step 1: Add the instrumented core tests**

Prepare a frame containing render, bounded destination copy, render, compute/filter, target transition, and optional prepared readback evidence. Assert exactly one encoder creation, ordered GPU scopes matching `GPUCommandEncoderPlan`, one finish, one command buffer, one submit, exact retained resource registration, completion arm immediately after submit, and no intermediate submission. Add stale-seal, synchronous encode failure, post-submit completion-arm failure, and callback failure paths. In 10A, host actions are refused and prepared readback evidence must not be turned into pixels.

Add coordinator cases for recording/planning refusal, preflight refusal, successful completion-only execution, readback request refusal, and completion failure. Test the closed `CompletionOnly`/`ReadbackRgba` request and terminal-result algebra, including immutable byte ownership, but do not construct a successful readback result through the 10A coordinator. A readback request is refused before executor entry and claims exclusive rollback ownership; an ownership conflict also fails closed without submission. Successful terminal readback execution is explicitly Slice 10C acceptance, not Slice 10A acceptance. Task 10 has no presentation output; Task 11 adds and tests presentation failure when it extends the closed algebra. Early refusal must never call the executor and returns an already-completed handle. Submitted work first returns a handle with attempt ID and immediate submit/output state; only its exact completion stage seals one immutable `GPUFrameStructuralTelemetrySnapshot` with furthest phase, final outcome/diagnostic, and the counters observed so far. Define `GPUFrameAttemptID`, closed telemetry phase/outcome/event kinds, attempt-scoped sink, and snapshot in `telemetry/TelemetryContracts.kt` without imports from `execution`, `resources`, or `recording`; those later packages depend one-way on telemetry and emit facts after decisions. Assert the package-boundary test remains acyclic, a pending handle cannot expose final telemetry, and completion never blocks the render thread. No global mutable last-frame state or log reconstruction is allowed.

- [ ] **Slice 10A — Step 2: Run the core tests red**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameCoordinatorTest' --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest'
```

Expected: FAIL because the executor/coordinator and attempt-scoped telemetry do not exist.

- [ ] **Slice 10A — Step 3: Implement the instrumented executor/coordinator core**

Create the handle/lifecycle, telemetry, `GPUSceneTarget`, executor, and
coordinator contracts needed by the instrumented tests. The executor validates
the generation seal, creates one encoder, visits scopes in plan order, finishes
once, submits once, registers retention, arms completion immediately, and then
completes without host output actions. `GPUFrameCoordinator` performs no routing
of its own: it invokes the canonical planner, preflight, then executor once for
completion-only output. Host actions and `ReadbackRgba` are fail-closed refusals
in 10A; the latter becomes an executable terminal output only in Slice 10C.

This slice proves only orchestration and lifecycle order. Its instrumented
backend is an internal test seam, not a product route: it cannot accept
arbitrary encode callbacks, and passing these tests must not mark native
execution, pixel parity, or the one-submission performance gate green.

- [ ] **Slice 10B — Step 4: Close the native-operand materialization gap**

First extend `GPUFramePreflighterTest`, `GPURuntimeResourceAdapterTest`, and
`GPUConcreteResourceProviderTest` to prove that every encoder scope resolves to
one typed native operand, a missing/mismatched operand refuses before encoder
creation, a stale or duplicate token cannot be consumed, rollback removes an
unsubmitted payload, and submitted payload resources remain retained until
completion or quarantine. Run them red:

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapterTest' --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutorTest'
```

Expected: FAIL because current prepared resources and
`GPUPassCommandStream.operandBridge` are handle-free evidence only; they do not
deliver textures, views, buffers, pipelines, or bind groups to an encoder.

Add internal `GPUPreparedNativeFrameToken` and
`GPUPreparedNativeFramePayload` contracts. The payload has closed typed
render/compute/copy/readback operands in exact `GPUCommandEncoderPlan` order.
Every operand also carries the exact handle-free logical resource/binding key
declared by its encoder scope and `operandBridge`; matching only the scope
index, operation kind, native type, or device generation is insufficient.
Preflight refuses a key mismatch before encoder creation, including a
same-generation source/destination or pipeline substitution.
`GPURuntimeResourceAdapter` owns a private frame-scoped registry; preflight
registers one payload after `GPUConcreteResourceProvider` has materially
created the descriptor-backed resources, and `PreparedGPUFrame` stores only
the opaque dump-safe token. No WebGPU object, native handle/address, or
handle-derived identity enters `PreparedGPUFrame`, its hash, plan dumps, or
telemetry. The executor resolves and consumes the token exactly once through
its injected adapter.

The adapter also owns one identity-based ledger for every payload-owned native
handle and for handles retained by its uniform-slab/bind-group registries. A
handle may be shared only when every reference is explicitly `Borrowed`; an
owned handle cannot enter a second payload or another adapter-owned registry.
The reservation remains live across submitted, output-pending, and quarantine
states and is removed only after the corresponding handle closes successfully.
Registration conflicts refuse before encoder creation and never double-close
the rejected or already-owned handle.

For this frame path, replace the adapter's callback-based pending creation with
typed descriptor/materializer calls. Do not pass arbitrary lambdas as encoded
work. An evidence-only lease/resource factory must return a typed pre-submit
refusal and cannot activate the native route. Rollback invalidates the token;
post-submit completion and failure respectively release or quarantine its
payload with the existing generation rules.

- [ ] **Slice 10C — Step 5a: Preserve the solid draw semantics through preflight**

Start with TDD tests for the read-only gap discovered before native encoding:
the accepted `FillRect` path currently keeps identities, bounds, slots, and
hashes but loses the exact rectangle and RGBA values before `GPUTaskList`.
Prove that the existing `GPUSolidPayloadGatherer` produces the sole packed
representation, that a closed semantic payload wraps a deeply immutable
snapshot of its `GPUDrawPayloadRef`, and that the same payload survives
`GPUDrawPacket` → `GPUTask.Render`/`GPUFramePlan` → `GPUFramePreflighter`.

Tests must mutate every caller-owned list after construction, vary the packed
rect/color bytes while keeping unrelated labels stable, and assert that plan
dumps and canonical hashes remain deterministic but change with semantic
payload content. Missing payload, mismatched command/render-step identity,
slot/fingerprint mismatch, malformed field ranges, invalid byte count, or
non-finite/out-of-range values must return a typed pre-submit refusal before
encoder creation. Run the focused tests red:

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGathererTest' --tests 'org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPURecorderTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanIntegrityTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest'
```

Expected: FAIL because the current production `FillRect` packet carries only
handle-free labels/slot evidence; it neither owns the gathered block nor keeps
the rectangle and color available to preflight.

Add a closed `GPUDrawSemanticPayload` algebra in `PayloadContracts.kt`; its
first `SolidRect` variant owns only a recursively snapshotted and validated
`GPUDrawPayloadRef` produced by the existing `GPUSolidPayloadGatherer`. Reuse
that gatherer's existing rectangle/color validation, byte layout, padding,
fingerprint, and slot facts. Do not add another rectangle/color DTO, packer, or
byte encoder. Do not reconstruct values from `boundsHash`, labels, fingerprints,
or diagnostic strings.

Attach the closed payload to `GPUDrawPacket`, preserve it without reinterpretation
through task and frame planning, and include its canonical type, exact packed
bytes, field metadata, slot facts, and fingerprint in both stable dump and hash.
Preflight validates the payload against packet identity and the selected render
step, then supplies that exact semantic payload to the typed render operand.
Any absent or invalid payload on the executable solid-rect route fails closed
before native operand registration and before encoder creation.

- [ ] **Slice 10C — Step 5b: Prove a real solid rectangle and final readback**

Add a native pixel case for clear plus one solid rectangle and
`ReadbackRgba`. The production backend must obtain the canonical target, typed
native operands, and the exact semantic draw payload from preflight. On the
executor's sole encoder it records clear/render setup and draw, ends the render
pass, then records the final `copyTextureToBuffer` into the preflighted padded
staging layout. It finishes one command buffer and calls `queue.submit()` once;
mapping never creates another encoder or submission.

Arm and await the accepted completion ticket for that exact submission before
calling `mapAsync(Read)` once. On success, copy/depad the mapped padded rows into
a new immutable RGBA byte owner while the buffer remains mapped, then unmap in
a guaranteed cleanup path and only then release the output-owned staging lease.
Only after that cleanup may the completion stage expose pixels and final
telemetry. On queue-completion failure, do not map and quarantine submitted
resources. On map failure, never read a mapped range; perform the state-legal
cleanup, return a typed terminal failure, and quarantine on device loss.

Delay mapping after queue completion, request a same-sized staging buffer from
a concurrent frame, and prove the first output-owned lease is not reused until
map/depad/unmap and terminal cleanup finish. `GPUPreparedSceneFrameHandle`
contains only the stable attempt ID and immediate submit state; its completion
stage is the sole final pixel/telemetry surface. Do not call or wrap
`target.encode`, `encodeOffscreenTexture`, `readRgba`, or any other immediate
method. Only this native pixel evidence can mark the basic offscreen route green.

- [ ] **Slice 10D — Step 6: Encode bounded destination copies natively**

Replace the product use of `snapshotTargetToOffscreenTexture()` with planned `copyTextureToTexture` on the active encoder. Encode destination shader draws against the snapshot texture and logical-origin uniforms. Keep output readback as a final planned buffer copy.

Verify bounded copy extents and origin math, no CPU upload between passes,
logical snapshots backed by larger pooled textures, exact destination pixels,
and one submission. The native executor must encode the copy itself; wrapping
`copyTargetToOffscreenTexture` or `snapshotTargetToOffscreenTexture` is not an
implementation of this slice.

- [ ] **Slice 10E — Step 7: Add persistent MSAA continuation**

Add native pixel coverage for untouched old pixels after a partial MSAA draw
and a neutral `CopyResourceStep` pass break, then for several breaks on the
same attachment. Own one exact frame-local color attachment set in
`GPUSceneTarget`; keep it alive across encoder scopes, store it, and resolve
each producing pass to the canonical scene texture. Put the exact typed
continuation key directly on every multisample `GPUTask.Render`; make those
render tasks the sole proof authority, and let destination grouping only
recoup and validate the consumer key. The E1 acceptance smoke must start from
`GPUTaskList` and cross planner, preflight, and executor; a hand-built
`GPUFramePlan` is permitted only for focused negative preflight fixtures. The
native materializer must require the canonical target's exact
`RenderAttachment + CopySource` usages and expected frame-local lifetime,
derive store operations from the checked load/store plan, and refuse any
contradiction with continuation store evidence before native handles exist.
This slice is explicitly color-only: depth/stencil and inter-frame retained
attachments refuse until separate ownership and pixel evidence exist. Both
`CopyDestinationStep` and `CopyAsDrawMaterializationStep` consumers form the
separate E2 gate and must select a proven exact single-sample geometry/clip
lowering or refuse with
`unsupported.blend.msaa_destination_read_exactness`; the E1 neutral copy does
not claim destination-read support. Treat a write named by either a compute
target or a writable compute resource use as canonical-authority invalidation.
Run
`GPUMsaaContinuationTest` and the native smoke cases before claiming this
slice green.

- [ ] **Slice 10F — Step 8: Migrate glyph and scene consumers last**

Migrate the existing color-glyph native smoke/parity tests from direct
`createOffscreenTarget`/`target.encode`/`readRgba` calls to a recorded frame
plus final planned readback through `GPUFrameCoordinator`. They remain pixel
tests, but no test keeps a removed immediate contract alive.

Expose a production `GPUPreparedSceneFrameSession` that retains a compatible
canonical target/device/resource context across frames and accepts recorded
task lists plus `ReadbackRgba` or `CurrentFrameCompletionOnly`. Task 11 extends
that closed output algebra with window presentation; no Task 10 type references
the not-yet-created window contract.

### Slice 10F-a implemented evidence — reusable prepared scene session

The lower-level reusable session seam is implemented without completing the
glyph and scene-consumer migration in the remainder of Slice 10F. One prepared
session owns one exact native texture/view pair, reuses it across compatible
frames, and creates a fresh planner/preflight/coordinator/materialization stack
for every frame. Its Task 10 output algebra is exactly `ReadbackRgba` or
`CurrentFrameCompletionOnly`; completion-only frames create no staging buffer,
copy, map, or empty completion anchor.

The session refuses concurrent frames, use after close, stale device
generation, logical-target substitution, and incompatible bounds/format before
native reuse. Parent close is child-aware: an idle child closes before device
teardown, while an in-flight child defers teardown until its real terminal
completion. Setup rollback remains retryable: successfully closed resources
leave the rollback ledger, failed resources stay quarantined and are retried by
the parent before device teardown, without closing an already closed native
handle twice.

Native two-frame evidence uses two distinct frame/attempt IDs and two distinct
retention tickets over one exact target: the first frame requests
`CurrentFrameCompletionOnly`, the second requests `ReadbackRgba` and returns the
exact premultiplied RGBA pixels. Counters prove 1 target creation, 2 native
target uses, 2 submits, 1 readback copy, 2 frame-local coordinator creations,
2 native-payload registrations, and 0 active/output-owned/quarantined payloads
after terminal completion. Target close occurs only when the prepared session
closes. Validation is recorded in the Task 10 report: the focused matrix passes
329 tests and the complete `gpu-renderer` module passes 1,715 tests with 2
skipped and no failure or error.

Migrate all existing `gpu-renderer-scenes` offscreen consumers now, after the
native primitive/copy/MSAA slices are green. `SceneIntermediatePlanExecutor`
records its scene/intermediate work into the canonical route instead of calling
`encodeOffscreenTexture` per operation. `RectOnlyOffscreenRenderer` records
direct and destination-reading draws instead of calling `target.encode` or
`copyTargetToOffscreenTexture`; PNG bytes come only from final planned
readback. `OffscreenFrameSampler` and `PerFamilyBenchmark` reuse the prepared
session rather than creating backend targets. No scene runner, benchmark,
immediate-method wrapper, or evidence-only provider may remain a second
executor or be reported as a green native route.

- [ ] **Step 9: Run native and module suites**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameCoordinatorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUColorGlyphRenderSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUColorGlyphReferenceParityTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaContinuationTest'
rtk ./gradlew :gpu-renderer-scenes:test --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.M25ExecutorWiringTest' --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.OffscreenScenePngParityTest' --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.OffscreenFrameSamplerTest' --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.PerFamilyBenchmarkTest'
rtk ./gradlew :gpu-renderer:test
rtk ./gradlew :gpu-renderer-scenes:test
```

- [ ] **Step 10: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUSolidPayloadGathererTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanIntegrityTest.kt
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUSceneTarget.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinator.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeResourceAdapter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/TelemetryContracts.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanExecutor.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RectOnlyOffscreenRenderer.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenFrameSampler.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/PerFamilyBenchmark.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutorTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinatorTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeResourceAdapterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphRenderSmokeTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphReferenceParityTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaContinuationTest.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/M25ExecutorWiringTest.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenScenePngParityTest.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenFrameSamplerTest.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/PerFamilyBenchmarkTest.kt
rtk git commit -m 'feat: execute offscreen scenes in one GPU submission'
```

## Task 11: Route window output through the canonical scene target

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinator.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedWindowOutput.kt`
- Modify: `gpu-renderer-scenes/src/kadre/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/KadreWindowedSceneRunner.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/RunGpuRendererSceneKadreMain.kt`
- Delete: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/WindowedRectOnlySceneShader.kt`
- Modify: `gpu-renderer-scenes/build.gradle.kts`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWindowFrameLifecycleTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/RunGpuRendererSceneKadreMainTest.kt`

**Interfaces:**

- Consumes: the canonical `GPUSceneTarget`, preflight's late surface acquisition, executor output, and independent completion state.
- Produces: opaque `GPUPreparedWindowOutput`, extends `GPUSceneFrameOutputRequest`/`GPUPreparedSceneFrameSession` with `PresentToWindow`, and adds a final surface-blit encoder scope plus `PostSubmitPresentAction`; presentation never completes resource lifetime. The prepared output is the only window binding accepted by the prepared scene session and the later Canvas facade.

- [ ] **Step 1: Test late acquire, surface blit, present, and completion independently**

Instrument success, lost, outdated, genuine timeout, out-of-memory, device loss, resize/generation change, throwing present, and completion failure. Add the coordinator presentation-failure case here, after `PresentToWindow` exists. Assert surface acquisition is the final preflight resource action, scene rendering never targets the surface texture directly, the surface blit is the final encoded render pass, completion is armed before present, and present failure cannot release or cancel completion. Exercise a generic recorded task list through `GPUPreparedSceneFrameSession.renderFrame(..., PresentToWindow(preparedOutput))`; it returns immediately after guarded submit/present with a pending completion stage, and the test drives completion on a dedicated context. It must not depend on `GPURendererScene`, rect-only WGSL, or a private window encoder.

- [ ] **Step 2: Run the test against current window behavior**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWindowFrameLifecycleTest'
```

Expected: FAIL because `WgpuWindowSurface.encodeAndPresent` combines responsibilities and treats presentation as completion evidence.

- [ ] **Step 3: Split window lifecycle operations**

Expose acquire/reconfigure normalization to preflight, surface-blit encoding to `GPUFrameExecutor`, and a non-throwing captured `PostSubmitPresentAction`. Add `GPUPreparedWindowOutput` as an opaque, reusable wrapper created from the host's native surface binding during setup; it owns configuration/generation/resize/close but exposes neither native surface nor acquired texture. In this task, modify `GPUFrameCoordinator.kt` to add `GPUSceneFrameOutputRequest.PresentToWindow` to the closed algebra and make `GPUPreparedSceneFrameSession` accept it; the Task 10 commit remains independently compilable with only readback/completion outputs. Migrate the real `KadreWindowedSceneRunner` consumer from `encodeAndPresent` and rect-only `WindowedRectOnlySceneShader` execution to the same common scene-to-task recording used by Task 10 → prepared scene session → late acquire → canonical scene blit → one submit → present → independent completion. The redraw callback never waits: it attaches lifecycle/reuse handling to the frame handle's completion stage on the dedicated completion context. Delete `WindowedRectOnlySceneShader` and the rect-only preflight refusal/reflective checks in `RunGpuRendererSceneKadreMain`; update its tests to assert arbitrary registry scene families reach the common task recorder. On present failure, complete required surface discard/cleanup handling while leaving submitted resources retained or quarantined until real completion/device teardown.

Add an explicit opt-in `kadreFrameLifecycleCheck` Gradle verification task that compiles the `kadre` source set and runs the focused lifecycle contract when `external/poc-koreos` is initialized. Keep it outside ordinary headless `check`/CI dependencies so unpublished Kadre artifacts are never resolved accidentally.

- [ ] **Step 4: Run window/native tests and commit**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWindowFrameLifecycleTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest'
rtk ./gradlew :gpu-renderer:test
rtk ./gradlew :gpu-renderer-scenes:kadreFrameLifecycleCheck
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinator.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedWindowOutput.kt gpu-renderer-scenes/src/kadre/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/KadreWindowedSceneRunner.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/RunGpuRendererSceneKadreMain.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/WindowedRectOnlySceneShader.kt gpu-renderer-scenes/build.gradle.kts gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWindowFrameLifecycleTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/RunGpuRendererSceneKadreMainTest.kt
rtk git commit -m 'feat: present canonical GPU scene targets'
```

## Task 12: Migrate every Kanvas drawing API in bounded family slices

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchVertices.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatch.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatch.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/GPUColorFormat.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSession.kt`
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
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSessionTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceWindowOutputTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUColorConfigMappingTest.kt`

**Interfaces:**

- Consumes: Canvas `DisplayOp` families, `GPUOpMapper`, canonical blend/coverage plans, recording, and the sole `GPUFrameCoordinator` entry point.
- Produces: one normalized task/frame route for every drawing API; a production-owned reusable `GPUPreparedSurfaceSession` for offscreen completion/readback and window presentation through Task 11's opaque prepared output; the temporary `GPULegacyImmediatePathAdapter` allowlist shrinks to empty and is deleted in Slice 12E.

- [ ] **Step 1: Introduce the migration boundary without changing pixels**

Make `GPUOpMapper` the only Canvas-state translator. Add `GPULegacyImmediatePathAdapter` as an explicit temporary boundary for families not yet moved: it may call the existing renderer entry points, but cannot classify blend/coverage/destination routing, create CPU snapshots, or be reachable from a migrated family. Give it a closed `LegacyDisplayOpFamily` allowlist and a dump counter so each slice proves its allowlist shrinks. Delete it in Slice 12E in the same commit that removes its final consumer.

Reserve the non-visual `Canvas.drawAnnotation` key `kanvas.frame.provenance` with values `harness-background`, `gm-content`, and `none`. `GPUOpMapper` treats these annotations only as ordered metadata context: the current value is copied into every subsequent normalized command/task until the next marker, and the annotation itself produces no draw, route, resource, or submission. Unknown annotations remain ordinary inert metadata. This is the sole production provenance channel used by Task 14; telemetry must not guess from draw position.

Translate Canvas state exactly once into normalized draw/composite commands. Map all 29 blend identities without a destination-read boolean. Consume active geometry and clip planners' coverage results. Resolve save/restore into composite command tokens before renderer-core entry; do not add `save`, `restore`, matrix, or clip-stack mutation APIs to `GPUFramePlanner`.

### Slice 12A: Core primitives, paths, and clips

- [ ] **Step 2: Add failing primitive/clip inventory cases**

Create `GPUFramePathApiInventoryTest` with color/clear, points/lines, rect/rrect/DRRect/path, transform metadata, scissor, analytic/mask/stencil clip coverage, and flush/snapshot ordering. For each visual operation, assert normalized provenance, target-space bounds, geometry/clip coverage, canonical `GPUBlendPlan`, frame-step provenance, and one executor submission. Assert transforms, clips, and annotations create state/metadata only. Place reserved provenance annotations around three unique draws and prove exact background/content/none partitioning through normalized commands, tasks, frame steps, and telemetry inputs without any extra visual packet; unknown annotation values cannot activate a reserved provenance. Exercise all 29 modes through the shared matrix on these core families.

- [ ] **Step 3: Run the focused red test**

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUClipCoverageDispatchTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPathClipRegressionTest'
rtk ./gradlew :kanvas:test
```

Expected: FAIL because primitive and clip commands still dispatch through immediate renderer branches.

- [ ] **Step 4: Migrate primitives and clips, then commit**

Route these families through recording and `GPUFrameCoordinator`, which alone performs finalization, preflight, and execution. Remove them from the legacy adapter allowlist. Ordinary scalar-AA `SrcOver` must remain a direct fixed-function draw without destination snapshot or fullscreen coverage-combine pass.

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

Move layer/filter/mask/picture task payloads to the common frame path. Convert `GPUClipCoverage`, `GPUImageFilterDispatch`, and `GPUMaskBlurDispatch` to emit normalized/task payloads; delete their calls to `encodeCoverageMask` and `encodeOffscreenTexture` instead of leaving dead immediate helpers for Task 13. Resolve save/restore entirely in the mapper. Assert the temporary adapter's family allowlist and invocation count are both empty across the complete inventory, then delete `GPULegacyImmediatePathAdapter.kt` before the slice commit.

- [ ] **Step 10: Run the complete Kanvas GPU surface suite and commit**

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.*'
rtk ./gradlew :kanvas:test
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatch.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatch.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatchTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatchTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt
rtk git commit -m 'refactor: route GPU composites through frame plans'
```

Expected: all Kanvas GPU surface tests pass, including interiors, exteriors, AA edges, clip edges, all RGBA channels, all inventory families, and the exhaustive canonical 29-mode tests. No legacy-adapter source or consumer remains.

### Slice 12F: Reusable prepared surface session

- [ ] **Step 11: Add the reusable-session contract test**

Create `GPUColorConfigMappingTest` first. `surface.GPUColorFormat` remains only a source-compatible public facade and is mapped exactly once to Task 4's canonical `gpu.renderer.color.GPUColorFormat` plus `GPUColorInterpretation`; it never enters planning. Make physical format and color interpretation distinct in `RenderConfig`: the default effective pair is physical `RGBA8Unorm` plus `Srgb`. Keep `RGBA8_UNORM_SRGB` only as a deprecated compatibility input that maps to that same pair and rejects an incompatible explicit interpretation. Test RGBA/BGRA physical support, legacy mapping, invalid combinations, runtime capability refusal, and the effective facts exposed by the prepared session. No second canonical color enum or transfer decision is allowed.

Create `GPUPreparedSurfaceSessionTest` around a public production-owned boundary:

```kotlin
sealed interface GPUPreparedSurfaceOutput {
    data object ReadbackRgba : GPUPreparedSurfaceOutput
    data object CurrentFrameCompletionOnly : GPUPreparedSurfaceOutput
    data class PresentToWindow(
        val output: GPUPreparedWindowOutput,
    ) : GPUPreparedSurfaceOutput
}

interface GPUPreparedSurfaceSession : AutoCloseable {
    fun renderFrame(
        output: GPUPreparedSurfaceOutput,
        record: (Canvas) -> Unit,
    ): GPUPreparedSurfaceFrameHandle
}

data class GPUPreparedSurfaceFrameHandle(
    val attemptId: GPUFrameAttemptID,
    val immediateOutcome: GPUPreparedSurfaceImmediateOutcome,
    val completed: CompletionStage<GPUPreparedSurfaceCompletedFrameResult>,
)

data class GPUPreparedSurfaceCompletedFrameResult(
    val attemptId: GPUFrameAttemptID,
    val outcome: GPUPreparedSurfaceFrameOutcome,
    val telemetry: GPUFrameStructuralTelemetrySnapshot,
)
```

`Surface.prepareGpuRenderSession()` prepares and retains the device, canonical scene target, generation, and reusable caches once. Each `renderFrame` creates a fresh frame-local recording canvas, invokes the caller's recording block exactly once, seals that frame without appending any operation from an earlier frame, maps it internally through `GPUOpMapper`, invokes `GPUFrameCoordinator` once, and returns a non-blocking two-phase handle. Recording-block and pre-submit failures return an already-completed handle rather than escaping without evidence. `ReadbackRgba` adds the final planned readback; its completed result exposes pixels only after queue completion and mapping. `CurrentFrameCompletionOnly` performs no readback and its stage completes on exact-frame queue completion. `PresentToWindow` consumes Task 11's opaque `GPUPreparedWindowOutput`, performs late acquisition and the planned final blit/present, exposes immediate presentation separately, then completes asynchronously on exact GPU completion. `GPUPreparedSurfaceCompletedFrameResult.telemetry` is the identical immutable snapshot from `GPUPreparedSceneCompletedFrameResult`, including early and post-submit failure outcomes; a pending handle exposes no final snapshot. The API exposes neither mapper internals nor native handles, and it has no global telemetry singleton. An internal adapter may submit the existing `Surface` display-list snapshot for backward-compatible `Surface.render()`, then await its exact `ReadbackRgba` completed result; repeated prepared-session frames never reuse an append-only list.

Test two or more frames reuse the same valid device/target/cache generations, create one submission each, and never reuse resources before completion. Give each recording block a unique draw and prove frame N+1 contains no operation from frame N. Cover an empty frame, thrown recording block before submit, resize/config mismatch, device loss, close, concurrent call refusal, completion failure, and stale surface generation. A pending handle cannot expose final telemetry; awaiting its completion yields exactly one immutable snapshot with the same attempt ID and object/value identity as the lower prepared-scene completed result. The thrown-block, preflight-refusal, success, present-failure, and completion-failure snapshots name the correct furthest phase without reading global state. Prove the ordinary Kadre-style caller attaches completion work without blocking its render thread. Assert the completion-only path has zero texture-to-buffer copy/map/readback while the readback path preserves current `Surface.render()` pixels. In `GPUPreparedSurfaceWindowOutputTest`, record representative primitive, image, text, and layer/filter Canvas operations and prove `PresentToWindow` sends their exact mapped task list through the same `GPUPreparedSceneFrameSession`, adds one final surface blit, submits once, presents once, and never invokes `WindowedRectOnlySceneShader` or another GM/Canvas mapper.

- [ ] **Step 12: Run red, implement, and prove ordinary render delegates**

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUColorConfigMappingTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceSessionTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceWindowOutputTest'
```

Expected before implementation: FAIL because the public format label conflates physical format/transfer, and `Surface.render()` recreates the GPU session/target and always reads RGBA. Implement the one-time color mapper and two-phase session without duplicating planning; make the existing GPU branch of `Surface.render()` delegate through a single-use `GPUPreparedSurfaceSession` with `ReadbackRgba` and await the exact completed result so public pixel behavior remains unchanged.

- [ ] **Step 13: Run and commit the prepared-session slice**

```bash
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUColorConfigMappingTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceSessionTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceWindowOutputTest' --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest'
rtk ./gradlew :kanvas:test
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/GPUColorFormat.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSession.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUColorConfigMappingTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSessionTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceWindowOutputTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt
rtk git commit -m 'feat: reuse prepared WebGPU surface sessions'
```

## Task 13: Remove superseded immediate and CPU-snapshot paths

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatch.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatch.kt`
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

Assert production source contains no `snapshotTargetToOffscreenTexture`, destination-read call to `readRgba`, CPU snapshot upload, immediate per-operation submit, high-level `GPUBackendOffscreenTarget.encode(...)`, `copyOffscreenTexture`, `encodeCoverageMask`, `destinationReadBlendModeIndex`, `clipCoverageBlendModeIndex`, `GPUDestinationReadExecutor`, legacy destination composer, coverage route boolean, or second blend planner. Permit `readRgba` only as the explicit terminal readback/output API until it is renamed to make that boundary obvious. Require every remaining native encode/copy primitive to receive the active frame encoder and already-prepared resources; no primitive may create, finish, or submit an encoder.

- [ ] **Step 2: Run the boundary test and observe legacy symbols**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest'
```

Expected: FAIL while legacy contracts and immediate helpers remain.

- [ ] **Step 3: Delete superseded contracts and implementations**

Remove `snapshotTargetToOffscreenTexture()` and its CPU read/upload implementation, the high-level `GPUBackendOffscreenTarget.encode(...)`, `copyOffscreenTexture(...)`, `encodeCoverageMask(...)`, `GPUDestinationReadExecutor` and its evidence-only test, immediate destination composer/executor branches in `GPUClipExecution`/`GPURenderer`, duplicate blend index switches, common-path fullscreen coverage combine shader, presentation-completion constants, evidence-only encoder plans, and adapters whose last consumers moved in Task 12. Recheck `GPUClipCoverage`, `GPUImageFilterDispatch`, and `GPUMaskBlurDispatch`: after Slice 12E they may retain only common frame payload/recording code; delete any obsolete class or dead helper wrapper whose last immediate caller moved. Keep only low-level native recording primitives whose signatures require the active frame encoder and prepared resources. Rename terminal readback APIs if needed so production search cannot confuse output readback with a continuation path.

- [ ] **Step 4: Run architecture scan and module tests**

```bash
rtk proxy rg -n 'snapshotTargetToOffscreenTexture|copyTargetToOffscreenTexture|copyOffscreenTexture|encodeOffscreenTexture|encodeCoverageMask|destinationReadBlendModeIndex|clipCoverageBlendModeIndex|GPUDestinationReadExecutor|DestinationReadComposer|GPU_QUEUE_COMPLETION_PRESENTED|GPU_QUEUE_COMPLETION_PRESENT_FAILED|GPU_QUEUE_COMPLETION_TARGET_CLOSE' gpu-renderer/src/main/kotlin gpu-renderer-scenes/src/main/kotlin kanvas/src/main/kotlin
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test :kanvas:test
```

Expected: scan has no matches; all three module suites pass. Terminal output readback remains only through the planned `GPUFrameReadbackRequest` path and cannot be confused with a destination continuation snapshot.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationReadExecutor.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationReadExecutorTest.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImageFilterDispatch.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurDispatch.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt
rtk git commit -m 'refactor: remove immediate GPU destination snapshots'
```

## Task 14: Validate pixels, regenerate GM evidence, and measure performance

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/TelemetryContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinator.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePool.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSession.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/PerformanceBudgetTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/FrameGateM23BaselineTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/GPUFrameStructuralTelemetryTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePoolTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmFrameStructuralTelemetryTest.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvasTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSessionTest.kt`
- Modify: `integration-tests/skia/build.gradle.kts`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkSceneSelection.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunner.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunnerTest.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmSteadyFrameBenchmarkRunner.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkWorkloadManifest.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkWorkloadManifestTest.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/AAXfermodesGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/HairModesGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/XfermodesGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ThinRectsGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/gradient/LinearGradientGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ManyCirclesGm.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/FramePlanWindowBenchmarkOptions.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/FramePlanWindowBenchmarkOptionsTest.kt`
- Create: `integration-tests/skia/src/kadreBenchmark/kotlin/org/graphiks/kanvas/skia/KadreFramePlanBenchmark.kt`
- Create: `tools/performance/run-frame-plan-benchmarks.sh`
- Create: `tools/performance/frame-plan-benchmark.schema.json`
- Create: `tools/performance/fixtures/frame-plan-gates/` schema-valid positive and negative raw-sample fixtures
- Create: `tools/performance/skia-nanobench-fixed-loop-multi-sample.patch`
- Modify: `integration-tests/skia/test-similarity-scores.properties`
- Modify: `integration-tests/skia/src/test/resources/generated-renders/`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/benchmark-manifest.json`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/raw/` sample artifacts
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/report.md`
- Verify locally: `integration-tests/skia/build/reports/skia-gm-dashboard/index.html`

**Interfaces:**

- Consumes: completed frame telemetry, the shared GM registry/draw implementation, stable `SkiaGmRenderer.render()` for end-to-end regression, Task 12's production `GPUPreparedSurfaceSession` completion-only boundary for steady frames, and pinned Graphite/Dawn source plus a strictly allowlisted driver patch.
- Produces: immutable structural telemetry snapshots, schema-validated raw samples, current generated renders/scores, benchmark manifest, hashes, p50/p95 report, and promotion verdicts.

- [ ] **Step 1: Add failing structural telemetry assertions**

In `:gpu-renderer`, use synthetic task lists to count direct draws, shader draws, planning/preflight/execution refusals, pass breaks, snapshot groups, copied pixels/bytes, scratch allocation/reuse/eviction, encoders, command buffers, submissions, presentation, completion, waits, `peakFrameTransientBytes`, and `targetResidentBytes`. Cover a planning refusal and preflight refusal that never reach the executor, plus success and post-submit failure. Exercise typed pool/provider events for reuse hit, create, eviction, allocation refusal with rollback, and two concurrent frame attempts; every event must carry and land in only its originating attempt ID. Freeze `direct-draw-dominated` as: denominator = accepted packets with `gm-content` provenance (exclude harness background and output readback/blit); numerator = those packets writing the canonical final target with an already-planned fixed-function target blend. Require denominator > 0, numerator == denominator, zero destination-read shader packet/snapshot/copied pixel/refused content packet/auxiliary content pass. A gradient material shader remains direct when its target blend meets this definition.

In `:integration-tests:skia`, add one shared `GmCanvas.drawBackgroundAndGmContent(gm)` helper. It emits `drawAnnotation(fullBounds, "kanvas.frame.provenance", "harness-background")`, draws the same white background used by `SkiaGmRenderer`, emits the `gm-content` marker, invokes the registry-owned GM's exact `draw`, then emits `none`; `onOnceBeforeDraw` remains caller-controlled outside timed work. `GmCanvasTest` proves annotations add no pixels/draw packets and preserve the exact background/content partition. `SkiaGmFrameStructuralTelemetryTest` selects the real registry-owned primary/control GMs, records them through public `Surface.prepareGpuRenderSession().renderFrame(...)` and this helper, awaits that handle's exact completed-frame stage, and reads only `GPUPreparedSurfaceCompletedFrameResult.telemetry`. It asserts per-scene one encoder/command-buffer/submission, zero CPU destination snapshots, zero AA-only `SrcOver` copies in `hairmodes`, bounded copy area, completion-based resource release, and the frozen direct-draw classification later consumed by performance gates. The lower module never imports integration-test GMs. A mutable global recorder, positional/background guess, “last snapshot”, log parse, or direct coordinator/mapper access fails the test.

- [ ] **Step 2: Run the telemetry test red**

```bash
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralTelemetryTest'
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.SkiaGmFrameStructuralTelemetryTest'
```

Expected: FAIL because the shared telemetry recorder/coordinator integration and real-GM structural test do not yet exist.

- [ ] **Step 3: Implement, verify, and commit telemetry before benchmarking**

Use one frame-scoped telemetry recorder shared by `GPUFrameCoordinator`, `GPUFramePreflighter`, `GPUConcreteResourceProvider`, `GPUScratchTexturePool`, and `GPUFrameExecutor`. Pass a typed attempt-scoped event sink/recorder through preflight and provider calls; the pool/provider emit typed hit/create/evict/refusal/rollback facts at the point they occur. A terminal pre-submit failure or the exact completion callback seals one immutable snapshot into `GPUPreparedSceneCompletedFrameResult`; `GPUPreparedSurfaceCompletedFrameResult` forwards that exact snapshot. The earlier handles expose only attempt/immediate state, never mutable or supposedly final telemetry, and there is no publish-to-global side channel. Do not infer runtime allocation events from plan steps or reconstruct counters from dumps/logs. Keep presentation and completion counters independent and include phase/refusal/error status. Extend the prepared-session test for refusal-before-submit, success, present failure, completion failure, concurrent sessions, non-blocking window callbacks, and exact snapshot association. Run focused plus affected suites:

```bash
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test :kanvas:test
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/TelemetryContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinator.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePool.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSession.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/GPUFrameStructuralTelemetryTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePoolTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/PerformanceBudgetTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/FrameGateM23BaselineTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSessionTest.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvasTest.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmFrameStructuralTelemetryTest.kt
rtk git commit -m 'feat: record structural GPU frame telemetry'
```

Expected: BUILD SUCCESSFUL with no quarantined unexpected resource, hidden fallback, or unstable evidence dump.

- [ ] **Step 4: Add the benchmark runner test before the runner**

Test command parsing and a versioned fixed set containing primary GMs `aaxfermodes,hairmodes,xfermodes` plus registry-backed direct-draw controls `rect,thinrects,linear_gradient,manycircles`. The control set participates in baseline/candidate 120-warmup/300-sample runs and the 10%/15% regression gates only after structural telemetry proves it is direct-draw-dominated under the frozen formula above. The schema/manifest contains all numerator/denominator counters, exact `directDrawRatio`, verdict, and stable reason. Test 30 cold process records, completion-boundary enforcement, nanosecond monotonic durations, p50/p95 calculation over every retained sample, manifest revision matching, and NDJSON schema validation.

Add schema-valid synthetic raw-sample fixtures for a passing case and independent failures of: primary p50 improvement below 2x, primary p95 not improved, control p50 regression above 10%, control p95 regression above 15%, candidate/Graphite p50 ratio above 2x, and direct-draw classification false/unproven. The script self-test must return non-zero with the exact stable gate reason for each negative fixture and prove the recorded baseline fixture hashes are unchanged before/after evaluation. These gates become release-blocking only for frame-plan performance promotion after all negative-fixture tests pass; until then they are reporting-only and cannot claim release enforcement.

Add `SkiaGmBenchmarkWorkloadManifestTest` against a checked-in manifest generated from pinned Skia revision `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`. The ordinary headless test validates the frozen revision, source hashes, and expected structural facts without requiring an external Skia checkout. The local benchmark script receives `--skia-root`, recomputes those hashes/facts from the pinned checkout, and refuses a mismatch. For every GM used in a Graphite ratio, verify operational equivalence rather than name equality: canvas dimensions, ordered draw count/fingerprint, source-type matrix, layer count, shader count, image/mask paths, sample plan, format, premultiplied alpha, and color space. Port the selected Kanvas GMs where required: `hairmodes` preserves Skia's per-cell `saveLayer` and checkerboard shader; `xfermodes` preserves all eight source types and image/layer/mask paths; `linear_gradient` moves its 100 shader constructions to `onOnceBeforeDraw`; `thinrects` preserves the ordered background/transforms/rect matrix; `manycircles` preserves Skia's seeded RNG sequence, white background, RGB565 color quantization, and 10,000 ordered oval draws. Record `gm/manypaths.cpp` as the pinned upstream source path for `manycircles` and update `ManyCirclesGm.kt` KDoc to that same path; do not cite the nonexistent `gm/manycircles.cpp`. `rect` has no pinned upstream `GM_rect`; `linear_gradient` cannot claim upstream equivalence while dithering is not a real Kanvas paint capability. Both remain Kanvas-only regression controls and are never requested from nanobench or used in a Graphite ratio. Any other same-name structural mismatch fails and may appear only as informational evidence without a ratio.

Prove all lanes select the same registry-owned `SkiaGm` objects and invoke their exact `onOnceBeforeDraw`/`draw` implementation through `GmCanvas`; a copied scene or alternate Kadre scene table fails. Test two distinct timing protocols:

1. `end-to-end-regression`: baseline versus candidate through the stable `SkiaGmRenderer.render()` boundary, including its existing lifecycle, for trustworthy regression deltas.
2. `steady-frame-cross-engine`: candidate versus Graphite with device/target and GM preparation performed once, `onOnceBeforeDraw` outside timing, then exactly one frame recording, one submit, and an exact current-frame GPU completion wait per sample. Cold initialization remains a separate informational lane and is never mixed into warm percentiles.

```bash
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.SkiaGmBenchmarkRunnerTest'
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.FramePlanWindowBenchmarkOptionsTest'
```

Expected: FAIL because the runner, Gradle task, script, and schema do not exist.

- [ ] **Step 5: Implement and commit the fixed benchmark harness**

Add `benchmarkSkiaGms` as a `JavaExec` task whose properties are:

```text
kanvasGmBenchNames=aaxfermodes,hairmodes,xfermodes,rect,thinrects,linear_gradient,manycircles
kanvasGmBenchLane=end-to-end-regression|steady-frame-cross-engine
kanvasGmBenchTarget=offscreen-readback|offscreen-current-frame-completion
kanvasGmBenchCacheState=warm|cold
kanvasGmBenchProcessIndex=0..29
kanvasGmBenchWarmup=120
kanvasGmBenchSamples=300
kanvasGmBenchOutput=/Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw
```

The baseline-compatible offscreen runner emits one schema-validated NDJSON row per measured frame for the `end-to-end-regression` lane. It starts timing immediately before the stable `SkiaGmRenderer.render()` call and stops only after that call returns its completed `SkiaRenderResult.rgba` readback. The test makes this boundary explicit: returned pixels are unavailable until GPU copy/map completion, while the candidate's `GPUQueueCompletionTicket` remains an internal implementation detail and is never referenced by the cherry-pickable harness. The shell script launches 30 independent Gradle processes per primary GM with one exact name, `cacheState=cold`, `warmup=0`, `samples=1`, and distinct indices. Each cold process creates its own device and caches before its only completed frame. The harness self-test evaluates every positive/negative gate fixture, checks non-zero failure exits and stable reasons, and verifies baseline fixture hashes before and after evaluation.

The schema validates the exact lane/target pair: `end-to-end-regression` requires `offscreen-readback`, while `steady-frame-cross-engine` requires `offscreen-current-frame-completion`. The candidate steady adapter runs all primary and control scenes so its structural telemetry can prove or reject direct-draw dominance; Graphite ratios are still emitted only for the manifest-equivalent subset. A target label may not hide a readback or claim one that is absent.

Port and freeze the structurally equivalent primary GM workloads plus the four control selections in this same baseline-compatible harness commit. The manifest records the pinned upstream source paths/revision and the operational fingerprints. It also fixes the common render contract to physical RGBA8Unorm, premultiplied alpha, sRGB color interpretation, identical dimensions, and the same sample count; a runtime surface that reports different effective facts refuses comparison.

Keep the tagged harness baseline-compatible: `FramePlanWindowBenchmarkOptions` and its test validate only pure command/schema values. Step 5 does not create a `kadreBenchmark` source set, resolve Kadre, compile `KadreFramePlanBenchmark`, or reference `GPUPreparedWindowOutput`, `GPUPreparedSurfaceSession`, `PresentToWindow`, queue tickets, or frame telemetry. The actual window runner and Gradle wiring are added only in the candidate-only post-tag commit below.

Check in this exact two-hunk driver-only patch against Skia revision `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`:

```diff
diff --git a/bench/nanobench.cpp b/bench/nanobench.cpp
--- a/bench/nanobench.cpp
+++ b/bench/nanobench.cpp
@@ -353,6 +353,7 @@ struct GraphiteTarget : public Target {
             std::unique_ptr<skgpu::graphite::Recording> recording = this->recorder->snap();
             if (recording) {
                 this->testContext->submitRecordingAndWaitOnSync(this->context, recording.get());
+                this->testContext->syncedSubmit(this->context);
             }
         }
     }
@@ -1410,7 +1410,6 @@ int main(int argc, char** argv) {
     grContextOpts.fShaderErrorHandler = &errorHandler;
 
     if (kAutoTuneLoops != FLAGS_loops) {
-        FLAGS_samples     = 1;
         FLAGS_gpuFrameLag = 0;
     }
```

The shell script validates clean revisions, host/adapter/driver/JDK/power metadata, exact dimensions/format/alpha/color-space/sample plan, Graphite source and derived commits, patch SHA-256, sample counts, hashes, and lane separation. Its `--self-test` uses temporary fixture output and performs no GPU benchmark or Kadre resolution. It rejects any Skia patch outside `bench/nanobench.cpp`, any hunk other than removal of the `FLAGS_samples` assignment and insertion of `syncedSubmit()` after the current recording submission, or a measured sample that leaves unfinished Graphite work. The latter assertion is backed by the synchronous current-frame barrier, not by `gpuFrameLag`.

```bash
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.SkiaGmBenchmarkRunnerTest'
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.FramePlanWindowBenchmarkOptionsTest'
rtk tools/performance/run-frame-plan-benchmarks.sh --self-test
rtk ./gradlew :integration-tests:skia:test
rtk git add integration-tests/skia/build.gradle.kts integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkSceneSelection.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunner.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunnerTest.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkWorkloadManifest.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkWorkloadManifestTest.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/AAXfermodesGm.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/HairModesGm.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/XfermodesGm.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ThinRectsGm.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/gradient/LinearGradientGm.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ManyCirclesGm.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/FramePlanWindowBenchmarkOptions.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/FramePlanWindowBenchmarkOptionsTest.kt tools/performance/run-frame-plan-benchmarks.sh tools/performance/frame-plan-benchmark.schema.json tools/performance/fixtures/frame-plan-gates tools/performance/skia-nanobench-fixed-loop-multi-sample.patch
rtk git commit -m 'test: add reproducible GPU frame benchmark harness'
rtk git tag kanvas-frame-plan-benchmark-harness-2026-07-13 HEAD
```

Keep this tagged commit source-compatible with the pre-implementation GM runner: it may depend only on the stable Skia GM registry/render result/readback surface, not new frame-plan, queue-ticket, or telemetry types. The fixed tag lets the exact harness/workload patch be applied to a baseline-derived worktree without moving renderer changes backward.

After tagging, add `SkiaGmSteadyFrameBenchmarkRunner` and the actual Kadre window lane in a separate candidate-only commit. The steady runner selects the registry-owned GM, calls `onOnceBeforeDraw`, then obtains one production `Surface.prepareGpuRenderSession()` before timing. Each retained sample performs this exact boundary: start the timer; call `renderFrame(CurrentFrameCompletionOnly) { canvas -> GmCanvas(canvas).drawBackgroundAndGmContent(...) }`; await that returned handle's `completed` stage and verify the completed attempt ID equals the handle's attempt ID; then stop the timer. The block records a fresh frame, including the same background/content work Graphite times, followed by exactly one submit and the exact completion of that same frame. `onOnceBeforeDraw` remains outside timing. The runner asserts no operation accumulation between samples, zero readback/copy-map work, and zero outstanding GPU work before the timer stops. It may not import `GPUOpMapper`, duplicate route logic, use reflection, or create a target itself.

In this same candidate-only commit, configure the opt-in `kadreBenchmark` source set and `runFramePlanKadreNativeBenchmark` task to consume the exact compiled output of `sourceSets["test"]`, including the selector, registry, `GmCanvas`, and selected GM classes. `KadreFramePlanBenchmark` creates one opaque `GPUPreparedWindowOutput`, reuses one production prepared surface session, records the registry-owned GM through `PresentToWindow`, records immediate presentation separately, and awaits the same handle's matching completed attempt on the benchmark worker. The ordinary interactive window callback remains non-blocking. Reject alternate scene tables, rect-only shaders, private mappers/executors, or copied GM classes. Kadre resolves only with `-PenableKadreFrameBenchmark=true`, initialized `external/poc-koreos`, `-XstartOnFirstThread`, and `--enable-native-access=ALL-UNNAMED`; ordinary headless tests never resolve it. Missing native window/submodule is informational, but a broken production prepared-window contract is a hard failure. Do not cherry-pick either candidate adapter into the baseline branch and do not use window results for baseline/candidate or Graphite ratios.

```bash
rtk ./gradlew :integration-tests:skia:test --tests 'org.graphiks.kanvas.skia.SkiaGmBenchmarkRunnerTest'
rtk git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmSteadyFrameBenchmarkRunner.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmBenchmarkRunnerTest.kt integration-tests/skia/src/kadreBenchmark/kotlin/org/graphiks/kanvas/skia/KadreFramePlanBenchmark.kt integration-tests/skia/build.gradle.kts
rtk git commit -m 'test: add candidate steady and window frame lanes'
```

The tagged Step 5 script/schema already define the optional steady/window
protocol and invoke those tasks only when present; the candidate-only commit
adds implementations and lazy Gradle wiring but does not modify the tagged
script, schema, fixtures, or Graphite patch. Their Git blob hashes therefore
remain identical in baseline, candidate, and the current orchestrator.

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

On one recorded host/adapter/driver/JDK/power mode, run two non-interchangeable protocols. The `end-to-end-regression` lane compares pre-change and candidate Kanvas through the same stable API and lifecycle. The `steady-frame-cross-engine` lane compares candidate Kanvas and Graphite/Dawn revision `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a` after identical one-time preparation, using the same structurally verified scene, dimensions, physical RGBA8Unorm, premultiplied alpha, sRGB color interpretation, single-sample plan, and current-frame completion boundary. Never compute a ratio across the two lanes. Use isolated checkouts and independent build directories. The Graphite checkout is derived from the pinned source solely by the checked-in nanobench-driver patch:

```bash
rtk git worktree add -b codex/kanvas-frame-benchmark-baseline /Users/chaos/.codex/worktrees/kanvas-frame-baseline bdbab8292a45cc44521d1642714a87feb5a8e8d2
rtk git -C /Users/chaos/.codex/worktrees/kanvas-frame-baseline cherry-pick kanvas-frame-plan-wgsl4k-dependency-2026-07-13
rtk git -C /Users/chaos/.codex/worktrees/kanvas-frame-baseline cherry-pick kanvas-frame-plan-wgpu4k-dependency-2026-07-13
rtk git -C /Users/chaos/.codex/worktrees/kanvas-frame-baseline cherry-pick kanvas-frame-plan-benchmark-harness-2026-07-13
rtk git worktree add --detach /Users/chaos/.codex/worktrees/kanvas-frame-candidate HEAD
rtk git -C /Users/chaos/workspace/kanvas-forge/skia-main worktree add -b codex/kanvas-frame-nanobench-driver /Users/chaos/.codex/worktrees/skia-frame-benchmark defc3a5a92966c32cb2a6a901e2fa3036a13bb8a
rtk git -C /Users/chaos/.codex/worktrees/skia-frame-benchmark apply --check /Users/chaos/.codex/worktrees/da7e/kanvas/tools/performance/skia-nanobench-fixed-loop-multi-sample.patch
rtk git -C /Users/chaos/.codex/worktrees/skia-frame-benchmark apply /Users/chaos/.codex/worktrees/da7e/kanvas/tools/performance/skia-nanobench-fixed-loop-multi-sample.patch
rtk git -C /Users/chaos/.codex/worktrees/skia-frame-benchmark add bench/nanobench.cpp
rtk git -C /Users/chaos/.codex/worktrees/skia-frame-benchmark commit -m 'bench: complete fixed-loop Graphite samples'
```

The baseline-derived branch starts from the exact pre-implementation renderer and adds only the tagged wgsl4k pin, wgpu4k pin, and benchmark harness commits. Before measuring, the script asserts that its diff from `bdbab8292a45cc44521d1642714a87feb5a8e8d2` contains only the dependency alias/consumer/verification/report paths from Tasks 3 and 9 plus the harness/workload/build-script/schema/patch paths from Step 5; any renderer change refuses the baseline. Resolve and record the declared coordinate, timestamped module metadata, SHA-256 of every wgpu4k/native artifact, and native revision independently in baseline and candidate, then require exact equality for `end-to-end-regression`. Any difference makes regression results informational rather than a promotion gate. The manifest records the original renderer commit, both dependency tags/commits, derived baseline commit, harness tag/commit, and candidate commit independently. The candidate worktree is created only after the telemetry and harness commits and its resolved commit ID is written to the manifest. The Graphite manifest records the pinned parent commit, derived local commit, exact one-file diff, and patch SHA-256; any other Skia diff refuses measurement. Require the isolated baseline, candidate, and derived Skia worktrees to be clean immediately before measurement (apart from the exact committed one-file Skia driver patch). Do not require the user's current orchestration/output worktree to be clean: verify the script, schema, and patch bytes against their recorded harness-tag Git blob SHA-256s, allow writes only below the declared report output root, and ignore—without reading, modifying, or staging—unrelated user changes outside those inputs/outputs. Never checkout, reset, or benchmark another revision inside the user's current worktree or the existing Skia checkout. Keep build directories inside their respective worktrees. Separate offscreen/readback from window/present. For each warm case, collect 120 untimed warmups and 300 measured frames; for cold diagnostics, collect 30 independent process launches. Check in all raw samples and SHA-256 hashes. Compute p50 and p95 over all 300 warm samples without deletion.

- [ ] **Step 10: Build and run the exact Kanvas and Graphite/Dawn commands**

The checked-in script runs the regression command in both isolated Kanvas worktrees and the steady-frame command only in the candidate worktree, with absolute output directories under the current worktree's report directory:

```bash
rtk ./gradlew :integration-tests:skia:benchmarkSkiaGms -PkanvasGmBenchNames=aaxfermodes,hairmodes,xfermodes,rect,thinrects,linear_gradient,manycircles -PkanvasGmBenchLane=end-to-end-regression -PkanvasGmBenchTarget=offscreen-readback -PkanvasGmBenchCacheState=warm -PkanvasGmBenchProcessIndex=0 -PkanvasGmBenchWarmup=120 -PkanvasGmBenchSamples=300 -PkanvasGmBenchOutput=/Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw
rtk ./gradlew :integration-tests:skia:benchmarkSkiaGms -PkanvasGmBenchNames=aaxfermodes,hairmodes,xfermodes,rect,thinrects,linear_gradient,manycircles -PkanvasGmBenchLane=steady-frame-cross-engine -PkanvasGmBenchTarget=offscreen-current-frame-completion -PkanvasGmBenchCacheState=warm -PkanvasGmBenchProcessIndex=0 -PkanvasGmBenchWarmup=120 -PkanvasGmBenchSamples=300 -PkanvasGmBenchOutput=/Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw
rtk ./gradlew :integration-tests:skia:runFramePlanKadreNativeBenchmark -PenableKadreFrameBenchmark=true -PkadreFrameBenchNames=aaxfermodes,hairmodes,xfermodes -PkadreFrameBenchWarmup=120 -PkadreFrameBenchSamples=300 -PkadreFrameBenchOutput=/Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw
```

For Graphite/Dawn on macOS Metal, the script verifies the pinned source revision and builds nanobench. One 420-sample process preserves the required immediate sequence: samples 0–119 are labeled warmup and excluded from percentiles, while samples 120–419 are the 300 measured samples. The script also launches 30 separate one-sample nanobench processes for cold informational records:

```bash
rtk bin/gn gen out/KanvasFramePlanRelease --args='is_debug=false skia_enable_graphite=true skia_use_dawn=true skia_use_metal=true'
rtk ninja -C out/KanvasFramePlanRelease nanobench
rtk out/KanvasFramePlanRelease/nanobench --config 'srgb-graphite[api=dawn_mtl,color=8888]' --match '^GM_aaxfermodes$' '^GM_hairmodes$' '^GM_xfermodes$' '^GM_thinrects$' '^GM_manycircles$' --loops 1 --samples 420 --outResultsFile /Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw/graphite-warm-and-measured.json
rtk out/KanvasFramePlanRelease/nanobench --config 'srgb-graphite[api=dawn_mtl,color=8888]' --match '^GM_aaxfermodes$' --loops 1 --samples 1 --outResultsFile /Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan/raw/graphite-aaxfermodes-cold-process-00.json
```

Execute the orchestrator from the current worktree:

```bash
rtk tools/performance/run-frame-plan-benchmarks.sh --baseline-root /Users/chaos/.codex/worktrees/kanvas-frame-baseline --candidate-root /Users/chaos/.codex/worktrees/kanvas-frame-candidate --skia-root /Users/chaos/.codex/worktrees/skia-frame-benchmark --output-root /Users/chaos/.codex/worktrees/da7e/kanvas/reports/upstream-rebaseline/graphite-dawn-frame-plan
```

Graphite's `GMBench.getUniqueName()` prefixes every selected GM with `GM_`, and `--match` consumes separate arguments; the script asserts exactly the manifest-approved names were emitted. `srgb-graphite[api=dawn_mtl,color=8888]` explicitly fixes Dawn/Metal, physical RGBA8Unorm, premultiplied alpha, and sRGB color interpretation; the manifest checks the effective target facts against Kanvas. Stock nanobench forces `FLAGS_samples = 1` for explicit loops, so the versioned driver patch removes that assignment. The second hunk calls `syncedSubmit()` after submitting the current recording, because Graphite's ordinary frame-lag tracker waits an older reusable slot and does not prove completion of the current sample. `--loops 1` prevents batching several `drawContent()` calls into one report. Exactly 420 samples per warm GM are required, indices 0–119 are excluded warmups, and 120–419 are the 300 measured samples. The steady-frame Kanvas adapter likewise performs one frame and waits its exact ticket; both runners assert no unfinished work after every sample. The script launches 30 independent cold processes only as informational initialization evidence. It refuses dirty baseline/candidate/derived-Skia measurement worktrees, wrong revisions, unverified harness input blobs, outputs outside the declared report root, a structurally inequivalent GM, unexpected Skia diff, format/alpha/color-space mismatch, missing current-frame completion, sample-count mismatch, mixed lanes, unsupported window claims, or schema-invalid output. It does not reject or mutate unrelated user-owned changes in the current orchestration worktree. Graphite remains an offscreen steady-frame comparison only; window-present results stay in their separate opt-in Kanvas/Kadre lane.

- [ ] **Step 11: Apply promotion gates**

From the `end-to-end-regression` lane only, require each primary GM to improve at least 2x at p50 and improve p95 against the fresh pre-change run. For the explicitly sampled controls `rect`, `thinrects`, `linear_gradient`, and `manycircles`, require the frozen direct-draw verdict and then allow at most 10% p50 and 15% p95 regression. From the `steady-frame-cross-engine` lane only, publish candidate/Graphite ratios for manifest-equivalent workloads and target at most 2x Graphite p50 for `thinrects` and `manycircles`; `rect` and `linear_gradient` remain Kanvas-only. Never divide an end-to-end value by a steady-frame value. A failed performance gate keeps structural correctness intact but blocks frame-plan performance promotion and requires a profile naming the next dominant cost. Mark these gates release-blocking for that promotion only when the checked-in negative-fixture suite has just passed without mutating any baseline; otherwise emit `reporting-only`.

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
