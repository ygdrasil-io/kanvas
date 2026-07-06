# GPU Uniform Slab Prototype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a limited GPU uniform slab prototype for fullscreen uniform passes, with backend-neutral planning contracts, runtime telemetry, and one live WGPU route integration.

**Architecture:** Keep the reusable slab model in `resources` as backend-neutral contracts. Keep the live materialization prototype private to `GPUBackendRuntimeWgpu.kt` and apply it only inside `recordFullscreenUniformPass`. Use the existing runtime telemetry counters plus slab-specific counters to prove the route changed without claiming broad renderer performance.

**Tech Stack:** Kotlin/JVM, Gradle, kotlin.test, JUnit assumptions, wgpu4k/WebGPU `BufferBinding(offset, size)`, existing `gpu-renderer` module.

---

## File Structure

- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/UniformSlabContracts.kt`
  - Owns `GPUUniformSlabPayload`, `GPUUniformSlabSlot`, `GPUUniformSlabPlan`, `GPUUniformSlabDiagnostic`, `GPUUniformSlabPlanningResult`, and `GPUUniformSlabPlanner`.
  - Has no WebGPU imports and no backend handle references.
- Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUUniformSlabPlannerTest.kt`
  - Covers deterministic layout, hash stability, budget checks, invalid alignment, stale generation, empty payload refusal, and dump safety.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
  - Adds slab counters to `GPUBackendRuntimeTelemetry`.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt`
  - Extends the existing telemetry contract tests for the new slab counters and dump line.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`
  - Imports the slab planner contracts.
  - Passes `GPUDeviceGeneration` into `WgpuRenderRecorder`.
  - Adds private runtime materialization helpers for fullscreen uniform slabs.
  - Changes only `recordFullscreenUniformPass` to use one slab buffer per pass when the planner accepts.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt`
  - Adds one smoke test proving three fullscreen uniform draws use one slab buffer, preserve pixels, and update slab telemetry.

## Execution Notes

- Use public wording as `GPU`, not `Wgpu`, except exact file/class names that already contain `Wgpu`.
- Do not edit the unrelated local PNG `integration-tests/skia/src/test/resources/generated-renders/composite/graphite-replay.png`.
- Keep this PR additive and scoped. Do not fix the existing full-suite failures in this plan.
- Recommended subagent split:
  - Task 1: `gpt-5.4-mini`, medium reasoning.
  - Task 2: `gpt-5.4-mini`, medium reasoning.
  - Task 3: `gpt-5.4`, high reasoning.
  - Task 4: `gpt-5.4`, high reasoning or inline verification.

### Task 1: Add Backend-Neutral Uniform Slab Planner

**Files:**
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUUniformSlabPlannerTest.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/UniformSlabContracts.kt`

- [ ] **Step 1: Write the failing planner tests**

Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUUniformSlabPlannerTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/** Verifies backend-neutral uniform slab layout planning. */
class GPUUniformSlabPlannerTest {
    @Test
    fun `planner aligns payload slots and emits dump-safe facts`() {
        val result = GPUUniformSlabPlanner.plan(
            sourceLabel = "fullscreen-uniform-pass",
            deviceGeneration = 7L,
            alignmentBytes = 256L,
            uploadBudgetBytes = 1024L,
            payloads = listOf(
                GPUUniformSlabPayload(slotLabel = "draw-0", bytes = byteArrayOf(1, 2, 3, 4)),
                GPUUniformSlabPayload(slotLabel = "draw-1", bytes = ByteArray(16) { 9 }),
                GPUUniformSlabPayload(slotLabel = "draw-2", bytes = ByteArray(48) { 3 }),
            ),
        )

        val accepted = assertIs<GPUUniformSlabPlanningResult.Accepted>(result)
        val plan = accepted.plan

        assertEquals("fullscreen-uniform-pass", plan.sourceLabel)
        assertEquals(7L, plan.deviceGeneration)
        assertEquals(256L, plan.alignmentBytes)
        assertEquals(768L, plan.totalBytes)
        assertEquals(listOf(0L, 256L, 512L), plan.slots.map { slot -> slot.alignedOffset })
        assertEquals(listOf(256L, 256L, 256L), plan.slots.map { slot -> slot.allocatedBytes })
        assertEquals(listOf(4L, 16L, 48L), plan.slots.map { slot -> slot.payloadBytes })
        assertEquals(listOf("draw-0", "draw-1", "draw-2"), plan.slots.map { slot -> slot.slotLabel })

        val dump = plan.dumpLines()
        assertContains(
            dump,
            "uniform-slab.plan source=fullscreen-uniform-pass deviceGeneration=7 " +
                "alignment=256 totalBytes=768 uploadBudgetBytes=1024 slots=3 hash=${plan.planHash}",
        )
        assertContains(
            dump,
            "uniform-slab.slot source=fullscreen-uniform-pass slot=draw-1 offset=256 " +
                "payloadBytes=16 allocatedBytes=256 payloadHash=${plan.slots[1].payloadHash}",
        )
        assertFalse(dump.joinToString("\n").contains("@"))
        assertFalse(dump.joinToString("\n").contains("0x"))
    }

    @Test
    fun `planner hash is stable and changes when payload bytes change`() {
        val first = acceptedPlan(payloadByte = 42)
        val second = acceptedPlan(payloadByte = 42)
        val changed = acceptedPlan(payloadByte = 43)

        assertEquals(first.planHash, second.planHash)
        assertEquals(first.slots.map { slot -> slot.payloadHash }, second.slots.map { slot -> slot.payloadHash })
        assertNotEquals(first.planHash, changed.planHash)
        assertNotEquals(first.slots.single().payloadHash, changed.slots.single().payloadHash)
    }

    @Test
    fun `planner refuses empty payload budget overflow invalid alignment stale generation and dump unsafe labels`() {
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 1L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = byteArrayOf())),
            ),
            expectedCode = "unsupported.uniform_slab_empty_payload",
        )
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 1L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 128L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = ByteArray(16) { 1 })),
            ),
            expectedCode = "unsupported.uniform_slab_budget_exceeded",
        )
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 1L,
                alignmentBytes = 0L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = ByteArray(16) { 1 })),
            ),
            expectedCode = "unsupported.uniform_slab_alignment_invalid",
        )
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = -1L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = ByteArray(16) { 1 })),
            ),
            expectedCode = "unsupported.uniform_slab_stale_generation",
        )
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen@uniform",
                deviceGeneration = 1L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = ByteArray(16) { 1 })),
            ),
            expectedCode = "unsupported.uniform_slab_dump_unsafe",
        )
    }

    private fun acceptedPlan(payloadByte: Byte): GPUUniformSlabPlan =
        assertIs<GPUUniformSlabPlanningResult.Accepted>(
            GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 3L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(
                    GPUUniformSlabPayload(slotLabel = "draw-0", bytes = byteArrayOf(payloadByte)),
                ),
            ),
        ).plan

    private fun assertRefused(
        result: GPUUniformSlabPlanningResult,
        expectedCode: String,
    ) {
        val refused = assertIs<GPUUniformSlabPlanningResult.Refused>(result)
        assertEquals(expectedCode, refused.diagnostic.code)
        val dump = refused.dumpLines().joinToString("\n")
        assertContains(dump, "uniform-slab.refused code=$expectedCode")
        assertFalse(dump.contains("@"))
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlannerTest
```

Expected: FAIL with unresolved references for `GPUUniformSlabPlanner`, `GPUUniformSlabPayload`, `GPUUniformSlabPlanningResult`, and `GPUUniformSlabPlan`.

- [ ] **Step 3: Add the planner contracts**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/UniformSlabContracts.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.resources

import java.security.MessageDigest

/** CPU-owned bytes for one pass-local uniform payload that can be placed in a slab. */
data class GPUUniformSlabPayload(
    val slotLabel: String,
    val bytes: ByteArray,
) {
    init {
        require(slotLabel.isNotBlank()) { "GPUUniformSlabPayload.slotLabel must not be blank" }
    }
}

/** One aligned payload range inside a backend-neutral uniform slab plan. */
data class GPUUniformSlabSlot(
    val slotLabel: String,
    val payloadHash: String,
    val payloadBytes: Long,
    val alignedOffset: Long,
    val allocatedBytes: Long,
) {
    init {
        require(slotLabel.isNotBlank()) { "GPUUniformSlabSlot.slotLabel must not be blank" }
        require(payloadHash.isNotBlank()) { "GPUUniformSlabSlot.payloadHash must not be blank" }
        require(payloadBytes > 0L) { "GPUUniformSlabSlot.payloadBytes must be positive" }
        require(alignedOffset >= 0L) { "GPUUniformSlabSlot.alignedOffset must be non-negative" }
        require(allocatedBytes >= payloadBytes) {
            "GPUUniformSlabSlot.allocatedBytes must cover payloadBytes"
        }
    }
}

/** Backend-neutral uniform slab layout accepted before runtime materialization. */
data class GPUUniformSlabPlan(
    val planHash: String,
    val sourceLabel: String,
    val deviceGeneration: Long,
    val alignmentBytes: Long,
    val totalBytes: Long,
    val uploadBudgetBytes: Long,
    val slots: List<GPUUniformSlabSlot>,
) {
    init {
        require(planHash.isNotBlank()) { "GPUUniformSlabPlan.planHash must not be blank" }
        require(sourceLabel.isNotBlank()) { "GPUUniformSlabPlan.sourceLabel must not be blank" }
        require(deviceGeneration >= 0L) { "GPUUniformSlabPlan.deviceGeneration must be non-negative" }
        require(alignmentBytes > 0L) { "GPUUniformSlabPlan.alignmentBytes must be positive" }
        require(totalBytes > 0L) { "GPUUniformSlabPlan.totalBytes must be positive" }
        require(uploadBudgetBytes >= totalBytes) {
            "GPUUniformSlabPlan.uploadBudgetBytes must cover totalBytes"
        }
        require(slots.isNotEmpty()) { "GPUUniformSlabPlan.slots must not be empty" }
        require(totalBytes % alignmentBytes == 0L) { "GPUUniformSlabPlan.totalBytes must be aligned" }
        slots.forEach { slot ->
            require(slot.alignedOffset % alignmentBytes == 0L) {
                "GPUUniformSlabPlan slot ${slot.slotLabel} offset must be aligned"
            }
        }
        listOf(planHash, sourceLabel).forEach { value ->
            requireDumpSafeUniformSlabValue(value)
        }
        slots.forEach { slot ->
            requireDumpSafeUniformSlabValue(slot.slotLabel)
            requireDumpSafeUniformSlabValue(slot.payloadHash)
        }
    }

    /** Deterministic evidence lines without backend handles. */
    fun dumpLines(): List<String> =
        listOf(
            "uniform-slab.plan source=$sourceLabel deviceGeneration=$deviceGeneration " +
                "alignment=$alignmentBytes totalBytes=$totalBytes uploadBudgetBytes=$uploadBudgetBytes " +
                "slots=${slots.size} hash=$planHash",
        ) + slots.map { slot ->
            "uniform-slab.slot source=$sourceLabel slot=${slot.slotLabel} offset=${slot.alignedOffset} " +
                "payloadBytes=${slot.payloadBytes} allocatedBytes=${slot.allocatedBytes} " +
                "payloadHash=${slot.payloadHash}"
        }
}

/** Stable refusal emitted before live uniform slab materialization. */
data class GPUUniformSlabDiagnostic(
    val code: String,
    val message: String,
) {
    init {
        require(code.isNotBlank()) { "GPUUniformSlabDiagnostic.code must not be blank" }
        require(message.isNotBlank()) { "GPUUniformSlabDiagnostic.message must not be blank" }
        requireDumpSafeUniformSlabValue(code)
        requireDumpSafeUniformSlabValue(message)
    }
}

/** Result of backend-neutral uniform slab planning. */
sealed interface GPUUniformSlabPlanningResult {
    data class Accepted(val plan: GPUUniformSlabPlan) : GPUUniformSlabPlanningResult
    data class Refused(val diagnostic: GPUUniformSlabDiagnostic) : GPUUniformSlabPlanningResult {
        fun dumpLines(): List<String> =
            listOf("uniform-slab.refused code=${diagnostic.code} message=${diagnostic.message}")
    }
}

/** Deterministic planner for pass-local uniform slab layout. */
object GPUUniformSlabPlanner {
    fun plan(
        sourceLabel: String,
        deviceGeneration: Long,
        alignmentBytes: Long,
        uploadBudgetBytes: Long,
        payloads: List<GPUUniformSlabPayload>,
    ): GPUUniformSlabPlanningResult {
        val unsafeValue = sequenceOf(sourceLabel) + payloads.asSequence().map { payload -> payload.slotLabel }
        if (unsafeValue.any { value -> !isDumpSafeUniformSlabValue(value) }) {
            return refused("unsupported.uniform_slab_dump_unsafe", "Uniform slab labels must be dump-safe")
        }
        if (deviceGeneration < 0L) {
            return refused("unsupported.uniform_slab_stale_generation", "Device generation must be non-negative")
        }
        if (alignmentBytes <= 0L) {
            return refused("unsupported.uniform_slab_alignment_invalid", "Alignment must be positive")
        }
        if (payloads.isEmpty() || payloads.any { payload -> payload.bytes.isEmpty() }) {
            return refused("unsupported.uniform_slab_empty_payload", "Uniform slab payloads must not be empty")
        }

        var offset = 0L
        val slots = payloads.map { payload ->
            val payloadBytes = payload.bytes.size.toLong()
            val allocatedBytes = alignUp(payloadBytes, alignmentBytes)
            val slot = GPUUniformSlabSlot(
                slotLabel = payload.slotLabel,
                payloadHash = sha256Hex(payload.bytes),
                payloadBytes = payloadBytes,
                alignedOffset = offset,
                allocatedBytes = allocatedBytes,
            )
            offset += allocatedBytes
            slot
        }
        val totalBytes = offset
        if (uploadBudgetBytes < totalBytes) {
            return refused(
                "unsupported.uniform_slab_budget_exceeded",
                "Uniform slab totalBytes=$totalBytes exceeds uploadBudgetBytes=$uploadBudgetBytes",
            )
        }

        val planHash = sha256Hex(
            buildString {
                appendLine("kind=uniform-slab-plan")
                appendLine("source=$sourceLabel")
                appendLine("deviceGeneration=$deviceGeneration")
                appendLine("alignment=$alignmentBytes")
                appendLine("totalBytes=$totalBytes")
                slots.forEach { slot ->
                    appendLine(
                        "slot=${slot.slotLabel}|hash=${slot.payloadHash}|bytes=${slot.payloadBytes}|" +
                            "offset=${slot.alignedOffset}|allocated=${slot.allocatedBytes}",
                    )
                }
            }.toByteArray(),
        )

        return GPUUniformSlabPlanningResult.Accepted(
            GPUUniformSlabPlan(
                planHash = planHash,
                sourceLabel = sourceLabel,
                deviceGeneration = deviceGeneration,
                alignmentBytes = alignmentBytes,
                totalBytes = totalBytes,
                uploadBudgetBytes = uploadBudgetBytes,
                slots = slots,
            ),
        )
    }

    private fun refused(code: String, message: String): GPUUniformSlabPlanningResult.Refused =
        GPUUniformSlabPlanningResult.Refused(
            GPUUniformSlabDiagnostic(code = code, message = message),
        )
}

private fun alignUp(value: Long, alignment: Long): Long =
    ((value + alignment - 1L) / alignment) * alignment

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun isDumpSafeUniformSlabValue(value: String): Boolean =
    value.isNotBlank() && !value.contains("@") && !value.contains("0x") && !value.contains("\n")

private fun requireDumpSafeUniformSlabValue(value: String) {
    require(isDumpSafeUniformSlabValue(value)) { "Uniform slab dump value is not safe: $value" }
}
```

- [ ] **Step 4: Run the focused planner test and verify it passes**

Run:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlannerTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/UniformSlabContracts.kt \
    gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUUniformSlabPlannerTest.kt
rtk git commit -m "Add GPU uniform slab planner"
```

### Task 2: Add Uniform Slab Runtime Telemetry Counters

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt`

- [ ] **Step 1: Extend the failing telemetry contract test**

In `GPUBackendRuntimeContractsTest.kt`, update the existing test `runtime telemetry defaults to zero counters and deterministic dump` so it checks the three new counters and the new dump line. The final assertions inside that test should include:

```kotlin
        assertEquals(0L, telemetry.uniformSlabsCreated)
        assertEquals(0L, telemetry.uniformSlabBytesAllocated)
        assertEquals(0L, telemetry.uniformSlabFallbacks)
        assertEquals(
            listOf(
                "gpu-runtime.telemetry renderPasses=0 offscreenPasses=0 windowPasses=0 " +
                    "submissions=0 buffersCreated=0 texturesCreated=0 bindGroupsCreated=0 " +
                    "samplersCreated=0 queueWrites=0 uniformSlabsCreated=0 " +
                    "uniformSlabBytesAllocated=0 uniformSlabFallbacks=0",
            ),
            telemetry.dumpLines(),
        )
```

In the test `runtime telemetry rejects negative counters`, add:

```kotlin
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(uniformSlabsCreated = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(uniformSlabBytesAllocated = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(uniformSlabFallbacks = -1L)
        }
```

- [ ] **Step 2: Run the focused contract test and verify it fails**

Run:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest
```

Expected: FAIL with unresolved references for `uniformSlabsCreated`, `uniformSlabBytesAllocated`, and `uniformSlabFallbacks`.

- [ ] **Step 3: Extend `GPUBackendRuntimeTelemetry`**

In `GPUBackendRuntimeContracts.kt`, add fields after `queueWrites`:

```kotlin
    val uniformSlabsCreated: Long = 0L,
    val uniformSlabBytesAllocated: Long = 0L,
    val uniformSlabFallbacks: Long = 0L,
```

Add validation in `init` after `queueWrites` validation:

```kotlin
        require(uniformSlabsCreated >= 0L) { "GPUBackendRuntimeTelemetry.uniformSlabsCreated must be non-negative" }
        require(uniformSlabBytesAllocated >= 0L) {
            "GPUBackendRuntimeTelemetry.uniformSlabBytesAllocated must be non-negative"
        }
        require(uniformSlabFallbacks >= 0L) { "GPUBackendRuntimeTelemetry.uniformSlabFallbacks must be non-negative" }
```

Update `dumpLines()` to append the new fields:

```kotlin
            "texturesCreated=$texturesCreated bindGroupsCreated=$bindGroupsCreated " +
                "samplersCreated=$samplersCreated queueWrites=$queueWrites " +
                "uniformSlabsCreated=$uniformSlabsCreated " +
                "uniformSlabBytesAllocated=$uniformSlabBytesAllocated " +
                "uniformSlabFallbacks=$uniformSlabFallbacks",
```

- [ ] **Step 4: Run the focused contract test and verify it passes**

Run:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
    gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt
rtk git commit -m "Track GPU uniform slab runtime telemetry"
```

### Task 3: Add Fullscreen Uniform Slab Runtime Prototype

**Files:**
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`

- [ ] **Step 1: Write the failing runtime smoke test**

In `GPUBackendRuntimeWgpuSmokeTest.kt`, add this test before `backend runtime records WGPU execution cache hit miss and create telemetry when backend is available`:

```kotlin
    @Test
    fun `backend runtime batches fullscreen uniform draws into one slab when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "WGPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 6,
                    height = 2,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                val before = session.runtimeTelemetry

                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 2,
                                scissorHeight = 2,
                            ),
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(0f, 1f, 0f, 1f),
                                scissorX = 2,
                                scissorY = 0,
                                scissorWidth = 2,
                                scissorHeight = 2,
                            ),
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(0f, 0f, 1f, 1f),
                                scissorX = 4,
                                scissorY = 0,
                                scissorWidth = 2,
                                scissorHeight = 2,
                            ),
                        ),
                    )
                }

                val rgba = target.readRgba()
                val after = session.runtimeTelemetry

                assertContentEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()), pixelAt(rgba, width = 6, x = 0, y = 0))
                assertContentEquals(byteArrayOf(0, 0xFF.toByte(), 0, 0xFF.toByte()), pixelAt(rgba, width = 6, x = 2, y = 0))
                assertContentEquals(byteArrayOf(0, 0, 0xFF.toByte(), 0xFF.toByte()), pixelAt(rgba, width = 6, x = 4, y = 0))
                assertEquals(1L, after.uniformSlabsCreated - before.uniformSlabsCreated)
                assertEquals(768L, after.uniformSlabBytesAllocated - before.uniformSlabBytesAllocated)
                assertEquals(0L, after.uniformSlabFallbacks - before.uniformSlabFallbacks)
                assertEquals(1L, after.buffersCreated - before.buffersCreated)
                assertEquals(3L, after.queueWrites - before.queueWrites)
                assertEquals(3L, after.bindGroupsCreated - before.bindGroupsCreated)
            }
        }
    }
```

Add this helper near the other private helpers at the bottom of the same test class:

```kotlin
    private fun pixelAt(bytes: ByteArray, width: Int, x: Int, y: Int): ByteArray {
        val offset = ((y * width) + x) * 4
        return bytes.copyOfRange(offset, offset + 4)
    }
```

- [ ] **Step 2: Run the smoke test and verify it fails**

Run:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: FAIL because `uniformSlabsCreated`, `uniformSlabBytesAllocated`, and `uniformSlabFallbacks` remain zero and `buffersCreated` increases by three for the new test.

- [ ] **Step 3: Import slab contracts in the runtime**

In `GPUBackendRuntimeWgpu.kt`, add these imports near the other `resources` imports:

```kotlin
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabSlot
```

Add this constant near `MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT`:

```kotlin
private const val FULLSCREEN_UNIFORM_SLAB_UPLOAD_BUDGET_BYTES: Long = 1_048_576L
```

- [ ] **Step 4: Extend the runtime telemetry recorder**

In `WgpuBackendRuntimeTelemetryRecorder`, add fields:

```kotlin
    private var uniformSlabsCreated = 0L
    private var uniformSlabBytesAllocated = 0L
    private var uniformSlabFallbacks = 0L
```

Add methods before `snapshot()`:

```kotlin
    /** Records one successfully materialized uniform slab. */
    @Synchronized
    fun recordUniformSlabCreated(totalBytes: Long) {
        uniformSlabsCreated += 1L
        uniformSlabBytesAllocated += totalBytes
    }

    /** Records one explicit fallback to the previous per-draw uniform path. */
    @Synchronized
    fun recordUniformSlabFallback() {
        uniformSlabFallbacks += 1L
    }
```

Pass the fields into `GPUBackendRuntimeTelemetry` inside `snapshot()`:

```kotlin
            uniformSlabsCreated = uniformSlabsCreated,
            uniformSlabBytesAllocated = uniformSlabBytesAllocated,
            uniformSlabFallbacks = uniformSlabFallbacks,
```

- [ ] **Step 5: Pass device generation into `WgpuRenderRecorder`**

Add a constructor property to `WgpuRenderRecorder`:

```kotlin
    private val deviceGeneration: GPUDeviceGeneration,
```

In each `WgpuRenderRecorder(` call, add the local generation:

```kotlin
                    deviceGeneration = deviceGeneration,
```

There are three creation sites in this file: offscreen `encode`, `encodeOffscreenTextureInternal`, and window `encodeAndPresent`. Use the `deviceGeneration` value already available in each containing class.

- [ ] **Step 6: Add private slab materialization helpers**

Inside `WgpuRenderRecorder`, add these private data classes near `WgpuFullscreenUniformDraw`:

```kotlin
    private data class WgpuUniformSlabUpload(
        val slot: GPUUniformSlabSlot,
        val bytes: ByteArray,
    )

    private data class WgpuUniformSlabMaterialization(
        val plan: GPUUniformSlabPlan,
        val buffer: GPUBuffer,
        val uploads: List<WgpuUniformSlabUpload>,
    )
```

Add this helper before `recordFullscreenUniformPass`:

```kotlin
    private fun materializeFullscreenUniformSlab(
        draws: List<WgpuFullscreenUniformDraw>,
    ): WgpuUniformSlabMaterialization? {
        val payloadBytes = draws.mapIndexed { index, draw ->
            "fullscreen-uniform:$index" to draw.uniformPayload.toByteArray()
        }
        val planning = GPUUniformSlabPlanner.plan(
            sourceLabel = "gpu-backend.fullscreen-uniform-pass",
            deviceGeneration = deviceGeneration.value,
            alignmentBytes = MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT.toLong(),
            uploadBudgetBytes = FULLSCREEN_UNIFORM_SLAB_UPLOAD_BUDGET_BYTES,
            payloads = payloadBytes.map { (label, bytes) ->
                GPUUniformSlabPayload(slotLabel = label, bytes = bytes)
            },
        )
        val plan = when (planning) {
            is GPUUniformSlabPlanningResult.Accepted -> planning.plan
            is GPUUniformSlabPlanningResult.Refused -> {
                telemetryRecorder.recordUniformSlabFallback()
                return null
            }
        }
        val buffer = resourceScope.track(
            createTrackedBuffer(
                BufferDescriptor(
                    size = plan.totalBytes.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.fullscreen.uniformSlab",
                ),
            ),
        ) { it.close() }
        val uploads = plan.slots.zip(payloadBytes).map { (slot, payload) ->
            val bytes = payload.second
            writeTrackedBuffer(buffer, slot.alignedOffset.toULong(), ArrayBuffer.of(bytes))
            WgpuUniformSlabUpload(slot = slot, bytes = bytes)
        }
        telemetryRecorder.recordUniformSlabCreated(plan.totalBytes)
        return WgpuUniformSlabMaterialization(
            plan = plan,
            buffer = buffer,
            uploads = uploads,
        )
    }
```

- [ ] **Step 7: Route `recordFullscreenUniformPass` through the slab when accepted**

In `recordFullscreenUniformPass`, replace the current `draws.forEach { draw -> ... }` block after `setPipelineAction(pipeline)` with:

```kotlin
        val slab = materializeFullscreenUniformSlab(draws)
        if (slab != null) {
            draws.zip(slab.uploads).forEach { (draw, upload) ->
                val bindGroup = resourceScope.track(
                    createTrackedBindGroup(
                        BindGroupDescriptor(
                            layout = bindGroupLayout,
                            entries = listOf(
                                BindGroupEntry(
                                    binding = 0u,
                                    resource = BufferBinding(
                                        buffer = slab.buffer,
                                        offset = upload.slot.alignedOffset.toULong(),
                                        size = upload.slot.payloadBytes.toULong(),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ) { it.close() }

                setBindGroupAction(0u, bindGroup)
                setScissorAction(
                    draw.scissorX.toUInt(),
                    draw.scissorY.toUInt(),
                    draw.scissorWidth.toUInt(),
                    draw.scissorHeight.toUInt(),
                )
                drawAction(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
            }
            return
        }

        draws.forEach { draw ->
            val uniform = resourceScope.track(
                createTrackedBuffer(
                    BufferDescriptor(
                        size = draw.uniformSizeBytes,
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.rect.color",
                    ),
                ),
            ) { it.close() }
            writeTrackedBuffer(uniform, 0uL, draw.uniformPayload)
            val bindGroup = resourceScope.track(
                createTrackedBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }

            setBindGroupAction(0u, bindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            drawAction(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
        }
```

- [ ] **Step 8: Run the smoke test and verify it passes**

Run:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: PASS. If WebGPU is unavailable, JUnit assumptions should skip backend-dependent tests, not fail them.

- [ ] **Step 9: Commit Task 3**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt \
    gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt
rtk git commit -m "Prototype GPU uniform slab fullscreen route"
```

### Task 4: Focused Verification And Full-Suite Status

**Files:**
- No source files expected.
- Use test logs and git status as evidence.

- [ ] **Step 1: Run all focused tests for the slice**

Run:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlannerTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: PASS.

- [ ] **Step 2: Run the whitespace/diff check**

Run:

```bash
rtk git diff --check origin/master...HEAD
```

Expected: no output and exit code 0.

- [ ] **Step 3: Run the full gpu-renderer suite and record status**

Run:

```bash
rtk zsh -lc './gradlew --no-daemon :gpu-renderer:test > /tmp/kanvas-gpu-renderer-test-uniform-slab.log 2>&1; rc=$?; tail -n 160 /tmp/kanvas-gpu-renderer-test-uniform-slab.log; exit $rc'
```

Expected for the current branch context: the suite may still fail with the known 8 failures outside this slice. If the failure count or names change, stop and debug before creating or updating a PR.

- [ ] **Step 4: Extract failure names when the full suite is still red**

Run:

```bash
rtk zsh -lc 'grep -n "FAILED\\|AssertionFailedError\\|tests completed" /tmp/kanvas-gpu-renderer-test-uniform-slab.log | sed -n "1,120p"'
```

Expected: names should match the known outside-scope failures from PR #2002 unless those have been fixed on the base branch:

- `GPURendererLayoutSurfaceTest > main scaffold declarations are documented()`
- `GPURendererPackageBoundaryTest > gpu renderer production source satisfies package boundary rules()`
- `GPUColorGlyphReferenceParityTest > GPU color glyph output matches CPU pixel-exact reference within tolerance()`
- `GPUColorGlyphRenderSmokeTest > drawColorGlyphPass renders two-layer COLRv0 composite with red and blue layers()`
- `GPUSeparableBlurTest > quality tier HIGH sigma=10 tap count matches spec ceil sigma 3 2 plus 1()`
- `GaussianBlurFilterTest > small sigma produces one tap()`
- `BlendWgslBuilderImageDrawTest > buildWgsl with ImageDraw src includes texture sampler bindings()`
- `BlendWgslBuilderImageDrawTest > buildWgsl with ImageDraw dst includes texture sampler bindings()`

- [ ] **Step 5: Check git status for unrelated local files**

Run:

```bash
rtk git status --short --branch
```

Expected: source changes committed, branch ahead of remote, and the unrelated PNG may remain modified:

```text
 M integration-tests/skia/src/test/resources/generated-renders/composite/graphite-replay.png
```

Do not stage the PNG.

### Task 5: Review And PR Update

**Files:**
- No required source files.
- Optional PR body update through `gh`.

- [ ] **Step 1: Request code review for the implementation range**

Use `superpowers:requesting-code-review` after Task 4. Review the implementation range from the commit before Task 1 to the current HEAD. Include this context:

```text
Description: Adds backend-neutral GPU uniform slab planning and a limited fullscreen uniform pass runtime prototype.
Requirements: docs/superpowers/specs/2026-07-06-gpu-uniform-slab-prototype-design.md and docs/superpowers/plans/2026-07-06-gpu-uniform-slab-prototype-implementation.md.
Known suite status: focused tests pass; full :gpu-renderer:test may still have the known outside-scope failures listed in Task 4.
Review focus: offset/size correctness for BufferBinding, runtime telemetry success semantics, fallback behavior, dump safety, and unintended changes outside fullscreen uniform pass.
```

- [ ] **Step 2: Fix any Critical or Important review findings**

If the reviewer reports a valid issue, make the smallest targeted fix, rerun:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlannerTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
rtk git diff --check origin/master...HEAD
```

Commit the fix with a terse message:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/UniformSlabContracts.kt \
    gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUUniformSlabPlannerTest.kt \
    gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
    gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt \
    gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt \
    gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt
rtk git commit -m "Fix GPU uniform slab prototype review feedback"
```

- [ ] **Step 3: Push the branch**

Run:

```bash
rtk git push
```

Expected: the existing PR #2002 updates because the branch is already tracking `origin/codex/gpu-baseline-caps-design`.

- [ ] **Step 4: Update PR body validation notes**

Run:

```bash
rtk gh pr edit 2002 --body-file /tmp/kanvas-gpu-uniform-slab-pr-body.md
```

Before running the command, create `/tmp/kanvas-gpu-uniform-slab-pr-body.md` with a concise body that includes:

```markdown
## Summary
- Add GPU backend runtime telemetry and conservative capability limits.
- Add backend-neutral GPU uniform slab planning contracts.
- Prototype one fullscreen uniform-pass route that uses a single slab buffer for multiple uniform draws.

## Validation
- [x] Focused GPU runtime/capability/slab tests pass.
- [x] `rtk git diff --check origin/master...HEAD`
- [ ] `rtk ./gradlew --no-daemon :gpu-renderer:test` still has the documented outside-scope failures if they are still present.

## Notes
- This does not change shaders, pipeline keys, GM references, or broad resource routing.
- The unrelated local PNG `integration-tests/skia/src/test/resources/generated-renders/composite/graphite-replay.png` is not included.
```

- [ ] **Step 5: Final status**

Report:

- branch name;
- latest commit SHA;
- PR URL;
- focused test result;
- full-suite status;
- unrelated dirty PNG status.
