# GPU Intermediate Planner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Activate the full phase 5 GPU intermediate/destination-read path by default, with explicit planning for destination copies, saveLayer targets, reusable intermediates, shader blends, MSAA resolves, diagnostics, and no crash/exception smoke validation.

**Architecture:** Add a product-level `GPUIntermediatePlanner` in `gpu-renderer` that composes the existing destination-read, saveLayer, blend, MSAA, resource, and pass contracts. The renderer and scene tooling consume an explicit `GPUIntermediatePlan`; procedural offscreen texture and saveLayer decisions move out of `RectOnlyOffscreenRenderer` into a transition adapter/executor. Visual deltas are documented artifacts, while invalid WebGPU states refuse with stable reason codes before encoding.

**Tech Stack:** Kotlin/JVM, Gradle, `kotlin.test`, WebGPU via `wgpu4k`, WGSL snippets, existing Kanvas GPU renderer modules.

## Global Constraints

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep WGSL as the implementation target.
- Do not use CPU readback as a product fallback.
- Do not sample the active attachment while writing it.
- Keep stable refusal diagnostics.
- Do not put backend handles, addresses, or uniform values in durable keys or dumps.
- Do not create runtime types named `Phase5`.
- Do not make `RectOnly` a target architecture; it is only a transitional scene/offscreen adapter.
- Routes are active by default. Product flags are not the normal path for this phase.
- Snapshot and score regressions are acceptable when documented; crashes, undocumented exceptions, stale resources, active attachment sampling, and invalid aliasing are blocking.
- Commands in this repository must be run with the `rtk` prefix.

---

## File Structure

- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/IntermediateContracts.kt`
  Owns backend-neutral intermediate descriptors, plan steps, plan diagnostics, telemetry, and deterministic dumps.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt`
  Composes destination-read, saveLayer, blend, and MSAA planners into one ordered `GPUIntermediatePlan`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStream.kt`
  Lowers accepted intermediate plan steps into existing `GPUPassCommandStream` evidence.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt`
  Adds generic intermediate and MSAA pass commands not covered by current layer-only commands.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/IntermediateResourceProvider.kt`
  Adds provider-owned create/reuse/refuse materialization for intermediate textures.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
  Exposes `materializeIntermediateTexture` and records `intermediate-texture` telemetry.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt`
  Activates `Multiply` and `Screen` when the planner supplies a valid destination-read plan.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt` and `GPUBackendRuntimeNative.kt`
  Extends telemetry for intermediate textures, destination copies, pass splits, and MSAA resolves; no raw handles in dumps.
- Keep `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/PipelineContracts.kt` unchanged unless tests prove a regression. The current `sampleStateHash` axis is the required MSAA pipeline-key boundary.
- Create `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapter.kt`
  Converts the current scene draw plan into core planner requests during transition.
- Create `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanExecutor.kt`
  Executes `GPUIntermediatePlan` with current offscreen runtime calls.
- Modify `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RectOnlyOffscreenRenderer.kt`
  Removes local saveLayer/offscreen decision logic and delegates to the scene adapter/executor.
- Modify scene catalog/report code only for new phase 5 validation scenes and diagnostic lines.
- Add tests under:
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/state/`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/`
  - `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/`
- Add reports under `reports/gpu-renderer/` after final validation.

---

### Task 1: Intermediate Plan Contracts

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/IntermediateContracts.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanContractsTest.kt`

**Interfaces:**
- Consumes: existing `GPUDestinationReadPlan`, `GPUDestinationCopyPlan`, `GPULayerPlan`, `GPUMultisampleTargetDescriptor`.
- Produces:
  - `enum class GPUIntermediatePurpose`
  - `data class GPUIntermediateTextureDescriptor`
  - `sealed interface GPUIntermediatePlanStep`
  - `data class GPUIntermediatePlan`
  - `data class GPUIntermediateTelemetry`
  - `data class GPUIntermediateDiagnostic`
  - `fun GPUIntermediatePlan.dumpLines(): List<String>`

- [ ] **Step 1: Write contract tests**

Add this test file:

```kotlin
package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GPUIntermediatePlanContractsTest {
    @Test
    fun `plan dump exposes ordered destination copy render and composite steps`() {
        val descriptor = GPUIntermediateTextureDescriptor(
            label = "intermediate:dst-copy:cmd-7",
            purpose = GPUIntermediatePurpose.DestinationCopy,
            descriptorHash = "sha256:dst-copy",
            sourceTargetLabel = "surface:main",
            boundsLabel = "bounds:cmd-7",
            width = 64,
            height = 32,
            formatClass = "rgba8unorm",
            usageLabels = listOf("copy_dst", "texture_binding"),
            sampleCount = 1,
            generation = 3,
            lifetimeClass = "pass-local",
            ownerScope = "target:main",
            byteEstimate = 8192,
        )
        val plan = GPUIntermediatePlan(
            planId = "intermediate-plan:screen-blend",
            targetId = "target:main",
            steps = listOf(
                GPUIntermediatePlanStep.CreateIntermediate(descriptor),
                GPUIntermediatePlanStep.CopyDestination(
                    sourceLabel = "surface:main",
                    destination = descriptor,
                    boundsLabel = "bounds:cmd-7",
                    tokenLabel = "dst-token:cmd-7:3",
                    passSplitRequired = true,
                    copyBeforeSample = true,
                ),
                GPUIntermediatePlanStep.BindIntermediate(
                    descriptor = descriptor,
                    bindingLabel = "dst-read:cmd-7",
                    layoutHash = "layout:dst-read",
                ),
                GPUIntermediatePlanStep.RenderToTarget(
                    commandId = "cmd-7",
                    targetLabel = "surface:main",
                    routeLabel = "shader-blend:Screen",
                    orderingToken = "order:cmd-7",
                ),
            ),
            telemetry = GPUIntermediateTelemetry(
                destinationReadCopies = 1,
                copiedBytes = 8192,
                passSplits = 1,
                intermediatesCreated = 1,
                liveIntermediateBytes = 8192,
            ),
        )

        assertEquals(
            listOf(
                "intermediate.plan id=intermediate-plan:screen-blend target=target:main steps=4 diagnostics=none",
                "intermediate.create label=intermediate:dst-copy:cmd-7 purpose=DestinationCopy descriptor=sha256:dst-copy source=surface:main bounds=bounds:cmd-7 size=64x32 format=rgba8unorm sampleCount=1 generation=3 usage=copy_dst,texture_binding lifetime=pass-local owner=target:main bytes=8192",
                "intermediate.copy source=surface:main destination=intermediate:dst-copy:cmd-7 bounds=bounds:cmd-7 token=dst-token:cmd-7:3 split=true copyBeforeSample=true",
                "intermediate.bind label=intermediate:dst-copy:cmd-7 binding=dst-read:cmd-7 layout=layout:dst-read",
                "intermediate.render command=cmd-7 target=surface:main route=shader-blend:Screen ordering=order:cmd-7",
                "intermediate.telemetry destinationReadCopies=1 destinationReadIntermediateBinds=0 copiedBytes=8192 passSplits=1 intermediatesCreated=1 intermediatesReused=0 intermediatesRefused=0 liveIntermediateBytes=8192 layerTargets=0 layerComposites=0 msaaTargets=0 msaaResolves=0",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun `accepted plans cannot mix accepted steps with terminal refusal`() {
        assertFailsWith<IllegalArgumentException> {
            GPUIntermediatePlan(
                planId = "intermediate-plan:invalid",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.RenderToTarget(
                        commandId = "cmd-1",
                        targetLabel = "surface:main",
                        routeLabel = "fixed-function:srcOver",
                        orderingToken = "order:cmd-1",
                    ),
                    GPUIntermediatePlanStep.Refuse(
                        scopeLabel = "cmd-2",
                        reasonCode = "unsupported.destination_read.active_attachment_sampled",
                    ),
                ),
            )
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanContractsTest"
```

Expected: compilation fails because `GPUIntermediatePlan` and related types do not exist.

- [ ] **Step 3: Add the contract implementation**

Create `IntermediateContracts.kt` with these concrete types:

```kotlin
package org.graphiks.kanvas.gpu.renderer.intermediates

enum class GPUIntermediatePurpose {
    DestinationCopy,
    ExistingIntermediate,
    LayerTarget,
    FilterIntermediate,
    MsaaResolve,
}

data class GPUIntermediateTextureDescriptor(
    val label: String,
    val purpose: GPUIntermediatePurpose,
    val descriptorHash: String,
    val sourceTargetLabel: String,
    val boundsLabel: String,
    val width: Int,
    val height: Int,
    val formatClass: String,
    val usageLabels: List<String>,
    val sampleCount: Int,
    val generation: Long,
    val lifetimeClass: String,
    val ownerScope: String,
    val byteEstimate: Long,
) {
    init {
        require(label.isNotBlank()) { "GPUIntermediateTextureDescriptor.label must not be blank" }
        require(descriptorHash.isNotBlank()) { "GPUIntermediateTextureDescriptor.descriptorHash must not be blank" }
        require(sourceTargetLabel.isNotBlank()) { "GPUIntermediateTextureDescriptor.sourceTargetLabel must not be blank" }
        require(boundsLabel.isNotBlank()) { "GPUIntermediateTextureDescriptor.boundsLabel must not be blank" }
        require(width > 0) { "GPUIntermediateTextureDescriptor.width must be positive" }
        require(height > 0) { "GPUIntermediateTextureDescriptor.height must be positive" }
        require(formatClass.isNotBlank()) { "GPUIntermediateTextureDescriptor.formatClass must not be blank" }
        require(usageLabels.isNotEmpty()) { "GPUIntermediateTextureDescriptor.usageLabels must not be empty" }
        require(usageLabels.none { it.isBlank() }) { "GPUIntermediateTextureDescriptor.usageLabels must not contain blanks" }
        require(sampleCount > 0) { "GPUIntermediateTextureDescriptor.sampleCount must be positive" }
        require(generation >= 0L) { "GPUIntermediateTextureDescriptor.generation must be non-negative" }
        require(lifetimeClass.isNotBlank()) { "GPUIntermediateTextureDescriptor.lifetimeClass must not be blank" }
        require(ownerScope.isNotBlank()) { "GPUIntermediateTextureDescriptor.ownerScope must not be blank" }
        require(byteEstimate >= 0L) { "GPUIntermediateTextureDescriptor.byteEstimate must be non-negative" }
    }

    val usageLabel: String get() = usageLabels.joinToString(",")
}

sealed interface GPUIntermediatePlanStep {
    data class RenderToTarget(
        val commandId: String,
        val targetLabel: String,
        val routeLabel: String,
        val orderingToken: String,
    ) : GPUIntermediatePlanStep

    data class CreateIntermediate(val descriptor: GPUIntermediateTextureDescriptor) : GPUIntermediatePlanStep

    data class ReuseIntermediate(val descriptor: GPUIntermediateTextureDescriptor) : GPUIntermediatePlanStep

    data class CopyDestination(
        val sourceLabel: String,
        val destination: GPUIntermediateTextureDescriptor,
        val boundsLabel: String,
        val tokenLabel: String,
        val passSplitRequired: Boolean,
        val copyBeforeSample: Boolean,
    ) : GPUIntermediatePlanStep

    data class BindIntermediate(
        val descriptor: GPUIntermediateTextureDescriptor,
        val bindingLabel: String,
        val layoutHash: String,
    ) : GPUIntermediatePlanStep

    data class RenderLayerChildren(
        val scopeLabel: String,
        val target: GPUIntermediateTextureDescriptor,
        val childrenLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep

    data class CompositeIntermediate(
        val source: GPUIntermediateTextureDescriptor,
        val parentTargetLabel: String,
        val blendModeLabel: String,
        val routeLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep

    data class ResolveMSAA(
        val source: GPUIntermediateTextureDescriptor,
        val destination: GPUIntermediateTextureDescriptor,
        val strategyLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep

    data class Refuse(
        val scopeLabel: String,
        val reasonCode: String,
    ) : GPUIntermediatePlanStep
}

data class GPUIntermediateDiagnostic(
    val code: String,
    val scopeLabel: String,
    val message: String,
    val terminal: Boolean,
) {
    init {
        require(code.isNotBlank()) { "GPUIntermediateDiagnostic.code must not be blank" }
        require(scopeLabel.isNotBlank()) { "GPUIntermediateDiagnostic.scopeLabel must not be blank" }
        require(message.isNotBlank()) { "GPUIntermediateDiagnostic.message must not be blank" }
    }
}

data class GPUIntermediateTelemetry(
    val destinationReadCopies: Long = 0L,
    val destinationReadIntermediateBinds: Long = 0L,
    val copiedBytes: Long = 0L,
    val passSplits: Long = 0L,
    val intermediatesCreated: Long = 0L,
    val intermediatesReused: Long = 0L,
    val intermediatesRefused: Long = 0L,
    val liveIntermediateBytes: Long = 0L,
    val layerTargets: Long = 0L,
    val layerComposites: Long = 0L,
    val msaaTargets: Long = 0L,
    val msaaResolves: Long = 0L,
) {
    fun dumpLine(): String =
        "intermediate.telemetry destinationReadCopies=$destinationReadCopies " +
            "destinationReadIntermediateBinds=$destinationReadIntermediateBinds copiedBytes=$copiedBytes " +
            "passSplits=$passSplits intermediatesCreated=$intermediatesCreated " +
            "intermediatesReused=$intermediatesReused intermediatesRefused=$intermediatesRefused " +
            "liveIntermediateBytes=$liveIntermediateBytes layerTargets=$layerTargets " +
            "layerComposites=$layerComposites msaaTargets=$msaaTargets msaaResolves=$msaaResolves"
}

data class GPUIntermediatePlan(
    val planId: String,
    val targetId: String,
    val steps: List<GPUIntermediatePlanStep>,
    val diagnostics: List<GPUIntermediateDiagnostic> = emptyList(),
    val telemetry: GPUIntermediateTelemetry = GPUIntermediateTelemetry(),
) {
    init {
        require(planId.isNotBlank()) { "GPUIntermediatePlan.planId must not be blank" }
        require(targetId.isNotBlank()) { "GPUIntermediatePlan.targetId must not be blank" }
        require(steps.isNotEmpty()) { "GPUIntermediatePlan.steps must not be empty" }
        val hasRefusal = steps.any { it is GPUIntermediatePlanStep.Refuse }
        require(!hasRefusal || steps.size == 1) {
            "GPUIntermediatePlan cannot mix terminal refusal with executable steps"
        }
    }
}

fun GPUIntermediatePlan.dumpLines(): List<String> =
    listOf(
        "intermediate.plan id=$planId target=$targetId steps=${steps.size} " +
            "diagnostics=${diagnostics.map { it.code }.ifEmpty { listOf("none") }.joinToString(",")}",
    ) + steps.map { step -> step.dumpLine() } + listOf(telemetry.dumpLine())

private fun GPUIntermediatePlanStep.dumpLine(): String =
    when (this) {
        is GPUIntermediatePlanStep.RenderToTarget ->
            "intermediate.render command=$commandId target=$targetLabel route=$routeLabel ordering=$orderingToken"
        is GPUIntermediatePlanStep.CreateIntermediate ->
            "intermediate.create ${descriptor.dumpFields()}"
        is GPUIntermediatePlanStep.ReuseIntermediate ->
            "intermediate.reuse ${descriptor.dumpFields()}"
        is GPUIntermediatePlanStep.CopyDestination ->
            "intermediate.copy source=$sourceLabel destination=${destination.label} bounds=$boundsLabel " +
                "token=$tokenLabel split=$passSplitRequired copyBeforeSample=$copyBeforeSample"
        is GPUIntermediatePlanStep.BindIntermediate ->
            "intermediate.bind label=${descriptor.label} binding=$bindingLabel layout=$layoutHash"
        is GPUIntermediatePlanStep.RenderLayerChildren ->
            "intermediate.layer-children scope=$scopeLabel target=${target.label} children=$childrenLabel token=$tokenLabel"
        is GPUIntermediatePlanStep.CompositeIntermediate ->
            "intermediate.composite source=${source.label} parent=$parentTargetLabel blend=$blendModeLabel route=$routeLabel token=$tokenLabel"
        is GPUIntermediatePlanStep.ResolveMSAA ->
            "intermediate.msaa-resolve source=${source.label} destination=${destination.label} strategy=$strategyLabel token=$tokenLabel"
        is GPUIntermediatePlanStep.Refuse ->
            "intermediate.refused scope=$scopeLabel reason=$reasonCode"
    }

private fun GPUIntermediateTextureDescriptor.dumpFields(): String =
    "label=$label purpose=$purpose descriptor=$descriptorHash source=$sourceTargetLabel bounds=$boundsLabel " +
        "size=${width}x$height format=$formatClass sampleCount=$sampleCount generation=$generation " +
        "usage=$usageLabel lifetime=$lifetimeClass owner=$ownerScope bytes=$byteEstimate"
```

- [ ] **Step 4: Run the contract tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanContractsTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/IntermediateContracts.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanContractsTest.kt
rtk git commit -m "Add GPU intermediate plan contracts"
```

---

### Task 2: Pass Commands and Command Stream Lowering

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStream.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStreamTest.kt`

**Interfaces:**
- Consumes: `GPUIntermediatePlan`, `GPUIntermediatePlanStep`, `GPUPassCommandStream`.
- Produces:
  - `GPUPassCommand.PrepareIntermediateTexture`
  - `GPUPassCommand.ResolveMSAA`
  - `GPUPassCommand.RefuseIntermediate`
  - `fun GPUPassCommandStream.Companion.fromIntermediatePlan(...)`

- [ ] **Step 1: Write failing command-stream tests**

Add:

```kotlin
package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream

class GPUIntermediateCommandStreamTest {
    @Test
    fun `destination copy lowers before draw that samples it`() {
        val descriptor = descriptor("intermediate:dst-copy:cmd-1", GPUIntermediatePurpose.DestinationCopy)
        val stream = GPUPassCommandStream.fromIntermediatePlan(
            streamId = "stream:intermediate",
            packetStreamId = "packets:intermediate",
            passId = "pass:main",
            targetStateHash = "target:rgba8:sample1",
            loadStoreLabel = "load-store:load-store",
            plan = GPUIntermediatePlan(
                planId = "plan:screen",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.CreateIntermediate(descriptor),
                    GPUIntermediatePlanStep.CopyDestination(
                        sourceLabel = "surface:main",
                        destination = descriptor,
                        boundsLabel = "bounds:cmd-1",
                        tokenLabel = "dst-token:cmd-1:1",
                        passSplitRequired = true,
                        copyBeforeSample = true,
                    ),
                    GPUIntermediatePlanStep.RenderToTarget(
                        commandId = "cmd-1",
                        targetLabel = "surface:main",
                        routeLabel = "shader-blend:Screen",
                        orderingToken = "order:cmd-1",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("beginRenderPass", "prepareIntermediateTexture", "copyTexture", "draw", "endRenderPass"),
            stream.commandLabels,
        )
        val prepare = assertIs<GPUPassCommand.PrepareIntermediateTexture>(stream.commands[1])
        assertEquals("intermediate:dst-copy:cmd-1", prepare.textureLabel)
        val copy = assertIs<GPUPassCommand.CopyTexture>(stream.commands[2])
        assertEquals("surface:main", copy.sourceLabel)
        assertEquals("intermediate:dst-copy:cmd-1", copy.destinationLabel)
    }

    @Test
    fun `msaa resolve lowers to explicit resolve command`() {
        val msaa = descriptor("intermediate:msaa:layer-a", GPUIntermediatePurpose.LayerTarget, sampleCount = 4)
        val resolved = descriptor("intermediate:resolved:layer-a", GPUIntermediatePurpose.MsaaResolve, sampleCount = 1)
        val stream = GPUPassCommandStream.fromIntermediatePlan(
            streamId = "stream:msaa",
            packetStreamId = "packets:msaa",
            passId = "pass:msaa",
            targetStateHash = "target:rgba8:sample4",
            loadStoreLabel = "load-store:clear-resolve",
            plan = GPUIntermediatePlan(
                planId = "plan:msaa",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.CreateIntermediate(msaa),
                    GPUIntermediatePlanStep.ResolveMSAA(
                        source = msaa,
                        destination = resolved,
                        strategyLabel = "WGPU_BUILTIN",
                        tokenLabel = "msaa-token:layer-a",
                    ),
                ),
            ),
        )

        assertEquals(listOf("beginRenderPass", "prepareIntermediateTexture", "resolveMSAA", "endRenderPass"), stream.commandLabels)
        assertIs<GPUPassCommand.ResolveMSAA>(stream.commands[2])
    }

    private fun descriptor(
        label: String,
        purpose: GPUIntermediatePurpose,
        sampleCount: Int = 1,
    ): GPUIntermediateTextureDescriptor =
        GPUIntermediateTextureDescriptor(
            label = label,
            purpose = purpose,
            descriptorHash = "sha256:$label",
            sourceTargetLabel = "surface:main",
            boundsLabel = "bounds:all",
            width = 64,
            height = 64,
            formatClass = "rgba8unorm",
            usageLabels = listOf("render_attachment", "texture_binding", "copy_dst"),
            sampleCount = sampleCount,
            generation = 1,
            lifetimeClass = "pass-local",
            ownerScope = "target:main",
            byteEstimate = 16384,
        )
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateCommandStreamTest"
```

Expected: compilation fails because pass commands and lowering function do not exist.

- [ ] **Step 3: Add pass commands**

In `PassContracts.kt`, add these classes inside `sealed interface GPUPassCommand`, next to `PrepareLayerTarget` and before refusal commands:

```kotlin
    /** Materializes or reuses a generic intermediate texture before copy, layer, filter, or MSAA work. */
    data class PrepareIntermediateTexture(
        val textureLabel: String,
        val purposeLabel: String,
        val descriptorHash: String,
        val usageLabel: String,
        val sampleCount: Int,
        val byteEstimate: Long,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "prepareIntermediateTexture"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(textureLabel.isNotBlank()) { "PrepareIntermediateTexture.textureLabel must not be blank" }
            require(purposeLabel.isNotBlank()) { "PrepareIntermediateTexture.purposeLabel must not be blank" }
            require(descriptorHash.isNotBlank()) { "PrepareIntermediateTexture.descriptorHash must not be blank" }
            require(usageLabel.isNotBlank()) { "PrepareIntermediateTexture.usageLabel must not be blank" }
            require(sampleCount > 0) { "PrepareIntermediateTexture.sampleCount must be positive" }
            require(byteEstimate >= 0L) { "PrepareIntermediateTexture.byteEstimate must be non-negative" }
        }
    }

    /** Resolves a multisample intermediate into a single-sample texture before sampling or presentation. */
    data class ResolveMSAA(
        val sourceLabel: String,
        val destinationLabel: String,
        val strategyLabel: String,
        val tokenLabel: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "resolveMSAA"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(sourceLabel.isNotBlank()) { "ResolveMSAA.sourceLabel must not be blank" }
            require(destinationLabel.isNotBlank()) { "ResolveMSAA.destinationLabel must not be blank" }
            require(strategyLabel.isNotBlank()) { "ResolveMSAA.strategyLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "ResolveMSAA.tokenLabel must not be blank" }
        }
    }

    /** Records a stable intermediate planning or materialization refusal. */
    data class RefuseIntermediate(
        val scopeLabel: String,
        val reasonCode: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "refuseIntermediate"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(scopeLabel.isNotBlank()) { "RefuseIntermediate.scopeLabel must not be blank" }
            require(reasonCode.isNotBlank()) { "RefuseIntermediate.reasonCode must not be blank" }
        }
    }
```

- [ ] **Step 4: Add command stream lowering**

Create `GPUIntermediateCommandStream.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.intermediates

import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream

fun GPUPassCommandStream.Companion.fromIntermediatePlan(
    streamId: String,
    packetStreamId: String,
    passId: String,
    targetStateHash: String,
    loadStoreLabel: String,
    plan: GPUIntermediatePlan,
): GPUPassCommandStream {
    require(targetStateHash.isNotBlank()) { "fromIntermediatePlan targetStateHash must not be blank" }
    require(loadStoreLabel.isNotBlank()) { "fromIntermediatePlan loadStoreLabel must not be blank" }

    val commands = buildList {
        add(GPUPassCommand.BeginRenderPass(targetStateHash = targetStateHash, loadStoreLabel = loadStoreLabel))
        plan.steps.forEach { step ->
            when (step) {
                is GPUIntermediatePlanStep.CreateIntermediate ->
                    add(step.descriptor.prepareCommand())
                is GPUIntermediatePlanStep.ReuseIntermediate ->
                    add(step.descriptor.prepareCommand())
                is GPUIntermediatePlanStep.CopyDestination ->
                    add(
                        GPUPassCommand.CopyTexture(
                            sourceLabel = step.sourceLabel,
                            destinationLabel = step.destination.label,
                            boundsLabel = step.boundsLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                is GPUIntermediatePlanStep.BindIntermediate -> Unit
                is GPUIntermediatePlanStep.RenderToTarget ->
                    add(
                        GPUPassCommand.Draw(
                            vertexSourceLabel = step.routeLabel,
                            packetId = GPUDrawPacketID(step.commandId),
                        ),
                    )
                is GPUIntermediatePlanStep.RenderLayerChildren ->
                    add(
                        GPUPassCommand.RenderLayerChildren(
                            scopeLabel = step.scopeLabel,
                            targetLabel = step.target.label,
                            childrenLabel = step.childrenLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                is GPUIntermediatePlanStep.CompositeIntermediate ->
                    add(
                        GPUPassCommand.CompositeLayer(
                            sourceLabel = step.source.label,
                            parentTargetLabel = step.parentTargetLabel,
                            blendModeLabel = step.blendModeLabel,
                            routeLabel = step.routeLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                is GPUIntermediatePlanStep.ResolveMSAA ->
                    add(
                        GPUPassCommand.ResolveMSAA(
                            sourceLabel = step.source.label,
                            destinationLabel = step.destination.label,
                            strategyLabel = step.strategyLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                is GPUIntermediatePlanStep.Refuse ->
                    add(GPUPassCommand.RefuseIntermediate(scopeLabel = step.scopeLabel, reasonCode = step.reasonCode))
            }
        }
        add(GPUPassCommand.EndRenderPass(passId = passId))
    }

    return GPUPassCommandStream(
        streamId = streamId,
        packetStreamId = packetStreamId,
        passId = passId,
        commands = commands,
        diagnostics = emptyList(),
        operandBridge = emptyList(),
    )
}

private fun GPUIntermediateTextureDescriptor.prepareCommand(): GPUPassCommand.PrepareIntermediateTexture =
    GPUPassCommand.PrepareIntermediateTexture(
        textureLabel = label,
        purposeLabel = purpose.name,
        descriptorHash = descriptorHash,
        usageLabel = usageLabel,
        sampleCount = sampleCount,
        byteEstimate = byteEstimate,
    )
```

- [ ] **Step 5: Run command-stream tests and existing pass tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateCommandStreamTest" --tests "org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchCommandStreamTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStream.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateCommandStreamTest.kt
rtk git commit -m "Lower GPU intermediate plans to pass commands"
```

---

### Task 3: GPUIntermediatePlanner Composition

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlannerTest.kt`

**Interfaces:**
- Consumes:
  - `GPUDestinationReadStrategyPlanner.plan(GPUDestinationReadStrategyRequest)`
  - `GPUSaveLayerIsolatedTargetPlanner.plan(GPUSaveLayerIsolatedTargetRequest)`
  - `GPUBlendAllowlistPlanner.plan(GPUBlendAllowlistRequest)`
  - `GPUMsaa.resolve(GPUMsaaRequest)`
- Produces:
  - `data class GPUIntermediateDrawRequest`
  - `data class GPUIntermediatePlannerRequest`
  - `class GPUIntermediatePlanner`
  - `fun GPUIntermediatePlanner.plan(request: GPUIntermediatePlannerRequest): GPUIntermediatePlan`

- [ ] **Step 1: Write planner tests**

Add:

```kotlin
package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

class GPUIntermediatePlannerTest {
    @Test
    fun `srcOver draw renders directly without destination copy`() {
        val plan = GPUIntermediatePlanner().plan(
            request(
                GPUIntermediateDrawRequest(
                    commandId = "cmd-1",
                    targetLabel = "surface:main",
                    targetGeneration = 1,
                    bounds = bounds("cmd-1"),
                    blendMode = GPUBlendMode.SrcOver,
                    materialKeyHash = "material:solid",
                    renderStepIdentity = "rect-fill",
                ),
            ),
        )

        assertEquals(
            listOf("GPUIntermediatePlanStep.RenderToTarget"),
            plan.steps.map { it::class.qualifiedName!!.substringAfterLast('.') },
        )
        assertEquals(0, plan.telemetry.destinationReadCopies)
        assertTrue(plan.dumpLines().any { it.contains("route=fixed-function:SrcOver") })
    }

    @Test
    fun `screen blend creates destination copy before shader blend render`() {
        val plan = GPUIntermediatePlanner().plan(
            request(
                GPUIntermediateDrawRequest(
                    commandId = "cmd-screen",
                    targetLabel = "surface:main",
                    targetGeneration = 9,
                    bounds = bounds("cmd-screen"),
                    blendMode = GPUBlendMode.Screen,
                    materialKeyHash = "material:screen",
                    renderStepIdentity = "rect-fill",
                ),
            ),
        )

        assertEquals(
            listOf(
                "CreateIntermediate",
                "CopyDestination",
                "BindIntermediate",
                "RenderToTarget",
            ),
            plan.steps.map { it::class.simpleName },
        )
        assertEquals(1, plan.telemetry.destinationReadCopies)
        assertEquals(1, plan.telemetry.passSplits)
        assertTrue(plan.dumpLines().any { it.contains("shader-blend:Screen") })
    }

    @Test
    fun `active attachment sampling refuses before encoding`() {
        val plan = GPUIntermediatePlanner().plan(
            request(
                GPUIntermediateDrawRequest(
                    commandId = "cmd-bad",
                    targetLabel = "surface:main",
                    targetGeneration = 2,
                    bounds = bounds("cmd-bad"),
                    blendMode = GPUBlendMode.Multiply,
                    materialKeyHash = "material:multiply",
                    renderStepIdentity = "rect-fill",
                    activeAttachmentSampled = true,
                ),
            ),
        )

        assertEquals(listOf("Refuse"), plan.steps.map { it::class.simpleName })
        assertEquals(
            "intermediate.refused scope=cmd-bad reason=unsupported.destination_read.active_attachment_sampled",
            plan.dumpLines()[1],
        )
    }

    private fun request(draw: GPUIntermediateDrawRequest): GPUIntermediatePlannerRequest =
        GPUIntermediatePlannerRequest(
            planId = "plan:test",
            targetId = "target:main",
            targetFormatClass = "rgba8unorm",
            targetUsageLabels = setOf("render_attachment", "copy_src", "copy_dst", "texture_binding"),
            deviceGeneration = 1,
            drawRequests = listOf(draw),
        )

    private fun bounds(commandId: String): GPUDestinationReadBounds =
        GPUDestinationReadBounds(
            boundsLabel = "bounds:$commandId",
            conservative = true,
            pixelAligned = true,
            requestedBoundsLabel = "requested:$commandId",
            unclippedBoundsLabel = "unclipped:$commandId",
            clippedBoundsLabel = "clipped:$commandId",
            copyBoundsLabel = "copy:$commandId",
            width = 32,
            height = 16,
            targetWidth = 320,
            targetHeight = 200,
        )
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerTest"
```

Expected: compilation fails because `GPUIntermediatePlanner` does not exist.

- [ ] **Step 3: Implement the planner request types**

Add these request types to `GPUIntermediatePlanner.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.intermediates

import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadAction
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyPlanner
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyRequest
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerBoundsPlan
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerExecutionPlan
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerSaveRecord
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerIsolatedTargetPlanner
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerIsolatedTargetRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendAllowlistPlanner
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendAllowlistRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

data class GPUIntermediateDrawRequest(
    val commandId: String,
    val targetLabel: String,
    val targetGeneration: Long,
    val bounds: GPUDestinationReadBounds,
    val blendMode: GPUBlendMode,
    val materialKeyHash: String,
    val renderStepIdentity: String,
    val saveLayer: GPULayerSaveRecord? = null,
    val activeAttachmentSampled: Boolean = false,
) {
    init {
        require(commandId.isNotBlank()) { "GPUIntermediateDrawRequest.commandId must not be blank" }
        require(targetLabel.isNotBlank()) { "GPUIntermediateDrawRequest.targetLabel must not be blank" }
        require(targetGeneration >= 0L) { "GPUIntermediateDrawRequest.targetGeneration must be non-negative" }
        require(materialKeyHash.isNotBlank()) { "GPUIntermediateDrawRequest.materialKeyHash must not be blank" }
        require(renderStepIdentity.isNotBlank()) { "GPUIntermediateDrawRequest.renderStepIdentity must not be blank" }
    }
}

data class GPUIntermediatePlannerRequest(
    val planId: String,
    val targetId: String,
    val targetFormatClass: String,
    val targetUsageLabels: Set<String>,
    val deviceGeneration: Long,
    val drawRequests: List<GPUIntermediateDrawRequest>,
) {
    init {
        require(planId.isNotBlank()) { "GPUIntermediatePlannerRequest.planId must not be blank" }
        require(targetId.isNotBlank()) { "GPUIntermediatePlannerRequest.targetId must not be blank" }
        require(targetFormatClass.isNotBlank()) { "GPUIntermediatePlannerRequest.targetFormatClass must not be blank" }
        require(targetUsageLabels.none { it.isBlank() }) { "GPUIntermediatePlannerRequest.targetUsageLabels must not contain blanks" }
        require(deviceGeneration >= 0L) { "GPUIntermediatePlannerRequest.deviceGeneration must be non-negative" }
        require(drawRequests.isNotEmpty()) { "GPUIntermediatePlannerRequest.drawRequests must not be empty" }
    }
}
```

- [ ] **Step 4: Implement planner orchestration**

In the same file, add:

```kotlin
class GPUIntermediatePlanner(
    private val destinationPlanner: GPUDestinationReadStrategyPlanner = GPUDestinationReadStrategyPlanner(),
    private val saveLayerPlanner: GPUSaveLayerIsolatedTargetPlanner = GPUSaveLayerIsolatedTargetPlanner(),
    private val blendPlanner: GPUBlendAllowlistPlanner = GPUBlendAllowlistPlanner(),
) {
    fun plan(request: GPUIntermediatePlannerRequest): GPUIntermediatePlan {
        val steps = mutableListOf<GPUIntermediatePlanStep>()
        var telemetry = GPUIntermediateTelemetry()

        for (draw in request.drawRequests) {
            if (draw.activeAttachmentSampled) {
                return request.refused(draw.commandId, "unsupported.destination_read.active_attachment_sampled")
            }

            val saveLayer = draw.saveLayer
            if (saveLayer != null) {
                val layerSteps = planSaveLayer(request, draw, saveLayer)
                steps += layerSteps
                telemetry = telemetry.copy(
                    intermediatesCreated = telemetry.intermediatesCreated + 1,
                    liveIntermediateBytes = telemetry.liveIntermediateBytes + layerSteps.layerBytes(),
                    layerTargets = telemetry.layerTargets + 1,
                    layerComposites = telemetry.layerComposites + 1,
                )
                continue
            }

            if (draw.blendMode == GPUBlendMode.Multiply || draw.blendMode == GPUBlendMode.Screen) {
                val destination = destinationPlanner.plan(draw.destinationReadRequest(request))
                val refusal = destination.diagnostics.firstOrNull { it.terminal }?.code
                if (refusal != null) return request.refused(draw.commandId, refusal)

                val blend = blendPlanner.plan(
                    GPUBlendAllowlistRequest(
                        commandId = draw.commandId,
                        mode = draw.blendMode,
                        targetFormatClass = request.targetFormatClass,
                        materialKeyHash = draw.materialKeyHash,
                        renderStepIdentity = draw.renderStepIdentity,
                        destinationReadPlan = destination,
                        destinationReadCopyBoundsLabel = destination.plan.bounds.copyBoundsLabel,
                        destinationReadGeneration = draw.targetGeneration,
                    ),
                )
                val blendRefusal = blend.diagnostics.firstOrNull { it.terminal }?.code
                if (blendRefusal != null && blendRefusal != "unsupported.blend.shader_route_unvalidated") {
                    return request.refused(draw.commandId, blendRefusal)
                }

                val descriptor = requireNotNull(destination.copyDescriptor).toIntermediateDescriptor(draw)
                steps += GPUIntermediatePlanStep.CreateIntermediate(descriptor)
                steps += GPUIntermediatePlanStep.CopyDestination(
                    sourceLabel = draw.targetLabel,
                    destination = descriptor,
                    boundsLabel = destination.plan.bounds.copyBoundsLabel,
                    tokenLabel = requireNotNull(destination.copyPlan).token.value,
                    passSplitRequired = true,
                    copyBeforeSample = true,
                )
                steps += GPUIntermediatePlanStep.BindIntermediate(
                    descriptor = descriptor,
                    bindingLabel = requireNotNull(destination.plan.binding).bindingLabel,
                    layoutHash = requireNotNull(destination.plan.binding).layoutHash,
                )
                steps += GPUIntermediatePlanStep.RenderToTarget(
                    commandId = draw.commandId,
                    targetLabel = draw.targetLabel,
                    routeLabel = "shader-blend:${draw.blendMode}",
                    orderingToken = "order:${draw.commandId}",
                )
                telemetry = telemetry.copy(
                    destinationReadCopies = telemetry.destinationReadCopies + 1,
                    copiedBytes = telemetry.copiedBytes + descriptor.byteEstimate,
                    passSplits = telemetry.passSplits + 1,
                    intermediatesCreated = telemetry.intermediatesCreated + 1,
                    liveIntermediateBytes = telemetry.liveIntermediateBytes + descriptor.byteEstimate,
                )
            } else {
                val blend = blendPlanner.plan(
                    GPUBlendAllowlistRequest(
                        commandId = draw.commandId,
                        mode = draw.blendMode,
                        targetFormatClass = request.targetFormatClass,
                        materialKeyHash = draw.materialKeyHash,
                        renderStepIdentity = draw.renderStepIdentity,
                    ),
                )
                val refusal = blend.diagnostics.firstOrNull { it.terminal }?.code
                if (refusal != null) return request.refused(draw.commandId, refusal)
                steps += GPUIntermediatePlanStep.RenderToTarget(
                    commandId = draw.commandId,
                    targetLabel = draw.targetLabel,
                    routeLabel = "fixed-function:${draw.blendMode}",
                    orderingToken = "order:${draw.commandId}",
                )
            }
        }

        return GPUIntermediatePlan(
            planId = request.planId,
            targetId = request.targetId,
            steps = steps,
            telemetry = telemetry,
        )
    }

    private fun planSaveLayer(
        request: GPUIntermediatePlannerRequest,
        draw: GPUIntermediateDrawRequest,
        saveLayer: GPULayerSaveRecord,
    ): List<GPUIntermediatePlanStep> {
        val layer = saveLayerPlanner.plan(
            GPUSaveLayerIsolatedTargetRequest(
                saveRecord = saveLayer,
                bounds = GPULayerBoundsPlan(
                    requestedBoundsLabel = draw.bounds.requestedBoundsLabel,
                    deviceBoundsLabel = draw.bounds.clippedBoundsLabel,
                    conservative = draw.bounds.conservative,
                    finite = draw.bounds.finite,
                    originX = draw.bounds.originX,
                    originY = draw.bounds.originY,
                    width = draw.bounds.width,
                    height = draw.bounds.height,
                ),
                parentTargetLabel = draw.targetLabel,
                targetFormatClass = request.targetFormatClass,
                sampleCount = 1,
                availableUsageLabels = request.targetUsageLabels,
                deviceGeneration = request.deviceGeneration,
            ),
        )
        val refusal = layer.diagnostics.firstOrNull { it.terminal }?.code
        if (refusal != null) return listOf(GPUIntermediatePlanStep.Refuse(saveLayer.scopeId.value, refusal))
        val isolated = layer.layerPlan.execution as GPULayerExecutionPlan.IsolatedTarget
        val descriptor = GPUIntermediateTextureDescriptor(
            label = isolated.target.targetLabel,
            purpose = GPUIntermediatePurpose.LayerTarget,
            descriptorHash = isolated.target.targetDescriptorHash,
            sourceTargetLabel = draw.targetLabel,
            boundsLabel = layer.layerPlan.bounds.deviceBoundsLabel,
            width = layer.layerPlan.bounds.width,
            height = layer.layerPlan.bounds.height,
            formatClass = isolated.target.formatClass,
            usageLabels = isolated.target.usageLabels,
            sampleCount = isolated.target.sampleCount,
            generation = request.deviceGeneration,
            lifetimeClass = isolated.target.lifetimeClass,
            ownerScope = isolated.target.ownerLabel,
            byteEstimate = isolated.target.byteEstimate,
        )
        return listOf(
            GPUIntermediatePlanStep.CreateIntermediate(descriptor),
            GPUIntermediatePlanStep.RenderLayerChildren(
                scopeLabel = saveLayer.scopeId.value,
                target = descriptor,
                childrenLabel = saveLayer.childCommandIds.joinToString(",").ifEmpty { "none" },
                tokenLabel = isolated.composite.orderingToken.value,
            ),
            GPUIntermediatePlanStep.CompositeIntermediate(
                source = descriptor,
                parentTargetLabel = draw.targetLabel,
                blendModeLabel = isolated.composite.blendModeLabel,
                routeLabel = isolated.composite.compositeRoute,
                tokenLabel = isolated.composite.orderingToken.value,
            ),
        )
    }
}
```

- [ ] **Step 5: Add private helpers**

Append helpers in `GPUIntermediatePlanner.kt`:

```kotlin
private fun GPUIntermediatePlannerRequest.refused(scopeLabel: String, reasonCode: String): GPUIntermediatePlan =
    GPUIntermediatePlan(
        planId = planId,
        targetId = targetId,
        steps = listOf(GPUIntermediatePlanStep.Refuse(scopeLabel = scopeLabel, reasonCode = reasonCode)),
        diagnostics = listOf(
            GPUIntermediateDiagnostic(
                code = reasonCode,
                scopeLabel = scopeLabel,
                message = "intermediate planner refused $scopeLabel: $reasonCode",
                terminal = true,
            ),
        ),
        telemetry = GPUIntermediateTelemetry(intermediatesRefused = 1),
    )

private fun GPUIntermediateDrawRequest.destinationReadRequest(
    request: GPUIntermediatePlannerRequest,
): GPUDestinationReadStrategyRequest =
    GPUDestinationReadStrategyRequest(
        commandId = commandId,
        requirement = GPUDestinationReadRequirement.TargetCopy,
        strategy = GPUDestinationReadStrategy.CopyTarget,
        action = GPUDestinationReadAction.SplitPassAndCopyTarget,
        bounds = bounds,
        sourceTargetLabel = targetLabel,
        sourceUsageLabels = request.targetUsageLabels,
        copyUsageLabels = setOf("copy_dst", "texture_binding"),
        targetFormatClass = request.targetFormatClass,
        targetGeneration = targetGeneration,
        observedTargetGeneration = targetGeneration,
        activeAttachmentSampled = activeAttachmentSampled,
        passSplitAllowed = true,
    )

private fun org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationCopyTextureDescriptor.toIntermediateDescriptor(
    draw: GPUIntermediateDrawRequest,
): GPUIntermediateTextureDescriptor =
    GPUIntermediateTextureDescriptor(
        label = label,
        purpose = GPUIntermediatePurpose.DestinationCopy,
        descriptorHash = descriptorHash,
        sourceTargetLabel = sourceTargetLabel,
        boundsLabel = draw.bounds.copyBoundsLabel,
        width = width,
        height = height,
        formatClass = formatClass,
        usageLabels = usageLabels,
        sampleCount = sampleCount,
        generation = targetGeneration,
        lifetimeClass = lifetimeClass,
        ownerScope = ownerLabel,
        byteEstimate = byteEstimate,
    )

private fun List<GPUIntermediatePlanStep>.layerBytes(): Long =
    filterIsInstance<GPUIntermediatePlanStep.CreateIntermediate>()
        .sumOf { it.descriptor.byteEstimate }
```

- [ ] **Step 6: Run planner and gate tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerTest" --tests "org.graphiks.kanvas.gpu.renderer.destination.DestinationReadStrategyGateTest" --tests "org.graphiks.kanvas.gpu.renderer.layers.SaveLayerIsolatedTargetGateTest" --tests "org.graphiks.kanvas.gpu.renderer.state.BlendAllowlistGateTest"
```

Expected: new planner tests pass. Existing blend test still shows `unsupported.blend.shader_route_unvalidated`; Task 5 will change that.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlannerTest.kt
rtk git commit -m "Add GPU intermediate planner composition"
```

---

### Task 4: Intermediate Resource Provider and Reuse

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/IntermediateResourceProvider.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUIntermediateResourceProviderTest.kt`

**Interfaces:**
- Consumes: `GPUIntermediateTextureDescriptor`, `GPUTargetPreparationContext`, `GPUResourceMaterializationDecision`.
- Produces:
  - `data class GPUIntermediateTextureMaterializationRequest`
  - `fun GPUConcreteResourceProvider.materializeIntermediateTexture(...)`
  - `resource-provider.cache lane=intermediate-texture result=create|reuse|refuse ...`

- [ ] **Step 1: Write provider tests**

Add:

```kotlin
package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePurpose
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor

class GPUIntermediateResourceProviderTest {
    @Test
    fun `intermediate texture create then reuse uses descriptor generation lifetime and owner`() {
        val provider = GPUConcreteResourceProvider()
        val context = context()
        val request = GPUIntermediateTextureMaterializationRequest(
            targetId = "target:main",
            descriptor = descriptor(),
            deviceGeneration = 5,
            actualResourceGeneration = 5,
            requiredUsageLabels = setOf("render_attachment", "texture_binding"),
            activeAttachmentSampled = false,
        )

        val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(request, context),
        )
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(request, context),
        )

        assertEquals(GPUResourceLeaseCacheResult.Create, first.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(GPUResourceLeaseCacheResult.Reuse, second.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(listOf("create", "reuse"), provider.telemetry.dumpEvents.filter { it.lane == "intermediate-texture" }.map { it.result })
    }

    @Test
    fun `intermediate texture refuses stale generation`() {
        val provider = GPUConcreteResourceProvider()
        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeIntermediateTexture(
                GPUIntermediateTextureMaterializationRequest(
                    targetId = "target:main",
                    descriptor = descriptor(generation = 4),
                    deviceGeneration = 5,
                    actualResourceGeneration = 4,
                    requiredUsageLabels = setOf("render_attachment", "texture_binding"),
                    activeAttachmentSampled = false,
                ),
                context(),
            ),
        )

        assertEquals("unsupported.intermediate.generation_stale", refused.diagnostic.code)
    }

    @Test
    fun `intermediate texture refuses active attachment sampling`() {
        val provider = GPUConcreteResourceProvider()
        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeIntermediateTexture(
                GPUIntermediateTextureMaterializationRequest(
                    targetId = "target:main",
                    descriptor = descriptor(),
                    deviceGeneration = 5,
                    actualResourceGeneration = 5,
                    requiredUsageLabels = setOf("render_attachment", "texture_binding"),
                    activeAttachmentSampled = true,
                ),
                context(),
            ),
        )

        assertEquals("unsupported.destination_read.active_attachment_sampled", refused.diagnostic.code)
    }

    private fun context(): GPUTargetPreparationContext =
        GPUTargetPreparationContext(
            targetId = "target:main",
            frameId = "frame:1",
            deviceGeneration = 5,
            budgetClass = "test",
        )

    private fun descriptor(generation: Long = 5): GPUIntermediateTextureDescriptor =
        GPUIntermediateTextureDescriptor(
            label = "intermediate:layer-a",
            purpose = GPUIntermediatePurpose.LayerTarget,
            descriptorHash = "sha256:layer-a",
            sourceTargetLabel = "surface:main",
            boundsLabel = "bounds:layer-a",
            width = 100,
            height = 80,
            formatClass = "rgba8unorm",
            usageLabels = listOf("render_attachment", "texture_binding"),
            sampleCount = 1,
            generation = generation,
            lifetimeClass = "layer-local",
            ownerScope = "scope:layer-a",
            byteEstimate = 32000,
        )
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest"
```

Expected: compilation fails because `GPUIntermediateTextureMaterializationRequest` and provider method do not exist.

- [ ] **Step 3: Add request and key types**

Create `IntermediateResourceProvider.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.resources

import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor

data class GPUIntermediateTextureMaterializationRequest(
    val targetId: String,
    val descriptor: GPUIntermediateTextureDescriptor,
    val deviceGeneration: Long,
    val actualResourceGeneration: Long,
    val requiredUsageLabels: Set<String>,
    val activeAttachmentSampled: Boolean,
) {
    init {
        require(targetId.isNotBlank()) { "GPUIntermediateTextureMaterializationRequest.targetId must not be blank" }
        require(deviceGeneration >= 0L) { "GPUIntermediateTextureMaterializationRequest.deviceGeneration must be non-negative" }
        require(actualResourceGeneration >= 0L) { "GPUIntermediateTextureMaterializationRequest.actualResourceGeneration must be non-negative" }
        require(requiredUsageLabels.none { it.isBlank() }) {
            "GPUIntermediateTextureMaterializationRequest.requiredUsageLabels must not contain blanks"
        }
    }
}

internal data class GPUIntermediateTextureLeaseCacheKey(
    val targetId: String,
    val descriptorHash: String,
    val boundsLabel: String,
    val formatClass: String,
    val usageLabels: List<String>,
    val sampleCount: Int,
    val generation: Long,
    val lifetimeClass: String,
    val ownerScope: String,
) {
    fun dumpToken(): String =
        "target=$targetId;descriptor=$descriptorHash;bounds=$boundsLabel;format=$formatClass;" +
            "usage=${usageLabels.joinToString("+")};sampleCount=$sampleCount;generation=$generation;" +
            "lifetime=$lifetimeClass;owner=$ownerScope"

    companion object {
        fun from(request: GPUIntermediateTextureMaterializationRequest): GPUIntermediateTextureLeaseCacheKey =
            GPUIntermediateTextureLeaseCacheKey(
                targetId = request.targetId,
                descriptorHash = request.descriptor.descriptorHash,
                boundsLabel = request.descriptor.boundsLabel,
                formatClass = request.descriptor.formatClass,
                usageLabels = request.descriptor.usageLabels.sorted(),
                sampleCount = request.descriptor.sampleCount,
                generation = request.actualResourceGeneration,
                lifetimeClass = request.descriptor.lifetimeClass,
                ownerScope = request.descriptor.ownerScope,
            )
    }
}
```

- [ ] **Step 4: Extend `GPUConcreteResourceProvider`**

In `GPUConcreteResourceProvider`, add a cache field:

```kotlin
    private val intermediateTextureLeases = linkedMapOf<GPUIntermediateTextureLeaseCacheKey, GPUResourceLease>()
```

Add this public method:

```kotlin
    fun materializeIntermediateTexture(
        request: GPUIntermediateTextureMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val diagnostic = when {
            request.targetId != context.targetId ->
                GPUResourceDiagnostic.resourceTargetMismatch(
                    resourceLabel = request.descriptor.label,
                    requestTargetId = request.targetId,
                    contextTargetId = context.targetId,
                )
            request.deviceGeneration != context.deviceGeneration ->
                GPUResourceDiagnostic.deviceGenerationStale(
                    resourceLabel = request.descriptor.label,
                    expectedDeviceGeneration = context.deviceGeneration,
                    actualDeviceGeneration = request.deviceGeneration,
                    resourceKind = "intermediate",
                )
            request.actualResourceGeneration != request.descriptor.generation ->
                GPUResourceDiagnostic(
                    code = "unsupported.intermediate.generation_stale",
                    resourceLabel = request.descriptor.label,
                    message = "intermediate generation ${request.actualResourceGeneration} != descriptor generation ${request.descriptor.generation}",
                    terminal = true,
                )
            request.activeAttachmentSampled ->
                GPUResourceDiagnostic(
                    code = "unsupported.destination_read.active_attachment_sampled",
                    resourceLabel = request.descriptor.label,
                    message = "intermediate texture would sample the active attachment",
                    terminal = true,
                )
            (request.requiredUsageLabels - request.descriptor.usageLabels.toSet()).isNotEmpty() ->
                GPUResourceDiagnostic.textureUsageMissing(
                    resourceLabel = request.descriptor.label,
                    missingUsageLabels = request.requiredUsageLabels - request.descriptor.usageLabels.toSet(),
                    availableUsageLabels = request.descriptor.usageLabels.toSet(),
                )
            else -> null
        }
        if (diagnostic != null) {
            record("intermediate-texture", "refuse", request.descriptor.descriptorHash, request.descriptor.label)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.descriptor.label),
            )
        }

        val key = GPUIntermediateTextureLeaseCacheKey.from(request)
        intermediateTextureLeases[key]?.let { cached ->
            val lease = cached.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            record("intermediate-texture", lease.cacheResult.dumpToken, key.dumpToken(), request.descriptor.label)
            return GPUResourceMaterializationDecision.Materialized(
                resources = listOf(GPUTextureResourceRef("texture-ref:${request.descriptor.label}")),
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.descriptor.label),
                resourceLeases = listOf(lease),
            )
        }

        val lease = GPUResourceLease(
            leaseId = "intermediate:${request.descriptor.label}",
            resourceKind = GPUResourceLeaseKind.Texture,
            deviceGeneration = context.deviceGeneration,
            descriptorHash = request.descriptor.descriptorHash,
            ownerScope = request.descriptor.ownerScope,
            usageLabels = request.descriptor.usageLabels,
            releasePolicy = request.descriptor.lifetimeClass,
            cacheResult = GPUResourceLeaseCacheResult.Create,
            evidenceFacts = mapOf(
                "purpose" to request.descriptor.purpose.name,
                "bounds" to request.descriptor.boundsLabel,
                "sampleCount" to request.descriptor.sampleCount.toString(),
            ),
        )
        intermediateTextureLeases[key] = lease
        record("intermediate-texture", lease.cacheResult.dumpToken, key.dumpToken(), request.descriptor.label)
        return GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("texture-ref:${request.descriptor.label}")),
            targetId = context.targetId,
            resourcePlanLabels = listOf(request.descriptor.label),
            resourceLeases = listOf(lease),
        )
    }
```

`GPUResourceDiagnostic` is a public data class in the current tree; use the direct constructor above for the two phase-specific reason codes so the emitted codes match the phase 5 spec exactly.

- [ ] **Step 5: Run provider tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest" --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest"
```

Expected: PASS, and existing provider telemetry tests stay green.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/IntermediateResourceProvider.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUIntermediateResourceProviderTest.kt
rtk git commit -m "Add reusable intermediate texture provider"
```

---

### Task 5: Activate Shader Blend with Valid Destination-Read

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/state/BlendAllowlistGateTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlannerTest.kt`

**Interfaces:**
- Consumes: accepted `GPUDestinationReadStrategyGatePlan`.
- Produces: `GPUBlendAllowlistGatePlan` with `planKind=ShaderBlendWithDstRead`, `routeKind=GPUNative`, `productActivation=true`, `fixedFunctionState=null`, and no terminal diagnostic when destination-read is valid.

- [ ] **Step 1: Write/adjust blend test for active shader route**

Add this test to `BlendAllowlistGateTest`:

```kotlin
@Test
fun `screen blend accepts shader route when destination read plan is valid`() {
    val destination = org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyPlanner().plan(
        org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyRequest(
            commandId = "cmd-screen",
            requirement = org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadRequirement.TargetCopy,
            strategy = org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy.CopyTarget,
            action = org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadAction.SplitPassAndCopyTarget,
            bounds = org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds(
                boundsLabel = "bounds:screen",
                conservative = true,
                pixelAligned = true,
                copyBoundsLabel = "copy:screen",
                width = 32,
                height = 32,
                targetWidth = 320,
                targetHeight = 200,
            ),
            sourceTargetLabel = "surface:main",
            sourceUsageLabels = setOf("render_attachment", "copy_src"),
            copyUsageLabels = setOf("copy_dst", "texture_binding"),
            targetFormatClass = "rgba8unorm",
            targetGeneration = 7,
        ),
    )

    val plan = GPUBlendAllowlistPlanner().plan(
        GPUBlendAllowlistRequest(
            commandId = "cmd-screen",
            mode = GPUBlendMode.Screen,
            targetFormatClass = "rgba8unorm",
            materialKeyHash = "material:screen",
            renderStepIdentity = "rect-fill",
            destinationReadPlan = destination,
            destinationReadCopyBoundsLabel = "copy:screen",
            destinationReadGeneration = 7,
        ),
    )

    assertEquals(GPUBlendPlanKind.ShaderBlendWithDstRead, plan.planKind)
    assertEquals("GPUNative", plan.routeKind)
    assertEquals(false, plan.diagnostics.any { it.terminal })
    assertEquals(org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy.CopyTarget, plan.destinationReadStrategy)
    assertEquals(org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadAction.SplitPassAndCopyTarget, plan.destinationReadAction)
    assertTrue(plan.dumpLines().any { it.contains("shaderBlend=true") })
}
```

- [ ] **Step 2: Run the blend test and confirm it fails**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.state.BlendAllowlistGateTest.screen blend accepts shader route when destination read plan is valid"
```

Expected: FAIL with `unsupported.blend.shader_route_unvalidated`.

- [ ] **Step 3: Implement accepted shader-blend path**

In `GPUBlendAllowlistPlanner.plan`, replace the final shader refusal branch with an accepted branch before `refusedPlan`:

```kotlin
        if (planKind == GPUBlendPlanKind.ShaderBlendWithDstRead && reasonCode == "unsupported.blend.shader_route_unvalidated") {
            return acceptedShaderBlendPlan(request)
        }
```

Add the helper:

```kotlin
    private fun acceptedShaderBlendPlan(request: GPUBlendAllowlistRequest): GPUBlendAllowlistGatePlan {
        val destinationReadPlan = requireNotNull(request.destinationReadPlan) {
            "accepted shader blend requires destinationReadPlan"
        }
        val blendStateHash = "shader-blend:${request.mode}:${request.targetFormatClass}:${destinationReadPlan.copyDescriptorHash}"
        val plan = GPUBlendPlan(
            mode = request.mode,
            requiresDestinationRead = true,
            pipelineBlendStateKey = blendStateHash,
        )
        val diagnostic = GPUBlendDiagnostic(
            code = "accepted.blend.shader_destination_read",
            mode = request.mode,
            message = "blend allowlist accepted shader destination-read mode ${request.mode}",
            terminal = false,
        )
        return GPUBlendAllowlistGatePlan(
            commandId = request.commandId,
            evidenceRow = BLEND_ALLOWLIST_EVIDENCE_ROW,
            routeKind = "GPUNative",
            classification = "TargetNative",
            promoted = false,
            productActivation = true,
            materialized = false,
            targetFormatClass = request.targetFormatClass,
            materialKeyHash = request.materialKeyHash,
            renderStepIdentity = request.renderStepIdentity,
            alphaPlan = request.alphaPlan,
            planKind = GPUBlendPlanKind.ShaderBlendWithDstRead,
            plan = plan,
            fixedFunctionState = null,
            destinationReadRequirement = request.mode.destinationReadRequirement(),
            destinationReadStrategy = destinationReadPlan.plan.strategy,
            destinationReadAction = destinationReadPlan.action,
            citedDestinationReadPlanRef = destinationReadPlan.planRef(),
            citedDestinationReadPlanStrategy = destinationReadPlan.plan.strategy,
            activeAttachmentSampled = request.activeAttachmentSampled,
            pipelineKeyHash = pipelineKeyHash(request, blendStateHash),
            diagnostics = listOf(diagnostic),
        )
    }
```

Update `GPUBlendAllowlistGatePlan.dumpLines()` so accepted shader-blend does not require `fixedFunctionState`:

```kotlin
        val stateLine = fixedFunctionState?.dumpLine()
            ?: "blend:shader mode=${plan.mode} destinationRead=TextureCopy route=shaderBlend"
        return listOf(
            "blend:allowlist row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "command=$commandId mode=${plan.mode} plan=$planKind target=$targetFormatClass " +
                "state=$blendStateHash pipeline=$pipelineBlendStateKey",
            alphaPlan.dumpLine(),
            stateLine,
            destinationReadLine(),
            "blend:pipeline-key material=$materialKeyHash renderStep=$renderStepIdentity " +
                "blendState=$blendStateHash pipelineKey=$pipelineKeyHash",
            "blend:diagnostic code=${diagnostic.code} terminal=${diagnostic.terminal} mode=${diagnostic.mode}",
            if (planKind == GPUBlendPlanKind.ShaderBlendWithDstRead) {
                "blend:nonclaim nativeAdvancedBlend=false shaderBlend=true framebufferFetch=false inputAttachment=false destinationReadTexture=true productActivation=true"
            } else {
                BLEND_ALLOWLIST_NONCLAIM_LINE
            },
        )
```

Keep fixed-function dump output byte-for-byte compatible unless tests explicitly change it.

- [ ] **Step 4: Run blend and planner tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.state.BlendAllowlistGateTest" --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerTest"
```

Expected: PASS. `GPUIntermediatePlannerTest.screen blend creates destination copy...` still passes, and blend dump now advertises `shaderBlend=true` only for validated destination-read routes.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/state/BlendAllowlistGateTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlannerTest.kt
rtk git commit -m "Activate shader blends with destination read"
```

---

### Task 6: Scene Adapter and SaveLayer Execution

**Files:**
- Create: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapter.kt`
- Create: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanExecutor.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RectOnlyOffscreenRenderer.kt`
- Create: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapterTest.kt`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/M25ExecutorWiringTest.kt`

**Interfaces:**
- Consumes: existing `RectOnlyDrawPlan`, `RectOnlyFillDraw`, `GPUBackendOffscreenTarget`, `GPUIntermediatePlanner`.
- Produces:
  - `class SceneIntermediatePlanAdapter`
  - `class SceneIntermediatePlanExecutor`
  - `fun RectOnlyOffscreenRenderer.renderToPixels(...)` delegates saveLayer intermediate work to executor.

- [ ] **Step 1: Write adapter tests**

Add:

```kotlin
package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanStep

class SceneIntermediatePlanAdapterTest {
    @Test
    fun `saveLayer fill becomes layer target children and composite steps`() {
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = "savelayer-isolated",
            commands = org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
                .scenes()
                .single { it.sceneId.value == "savelayer-isolated" }
                .commands,
            width = 320,
            height = 200,
        )

        val plan = SceneIntermediatePlanAdapter().plan(
            sceneId = "savelayer-isolated",
            drawPlan = drawPlan,
            width = 320,
            height = 200,
        )

        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.CreateIntermediate })
        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.RenderLayerChildren })
        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.CompositeIntermediate })
        assertEquals(1, plan.telemetry.layerTargets)
        assertEquals(1, plan.telemetry.layerComposites)
    }
}
```

- [ ] **Step 2: Run adapter test and confirm it fails**

```bash
rtk ./gradlew :gpu-renderer-scenes:test --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.SceneIntermediatePlanAdapterTest"
```

Expected: compilation fails because adapter does not exist.

- [ ] **Step 3: Implement scene adapter**

Create `SceneIntermediatePlanAdapter.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateDrawRequest
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlan
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanner
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerRequest
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerSaveRecord
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerScopeID
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

class SceneIntermediatePlanAdapter(
    private val planner: GPUIntermediatePlanner = GPUIntermediatePlanner(),
) {
    fun plan(
        sceneId: String,
        drawPlan: RectOnlyDrawPlan,
        width: Int,
        height: Int,
    ): GPUIntermediatePlan {
        val saveLayerFills = drawPlan.fills.filter { it.family == "save-layer" }
        val requests = if (saveLayerFills.isEmpty()) {
            listOf(
                GPUIntermediateDrawRequest(
                    commandId = "scene:$sceneId:direct",
                    targetLabel = "surface:$sceneId",
                    targetGeneration = 1,
                    bounds = sceneBounds("scene:$sceneId:direct", width, height),
                    blendMode = GPUBlendMode.SrcOver,
                    materialKeyHash = "material:scene-direct",
                    renderStepIdentity = "scene-direct",
                ),
            )
        } else {
            saveLayerFills.mapIndexed { index, fill ->
                val nextPaintOrder = saveLayerFills.getOrNull(index + 1)?.paintOrder ?: Int.MAX_VALUE
                val childIds = drawPlan.fills
                    .filter { it.paintOrder > fill.paintOrder && it.paintOrder < nextPaintOrder && it.family != "save-layer" }
                    .map { it.label }
                GPUIntermediateDrawRequest(
                    commandId = fill.label,
                    targetLabel = "surface:$sceneId",
                    targetGeneration = 1,
                    bounds = sceneBounds(fill.label, width, height),
                    blendMode = GPUBlendMode.SrcOver,
                    materialKeyHash = "material:savelayer",
                    renderStepIdentity = "scene-savelayer",
                    saveLayer = GPULayerSaveRecord(
                        scopeId = GPULayerScopeID("layer:${fill.label}"),
                        boundsLabel = "bounds:${fill.label}",
                        paintLabel = fill.label,
                        backdropRequired = false,
                        childCommandIds = childIds,
                        restoreBlendMode = "srcOver",
                    ),
                )
            }
        }
        return planner.plan(
            GPUIntermediatePlannerRequest(
                planId = "scene-intermediate:$sceneId",
                targetId = "target:$sceneId",
                targetFormatClass = OFFSCREEN_COLOR_FORMAT,
                targetUsageLabels = setOf("render_attachment", "copy_src", "copy_dst", "texture_binding"),
                deviceGeneration = 1,
                drawRequests = requests,
            ),
        )
    }

    private fun sceneBounds(label: String, width: Int, height: Int): GPUDestinationReadBounds =
        GPUDestinationReadBounds(
            boundsLabel = "bounds:$label",
            conservative = true,
            pixelAligned = true,
            requestedBoundsLabel = "requested:$label",
            unclippedBoundsLabel = "unclipped:$label",
            clippedBoundsLabel = "device:$width:x:$height",
            copyBoundsLabel = "copy:$label",
            originX = 0,
            originY = 0,
            width = width,
            height = height,
            targetWidth = width,
            targetHeight = height,
        )
}
```

- [ ] **Step 4: Implement scene executor**

Create `SceneIntermediatePlanExecutor.kt` and move the current saveLayer texture creation/composite logic into it. Keep draw-family rendering helpers in `RectOnlyOffscreenRenderer` until a broader scene renderer refactor exists.

```kotlin
package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlan
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanStep

data class SceneIntermediateExecutionResult(
    val childLabels: Set<String>,
    val layerTextureByFillLabel: Map<String, String>,
    val diagnostics: List<String>,
)

class SceneIntermediatePlanExecutor {
    fun executeSaveLayerPreparation(
        target: GPUBackendOffscreenTarget,
        drawPlan: RectOnlyDrawPlan,
        plan: GPUIntermediatePlan,
        renderSolidFills: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder.(List<RectOnlyFillDraw>) -> Unit,
    ): SceneIntermediateExecutionResult {
        val childLabels = mutableSetOf<String>()
        val textureByFill = linkedMapOf<String, String>()
        val diagnostics = mutableListOf<String>()

        plan.steps.filterIsInstance<GPUIntermediatePlanStep.RenderLayerChildren>().forEach { layerStep ->
            val fillLabel = layerStep.scopeLabel.removePrefix("layer:")
            val fill = drawPlan.fills.firstOrNull { it.label == fillLabel } ?: return@forEach
            val children = layerStep.childrenLabel
                .split(',')
                .filter { it.isNotBlank() && it != "none" }
                .mapNotNull { label -> drawPlan.fills.firstOrNull { it.label == label } }
            childLabels += children.map { it.label }
            val textureLabel = target.createOffscreenTexture(
                GPUBackendOffscreenTexture(
                    width = target.target.descriptor.width,
                    height = target.target.descriptor.height,
                    format = OFFSCREEN_COLOR_FORMAT,
                ),
            )
            target.encodeOffscreenTexture(textureLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                renderSolidFills(listOf(fill) + children)
            }
            textureByFill[fill.label] = textureLabel
            diagnostics += "intermediate.scene.layer-prepared scope=${layerStep.scopeLabel} target=${layerStep.target.label} texture=$textureLabel children=${layerStep.childrenLabel}"
        }

        return SceneIntermediateExecutionResult(
            childLabels = childLabels,
            layerTextureByFillLabel = textureByFill,
            diagnostics = diagnostics + plan.dumpLines(),
        )
    }

    fun org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder.compositeSaveLayers(
        drawPlan: RectOnlyDrawPlan,
        execution: SceneIntermediateExecutionResult,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        drawPlan.fills.filter { it.family == "save-layer" }.forEach { fill ->
            val textureLabel = execution.layerTextureByFillLabel[fill.label] ?: return@forEach
            drawCompositePass(
                wgsl = composeSaveLayerCompositeWgsl(),
                colorFormat = OFFSCREEN_COLOR_FORMAT,
                textureLabel = textureLabel,
                draws = listOf(
                    GPUBackendRawUniformDraw(
                        uniformBytes = UniformPacker.layerCompositeBytes(
                            org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor(0f, 0f, 0f, 0f),
                            fill.groupAlpha,
                        ),
                        scissorX = 0,
                        scissorY = 0,
                        scissorWidth = viewportWidth,
                        scissorHeight = viewportHeight,
                    ),
                ),
            )
        }
    }

    companion object {
        fun solidRectDraws(fills: List<RectOnlyFillDraw>): List<GPUBackendRectDraw> =
            fills.map { fill ->
                GPUBackendRectDraw(
                    rgbaPremul = floatArrayOf(
                        fill.startColor.r * fill.startColor.a,
                        fill.startColor.g * fill.startColor.a,
                        fill.startColor.b * fill.startColor.a,
                        fill.startColor.a,
                    ),
                    scissorX = fill.scissorX,
                    scissorY = fill.scissorY,
                    scissorWidth = fill.scissorWidth,
                    scissorHeight = fill.scissorHeight,
                )
            }
    }
}
```

`RectOnlyDrawPlan` and `RectOnlyFillDraw` are already `internal` in the offscreen package, so the adapter and executor can use them without widening visibility.

- [ ] **Step 5: Wire `RectOnlyOffscreenRenderer` to adapter/executor**

In `renderToPixels`, replace local `saveLayerReroutes` texture planning with:

```kotlin
        val intermediatePlan = SceneIntermediatePlanAdapter().plan(
            sceneId = drawPlan.sceneId,
            drawPlan = drawPlan,
            width = viewportWidth,
            height = viewportHeight,
        )
        val intermediateExecutor = SceneIntermediatePlanExecutor()
        val intermediateExecution = intermediateExecutor.executeSaveLayerPreparation(
            target = target,
            drawPlan = drawPlan,
            plan = intermediatePlan,
        ) { fills ->
            val solidDraws = SceneIntermediatePlanExecutor.solidRectDraws(fills)
            if (solidDraws.isNotEmpty()) {
                drawFullscreenPass(
                    wgsl = SOLID_RECT_WGSL,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = solidDraws,
                )
            }
        }
```

Replace `saveLayerChildLabels` references with `intermediateExecution.childLabels`.

Replace final saveLayer composite block with:

```kotlin
            intermediateExecutor.run {
                compositeSaveLayers(
                    drawPlan = drawPlan,
                    execution = intermediateExecution,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                )
            }
```

Append `intermediateExecution.diagnostics` to the offscreen report diagnostics.

- [ ] **Step 6: Run scene unit tests**

```bash
rtk ./gradlew :gpu-renderer-scenes:test --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.SceneIntermediatePlanAdapterTest" --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.M25ExecutorWiringTest" --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest"
```

Expected: PASS. SaveLayer wiring still reports real secondary target allocation, and diagnostics include `intermediate.plan`.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapter.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanExecutor.kt gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RectOnlyOffscreenRenderer.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/SceneIntermediatePlanAdapterTest.kt gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/M25ExecutorWiringTest.kt
rtk git commit -m "Route offscreen saveLayer through intermediate plans"
```

---

### Task 7: MSAA Plan Integration

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/GPUPipelineKeyDerivationTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateMsaaPlanTest.kt`

**Interfaces:**
- Consumes: `GPUMsaa.resolve`, existing `sampleStateHash` in `GPUPipelineKeyPreimage.Render`.
- Produces:
  - `requestedSampleCount` field in `GPUIntermediatePlannerRequest`.
  - `ResolveMSAA` steps when sample count is greater than 1.
  - Runtime telemetry counters for `msaaTargets` and `msaaResolves`.

- [ ] **Step 1: Write MSAA planner tests**

Add:

```kotlin
package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaAdapterCapability
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

class GPUIntermediateMsaaPlanTest {
    @Test
    fun `sample count four emits msaa target and resolve steps`() {
        val plan = GPUIntermediatePlanner().plan(
            GPUIntermediatePlannerRequest(
                planId = "plan:msaa",
                targetId = "target:main",
                targetFormatClass = "rgba8unorm",
                targetUsageLabels = setOf("render_attachment", "texture_binding", "copy_src", "copy_dst"),
                deviceGeneration = 1,
                requestedSampleCount = 4,
                msaaAdapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:test",
                    maxSampleCount = 4,
                    supportsAlphaToCoverage = false,
                ),
                drawRequests = listOf(
                    GPUIntermediateDrawRequest(
                        commandId = "cmd-aa",
                        targetLabel = "surface:main",
                        targetGeneration = 1,
                        bounds = GPUDestinationReadBounds(
                            boundsLabel = "bounds:aa",
                            conservative = true,
                            pixelAligned = true,
                            width = 64,
                            height = 64,
                            targetWidth = 320,
                            targetHeight = 200,
                        ),
                        blendMode = GPUBlendMode.SrcOver,
                        materialKeyHash = "material:solid",
                        renderStepIdentity = "rect-fill",
                    ),
                ),
            ),
        )

        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.ResolveMSAA })
        assertEquals(1, plan.telemetry.msaaTargets)
        assertEquals(1, plan.telemetry.msaaResolves)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest"
```

Expected: compilation fails because `requestedSampleCount` and `msaaAdapter` are not request fields.

- [ ] **Step 3: Extend planner request and MSAA logic**

Add fields to `GPUIntermediatePlannerRequest`:

```kotlin
    val requestedSampleCount: Int = 1,
    val msaaAdapter: org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaAdapterCapability? = null,
```

In the `init` block:

```kotlin
        require(requestedSampleCount > 0) { "GPUIntermediatePlannerRequest.requestedSampleCount must be positive" }
```

At the start of `plan`, before draw loop, add:

```kotlin
        val msaaRoute = if (request.requestedSampleCount > 1) {
            org.graphiks.kanvas.gpu.renderer.passes.GPUMsaa.resolve(
                org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaRequest(
                    requestedSampleCount = request.requestedSampleCount,
                    coverageMode = org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaCoverageMode.Standard,
                    adapter = request.msaaAdapter,
                ),
            )
        } else null
        if (msaaRoute is org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaRoute.Refused) {
            return request.refused(request.targetId, msaaRoute.diagnostic.code)
        }
```

After normal draw steps are collected, before returning, append MSAA descriptors when `requestedSampleCount > 1`:

```kotlin
        val finalSteps = if (request.requestedSampleCount > 1) {
            val msaa = GPUIntermediateTextureDescriptor(
                label = "intermediate:msaa:${request.targetId}",
                purpose = GPUIntermediatePurpose.LayerTarget,
                descriptorHash = "msaa:${request.targetFormatClass}:${request.requestedSampleCount}:${request.targetId}",
                sourceTargetLabel = request.targetId,
                boundsLabel = "bounds:${request.targetId}",
                width = request.drawRequests.first().bounds.targetWidth,
                height = request.drawRequests.first().bounds.targetHeight,
                formatClass = request.targetFormatClass,
                usageLabels = listOf("render_attachment"),
                sampleCount = request.requestedSampleCount,
                generation = request.deviceGeneration,
                lifetimeClass = "pass-local",
                ownerScope = request.targetId,
                byteEstimate = request.drawRequests.first().bounds.targetWidth.toLong() *
                    request.drawRequests.first().bounds.targetHeight.toLong() * 4L *
                    request.requestedSampleCount.toLong(),
            )
            val resolved = msaa.copy(
                label = "intermediate:msaa-resolved:${request.targetId}",
                purpose = GPUIntermediatePurpose.MsaaResolve,
                descriptorHash = "msaa-resolved:${request.targetFormatClass}:1:${request.targetId}",
                usageLabels = listOf("texture_binding", "copy_src"),
                sampleCount = 1,
                byteEstimate = request.drawRequests.first().bounds.targetWidth.toLong() *
                    request.drawRequests.first().bounds.targetHeight.toLong() * 4L,
            )
            listOf(GPUIntermediatePlanStep.CreateIntermediate(msaa)) + steps + listOf(
                GPUIntermediatePlanStep.ResolveMSAA(
                    source = msaa,
                    destination = resolved,
                    strategyLabel = "WGPU_BUILTIN",
                    tokenLabel = "msaa-token:${request.targetId}",
                ),
            )
        } else {
            steps
        }
```

Return `finalSteps` and update telemetry:

```kotlin
            steps = finalSteps,
            telemetry = if (request.requestedSampleCount > 1) {
                telemetry.copy(msaaTargets = telemetry.msaaTargets + 1, msaaResolves = telemetry.msaaResolves + 1)
            } else {
                telemetry
            },
```

- [ ] **Step 4: Extend runtime telemetry**

In `GPUBackendRuntimeTelemetry`, add:

```kotlin
    val intermediateTexturesCreated: Long = 0L,
    val destinationCopies: Long = 0L,
    val msaaTargets: Long = 0L,
    val msaaResolves: Long = 0L,
```

Add constructor guards and append these fields to `dumpLines()`.

In `GPUBackendRuntimeNative.kt`, extend `WgpuBackendRuntimeTelemetryRecorder` with:

```kotlin
    @Synchronized
    fun recordIntermediateTextureCreated() {
        intermediateTexturesCreated += 1L
    }

    @Synchronized
    fun recordDestinationCopy() {
        destinationCopies += 1L
    }

    @Synchronized
    fun recordMsaaTarget() {
        msaaTargets += 1L
    }

    @Synchronized
    fun recordMsaaResolve() {
        msaaResolves += 1L
    }
```

Add matching mutable fields beside `texturesCreated`, pass them through `snapshot()`, and update these call sites:

- `WgpuOffscreenTarget.createOffscreenTexture`: after a new texture is inserted into `offscreenTextures`, call `telemetryRecorder.recordIntermediateTextureCreated()`.
- `WgpuOffscreenTarget.encodeOffscreenTextureInternal`: when the encoded texture is a multisample target after Task 7 wiring, call `recordMsaaTarget()` exactly once per accepted target encode.
- `WgpuRenderRecorder.drawBlendPass`: when it consumes a distinct destination-copy texture label, call `recordDestinationCopy()` once per pass.
- The native MSAA resolve call introduced in this task: call `recordMsaaResolve()` only after the backend accepts the resolve command. If the backend cannot encode a real resolve, the planner must refuse via `GPUMsaa.resolve` and this counter stays unchanged.

- [ ] **Step 5: Run MSAA and pipeline tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateMsaaPlanTest" --tests "org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaTest" --tests "org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyDerivationTest" --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest"
```

Expected: PASS. Pipeline key tests still show `sampleStateHash` as a key-changing axis and no concrete resource fields.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediatePlanner.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/intermediates/GPUIntermediateMsaaPlanTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUMsaaTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/GPUPipelineKeyDerivationTest.kt
rtk git commit -m "Integrate MSAA into intermediate planning"
```

---

### Task 8: Validation Scenes, Reports, and Anti-Crash Regeneration

**Files:**
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/catalog/GPURendererSceneRegistry.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/catalog/SceneHumanDocumentation.kt`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenScenePngParityTest.kt`
- Modify or create: `reports/gpu-renderer/2026-07-08-gpu-intermediate-planner.md`
- Regenerate: `reports/gpu-renderer-scenes/offscreen/**/render.png`
- Regenerate when available: `integration-tests/skia/src/test/resources/generated-renders/**`, `integration-tests/skia/src/test/resources/test-similarity-scores.properties`, dashboard HTML.

**Interfaces:**
- Consumes: offscreen render Gradle tasks and Skia GM dashboard tasks.
- Produces: crash/exception status, visual-delta classification, intermediate telemetry counters, updated report artifacts.

- [ ] **Step 1: Add validation scene tests**

Add focused assertions in existing scene tests:

```kotlin
@Test
fun `phase five validation scenes expose intermediate diagnostics`() {
    val root = java.nio.file.Files.createTempDirectory("gpu-intermediate-scenes")
    val scenes = listOf("savelayer-isolated", "savelayer-group-alpha", "dst-read-strategy")

    scenes.forEach { sceneId ->
        val report = RectOnlyOffscreenRenderer().render(
            scene = org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
                .scenes()
                .single { it.sceneId.value == sceneId },
            outputDir = root,
        )
        assertTrue(report.diagnostics.any { it.startsWith("intermediate.plan") }, report.diagnostics.joinToString("\n"))
        assertTrue(report.diagnostics.none { it.contains("CrashOrException") }, report.diagnostics.joinToString("\n"))
    }
}
```

If in-process WebGPU is unavailable in the test JVM, put this assertion in `RenderGpuRendererSceneOffscreenMainTest` using the existing subprocess runner style.

- [ ] **Step 2: Run targeted unit/smoke tests**

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.intermediates.*" --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceProviderTest" --tests "org.graphiks.kanvas.gpu.renderer.state.BlendAllowlistGateTest"
rtk ./gradlew :gpu-renderer-scenes:test --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.*"
```

Expected: PASS or explicit WebGPU-unavailable skip/failure report already supported by the test harness. No undocumented exception is acceptable.

- [ ] **Step 3: Regenerate offscreen scene artifacts**

Run at least these scenes:

```bash
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=savelayer-isolated
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=savelayer-group-alpha
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=dst-read-strategy
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=destination-read-strategy-gate-board
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=savelayer-isolation-gate-board
```

Expected: each command exits 0 or writes an explicit `webgpu-context-unavailable` report without crashing. When output PNGs change, keep them and classify the visual delta in the report.

- [ ] **Step 4: Regenerate Skia GM dashboard artifacts when the local suite is available**

Run:

```bash
rtk ./gradlew :integration-tests:skia:regenerateSkiaGmRenders
rtk ./gradlew :integration-tests:skia:generateSkiaDashboard
```

Expected: either successful regeneration or a documented dependency-gated failure. Do not require Kadre native demos or unpublished artifacts for checked-in validators.

- [ ] **Step 5: Write final phase report**

Create `reports/gpu-renderer/2026-07-08-gpu-intermediate-planner.md` after Steps 2-4 finish. The report must contain:

- A summary section with these exact facts: default activation, product flags required, crash/exception status, and visual-delta policy.
- A tests table with one row for each verification command actually run in this task. Each row must include command text, exit code, result classification, and the evidence file or log location when Gradle produced one.
- A runtime artifacts table with one row for each rendered scene. Each row must include scene id, command exit code, output path, crash/exception classification, visual-delta classification, and the diagnostic line that proves `intermediate.plan` was emitted.
- A telemetry section quoting only short diagnostic lines emitted by this repo: `intermediate.telemetry`, `gpu-runtime.telemetry`, and `resource-provider.cache lane=intermediate-texture`.
- A known limitations section preserving these statements when still true: visual correctness is not globally complete in this phase; remaining unsupported routes keep stable reason codes; no CPU-rendered texture product fallback was added.

- [ ] **Step 6: Run final verification commands**

```bash
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test
rtk git diff --check
rtk git status --short
```

Expected: Gradle tests pass or report documented environment-gated WebGPU unavailability; `git diff --check` has no output; `git status --short` lists only intentional source/test/report/artifact changes.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer gpu-renderer-scenes reports/gpu-renderer reports/gpu-renderer-scenes integration-tests/skia/src/test/resources/generated-renders integration-tests/skia/src/test/resources/test-similarity-scores.properties
rtk git commit -m "Activate GPU intermediate planner phase"
```

---

## Final Acceptance Checklist

- [ ] `GPUIntermediatePlanner` is active by default for phase 5 routes.
- [ ] Destination-read plans produce explicit copy/bind/render steps.
- [ ] `Screen` and `Multiply` shader blends activate when destination-read evidence is valid.
- [ ] saveLayer offscreen targets and composites flow through intermediate plan execution.
- [ ] Intermediate resource provider creates, reuses, and refuses with stable evidence.
- [ ] MSAA sample count is represented in intermediate descriptors and pipeline sample state.
- [ ] No active attachment sampling is accepted.
- [ ] No CPU readback product fallback is introduced.
- [ ] No backend handles, addresses, texture contents, or uniform values enter durable keys or dumps.
- [ ] No runtime classes are named `Phase5`.
- [ ] `RectOnly` remains a transition adapter, not the core architecture.
- [ ] Offscreen scenes and available GM/dashboard artifacts are regenerated or dependency-gated in a report.
- [ ] Visual deltas are documented and accepted for this phase.
- [ ] Crashes and undocumented exceptions fail validation.

## Self-Review

- Spec coverage: tasks cover contracts/dumps, destination-read copy/intermediate, shader blends, saveLayer intermediates, provider reuse, MSAA, scene adapter cleanup, telemetry, validation, and reports.
- Scope split: each task has its own tests and commit. The broad phase remains one architecture because all sub-routes share the same `GPUIntermediatePlan` boundary.
- Type consistency: later tasks consume the types introduced in Tasks 1-4. `GPUPassCommandStream.fromIntermediatePlan` consumes `GPUIntermediatePlan`, and scene code consumes the same plan through the adapter.
- Known execution risk: Task 6 may require moving `RectOnlyFillDraw` and `RectOnlyDrawPlan` out of private scope. That move is local to the offscreen package and keeps `RectOnly` transitional.
