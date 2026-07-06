# GPU Phase 0b Baseline Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the GPU Phase 0 drift by adding command buffer telemetry and a local Phase 0 baseline report.

**Architecture:** Extend the existing `GPUBackendRuntimeTelemetry` contract and existing GPU runtime telemetry recorder. Keep the instrumentation passive, keep public wording GPU-neutral, and document dashboard/GM aggregation as a named follow-up rather than an implicit Phase 0 requirement.

**Tech Stack:** Kotlin/JVM, Gradle, kotlin.test, existing `gpu-renderer` module, existing GPU runtime contracts and smoke tests.

---

## Scope Notes

Do not touch unrelated dirty files. At plan creation time these local files were dirty and out of scope:

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUSeparableBlur.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BlendWgslBuilder.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererLayoutSurfaceTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphReferenceParityTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphRenderSmokeTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUSeparableBlurTest.kt`
- `integration-tests/skia/src/test/resources/generated-renders/composite/graphite-replay.png`

Public wording rule for this plan:

- Use "GPU" in new public text, dump text, report text, test names, and docs.
- Do not introduce new public wording that names the concrete implementation.
- Existing file paths and existing class names are not renamed in this slice.

## File Structure

- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
  - Add `commandBuffers` to `GPUBackendRuntimeTelemetry`.
  - Validate it as non-negative.
  - Include it in `dumpLines()`.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt`
  - Add default, dump, and negative-value coverage for `commandBuffers`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`
  - Add a private command buffer counter to the existing telemetry recorder.
  - Increment it after successful command buffer finalization.
  - Include it in runtime telemetry snapshots.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt`
  - Assert a simple GPU scene increases `commandBuffers` with submissions.
  - Assert the runtime dump includes `commandBuffers=`.
- Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPhase0BaselineReportTest.kt`
  - Verify the local Phase 0 report exists and stays wording-safe.
- Create `reports/gpu-renderer/phase-0-baseline.md`
  - Record Phase 0 local baseline closure and explicit dashboard/GM follow-ups.

## Task 1: Add Command Buffer Telemetry Contract

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt`

- [ ] **Step 1: Write failing contract assertions**

In `GPUBackendRuntimeContractsTest.kt`, update `runtime telemetry defaults to zero counters and deterministic dump`.

Add this assertion after the existing `submissions` assertion:

```kotlin
        assertEquals(0L, telemetry.commandBuffers)
```

Update the expected dump string in the same test to include `commandBuffers=0` immediately after `submissions=0`:

```kotlin
        assertEquals(
            listOf(
                "gpu-runtime.telemetry renderPasses=0 offscreenPasses=0 windowPasses=0 " +
                    "submissions=0 commandBuffers=0 buffersCreated=0 texturesCreated=0 " +
                    "bindGroupsCreated=0 samplersCreated=0 queueWrites=0 uniformSlabsCreated=0 " +
                    "uniformSlabBytesAllocated=0 uniformSlabFallbacks=0",
            ),
            telemetry.dumpLines(),
        )
```

In `runtime telemetry rejects negative counters`, add this assertion after the existing `queueWrites` assertion:

```kotlin
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRuntimeTelemetry(commandBuffers = -1L)
        }
```

- [ ] **Step 2: Run the contract test to verify RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest
```

Expected: compile failure mentioning unresolved `commandBuffers` on `GPUBackendRuntimeTelemetry`.

- [ ] **Step 3: Add the `commandBuffers` contract field**

In `GPUBackendRuntimeContracts.kt`, add `commandBuffers` after `submissions`:

```kotlin
data class GPUBackendRuntimeTelemetry(
    val renderPasses: Long = 0L,
    val offscreenPasses: Long = 0L,
    val windowPasses: Long = 0L,
    val submissions: Long = 0L,
    val commandBuffers: Long = 0L,
    val buffersCreated: Long = 0L,
    val texturesCreated: Long = 0L,
    val bindGroupsCreated: Long = 0L,
    val samplersCreated: Long = 0L,
    val queueWrites: Long = 0L,
    val uniformSlabsCreated: Long = 0L,
    val uniformSlabBytesAllocated: Long = 0L,
    val uniformSlabFallbacks: Long = 0L,
)
```

In the `init` block, add the non-negative validation immediately after `submissions`:

```kotlin
        require(commandBuffers >= 0L) { "GPUBackendRuntimeTelemetry.commandBuffers must be non-negative" }
```

Update `dumpLines()` so `commandBuffers` appears immediately after `submissions`:

```kotlin
    fun dumpLines(): List<String> =
        listOf(
            "gpu-runtime.telemetry renderPasses=$renderPasses offscreenPasses=$offscreenPasses " +
                "windowPasses=$windowPasses submissions=$submissions commandBuffers=$commandBuffers " +
                "buffersCreated=$buffersCreated texturesCreated=$texturesCreated " +
                "bindGroupsCreated=$bindGroupsCreated samplersCreated=$samplersCreated " +
                "queueWrites=$queueWrites uniformSlabsCreated=$uniformSlabsCreated " +
                "uniformSlabBytesAllocated=$uniformSlabBytesAllocated " +
                "uniformSlabFallbacks=$uniformSlabFallbacks",
        )
```

- [ ] **Step 4: Run the contract test to verify GREEN**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Task 1**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt
rtk git commit -m "Add GPU command buffer telemetry contract"
```

## Task 2: Instrument Command Buffer Finalization

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt`

- [ ] **Step 1: Write failing smoke assertions**

In `GPUBackendRuntimeWgpuSmokeTest.kt`, update `backend runtime records GPU runtime telemetry when backend is available`.

After:

```kotlin
            val after = session.runtimeTelemetry
            val dump = session.runtimeTelemetryDumpLines.joinToString("\n")
```

add:

```kotlin
            val submissionDelta = after.submissions - before.submissions
            val commandBufferDelta = after.commandBuffers - before.commandBuffers
```

After the existing assertion:

```kotlin
            assertTrue(after.submissions - before.submissions >= 2L)
```

add:

```kotlin
            assertTrue(commandBufferDelta >= 2L)
            assertTrue(commandBufferDelta >= submissionDelta)
```

After:

```kotlin
            assertTrue(dump.contains("gpu-runtime.telemetry"))
```

add:

```kotlin
            assertTrue(dump.contains("commandBuffers="))
```

- [ ] **Step 2: Run the smoke test to verify RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: the runtime telemetry smoke test fails because `commandBufferDelta` is `0`.

If the local environment skips GPU runtime tests because no GPU backend is available, run this compile-focused command and report the skip explicitly:

```bash
rtk ./gradlew :gpu-renderer:compileTestKotlin
```

- [ ] **Step 3: Add command buffer counting to the telemetry recorder**

In `GPUBackendRuntimeWgpu.kt`, add a private counter to `WgpuBackendRuntimeTelemetryRecorder` immediately after `submissions`:

```kotlin
    private var commandBuffers = 0L
```

Add this method immediately after `recordSubmission()`:

```kotlin
    /** Records one successfully-finished GPU command buffer. */
    @Synchronized
    fun recordCommandBufferFinished() {
        commandBuffers += 1L
    }
```

Update `snapshot()` to pass `commandBuffers`:

```kotlin
            submissions = submissions,
            commandBuffers = commandBuffers,
            buffersCreated = buffersCreated,
```

- [ ] **Step 4: Increment after successful command buffer finalization**

In each existing encode path, call `recordCommandBufferFinished()` immediately after `encoder.finish()` succeeds and before queue submission.

First location, offscreen texture encoding:

```kotlin
            val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
            telemetryRecorder.recordCommandBufferFinished()
            queue.submit(listOf(commandBuffer))
            telemetryRecorder.recordSubmission()
```

Second location, offscreen target encoding:

```kotlin
        val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
        telemetryRecorder.recordCommandBufferFinished()
        queue.submit(listOf(commandBuffer))
        telemetryRecorder.recordSubmission()
```

Third location, window presentation encoding:

```kotlin
            val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
            telemetryRecorder.recordCommandBufferFinished()
            runtime.device.queue.submit(listOf(commandBuffer))
            telemetryRecorder.recordSubmission()
```

- [ ] **Step 5: Run the smoke test to verify GREEN**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt
rtk git commit -m "Record GPU command buffer telemetry"
```

## Task 3: Add Phase 0 Baseline Report

**Files:**
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPhase0BaselineReportTest.kt`
- Create: `reports/gpu-renderer/phase-0-baseline.md`

- [ ] **Step 1: Write the failing report test**

Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPhase0BaselineReportTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class GPUPhase0BaselineReportTest {
    @Test
    fun `phase 0 baseline report documents local closure without implementation wording`() {
        val report = Path.of("reports/gpu-renderer/phase-0-baseline.md")

        assertTrue(Files.isRegularFile(report), "Phase 0 baseline report must exist")

        val text = Files.readString(report)

        assertTrue(text.contains("Phase 0 baseline locale close"))
        assertTrue(text.contains("command buffers"))
        assertTrue(text.contains("render passes"))
        assertTrue(text.contains("submissions"))
        assertTrue(text.contains("buffers/textures/samplers/bind groups"))
        assertTrue(text.contains("queue writes"))
        assertTrue(text.contains("uniform slab counters"))
        assertTrue(text.contains("maxTextureDimension2D"))
        assertTrue(text.contains("copyBytesPerRowAlignment"))
        assertTrue(text.contains("minUniformBufferOffsetAlignment"))
        assertTrue(text.contains("aggregation par GM"))
        assertTrue(text.contains("integration dashboard GM"))
        assertTrue(text.contains("rapport par famille"))
        assertTrue(!text.contains("@"))
        assertTrue(!text.contains("0x"))
        assertTrue(!text.contains("WGPU"))
        assertTrue(!text.contains("wgpu"))
        assertTrue(!text.contains("Metal"))
    }
}
```

- [ ] **Step 2: Run the report test to verify RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUPhase0BaselineReportTest
```

Expected: failure with message `Phase 0 baseline report must exist`.

- [ ] **Step 3: Add the local Phase 0 report**

Create `reports/gpu-renderer/phase-0-baseline.md`:

```markdown
# GPU Phase 0 baseline

Statut: Phase 0 baseline locale close.

Cette baseline locale ferme le socle d'instrumentation GPU avant les phases de
ressources, lifetime et batching. Elle ne change pas le rendu et ne promet pas
de resultat dashboard par famille.

## Compteurs disponibles

- render passes
- offscreen/window passes
- submissions
- command buffers
- buffers/textures/samplers/bind groups
- queue writes
- uniform slab counters

## Limits GPU disponibles

- `maxTextureDimension2D`
- `copyBytesPerRowAlignment`
- `minUniformBufferOffsetAlignment`

Les limits peuvent etre conservatrices quand le runtime ne fournit pas une
source observee fiable. La source du fact doit rester explicite dans les dumps
de capabilities.

## Preuve de validation

La validation ciblee couvre:

- tests de contrats runtime
- tests de capabilities
- smoke runtime GPU
- test du rapport Phase 0

Le test complet du module peut encore echouer sur le package-boundary connu.
Cet echec n'est pas un critere Phase 0b tant qu'aucun nouvel echec n'apparait.

## Hors Phase 0b

Ces points restent des follow-ups nommes:

- aggregation par GM
- integration dashboard GM
- rapport par famille

La Phase 0b ferme la baseline locale. Les follow-ups ci-dessus ne doivent pas
etre traites comme des criteres implicites de cette tranche.
```

- [ ] **Step 4: Run the report test to verify GREEN**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUPhase0BaselineReportTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Task 3**

Run:

```bash
rtk git add gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPhase0BaselineReportTest.kt \
  reports/gpu-renderer/phase-0-baseline.md
rtk git commit -m "Document GPU phase 0 baseline closure"
```

## Task 4: Final Verification And PR Update

**Files:**
- No code changes expected unless verification exposes a bug in the previous tasks.

- [ ] **Step 1: Run focused verification**

Run:

```bash
rtk ./gradlew :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest \
  --tests org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityContractsTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUPhase0BaselineReportTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run whitespace and wording checks**

Run:

```bash
rtk git diff --check -- \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpuSmokeTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPhase0BaselineReportTest.kt \
  reports/gpu-renderer/phase-0-baseline.md
```

Expected: no output and exit code 0.

Run:

```bash
rtk rg -n "WGPU|wgpu|Metal|0x|@" reports/gpu-renderer/phase-0-baseline.md
```

Expected: no output and exit code 1.

- [ ] **Step 3: Run full module test and document status**

Run:

```bash
rtk ./gradlew :gpu-renderer:test
```

Expected current known status: this may fail only on:

```text
GPURendererPackageBoundaryTest > gpu renderer production source satisfies package boundary rules
```

The known failure mentions package-boundary violations in the existing GPU runtime file, `GradientWgslShaderProvider.kt`, `WgslReflection.kt`, and existing package cycles. If any additional test fails, stop and debug before pushing.

- [ ] **Step 4: Inspect git status for unrelated dirty files**

Run:

```bash
rtk git status --short --branch
```

Expected: implementation files for this plan are committed. Unrelated dirty files listed in the Scope Notes may still be present and must not be staged or reverted.

- [ ] **Step 5: Push the PR branch**

Run:

```bash
rtk git push origin codex/gpu-baseline-caps-design
```

Expected: push succeeds and updates the existing PR branch.

## Self-Review Checklist

- Spec coverage:
  - `commandBuffers` contract: Task 1.
  - Runtime command buffer instrumentation: Task 2.
  - Local Phase 0 report: Task 3.
  - Verification and push: Task 4.
- Wording:
  - New public report/test wording uses GPU-neutral terms.
  - New report does not name the concrete implementation.
  - Existing exact file paths are not renamed in this slice.
- Type consistency:
  - `commandBuffers` appears in the data class, validation, dump line, recorder, snapshot, and smoke test.
  - The recorder method name matches the actual instrumentation point after `encoder.finish()`.
- Scope:
  - No dashboard GM integration.
  - No aggregation by GM.
  - No rendering, GM golden, pipeline key, batching, or provider behavior changes.
