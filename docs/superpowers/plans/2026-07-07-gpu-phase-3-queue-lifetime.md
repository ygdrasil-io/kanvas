# GPU Phase 3 Queue Lifetime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finaliser la phase 3 GPU en gardant les ressources retenues entre `submit` et une vraie preuve de fin sur les routes offscreen/readback et fenetre/present.

**Architecture:** `GPUQueueManager` devient le carnet de suivi des soumissions. Le runtime offscreen laisse les soumissions pending jusqu'au readback, et la route fenetre marque la completion apres un `present` reussi. Les caches provider ne sont pas fermes a chaque frame ; la release est logique et visible dans les dumps.

**Tech Stack:** Kotlin/JVM, Gradle, `kotlin.test`, module `:gpu-renderer`, runtime GPU natif Kanvas, Skia GM scan pour smoke de non-regression.

---

## File Structure

### Modify

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`
  - Ajoute l'etat pending explicite, des reasons de completion stables, un compteur pending et des helpers pour les ids pending.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt`
  - Prouve pending -> completed -> released, release unique, waits, reasons dump-safe, completion inconnue.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
  - Branche la queue offscreen/readback et fenetre/present.
  - Ajoute un helper interne pour construire les refs retenues sans dupliquer les labels.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`
  - Adapte les assertions de queue.
  - Ajoute un smoke offscreen qui observe pending avant readback puis completed/released apres readback.

### Do Not Modify

- `refactor/`
  - Le dossier reste le rapport source. La spec phase 3 deja validee est dans `docs/superpowers/specs/2026-07-07-gpu-phase-3-queue-lifetime-design.md`.
- PNGs de GM
  - Ne pas regenerer ni committer des PNGs sans justification visuelle explicite.

---

## Task 1: Queue Manager State Model

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt`

- [ ] **Step 1: Replace queue manager tests with failing phase 3 expectations**

Replace `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt` with:

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUQueueManagerTest {
    @Test
    fun `queue manager retains lease resources while submission is pending`() {
        val manager = GPUQueueManager()
        val lease = queueLease("uniform-slab:frame-1")

        val submission = manager.submitLeases(
            label = "frame-1",
            retainedLeases = listOf(lease),
        )

        assertEquals(listOf(submission.id), manager.pendingSubmissionIds())
        assertEquals(listOf(GPUQueuedResourceRef("lease:uniform-slab:frame-1")), manager.retainedResources(submission.id))
        assertEquals(emptyList(), manager.releaseCompleted())

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("submitted=1 completed=0 released=0 pending=1 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=pending"))
    }

    @Test
    fun `queue manager releases resources once after readback completion`() {
        val manager = GPUQueueManager()
        val resource = GPUQueuedResourceRef("readback:frame-1")

        val submission = manager.submit(
            label = "offscreen-pass:frame-1",
            retainedResources = listOf(resource),
        )

        assertTrue(manager.markCompleted(submission.id, GPU_QUEUE_COMPLETION_READBACK_COMPLETE))
        assertEquals(listOf(resource), manager.releaseCompleted())
        assertEquals(emptyList(), manager.releaseCompleted())
        assertTrue(manager.retainedResources(submission.id).isEmpty())

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("submitted=1 completed=1 released=1 pending=0 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=readback-complete"))
    }

    @Test
    fun `queue manager records presented completion reason`() {
        val manager = GPUQueueManager()

        val submission = manager.submit(
            label = "window-frame:frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:window-1")),
        )

        assertTrue(manager.markCompleted(submission.id, GPU_QUEUE_COMPLETION_PRESENTED))
        assertEquals(listOf(GPUQueuedResourceRef("target:window-1")), manager.releaseCompleted())

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("label=window-frame:frame-1"))
        assertTrue(dump.contains("completion=presented"))
    }

    @Test
    fun `queue manager pending ids can be filtered by label prefix`() {
        val manager = GPUQueueManager()
        val first = manager.submit(
            label = "offscreen-pass:frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:offscreen-1")),
        )
        manager.submit(
            label = "window-frame:frame-2",
            retainedResources = listOf(GPUQueuedResourceRef("target:window-1")),
        )

        assertEquals(listOf(first.id), manager.pendingSubmissionIds(labelPrefix = "offscreen-pass:"))
    }

    @Test
    fun `queue manager telemetry records waits and unknown completions`() {
        val manager = GPUQueueManager()

        manager.recordWait()
        assertFalse(manager.markCompleted(GPUQueueSubmissionId(99), GPU_QUEUE_COMPLETION_READBACK_COMPLETE))

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("waits=1"))
        assertTrue(dump.contains("unknownCompletions=1"))
    }

    @Test
    fun `queue manager rejects unsafe completion reasons`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:frame-1")),
        )

        assertFailsWith<IllegalArgumentException> {
            manager.markCompleted(submission.id, "bad reason")
        }
    }

    @Test
    fun `queue manager dumps stay backend-neutral`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("readback:frame-1")),
        )

        assertTrue(manager.markCompleted(submission.id, GPU_QUEUE_COMPLETION_READBACK_COMPLETE))
        assertEquals(listOf(GPUQueuedResourceRef("readback:frame-1")), manager.releaseCompleted())

        val dump = manager.telemetry.dumpLines().joinToString("\n")

        assertFalse(dump.contains("@"))
        assertFalse(dump.contains("0x"))
        assertFalse(dump.contains("W" + "GPU"))
        assertFalse(dump.contains("w" + "gpu"))
    }
}

private fun queueLease(leaseId: String): GPUResourceLease =
    GPUResourceLease(
        leaseId = leaseId,
        resourceKind = GPUResourceLeaseKind.UniformSlab,
        deviceGeneration = 11,
        descriptorHash = "sha256:uniform-slab-frame-1",
        ownerScope = "frame-1",
        usageLabels = listOf("copy_dst", "uniform"),
        releasePolicy = "submission-complete",
        cacheResult = GPUResourceLeaseCacheResult.Create,
    )
```

- [ ] **Step 2: Run queue tests and verify failure**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
```

Expected: FAIL because `pendingSubmissionIds`, `GPU_QUEUE_COMPLETION_READBACK_COMPLETE`, and `GPU_QUEUE_COMPLETION_PRESENTED` do not exist yet, and telemetry does not include `pending=`.

- [ ] **Step 3: Replace `GPUQueueManager.kt` with the phase 3 model**

Replace `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt` with:

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease

@JvmInline
value class GPUQueueSubmissionId(val value: Long) {
    init {
        require(value > 0L) { "GPUQueueSubmissionId.value must be positive" }
    }
}

@JvmInline
value class GPUQueuedResourceRef(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUQueuedResourceRef.value must not be blank" }
        require(value.isQueueDumpSafeToken()) {
            "GPUQueuedResourceRef.value must use dump-safe GPU evidence labels"
        }
    }
}

data class GPUQueueSubmission(
    val id: GPUQueueSubmissionId,
    val label: String,
    val retainedResources: List<GPUQueuedResourceRef>,
    val completed: Boolean = false,
    val released: Boolean = false,
    val completion: String = GPU_QUEUE_COMPLETION_PENDING,
) {
    init {
        require(label.isNotBlank()) { "GPUQueueSubmission.label must not be blank" }
        require(label.isQueueDumpSafeToken()) { "GPUQueueSubmission.label must be dump-safe" }
        require(completion.isQueueDumpSafeToken()) { "GPUQueueSubmission.completion must be dump-safe" }
    }
}

data class GPUQueueTelemetry(
    val submitted: Long = 0L,
    val completed: Long = 0L,
    val released: Long = 0L,
    val pending: Long = 0L,
    val waits: Long = 0L,
    val unknownCompletions: Long = 0L,
    val submissions: List<GPUQueueSubmission> = emptyList(),
) {
    fun dumpLines(): List<String> =
        listOf(
            "gpu-queue.telemetry submitted=$submitted completed=$completed released=$released " +
                "pending=$pending waits=$waits unknownCompletions=$unknownCompletions",
        ) + submissions.map { submission ->
            "gpu-queue.submission id=${submission.id.value} label=${submission.label} " +
                "retained=${submission.retainedResources.size} completed=${submission.completed} " +
                "released=${submission.released} completion=${submission.completion}"
        }
}

class GPUQueueManager {
    private var nextSubmissionId: Long = 1L
    private val submissions = linkedMapOf<GPUQueueSubmissionId, GPUQueueSubmission>()
    private var waitCount: Long = 0L
    private var unknownCompletionCount: Long = 0L

    val telemetry: GPUQueueTelemetry
        get() {
            val orderedSubmissions = submissions.values.toList()
            return GPUQueueTelemetry(
                submitted = orderedSubmissions.size.toLong(),
                completed = orderedSubmissions.count { submission -> submission.completed }.toLong(),
                released = orderedSubmissions.count { submission -> submission.released }.toLong(),
                pending = orderedSubmissions.count { submission -> !submission.completed }.toLong(),
                waits = waitCount,
                unknownCompletions = unknownCompletionCount,
                submissions = orderedSubmissions,
            )
        }

    fun submit(
        label: String,
        retainedResources: List<GPUQueuedResourceRef>,
    ): GPUQueueSubmission {
        val submission = GPUQueueSubmission(
            id = GPUQueueSubmissionId(nextSubmissionId++),
            label = label,
            retainedResources = retainedResources.toList(),
        )
        submissions[submission.id] = submission
        return submission
    }

    fun submitLeases(
        label: String,
        retainedLeases: List<GPUResourceLease>,
    ): GPUQueueSubmission =
        submit(
            label = label,
            retainedResources = retainedLeases.map { lease ->
                GPUQueuedResourceRef("lease:${lease.leaseId}")
            },
        )

    fun markCompleted(
        id: GPUQueueSubmissionId,
        completion: String,
    ): Boolean {
        require(completion.isQueueDumpSafeToken()) { "completion must be dump-safe" }
        val current = submissions[id]
        if (current == null) {
            unknownCompletionCount += 1L
            return false
        }
        if (!current.completed) {
            submissions[id] = current.copy(completed = true, completion = completion)
        }
        return true
    }

    fun pendingSubmissionIds(labelPrefix: String? = null): List<GPUQueueSubmissionId> {
        labelPrefix?.let { prefix ->
            require(prefix.isQueueDumpSafeToken()) { "labelPrefix must be dump-safe" }
        }
        return submissions.values
            .asSequence()
            .filter { submission -> !submission.completed }
            .filter { submission -> labelPrefix == null || submission.label.startsWith(labelPrefix) }
            .map { submission -> submission.id }
            .toList()
    }

    fun retainedResources(id: GPUQueueSubmissionId): List<GPUQueuedResourceRef> =
        submissions[id]
            ?.takeUnless { submission -> submission.released }
            ?.retainedResources
            .orEmpty()

    fun releaseCompleted(): List<GPUQueuedResourceRef> {
        val releasedResources = mutableListOf<GPUQueuedResourceRef>()
        submissions.entries.forEach { (id, submission) ->
            if (submission.completed && !submission.released) {
                releasedResources += submission.retainedResources
                submissions[id] = submission.copy(released = true)
            }
        }
        return releasedResources
    }

    fun recordWait() {
        waitCount += 1L
    }
}

private fun String.isQueueDumpSafeToken(): Boolean =
    isNotBlank() &&
        matches(QUEUE_DUMP_SAFE_LABEL_PATTERN) &&
        !QUEUE_RAW_HANDLE_DUMP_PATTERN.containsMatchIn(this) &&
        '@' !in this

private val QUEUE_DUMP_SAFE_LABEL_PATTERN = Regex("^[A-Za-z0-9._:-]+$")
private val QUEUE_RAW_BACKEND_TOKEN = "w" + "gpu"
private val QUEUE_RAW_HANDLE_DUMP_PATTERN =
    Regex("(?i)($QUEUE_RAW_BACKEND_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle|@0x[0-9a-f]+|0x[0-9a-f]{6,})")

internal const val GPU_QUEUE_COMPLETION_PENDING = "pending"
internal const val GPU_QUEUE_COMPLETION_READBACK_COMPLETE = "readback-complete"
internal const val GPU_QUEUE_COMPLETION_PRESENTED = "presented"
internal const val GPU_QUEUE_COMPLETION_TARGET_CLOSE = "target-close"
```

- [ ] **Step 4: Run queue tests and verify pass**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
```

Expected: PASS.

- [ ] **Step 5: Commit queue manager model**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt
rtk git commit -m "Finalize GPU queue submission states"
```

Expected: commit created.

---

## Task 2: Offscreen Readback Completion

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

- [ ] **Step 1: Add failing offscreen pending/readback smoke test**

Insert this test in `GPUBackendRuntimeNativeSmokeTest` after `backend runtime offscreen encode and read rgba when backend is available`:

```kotlin
    @Test
    fun `offscreen submission stays pending until readback completes when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
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
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }

                val pendingDump = session.phase0EvidenceDumpLines.joinToString("\n")
                assertTrue(
                    pendingDump.contains(
                        "gpu-queue.telemetry submitted=1 completed=0 released=0 pending=1 waits=0 unknownCompletions=0",
                    ),
                )
                assertTrue(pendingDump.contains("completion=pending"))

                val rgba = target.readRgba()
                assertContentEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()), rgba.copyOfRange(0, 4))

                val completedDump = session.phase0EvidenceDumpLines.joinToString("\n")
                assertTrue(
                    completedDump.contains(
                        "gpu-queue.telemetry submitted=1 completed=1 released=1 pending=0 waits=1 unknownCompletions=0",
                    ),
                )
                assertTrue(completedDump.contains("completion=readback-complete"))
            }
        }
    }
```

- [ ] **Step 2: Update existing queue evidence smoke expectations**

In `GPUBackendRuntimeNativeSmokeTest`, update `fullscreen uniform path exposes provider cache evidence when runtime is available`.

Replace:

```kotlin
                    "gpu-queue.telemetry submitted=1 completed=1 released=1 waits=1 unknownCompletions=0",
```

with:

```kotlin
                    "gpu-queue.telemetry submitted=1 completed=1 released=1 pending=0 waits=1 unknownCompletions=0",
```

Replace:

```kotlin
            assertTrue(evidenceDump.contains("retained=3"))
            assertTrue(evidenceDump.contains("completion=scaffold-immediate"))
```

with:

```kotlin
            assertTrue(evidenceDump.contains("retained=4"))
            assertTrue(evidenceDump.contains("completion=readback-complete"))
```

- [ ] **Step 3: Run native smoke test and verify failure**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: FAIL because runtime still marks offscreen submissions completed immediately and retained count is still 3.

- [ ] **Step 4: Add retained-resource helper to native runtime**

In `GPUBackendRuntimeNative.kt`, add this helper near the other internal helpers, before `private const val MAX_TEXTURE_DIMENSION`:

```kotlin
internal fun gpuRuntimeRetainedResourceRefs(
    targetRef: GPUQueuedResourceRef,
    leases: List<GPUResourceLease>,
    extraRefs: List<GPUQueuedResourceRef> = emptyList(),
): List<GPUQueuedResourceRef> =
    listOf(targetRef) + extraRefs + leases.map { lease ->
        GPUQueuedResourceRef("lease:${lease.leaseId}")
    }
```

- [ ] **Step 5: Track pending offscreen submissions**

Inside the private offscreen target class in `GPUBackendRuntimeNative.kt`, add this field after `textureFrameOrdinalCounter`:

```kotlin
    private val pendingReadbackSubmissionIds = ArrayDeque<GPUQueueSubmissionId>()
```

Add these helper methods inside the same class, before `override fun encode`:

```kotlin
    private fun retainPendingReadbackSubmission(submission: GPUQueueSubmission) {
        pendingReadbackSubmissionIds.addLast(submission.id)
    }

    private fun completePendingReadbackSubmissions(completion: String) {
        while (pendingReadbackSubmissionIds.isNotEmpty()) {
            queueManager.markCompleted(
                id = pendingReadbackSubmissionIds.removeFirst(),
                completion = completion,
            )
        }
        queueManager.releaseCompleted()
    }
```

- [ ] **Step 6: Leave primary offscreen encode pending until readback**

In `GPUBackendRuntimeNative.kt`, inside primary offscreen `encode`, replace the queue submission block:

```kotlin
            val submission = queueManager.submit(
                label = "offscreen-pass:$frameId",
                retainedResources = listOf(GPUQueuedResourceRef("target:${target.targetId}")) +
                    frameResourceLeases.map { lease -> GPUQueuedResourceRef("lease:${lease.leaseId}") },
            )
            recordRuntimeResourceLeasesAction(frameResourceLeases)
            queueManager.markCompleted(submission.id)
            queueManager.releaseCompleted()
            telemetryRecorder.recordSubmission()
            telemetryRecorder.recordOffscreenRenderPass()
```

with:

```kotlin
            val submission = queueManager.submit(
                label = "offscreen-pass:$frameId",
                retainedResources = gpuRuntimeRetainedResourceRefs(
                    targetRef = GPUQueuedResourceRef("target:${target.targetId}"),
                    leases = frameResourceLeases,
                    extraRefs = listOf(GPUQueuedResourceRef("readback:$frameId")),
                ),
            )
            recordRuntimeResourceLeasesAction(frameResourceLeases)
            retainPendingReadbackSubmission(submission)
            telemetryRecorder.recordSubmission()
            telemetryRecorder.recordOffscreenRenderPass()
```

- [ ] **Step 7: Leave offscreen texture encode pending until readback or target close**

In `GPUBackendRuntimeNative.kt`, inside `encodeOffscreenTextureInternal`, replace the queue submission block:

```kotlin
        val submission = queueManager.submit(
            label = "offscreen-texture-pass:$frameId",
            retainedResources = listOf(GPUQueuedResourceRef("target:${target.targetId}:$textureLabel")) +
                frameResourceLeases.map { lease -> GPUQueuedResourceRef("lease:${lease.leaseId}") },
        )
        recordRuntimeResourceLeasesAction(frameResourceLeases)
        queueManager.markCompleted(submission.id)
        queueManager.releaseCompleted()
        telemetryRecorder.recordSubmission()
        telemetryRecorder.recordOffscreenRenderPass()
```

with:

```kotlin
        val submission = queueManager.submit(
            label = "offscreen-texture-pass:$frameId",
            retainedResources = gpuRuntimeRetainedResourceRefs(
                targetRef = GPUQueuedResourceRef("target:${target.targetId}:$textureLabel"),
                leases = frameResourceLeases,
            ),
        )
        recordRuntimeResourceLeasesAction(frameResourceLeases)
        retainPendingReadbackSubmission(submission)
        telemetryRecorder.recordSubmission()
        telemetryRecorder.recordOffscreenRenderPass()
```

- [ ] **Step 8: Complete offscreen submissions after readback wait**

In `GPUBackendRuntimeNative.kt`, inside `readRgba`, replace the `finally` block:

```kotlin
        } finally {
            stagingBuffer.unmap()
        }
```

with:

```kotlin
        } finally {
            stagingBuffer.unmap()
            completePendingReadbackSubmissions(GPU_QUEUE_COMPLETION_READBACK_COMPLETE)
        }
```

- [ ] **Step 9: Complete pending offscreen submissions on target close**

At the start of the offscreen target `close()` method, before `var firstFailure: Throwable? = null`, add:

```kotlin
        completePendingReadbackSubmissions(GPU_QUEUE_COMPLETION_TARGET_CLOSE)
```

- [ ] **Step 10: Run native smoke test and verify pass**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: PASS, or individual backend-dependent tests skipped with the existing "GPU backend unavailable" assumption.

- [ ] **Step 11: Commit offscreen readback lifetime**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt
rtk git commit -m "Complete GPU offscreen submissions on readback"
```

Expected: commit created.

---

## Task 3: Window Present Completion

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

- [ ] **Step 1: Add helper test for retained refs used by window and offscreen routes**

Insert this test in `GPUBackendRuntimeNativeSmokeTest` after `offscreen target helper derives deterministic unique target id per session and target`:

```kotlin
    @Test
    fun `runtime retained resource refs include target extras and leases`() {
        val refs = gpuRuntimeRetainedResourceRefs(
            targetRef = GPUQueuedResourceRef("target:window-frame-1"),
            leases = listOf(
                GPUResourceLease(
                    leaseId = "uniform-slab:frame-1",
                    resourceKind = GPUResourceLeaseKind.UniformSlab,
                    deviceGeneration = 11,
                    descriptorHash = "sha256:uniform-slab-frame-1",
                    ownerScope = "frame-1",
                    usageLabels = listOf("copy_dst", "uniform"),
                    releasePolicy = "submission-complete",
                    cacheResult = GPUResourceLeaseCacheResult.Create,
                ),
            ),
            extraRefs = listOf(GPUQueuedResourceRef("readback:frame-1")),
        )

        assertEquals(
            listOf(
                GPUQueuedResourceRef("target:window-frame-1"),
                GPUQueuedResourceRef("readback:frame-1"),
                GPUQueuedResourceRef("lease:uniform-slab:frame-1"),
            ),
            refs,
        )
    }
```

Add these imports to `GPUBackendRuntimeNativeSmokeTest`:

```kotlin
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
```

- [ ] **Step 2: Run helper test and verify pass**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: PASS if Task 2 is complete. The helper was added in Task 2, so this test should pass.

- [ ] **Step 3: Pass the session queue manager into the window surface**

In `GPUBackendRuntimeNative.kt`, update `createWindowSurface` in the backend session.

Find the window surface factory call that currently passes `binding`, `capabilities`, and
`telemetryRecorder`. Add these two named arguments before the closing parenthesis:

```kotlin
            queueManager = queueManager,
            recordRuntimeResourceLeasesAction = { leases -> recordRuntimeResourceLeases(leases) },
```

Update the private window surface constructor by adding these parameters immediately after
the existing `private val telemetryRecorder` constructor parameter:

```kotlin
    private val queueManager: GPUQueueManager,
    private val recordRuntimeResourceLeasesAction: (List<GPUResourceLease>) -> Unit,
```

Do not rename the existing private class in this task.

- [ ] **Step 4: Record frame leases during window encoding**

Inside `encodeAndPresent`, immediately after `val frameOrdinal = frameOrdinalCounter.incrementAndGet()`, add:

```kotlin
        val frameId = "window-$windowRuntimeOrdinal-frame-$targetGeneration-$frameOrdinal"
        val frameResourceLeases = mutableListOf<GPUResourceLease>()
```

In the render recorder call inside `encodeAndPresent`, replace:

```kotlin
                    frameId = "window-$windowRuntimeOrdinal-frame-$targetGeneration-$frameOrdinal",
```

with:

```kotlin
                    frameId = frameId,
```

Replace:

```kotlin
                    recordResourceLeasesAction = { leases -> lastFrameResourceLeases = leases },
```

with:

```kotlin
                    recordResourceLeasesAction = { leases ->
                        frameResourceLeases += leases
                        lastFrameResourceLeases = frameResourceLeases.toList()
                    },
```

- [ ] **Step 5: Submit window frames through `GPUQueueManager` and complete after present**

In `encodeAndPresent`, replace:

```kotlin
            runtime.device.queue.submit(listOf(commandBuffer))
            telemetryRecorder.recordSubmission()
            runtime.surface.present()
            telemetryRecorder.recordWindowRenderPass()
```

with:

```kotlin
            runtime.device.queue.submit(listOf(commandBuffer))
            val submission = queueManager.submit(
                label = "window-frame:$frameId",
                retainedResources = gpuRuntimeRetainedResourceRefs(
                    targetRef = GPUQueuedResourceRef("target:$targetId"),
                    leases = frameResourceLeases,
                ),
            )
            recordRuntimeResourceLeasesAction(frameResourceLeases)
            telemetryRecorder.recordSubmission()
            runtime.surface.present()
            queueManager.markCompleted(submission.id, GPU_QUEUE_COMPLETION_PRESENTED)
            queueManager.releaseCompleted()
            telemetryRecorder.recordWindowRenderPass()
```

- [ ] **Step 6: Run native smoke test**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: PASS, or backend-dependent tests skipped with the existing assumption.

- [ ] **Step 7: Run queue tests again**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
```

Expected: PASS.

- [ ] **Step 8: Commit window queue integration**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt
rtk git commit -m "Track GPU window submissions through queue"
```

Expected: commit created.

---

## Task 4: Verification And Wording Audit

**Files:**
- No code edits expected.
- Verify all files changed by Tasks 1-3.

- [ ] **Step 1: Run focused tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: both PASS, with backend-dependent tests skipped only when the existing assumption says the GPU backend is unavailable.

- [ ] **Step 2: Run full GPU renderer tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test
```

Expected: PASS.

- [ ] **Step 3: Run Skia scan smoke**

Run:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaScan --args='--from 0 --to 8 --timeout 20'
```

Expected: PASS for the scan task with no timeout. The support percentage must not regress in a way that indicates a rendering regression.

- [ ] **Step 4: Check whitespace**

Run:

```bash
rtk git diff --check
```

Expected: no output.

- [ ] **Step 5: Audit added public wording**

Run:

```bash
rtk git diff -U0 HEAD~3..HEAD | rtk rg '^\\+.*(W''GPU|W''gpu|w''gpu|Web''GPU)'
```

Expected: no output. Exact existing imports or package names should not appear as added public wording. If this command reports a new public label, dump, test name, comment, or doc sentence, replace the wording with `GPU`.

- [ ] **Step 6: Review changed files**

Run:

```bash
rtk git diff --stat HEAD~3..HEAD
rtk git diff --name-status HEAD~3..HEAD
```

Expected: only these files changed:

```text
gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt
gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt
gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt
gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt
```

- [ ] **Step 7: Commit verification note only if files changed during audit**

If Step 4 or Step 5 required edits, run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt
rtk git commit -m "Polish GPU queue lifetime evidence"
```

Expected: commit created only if audit edits were necessary.

---

## Task 5: PR Readiness Summary

**Files:**
- No code edits expected.

- [ ] **Step 1: Capture final status**

Run:

```bash
rtk git status -sb
rtk git log --oneline -n 6
```

Expected: working tree clean except for intentionally ignored local files, and the latest commits are the phase 3 commits.

- [ ] **Step 2: Prepare PR summary**

Use this summary:

```markdown
## Summary

- finalizes GPU queue submissions with explicit pending/completed/released states
- completes offscreen submissions after readback instead of immediately after submit
- routes window present submissions through GPUQueueManager with `presented` completion evidence

## Tests

- `rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest`
- `rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest`
- `rtk ./gradlew :gpu-renderer:test`
- `rtk ./gradlew :integration-tests:skia:generateSkiaScan --args='--from 0 --to 8 --timeout 20'`
- `rtk git diff --check`
```

Expected: summary accurately matches the commits and test results.
