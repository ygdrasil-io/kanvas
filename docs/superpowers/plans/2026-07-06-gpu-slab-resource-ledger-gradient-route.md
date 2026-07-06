# GPU Slab Resource Ledger Gradient Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backend-neutral payload slab resource ledger, harden slab invalidation diagnostics, and apply the slab path to a first gradient/material uniform-payload route.

**Architecture:** Keep the planner and ledger in `resources` with no backend handles. The runtime records ledger events around existing private GPU materialization, then routes gradient uniform payload draws through the same slab machinery with a distinct `gradient-material-pass` source label.

**Tech Stack:** Kotlin/JVM, Gradle, Kotlin test/JUnit, WebGPU runtime implementation, existing `GPUPayloadMaterializationRequest`, `GPUPayloadSlabBatchPlanner`, `GPUBackendRuntimeTelemetry`, `GradientWgslShaderProvider`, and `GPUBackendUniformPayloadDraw`.

---

## File Structure

- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/PayloadSlabContracts.kt`
  - Owns backend-neutral payload slab batch contracts today.
  - Add `GPUPayloadSlabResourceLedger`, `GPUPayloadSlabResourceEvent`, and invalidation input.
  - Keep dump-safety helpers private in the same file to avoid a new utility surface.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadSlabBatchPlannerTest.kt`
  - Add ledger tests and invalidation refusal tests next to existing planner tests.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`
  - Record ledger events in `WgpuBackendRuntimeTelemetryRecorder`.
  - Generalize fullscreen payload request construction enough to carry a source profile.
  - Add gradient source label support without changing public backend handles.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt`
  - Add runtime smoke coverage for gradient payload slab accept and fallback.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadMaterializationProviderTest.kt`
  - Add provider-to-planner compatibility coverage for gradient payload requests.

Do not touch these dirty files unless a later user request explicitly changes scope:

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUSeparableBlur.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BlendWgslBuilder.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererLayoutSurfaceTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphReferenceParityTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphRenderSmokeTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUSeparableBlurTest.kt`
- `integration-tests/skia/src/test/resources/generated-renders/composite/graphite-replay.png`

## Task 1: Add Payload Slab Resource Ledger Contracts

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/PayloadSlabContracts.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadSlabBatchPlannerTest.kt`

- [ ] **Step 1: Write failing ledger tests**

Append these tests inside `GPUPayloadSlabBatchPlannerTest`, before `plan constructor rejects invalid slot coverage invariants`:

```kotlin
    @Test
    fun `resource ledger records bounded dump-safe payload slab events`() {
        val ledger = GPUPayloadSlabResourceLedger(maxEvents = 2)

        ledger.record(
            GPUPayloadSlabResourceEvent.Planned(
                sourceLabel = "payload-slab-source",
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11L,
                payloadCount = 3,
            ),
        )
        ledger.record(
            GPUPayloadSlabResourceEvent.Accepted(
                sourceLabel = "payload-slab-source",
                planHash = "plan-hash-1",
                totalBytes = 768L,
                slotCount = 3,
            ),
        )
        ledger.record(
            GPUPayloadSlabResourceEvent.Fallback(
                sourceLabel = "gradient-material-pass",
                reason = "binding_layout_mismatch",
                payloadCount = 2,
            ),
        )

        val dump = ledger.dumpLines()
        val dumpText = dump.joinToString("\n")

        assertEquals(2, dump.size)
        assertEquals(
            "payload-slab.resource.accepted source=payload-slab-source plan=plan-hash-1 totalBytes=768 slots=3",
            dump[0],
        )
        assertEquals(
            "payload-slab.resource.fallback source=gradient-material-pass reason=binding_layout_mismatch payloads=2",
            dump[1],
        )
        assertFalse(dumpText.contains("WGPU"))
        assertFalse(dumpText.contains("wgpu"))
        assertFalse(dumpText.contains("@"))
        assertFalse(dumpText.contains("0x"))
    }

    @Test
    fun `resource ledger rejects unsafe evidence`() {
        val ledger = GPUPayloadSlabResourceLedger(maxEvents = 4)

        assertFailsWith<IllegalArgumentException> {
            ledger.record(
                GPUPayloadSlabResourceEvent.Fallback(
                    sourceLabel = "payload@source",
                    reason = "binding_layout_mismatch",
                    payloadCount = 1,
                ),
            )
        }
    }
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadSlabBatchPlannerTest
```

Expected: compile failure mentioning `Unresolved reference: GPUPayloadSlabResourceLedger` and `Unresolved reference: GPUPayloadSlabResourceEvent`.

- [ ] **Step 3: Implement minimal ledger contracts**

In `PayloadSlabContracts.kt`, after `GPUPayloadSlabBatchPlanningResult`, add:

```kotlin
/** Backend-neutral append-only ledger for payload slab planning and fallback evidence. */
class GPUPayloadSlabResourceLedger(maxEvents: Int) {
    private val maxEvents: Int = maxEvents
    private val events = ArrayDeque<GPUPayloadSlabResourceEvent>()

    init {
        require(maxEvents > 0) { "GPUPayloadSlabResourceLedger.maxEvents must be positive" }
    }

    fun record(event: GPUPayloadSlabResourceEvent) {
        event.dumpFields().forEach { (field, value) ->
            requireDumpSafePayloadSlabValue("GPUPayloadSlabResourceEvent.$field", value)
        }
        events += event
        while (events.size > maxEvents) {
            events.removeFirst()
        }
    }

    fun dumpLines(): List<String> =
        events.map { event -> event.dumpLine() }
}

/** Backend-neutral evidence about payload slab planning, invalidation, and fallback decisions. */
sealed interface GPUPayloadSlabResourceEvent {
    data class Planned(
        val sourceLabel: String,
        val targetId: String,
        val frameId: String,
        val deviceGeneration: Long,
        val payloadCount: Int,
    ) : GPUPayloadSlabResourceEvent

    data class Accepted(
        val sourceLabel: String,
        val planHash: String,
        val totalBytes: Long,
        val slotCount: Int,
    ) : GPUPayloadSlabResourceEvent

    data class Fallback(
        val sourceLabel: String,
        val reason: String,
        val payloadCount: Int,
    ) : GPUPayloadSlabResourceEvent

    data class Invalidated(
        val sourceLabel: String,
        val targetId: String,
        val reason: String,
    ) : GPUPayloadSlabResourceEvent

    data class BudgetRefused(
        val sourceLabel: String,
        val requestedBytes: Long,
        val budgetBytes: Long,
    ) : GPUPayloadSlabResourceEvent
}
```

Then add these private helpers near `dumpFacts()`:

```kotlin
private fun GPUPayloadSlabResourceEvent.dumpLine(): String =
    when (this) {
        is GPUPayloadSlabResourceEvent.Planned ->
            "payload-slab.resource.planned source=$sourceLabel target=$targetId frame=$frameId " +
                "deviceGeneration=$deviceGeneration payloads=$payloadCount"
        is GPUPayloadSlabResourceEvent.Accepted ->
            "payload-slab.resource.accepted source=$sourceLabel plan=$planHash totalBytes=$totalBytes slots=$slotCount"
        is GPUPayloadSlabResourceEvent.Fallback ->
            "payload-slab.resource.fallback source=$sourceLabel reason=$reason payloads=$payloadCount"
        is GPUPayloadSlabResourceEvent.Invalidated ->
            "payload-slab.resource.invalidated source=$sourceLabel target=$targetId reason=$reason"
        is GPUPayloadSlabResourceEvent.BudgetRefused ->
            "payload-slab.resource.budget_refused source=$sourceLabel requestedBytes=$requestedBytes budgetBytes=$budgetBytes"
    }

private fun GPUPayloadSlabResourceEvent.dumpFields(): List<Pair<String, String>> =
    when (this) {
        is GPUPayloadSlabResourceEvent.Planned -> listOf(
            "sourceLabel" to sourceLabel,
            "targetId" to targetId,
            "frameId" to frameId,
        )
        is GPUPayloadSlabResourceEvent.Accepted -> listOf(
            "sourceLabel" to sourceLabel,
            "planHash" to planHash,
        )
        is GPUPayloadSlabResourceEvent.Fallback -> listOf(
            "sourceLabel" to sourceLabel,
            "reason" to reason,
        )
        is GPUPayloadSlabResourceEvent.Invalidated -> listOf(
            "sourceLabel" to sourceLabel,
            "targetId" to targetId,
            "reason" to reason,
        )
        is GPUPayloadSlabResourceEvent.BudgetRefused -> listOf(
            "sourceLabel" to sourceLabel,
        )
    }
```

- [ ] **Step 4: Run tests to verify GREEN**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadSlabBatchPlannerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/PayloadSlabContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadSlabBatchPlannerTest.kt
rtk git commit -m "Add payload slab resource ledger"
```

## Task 2: Add Explicit Payload Slab Invalidation Refusal

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/PayloadSlabContracts.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadSlabBatchPlannerTest.kt`

- [ ] **Step 1: Write failing invalidation test**

Add this test after `planner refuses invalid batch inputs with stable diagnostics`:

```kotlin
    @Test
    fun `planner refuses invalidated target evidence before slab materialization`() {
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                GPUPayloadSlabBatchRequest(
                    targetId = "root-target",
                    frameId = "frame-1",
                    sourceLabel = "payload-slab-source",
                    deviceGeneration = 11L,
                    alignmentBytes = 256L,
                    uploadBudgetBytes = 1024L,
                    payloadRequests = listOf(payloadRequest(index = 0)),
                    invalidatedReason = "target_generation_invalidated",
                ),
            ),
            expectedCode = "unsupported.payload_slab_resource_invalidated",
            expectedReason = "target_generation_invalidated",
        )
    }
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadSlabBatchPlannerTest
```

Expected: compile failure because `GPUPayloadSlabBatchRequest` has no `invalidatedReason` parameter.

- [ ] **Step 3: Add invalidation input and planner refusal**

Change the constructor in `PayloadSlabContracts.kt` to include a nullable reason:

```kotlin
class GPUPayloadSlabBatchRequest(
    val targetId: String,
    val frameId: String,
    val sourceLabel: String,
    val deviceGeneration: Long,
    val alignmentBytes: Long,
    val uploadBudgetBytes: Long,
    val invalidatedReason: String? = null,
    payloadRequests: List<GPUPayloadMaterializationRequest>,
)
```

In the `init` block add:

```kotlin
        require(invalidatedReason == null || invalidatedReason.isNotBlank()) {
            "GPUPayloadSlabBatchRequest.invalidatedReason must not be blank"
        }
        invalidatedReason?.let { reason ->
            requireDumpSafePayloadSlabValue("GPUPayloadSlabBatchRequest.invalidatedReason", reason)
        }
```

In `GPUPayloadSlabBatchPlanner.plan`, after the `unsafeBatchField` check and before `payloadRequests.isEmpty()`, add:

```kotlin
        request.invalidatedReason?.let { reason ->
            return refused(
                "unsupported.payload_slab_resource_invalidated",
                mapOf(
                    "targetId" to request.targetId,
                    "reason" to reason,
                ),
            )
        }
```

- [ ] **Step 4: Run tests to verify GREEN**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadSlabBatchPlannerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/PayloadSlabContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadSlabBatchPlannerTest.kt
rtk git commit -m "Refuse invalidated payload slab targets"
```

## Task 3: Record Ledger Events In Runtime Telemetry Dumps

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt`

- [ ] **Step 1: Write failing accepted-ledger smoke assertion**

In `backend runtime batches fullscreen uniform draws into one slab when backend is available`, after the assertion for `payload-slab.batch.plan source=fullscreen-uniform-pass`, add:

```kotlin
                assertTrue(dump.contains("payload-slab.resource.planned source=fullscreen-uniform-pass"))
                assertTrue(dump.contains("payload-slab.resource.accepted source=fullscreen-uniform-pass"))
```

- [ ] **Step 2: Write failing fallback-ledger smoke assertion**

In `backend runtime falls back when fullscreen uniform slab planner refuses and backend is available`, after `val dump = session.runtimeTelemetryDumpLines.joinToString("\n")`, add:

```kotlin
                    assertTrue(dump.contains("payload-slab.resource.planned source=fullscreen-uniform-pass"))
                    assertTrue(dump.contains("payload-slab.resource.fallback source=fullscreen-uniform-pass"))
                    assertTrue(dump.contains("reason=unsupported.payload_slab_dump_unsafe"))
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: the two tests fail because `runtimeTelemetryDumpLines` does not contain `payload-slab.resource.*`.

- [ ] **Step 4: Import ledger event type in runtime**

In `GPUBackendRuntimeWgpu.kt`, add:

```kotlin
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadSlabResourceEvent
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadSlabResourceLedger
```

- [ ] **Step 5: Add ledger to telemetry recorder**

In `WgpuBackendRuntimeTelemetryRecorder`, add this field near `payloadSlabDumpLines`:

```kotlin
    private val payloadSlabResourceLedger = GPUPayloadSlabResourceLedger(maxEvents = MAX_PAYLOAD_SLAB_DUMP_LINES)
```

Add this method:

```kotlin
    /** Records backend-neutral payload slab planning/fallback evidence without changing counters. */
    @Synchronized
    fun recordPayloadSlabResourceEvent(event: GPUPayloadSlabResourceEvent) {
        payloadSlabResourceLedger.record(event)
    }
```

Change `dumpLines()` to:

```kotlin
    fun dumpLines(): List<String> =
        snapshot().dumpLines() + payloadSlabDumpLines.toList() + payloadSlabResourceLedger.dumpLines()
```

- [ ] **Step 6: Record planned, accepted, and fallback events**

In `materializeFullscreenUniformSlab`, immediately after `val payloadRequests = draws.mapIndexed { index, draw -> fullscreenPayloadRequest(index, draw) }`, add:

```kotlin
        val sourceLabel = fullscreenUniformSlabSourceLabel()
        telemetryRecorder.recordPayloadSlabResourceEvent(
            GPUPayloadSlabResourceEvent.Planned(
                sourceLabel = sourceLabel,
                targetId = payloadTargetId,
                frameId = frameId,
                deviceGeneration = deviceGeneration.value,
                payloadCount = payloadRequests.size,
            ),
        )
```

Then change the batch request `sourceLabel = fullscreenUniformSlabSourceLabel()` to:

```kotlin
                    sourceLabel = sourceLabel,
```

In the `Refused` branch, before `null`, add:

```kotlin
                telemetryRecorder.recordPayloadSlabResourceEvent(
                    GPUPayloadSlabResourceEvent.Fallback(
                        sourceLabel = sourceLabel,
                        reason = planning.diagnostic.code,
                        payloadCount = payloadRequests.size,
                    ),
                )
```

In the `Accepted` branch, after `telemetryRecorder.recordPayloadSlabBatchPlan(plan)`, add:

```kotlin
                telemetryRecorder.recordPayloadSlabResourceEvent(
                    GPUPayloadSlabResourceEvent.Accepted(
                        sourceLabel = sourceLabel,
                        planHash = plan.planHash,
                        totalBytes = plan.uniformSlabPlan.totalBytes,
                        slotCount = plan.slotBindings.size,
                    ),
                )
```

- [ ] **Step 7: Run tests to verify GREEN**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt
rtk git commit -m "Record payload slab resource ledger events"
```

## Task 4: Add Gradient Source Profile For Fullscreen Uniform Payload Slabs

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt`

- [ ] **Step 1: Write failing gradient source-label smoke test**

Add this test before `backend runtime uploads uniform payload bytes and binds them when backend is available`:

```kotlin
    @Test
    fun `backend runtime records gradient material payload slabs when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 6,
                    height = 2,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenUniformPayloadPass(
                        wgsl = solidColorPayloadWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendUniformPayloadDraw(
                                uniformBytes = uniformPayloadBlock().bytes.map { byte -> byte.toByte() }.toByteArray(),
                                materialization = gradientMaterialization("gradient-red"),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 3,
                                scissorHeight = 2,
                            ),
                            GPUBackendUniformPayloadDraw(
                                uniformBytes = uniformPayloadBlock().bytes.map { byte -> byte.toByte() }.toByteArray(),
                                materialization = gradientMaterialization("gradient-green"),
                                scissorX = 3,
                                scissorY = 0,
                                scissorWidth = 3,
                                scissorHeight = 2,
                            ),
                        ),
                        sourceLabel = "gradient-material-pass",
                    )
                }

                val after = session.runtimeTelemetry
                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                assertEquals(1L, after.uniformSlabsCreated - before.uniformSlabsCreated)
                assertTrue(dump.contains("payload-slab.batch.plan source=gradient-material-pass"))
                assertTrue(dump.contains("payload-slab.resource.planned source=gradient-material-pass"))
                assertTrue(dump.contains("payload-slab.resource.accepted source=gradient-material-pass"))
                assertTrue(!dump.contains("@"))
                assertTrue(!dump.contains("WGPU"))
                assertTrue(!dump.contains("wgpu"))
                assertTrue(!dump.contains("0x"))
            }
        }
    }
```

Also add this helper near `payloadMaterializationRequest`:

```kotlin
    private fun gradientMaterialization(label: String): GPUResourceMaterializationDecision.Materialized {
        val uniformBlock = uniformPayloadBlock()
        val request = payloadMaterializationRequest(
            uniformBlock = uniformBlock,
            resourceDescriptorLabel = "uniform:gradient-material-payload",
            layoutHash = "layout:linear-gradient-material-block:v1",
            resourceLabel = label,
        )
        return assertIs<GPUResourceMaterializationDecision.Materialized>(
            ValidatingPayloadResourceProvider().materializePayloadBindings(
                request = request,
                context = GPUTargetPreparationContext(
                    targetId = "root-target",
                    frameId = "frame-1",
                    deviceGeneration = 1,
                    budgetClass = "smoke-test",
                ),
            ),
        )
    }
```

Change the existing `payloadMaterializationRequest` helper signature to:

```kotlin
    private fun payloadMaterializationRequest(
        uniformBlock: GPUUniformPayloadBlock,
        resourceDescriptorLabel: String = "uniform:solid-payload",
        layoutHash: String = "layout-solid-v1",
        resourceLabel: String = "solid-fill",
    ): GPUPayloadMaterializationRequest =
```

Inside that helper, update:

```kotlin
            resourcePlanLabels = listOf("payload-materialization:$resourceLabel"),
```

and:

```kotlin
                bindingPlanHash = layoutHash,
                resourceDescriptorLabels = listOf(resourceDescriptorLabel),
```

and:

```kotlin
            reflectedBindingLayoutHash = layoutHash,
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: compile failure because `drawFullscreenUniformPayloadPass` has no `sourceLabel` parameter.

- [ ] **Step 3: Add source label to recorder API with default**

In `GPUBackendRuntimeContracts.kt`, change the interface method to:

```kotlin
    fun drawFullscreenUniformPayloadPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendUniformPayloadDraw>,
        blendMode: GPUBlendMode? = null,
        sourceLabel: String = "fullscreen-uniform-pass",
    )
```

In `GPUBackendRuntimeWgpu.kt`, change the override signature to include the same parameter:

```kotlin
    override fun drawFullscreenUniformPayloadPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendUniformPayloadDraw>,
        blendMode: GPUBlendMode?,
        sourceLabel: String,
    ) {
```

- [ ] **Step 4: Route source label through internal fullscreen pass**

Change `recordFullscreenUniformPass` signature to:

```kotlin
    private fun recordFullscreenUniformPass(
        wgsl: String,
        colorFormat: String,
        draws: List<WgpuFullscreenUniformDraw>,
        blendMode: GPUBlendMode? = null,
        sourceLabel: String = fullscreenUniformSlabSourceLabel(),
    ) {
```

In `drawFullscreenPass`, pass the default explicitly:

```kotlin
            sourceLabel = fullscreenUniformSlabSourceLabel(),
```

In `drawFullscreenUniformPayloadPass`, pass:

```kotlin
            sourceLabel = sourceLabel,
```

Change `materializeFullscreenUniformSlab(draws)` to:

```kotlin
        val slab = materializeFullscreenUniformSlab(draws = draws, sourceLabel = sourceLabel)
```

Change `materializeFullscreenUniformSlab` signature to:

```kotlin
    private fun materializeFullscreenUniformSlab(
        draws: List<WgpuFullscreenUniformDraw>,
        sourceLabel: String,
    ): WgpuPayloadSlabMaterialization? {
```

Remove the local `val sourceLabel = fullscreenUniformSlabSourceLabel()` from that function.

- [ ] **Step 5: Run tests to verify GREEN**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt
rtk git commit -m "Route gradient payload slabs with source labels"
```

## Task 5: Add Provider-To-Planner Gradient Compatibility Coverage

**Files:**
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadMaterializationProviderTest.kt`

- [ ] **Step 1: Add failing gradient compatibility test**

Add a test next to `accepted payload provider request can seed a payload slab batch plan`:

```kotlin
    @Test
    fun `accepted gradient payload provider request can seed a gradient payload slab batch plan`() {
        val firstRequest = payloadMaterializationRequest(
            packetId = "gradient-packet-0",
            resourcePlanLabel = "gradient-linear-0",
            uniformBlock = uniformBlock(
                fingerprint = "uniform-fingerprint-gradient-0",
                packingPlanHash = "linear-gradient-layout-v1",
                bytes = gradientUniformBytes(seed = 1),
            ),
            uniformSlot = uniformSlot(
                slotId = "gradient-pass:uniform:0",
                fingerprint = "uniform-fingerprint-gradient-0",
            ),
            resourceBlock = resourceBlock(
                fingerprint = "resource-fingerprint-gradient-0",
                bindingPlanHash = "layout:linear-gradient-material-block:v1",
                resourceDescriptorLabels = listOf("uniform:gradient-material-payload"),
            ),
            resourceSlot = resourceSlot(
                slotId = "gradient-pass:resource:0",
                fingerprint = "resource-fingerprint-gradient-0",
            ),
            uploadPlan = uploadPlan(
                planHash = "upload-gradient-v1-0",
                stagingScope = "gradient-pass-staging",
                beforeUseToken = "before-gradient-draw-0",
            ),
            reflectedBindingLayoutHash = "layout:linear-gradient-material-block:v1",
        )
        val secondRequest = payloadMaterializationRequest(
            packetId = "gradient-packet-1",
            resourcePlanLabel = "gradient-linear-1",
            uniformBlock = uniformBlock(
                fingerprint = "uniform-fingerprint-gradient-1",
                packingPlanHash = "linear-gradient-layout-v1",
                bytes = gradientUniformBytes(seed = 2),
            ),
            uniformSlot = uniformSlot(
                slotId = "gradient-pass:uniform:1",
                fingerprint = "uniform-fingerprint-gradient-1",
            ),
            resourceBlock = resourceBlock(
                fingerprint = "resource-fingerprint-gradient-1",
                bindingPlanHash = "layout:linear-gradient-material-block:v1",
                resourceDescriptorLabels = listOf("uniform:gradient-material-payload"),
            ),
            resourceSlot = resourceSlot(
                slotId = "gradient-pass:resource:1",
                fingerprint = "resource-fingerprint-gradient-1",
            ),
            uploadPlan = uploadPlan(
                planHash = "upload-gradient-v1-1",
                stagingScope = "gradient-pass-staging",
                beforeUseToken = "before-gradient-draw-1",
            ),
            reflectedBindingLayoutHash = "layout:linear-gradient-material-block:v1",
        )

        val provider = ValidatingPayloadResourceProvider()
        val context = targetPreparationContext()
        assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializePayloadBindings(firstRequest, context),
        )
        assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializePayloadBindings(secondRequest, context),
        )

        val slab = GPUPayloadSlabBatchPlanner.plan(
            GPUPayloadSlabBatchRequest(
                targetId = context.targetId,
                frameId = context.frameId,
                sourceLabel = "gradient-material-pass",
                deviceGeneration = context.deviceGeneration,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloadRequests = listOf(firstRequest, secondRequest),
            ),
        )

        val accepted = assertIs<GPUPayloadSlabBatchPlanningResult.Accepted>(slab)
        assertEquals("gradient-material-pass", accepted.plan.sourceLabel)
        assertEquals(2, accepted.plan.slotBindings.size)
        assertEquals(
            listOf("layout:linear-gradient-material-block:v1", "layout:linear-gradient-material-block:v1"),
            accepted.plan.slotBindings.map { binding -> binding.reflectedBindingLayoutHash },
        )
    }
```

Do not add `gradientUniformBytes` yet. The first RED run should fail because helper parameters and the helper function do not exist yet.

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest
```

Expected: compile failure mentioning at least one missing named parameter such as `packetId`, `resourcePlanLabel`, `reflectedBindingLayoutHash`, or `Unresolved reference: gradientUniformBytes`.

- [ ] **Step 3: Extend provider test helpers**

Replace the `payloadMaterializationRequest` helper signature with:

```kotlin
    private fun payloadMaterializationRequest(
        deviceGeneration: Long = 11L,
        payloadGeneration: Long = 7L,
        uploadBudgetBytes: Long = 256L,
        uploadCapabilityAvailable: Boolean = true,
        availableUniformUsageLabels: Set<String> = setOf("copy_dst", "uniform"),
        packetId: String = "packet-9",
        resourcePlanLabel: String = "solid-fill",
        reflectedBindingLayoutHash: String = "layout-solid-v1",
        uniformBlock: GPUUniformPayloadBlock = uniformBlock(),
        uniformSlot: GPUUniformPayloadSlot = uniformSlot(),
        resourceBlock: GPUResourceBindingBlock = resourceBlock(),
        resourceSlot: GPUResourceBindingSlot = resourceSlot(),
        uploadPlan: GPUPayloadUploadPlan = uploadPlan(),
    ): GPUPayloadMaterializationRequest =
```

Inside that helper, change:

```kotlin
            packetId = "packet-9",
            resourcePlanLabels = listOf("payload-materialization:solid-fill"),
```

to:

```kotlin
            packetId = packetId,
            resourcePlanLabels = listOf("payload-materialization:$resourcePlanLabel"),
```

and change:

```kotlin
            reflectedBindingLayoutHash = "layout-solid-v1",
```

to:

```kotlin
            reflectedBindingLayoutHash = reflectedBindingLayoutHash,
```

Replace the `uniformBlock` helper with:

```kotlin
    private fun uniformBlock(
        scope: String = "pass-a",
        fingerprint: String = "uniform-fingerprint-solid",
        packingPlanHash: String = "solid-rect-layout-v1",
        bytes: List<Int> = listOf(1, 2, 3, 4) + List(60) { 0 },
    ): GPUUniformPayloadBlock =
        GPUUniformPayloadBlock(
            fingerprint = GPUPayloadFingerprint(fingerprint),
            packingPlanHash = packingPlanHash,
            byteSize = bytes.size.toLong(),
            zeroedPadding = true,
            scope = scope,
            bytes = bytes,
        )
```

Replace the `uniformSlot` helper with:

```kotlin
    private fun uniformSlot(
        slotId: String = "pass-a:uniform:0",
        fingerprint: String = "uniform-fingerprint-solid",
    ): GPUUniformPayloadSlot =
        GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID(slotId),
            fingerprint = GPUPayloadFingerprint(fingerprint),
            byteOffset = 0L,
        )
```

Replace the `resourceBlock` helper with:

```kotlin
    private fun resourceBlock(
        fingerprint: String = "resource-fingerprint-solid",
        bindingPlanHash: String = "layout-solid-v1",
        dynamicOffsets: List<Long> = listOf(0L),
        resourceDescriptorLabels: List<String> = listOf("uniform:solid-payload"),
        bindingFacts: List<GPUResourceBindingFact> = emptyList(),
        bindingCount: Int = resourceDescriptorLabels.size,
    ): GPUResourceBindingBlock =
        GPUResourceBindingBlock(
            fingerprint = GPUPayloadFingerprint(fingerprint),
            bindingPlanHash = bindingPlanHash,
            bindingCount = bindingCount,
            resourceDescriptorLabels = resourceDescriptorLabels,
            dynamicOffsets = dynamicOffsets,
            bindingFacts = bindingFacts,
        )
```

Replace the `resourceSlot` helper with:

```kotlin
    private fun resourceSlot(
        slotId: String = "pass-a:resource:0",
        fingerprint: String = "resource-fingerprint-solid",
    ): GPUResourceBindingSlot =
        GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID(slotId),
            fingerprint = GPUPayloadFingerprint(fingerprint),
            bindingIndex = 0,
        )
```

Replace the `uploadPlan` helper with:

```kotlin
    private fun uploadPlan(
        planHash: String = "upload-solid-v1",
        byteRanges: List<LongRange> = listOf(0L..63L),
        stagingScope: String = "pass-a-staging",
        beforeUseToken: String = "before-draw-9",
    ): GPUPayloadUploadPlan =
        GPUPayloadUploadPlan(
            planHash = planHash,
            byteRanges = byteRanges,
            stagingScope = stagingScope,
            budgetClass = "unit-test",
            beforeUseToken = beforeUseToken,
        )
```

Add this helper before `targetPreparationContext()`:

```kotlin
    private fun gradientUniformBytes(seed: Int): List<Int> {
        val bytes = ByteArray(64)
        bytes[0] = seed.toByte()
        bytes[4] = (seed + 1).toByte()
        bytes[8] = (seed + 2).toByte()
        bytes[12] = 1
        return bytes.map { byte -> byte.toInt() and 0xff }
    }
```

- [ ] **Step 4: Run provider test to verify GREEN**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run provider and planner tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadSlabBatchPlannerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadMaterializationProviderTest.kt
rtk git commit -m "Test gradient payload provider slab compatibility"
```

## Task 6: Final Verification And PR Update

**Files:**
- No code changes expected unless verification exposes a bug in the previous tasks.

- [ ] **Step 1: Run focused verification**

Run:

```bash
rtk ./gradlew :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadSlabBatchPlannerTest \
  --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run diff check**

Run:

```bash
rtk git diff --check -- \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/PayloadSlabContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadSlabBatchPlannerTest.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPayloadMaterializationProviderTest.kt
```

Expected: no output and exit code 0.

- [ ] **Step 3: Run full module test and document status**

Run:

```bash
rtk ./gradlew :gpu-renderer:test
```

Expected current known status: this may fail only on `GPURendererPackageBoundaryTest > gpu renderer production source satisfies package boundary rules`, with preexisting violations in `GPUBackendRuntimeWgpu.kt`, `GradientWgslShaderProvider.kt`, `WgslReflection.kt`, and package cycles. If a different failure appears, stop and debug before pushing.

- [ ] **Step 4: Inspect status for unrelated dirty files**

Run:

```bash
rtk git status --short
```

Expected: committed implementation files clean; unrelated dirty files may remain unstaged exactly as before.

- [ ] **Step 5: Push branch**

Run:

```bash
rtk git push origin codex/gpu-baseline-caps-design
```

Expected: push succeeds to the existing PR branch.

## Self-Review Checklist

- Spec coverage:
  - Ledger contracts: Task 1.
  - Invalidation diagnostic: Task 2.
  - Runtime ledger evidence: Task 3.
  - Gradient route pilot: Task 4.
  - Provider compatibility: Task 5.
  - Verification and PR update: Task 6.
- Placeholder scan:
  - No placeholder markers or copy-by-reference steps.
  - Each code step includes concrete snippets or exact adaptation instructions.
- Type consistency:
  - Ledger types use `GPUPayloadSlabResourceLedger` and `GPUPayloadSlabResourceEvent`.
  - Runtime source label remains `String` and defaults to `fullscreen-uniform-pass`.
  - New gradient smoke test uses existing `GPUBackendUniformPayloadDraw` and `drawFullscreenUniformPayloadPass`.
