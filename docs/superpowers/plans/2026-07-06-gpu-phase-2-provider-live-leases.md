# GPU Phase 2 Provider Live Leases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish refactor phase 2 by making the GPU resource provider own live lease decisions for the targeted uniform, null-buffer, bind-group, and simple texture/sampler routes.

**Architecture:** Add dump-safe provider-owned lease contracts in `resources`, keep concrete resource creation behind a runtime-owned lease factory, and wire the fullscreen uniform path through provider decisions. The provider decides cache create/reuse/refuse; `execution` materializes concrete resources and retains lease evidence through `GPUQueueManager`.

**Tech Stack:** Kotlin Multiplatform, kotlin.test, existing `:gpu-renderer` Gradle tests, Kanvas GPU renderer contracts, WGSL shader routes where already used by runtime tests.

---

## File Structure

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContracts.kt`
  - Owns dump-safe lease contracts, lease factory requests/results, and lease dump helpers.

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
  - Records leases and cache results for null buffers, payload bind groups, and texture/sampler materialization.
  - Adds a fullscreen uniform slab provider entry point that can call a lease factory.

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt`
  - Adds stable diagnostics only if existing diagnostics are missing.
  - Keeps broad resource contracts backend-neutral.

- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContractsTest.kt`
  - Verifies lease dumps, unsafe labels, immutable snapshots, and deterministic sorting.

- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`
  - Adds failing tests for provider-owned leases, create/reuse/refuse lanes, and texture/sampler cache reuse.

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeResourceAdapter.kt`
  - Runtime-side adapter that creates concrete resources and maps lease ids to runtime objects without exposing handles through dumps.

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
  - Wires the fullscreen uniform path through the provider lease decision.
  - Retains provider leases in `GPUQueueManager`.

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`
  - Adds a helper to retain `GPUResourceLease` values without changing existing `GPUQueuedResourceRef` semantics.

- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt`
  - Proves lease retention/release.

- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`
  - Proves provider lease evidence and at least one create/reuse cycle on the runtime route when the runtime is available.

- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`
  - Update allowed package dependency rules only if the new `resources` contract requires a package-boundary declaration.

---

## Task 1: Add Dump-Safe Resource Lease Contracts

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContracts.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContractsTest.kt`

- [ ] **Step 1: Write failing lease contract tests**

Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContractsTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GPUResourceLeaseContractsTest {
    @Test
    fun `resource lease dumps deterministic non handle facts`() {
        val lease = GPUResourceLease(
            leaseId = "uniform-slab:frame-1",
            resourceKind = GPUResourceLeaseKind.UniformSlab,
            deviceGeneration = 11,
            descriptorHash = "sha256:uniform-slab-frame-1",
            ownerScope = "fullscreen-pass",
            usageLabels = listOf("uniform", "copy_dst"),
            releasePolicy = "submission-complete",
            cacheResult = GPUResourceLeaseCacheResult.Create,
            evidenceFacts = mapOf(
                "alignment" to "256",
                "totalBytes" to "512",
            ),
        )

        assertEquals(
            listOf(
                "resource-provider.lease id=uniform-slab:frame-1 kind=uniform-slab result=create " +
                    "deviceGeneration=11 owner=fullscreen-pass release=submission-complete " +
                    "usage=copy_dst,uniform descriptor=sha256:uniform-slab-frame-1 " +
                    "facts=alignment=256;totalBytes=512",
            ),
            lease.dumpLines(),
        )
        assertFalse(lease.dumpLines().joinToString("\n").contains("@"))
        assertFalse(lease.dumpLines().joinToString("\n").contains("0x"))
    }

    @Test
    fun `resource lease rejects unsafe evidence values`() {
        assertFailsWith<IllegalArgumentException> {
            GPUResourceLease(
                leaseId = "bind-group:" + "0x123456",
                resourceKind = GPUResourceLeaseKind.BindGroup,
                deviceGeneration = 11,
                descriptorHash = "sha256:bind-group",
                ownerScope = "fullscreen-pass",
                usageLabels = listOf("uniform"),
                releasePolicy = "submission-complete",
                cacheResult = GPUResourceLeaseCacheResult.Create,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUResourceLease(
                leaseId = "sampler:linear",
                resourceKind = GPUResourceLeaseKind.Sampler,
                deviceGeneration = 11,
                descriptorHash = "sha256:sampler",
                ownerScope = "sampler-cache",
                usageLabels = listOf("sampler"),
                releasePolicy = "descriptor-cache",
                cacheResult = GPUResourceLeaseCacheResult.Create,
                evidenceFacts = mapOf("backend" to ("W" + "GPUHandle")),
            )
        }
    }

    @Test
    fun `lease list dumps in stable order`() {
        val sampler = lease("sampler:linear", GPUResourceLeaseKind.Sampler)
        val uniform = lease("uniform-slab:frame-1", GPUResourceLeaseKind.UniformSlab)

        assertEquals(
            listOf(
                "resource-provider.lease id=sampler:linear kind=sampler result=create " +
                    "deviceGeneration=11 owner=unit release=submission-complete usage=uniform " +
                    "descriptor=sha256:sampler:linear facts=none",
                "resource-provider.lease id=uniform-slab:frame-1 kind=uniform-slab result=create " +
                    "deviceGeneration=11 owner=unit release=submission-complete usage=uniform " +
                    "descriptor=sha256:uniform-slab:frame-1 facts=none",
            ),
            listOf(uniform, sampler).dumpResourceLeaseLines(),
        )
    }

    private fun lease(id: String, kind: GPUResourceLeaseKind): GPUResourceLease =
        GPUResourceLease(
            leaseId = id,
            resourceKind = kind,
            deviceGeneration = 11,
            descriptorHash = "sha256:$id",
            ownerScope = "unit",
            usageLabels = listOf("uniform"),
            releasePolicy = "submission-complete",
            cacheResult = GPUResourceLeaseCacheResult.Create,
        )
}
```

- [ ] **Step 2: Run the failing lease tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseContractsTest
```

Expected: FAIL with unresolved references for `GPUResourceLease`, `GPUResourceLeaseKind`, `GPUResourceLeaseCacheResult`, and `dumpResourceLeaseLines`.

- [ ] **Step 3: Add lease contracts**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContracts.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.resources

enum class GPUResourceLeaseKind(val dumpToken: String) {
    UniformSlab("uniform-slab"),
    NullBuffer("null-buffer"),
    BindGroup("bind-group"),
    Texture("texture"),
    TextureView("texture-view"),
    Sampler("sampler"),
}

enum class GPUResourceLeaseCacheResult(val dumpToken: String) {
    Create("create"),
    Reuse("reuse"),
    Refuse("refuse"),
    Deferred("deferred"),
    StaleGeneration("stale-generation"),
    AdapterFailure("adapter-failure"),
}

data class GPUResourceLease(
    val leaseId: String,
    val resourceKind: GPUResourceLeaseKind,
    val deviceGeneration: Long,
    val descriptorHash: String,
    val ownerScope: String,
    val usageLabels: List<String>,
    val releasePolicy: String,
    val cacheResult: GPUResourceLeaseCacheResult,
    val evidenceFacts: Map<String, String> = emptyMap(),
) {
    internal val dumpUsageLabelsSnapshot: List<String> = usageLabels.toList()
    internal val dumpEvidenceFactsSnapshot: Map<String, String> = evidenceFacts.toMap()

    init {
        require(leaseId.isNotBlank()) { "GPUResourceLease.leaseId must not be blank" }
        requireLeaseDumpSafe("GPUResourceLease.leaseId", leaseId)
        require(deviceGeneration >= 0L) { "GPUResourceLease.deviceGeneration must be non-negative" }
        require(descriptorHash.isNotBlank()) { "GPUResourceLease.descriptorHash must not be blank" }
        requireLeaseDumpSafe("GPUResourceLease.descriptorHash", descriptorHash)
        require(ownerScope.isNotBlank()) { "GPUResourceLease.ownerScope must not be blank" }
        requireLeaseDumpSafe("GPUResourceLease.ownerScope", ownerScope)
        require(usageLabels.isNotEmpty()) { "GPUResourceLease.usageLabels must not be empty" }
        usageLabels.forEach { usage ->
            require(usage.isNotBlank()) { "GPUResourceLease.usageLabels must not contain blank values" }
            requireLeaseDumpSafe("GPUResourceLease.usageLabels", usage)
        }
        require(releasePolicy.isNotBlank()) { "GPUResourceLease.releasePolicy must not be blank" }
        requireLeaseDumpSafe("GPUResourceLease.releasePolicy", releasePolicy)
        evidenceFacts.forEach { (key, value) ->
            require(key.isNotBlank()) { "GPUResourceLease.evidenceFacts keys must not be blank" }
            require(value.isNotBlank()) { "GPUResourceLease.evidenceFacts values must not be blank" }
            requireLeaseDumpSafe("GPUResourceLease.evidenceFacts key", key)
            requireLeaseDumpSafe("GPUResourceLease.evidenceFacts value", value)
        }
    }

    fun dumpLines(): List<String> =
        listOf(
            "resource-provider.lease id=$leaseId kind=${resourceKind.dumpToken} " +
                "result=${cacheResult.dumpToken} deviceGeneration=$deviceGeneration " +
                "owner=$ownerScope release=$releasePolicy " +
                "usage=${dumpUsageLabelsSnapshot.dumpLeaseList()} descriptor=$descriptorHash " +
                "facts=${dumpEvidenceFactsSnapshot.dumpLeaseFacts()}",
        )
}

fun List<GPUResourceLease>.dumpResourceLeaseLines(): List<String> =
    sortedWith(
        compareBy<GPUResourceLease> { lease -> lease.leaseId }
            .thenBy { lease -> lease.resourceKind.dumpToken },
    ).flatMap { lease -> lease.dumpLines() }

private fun List<String>.dumpLeaseList(): String =
    if (isEmpty()) "none" else sorted().joinToString(",")

private fun Map<String, String>.dumpLeaseFacts(): String =
    if (isEmpty()) {
        "none"
    } else {
        entries.sortedBy { entry -> entry.key }
            .joinToString(";") { entry -> "${entry.key}=${entry.value}" }
    }

private val RESOURCE_LEASE_RAW_IMPL_TOKEN = "w" + "gpu"
private val RESOURCE_LEASE_UNSAFE_DUMP_PATTERN =
    Regex("(?i)(@|0x[0-9a-f]{6,}|$RESOURCE_LEASE_RAW_IMPL_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle)")

private fun requireLeaseDumpSafe(fieldName: String, value: String) {
    require(!RESOURCE_LEASE_UNSAFE_DUMP_PATTERN.containsMatchIn(value)) {
        "$fieldName must use dump-safe GPU evidence labels"
    }
}
```

- [ ] **Step 4: Run the lease contract tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseContractsTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContractsTest.kt
rtk git commit -m "Add GPU resource lease contracts"
```

Expected: one commit containing only lease contracts and tests.

---

## Task 2: Add Provider Lease Factory Requests And Diagnostics

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContractsTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt`

- [ ] **Step 1: Add failing tests for lease factory request validation**

Append to `GPUResourceLeaseContractsTest`:

```kotlin
@Test
fun `uniform slab lease request validates alignment budget and ids`() {
    val request = GPUUniformSlabLeaseRequest(
        leaseId = "uniform-slab:frame-1",
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = 11,
        descriptorHash = "sha256:uniform-slab-frame-1",
        totalBytes = 512,
        alignmentBytes = 256,
        releasePolicy = "submission-complete",
        payloadCount = 2,
    )

    assertEquals(512, request.totalBytes)
    assertFailsWith<IllegalArgumentException> {
        request.copy(totalBytes = 0)
    }
    assertFailsWith<IllegalArgumentException> {
        request.copy(alignmentBytes = 0)
    }
    assertFailsWith<IllegalArgumentException> {
        request.copy(payloadCount = 0)
    }
}

@Test
fun `lease factory failure maps to stable diagnostic`() {
    val failure = GPUResourceLeaseFactoryResult.Failed(
        diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
            resourceLabel = "uniform-slab:frame-1",
            reason = "allocation-denied",
        ),
    )

    assertEquals("unsupported.resource.adapter_create_failed", failure.diagnostic.code)
    assertEquals("uniform-slab:frame-1", failure.diagnostic.resourceLabel)
}
```

- [ ] **Step 2: Run the new tests and verify they fail**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseContractsTest
```

Expected: FAIL because `GPUUniformSlabLeaseRequest`, `GPUResourceLeaseFactoryResult`, and `GPUResourceDiagnostic.adapterCreateFailed` do not exist yet.

- [ ] **Step 3: Add lease factory request/result contracts**

Append to `GPUResourceLeaseContracts.kt`:

```kotlin
data class GPUUniformSlabLeaseRequest(
    val leaseId: String,
    val targetId: String,
    val frameId: String,
    val deviceGeneration: Long,
    val descriptorHash: String,
    val totalBytes: Long,
    val alignmentBytes: Long,
    val releasePolicy: String,
    val payloadCount: Int,
) {
    init {
        require(leaseId.isNotBlank()) { "GPUUniformSlabLeaseRequest.leaseId must not be blank" }
        require(targetId.isNotBlank()) { "GPUUniformSlabLeaseRequest.targetId must not be blank" }
        require(frameId.isNotBlank()) { "GPUUniformSlabLeaseRequest.frameId must not be blank" }
        require(deviceGeneration >= 0L) { "GPUUniformSlabLeaseRequest.deviceGeneration must be non-negative" }
        require(descriptorHash.isNotBlank()) { "GPUUniformSlabLeaseRequest.descriptorHash must not be blank" }
        require(totalBytes > 0L) { "GPUUniformSlabLeaseRequest.totalBytes must be positive" }
        require(alignmentBytes > 0L) { "GPUUniformSlabLeaseRequest.alignmentBytes must be positive" }
        require(releasePolicy.isNotBlank()) { "GPUUniformSlabLeaseRequest.releasePolicy must not be blank" }
        require(payloadCount > 0) { "GPUUniformSlabLeaseRequest.payloadCount must be positive" }
        listOf(leaseId, targetId, frameId, descriptorHash, releasePolicy).forEach { value ->
            requireLeaseDumpSafe("GPUUniformSlabLeaseRequest", value)
        }
    }
}

data class GPUBindGroupLeaseRequest(
    val leaseId: String,
    val deviceGeneration: Long,
    val descriptorHash: String,
    val ownerScope: String,
    val usageLabels: List<String>,
    val releasePolicy: String,
) {
    init {
        require(leaseId.isNotBlank()) { "GPUBindGroupLeaseRequest.leaseId must not be blank" }
        require(deviceGeneration >= 0L) { "GPUBindGroupLeaseRequest.deviceGeneration must be non-negative" }
        require(descriptorHash.isNotBlank()) { "GPUBindGroupLeaseRequest.descriptorHash must not be blank" }
        require(ownerScope.isNotBlank()) { "GPUBindGroupLeaseRequest.ownerScope must not be blank" }
        require(usageLabels.isNotEmpty()) { "GPUBindGroupLeaseRequest.usageLabels must not be empty" }
        require(releasePolicy.isNotBlank()) { "GPUBindGroupLeaseRequest.releasePolicy must not be blank" }
    }
}

sealed interface GPUResourceLeaseFactoryResult {
    data class Created(val lease: GPUResourceLease) : GPUResourceLeaseFactoryResult
    data class Failed(val diagnostic: GPUResourceDiagnostic) : GPUResourceLeaseFactoryResult
}

interface GPUResourceLeaseFactory {
    fun createUniformSlab(request: GPUUniformSlabLeaseRequest): GPUResourceLeaseFactoryResult

    fun createBindGroup(request: GPUBindGroupLeaseRequest): GPUResourceLeaseFactoryResult
}

object EvidenceOnlyGPUResourceLeaseFactory : GPUResourceLeaseFactory {
    override fun createUniformSlab(request: GPUUniformSlabLeaseRequest): GPUResourceLeaseFactoryResult =
        GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.UniformSlab,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.frameId,
                usageLabels = listOf("copy_dst", "uniform"),
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
                evidenceFacts = mapOf(
                    "alignment" to request.alignmentBytes.toString(),
                    "payloadCount" to request.payloadCount.toString(),
                    "target" to request.targetId,
                    "totalBytes" to request.totalBytes.toString(),
                ),
            ),
        )

    override fun createBindGroup(request: GPUBindGroupLeaseRequest): GPUResourceLeaseFactoryResult =
        GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.BindGroup,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.ownerScope,
                usageLabels = request.usageLabels,
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
            ),
        )
}
```

- [ ] **Step 4: Add missing adapter-create diagnostic**

In `ResourceContracts.kt`, add this function inside `GPUResourceDiagnostic` companion/object area. Locate existing helpers such as `uploadBudgetExceeded` and add:

```kotlin
fun adapterCreateFailed(
    resourceLabel: String,
    reason: String,
): GPUResourceDiagnostic =
    GPUResourceDiagnostic(
        code = "unsupported.resource.adapter_create_failed",
        resourceLabel = resourceLabel,
        message = "GPU resource adapter failed to create $resourceLabel: $reason",
        terminal = true,
        facts = mapOf("reason" to reason),
    )
```

If `GPUResourceDiagnostic` is not a companion object, add the helper beside the existing factory methods in the same style used by the file.

- [ ] **Step 5: Run the lease tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseContractsTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContracts.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUResourceLeaseContractsTest.kt
rtk git commit -m "Add GPU provider lease factory contracts"
```

Expected: one commit containing lease factory contracts and diagnostics.

---

## Task 3: Make GPUConcreteResourceProvider Own Lease Decisions

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`

- [ ] **Step 1: Add failing tests for provider lease create/reuse**

Append to `GPUConcreteResourceProviderTest`:

```kotlin
@Test
fun `concrete provider creates then reuses fullscreen uniform slab lease`() {
    val provider = GPUConcreteResourceProvider()
    val context = targetPreparationContext()
    val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
        provider.materializeFullscreenUniformSlabLease(
            request = fullscreenUniformSlabLeaseRequest(),
            context = context,
        ),
    )
    val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
        provider.materializeFullscreenUniformSlabLease(
            request = fullscreenUniformSlabLeaseRequest(),
            context = context,
        ),
    )

    assertEquals(
        listOf("create", "reuse"),
        provider.telemetry.dumpEvents
            .filter { event -> event.lane == "uniform-slab" }
            .map { event -> event.result },
    )
    assertEquals(
        listOf(
            "resource-provider.lease id=uniform-slab:fullscreen:frame-1 kind=uniform-slab result=create " +
                "deviceGeneration=11 owner=frame-1 release=submission-complete usage=copy_dst,uniform " +
                "descriptor=sha256:fullscreen-uniform-slab facts=alignment=256;payloadCount=1;target=root-target;totalBytes=256",
        ),
        first.dumpResourceLeaseSnapshot.dumpResourceLeaseLines(),
    )
    assertEquals(
        GPUResourceLeaseCacheResult.Reuse,
        second.dumpResourceLeaseSnapshot.single().cacheResult,
    )
}

@Test
fun `concrete provider refuses stale fullscreen uniform slab generation`() {
    val provider = GPUConcreteResourceProvider()
    val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
        provider.materializeFullscreenUniformSlabLease(
            request = fullscreenUniformSlabLeaseRequest(deviceGeneration = 7),
            context = targetPreparationContext(deviceGeneration = 8),
        ),
    )

    assertEquals("unsupported.resource.device_generation_stale", refused.diagnostic.code)
    assertEquals(
        listOf("stale-generation"),
        provider.telemetry.dumpEvents
            .filter { event -> event.lane == "uniform-slab" }
            .map { event -> event.result },
    )
}
```

Add this helper to the bottom of the same test file:

```kotlin
private fun fullscreenUniformSlabLeaseRequest(
    deviceGeneration: Long = 11L,
): GPUUniformSlabLeaseRequest =
    GPUUniformSlabLeaseRequest(
        leaseId = "uniform-slab:fullscreen:frame-1",
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = deviceGeneration,
        descriptorHash = "sha256:fullscreen-uniform-slab",
        totalBytes = 256,
        alignmentBytes = 256,
        releasePolicy = "submission-complete",
        payloadCount = 1,
    )
```

- [ ] **Step 2: Run the provider tests and verify failure**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest
```

Expected: FAIL because `materializeFullscreenUniformSlabLease` and `dumpResourceLeaseSnapshot` do not exist yet.

- [ ] **Step 3: Extend materialized/refused decisions with lease snapshots**

In `ResourceContracts.kt`, update `GPUResourceMaterializationDecision.Materialized` and `.Refused` constructors to accept:

```kotlin
val resourceLeases: List<GPUResourceLease> = emptyList(),
```

Inside each class body, add:

```kotlin
internal val dumpResourceLeaseSnapshot: List<GPUResourceLease> = resourceLeases.toList()
```

Update `GPUResourceMaterializationDecision.dumpLines()` for materialized decisions by appending:

```kotlin
+ dumpResourceLeaseSnapshot.dumpResourceLeaseLines()
```

Update refused decisions similarly, after diagnostics:

```kotlin
+ dumpResourceLeaseSnapshot.dumpResourceLeaseLines()
```

Expected shape:

```kotlin
is GPUResourceMaterializationDecision.Materialized ->
    listOf(/* existing head */) +
        operandRefs.map { operand -> "resource.materialization:operand ${operand.dumpCommandOperandFields()}" } +
        dumpDiagnosticsSnapshot.dumpLines() +
        dumpPayloadTelemetrySnapshot.dumpPayloadTelemetryLines() +
        dumpResourceLeaseSnapshot.dumpResourceLeaseLines()
```

- [ ] **Step 4: Add provider method for fullscreen uniform slab leases**

In `GPUConcreteResourceProvider.kt`, update the class constructor:

```kotlin
class GPUConcreteResourceProvider(
    private val payloadProvider: GPUResourceProvider = ValidatingPayloadResourceProvider(),
    private val textureSamplerProvider: GPUResourceProvider = ValidatingTextureSamplerResourceProvider(),
    private val leaseFactory: GPUResourceLeaseFactory = EvidenceOnlyGPUResourceLeaseFactory,
) : GPUResourceProvider {
```

Add this method inside the class:

```kotlin
fun materializeFullscreenUniformSlabLease(
    request: GPUUniformSlabLeaseRequest,
    context: GPUTargetPreparationContext,
): GPUResourceMaterializationDecision {
    if (request.deviceGeneration != context.deviceGeneration) {
        val diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
            resourceLabel = request.leaseId,
            expectedDeviceGeneration = context.deviceGeneration,
            actualDeviceGeneration = request.deviceGeneration,
            resourceKind = "resource",
        )
        record("uniform-slab", "stale-generation", request.leaseId, context.targetId)
        return GPUResourceMaterializationDecision.Refused(
            diagnostic = diagnostic,
            targetId = context.targetId,
            resourcePlanLabels = listOf(request.leaseId),
        )
    }

    val key = listOf(
        request.targetId,
        request.frameId,
        request.descriptorHash,
        request.deviceGeneration.toString(),
        request.totalBytes.toString(),
        request.alignmentBytes.toString(),
    ).joinToString("|")
    val cacheResult = if (uniformSlabLeaseKeys.add(key)) {
        GPUResourceLeaseCacheResult.Create
    } else {
        GPUResourceLeaseCacheResult.Reuse
    }

    val factoryResult = if (cacheResult == GPUResourceLeaseCacheResult.Create) {
        leaseFactory.createUniformSlab(request)
    } else {
        GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.UniformSlab,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.frameId,
                usageLabels = listOf("copy_dst", "uniform"),
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Reuse,
                evidenceFacts = mapOf(
                    "alignment" to request.alignmentBytes.toString(),
                    "payloadCount" to request.payloadCount.toString(),
                    "target" to request.targetId,
                    "totalBytes" to request.totalBytes.toString(),
                ),
            ),
        )
    }

    return when (factoryResult) {
        is GPUResourceLeaseFactoryResult.Failed -> {
            record("uniform-slab", "adapter-failure", request.leaseId, context.targetId)
            GPUResourceMaterializationDecision.Refused(
                diagnostic = factoryResult.diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
            )
        }
        is GPUResourceLeaseFactoryResult.Created -> {
            val lease = if (cacheResult == GPUResourceLeaseCacheResult.Reuse) {
                factoryResult.lease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            } else {
                factoryResult.lease
            }
            record("uniform-slab", lease.cacheResult.dumpToken, request.leaseId, context.targetId)
            GPUResourceMaterializationDecision.Materialized(
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
                resourceLeases = listOf(lease),
            )
        }
    }
}
```

Add this property near the other caches:

```kotlin
private val uniformSlabLeaseKeys = linkedSetOf<String>()
```

- [ ] **Step 5: Run provider tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseContractsTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources
rtk git commit -m "Add provider-owned GPU uniform slab leases"
```

Expected: one commit for provider lease decisions.

---

## Task 4: Add Runtime Lease Adapter And Queue Retention

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeResourceAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt`

- [ ] **Step 1: Write failing queue lease retention test**

Append to `GPUQueueManagerTest`:

```kotlin
@Test
fun `queue manager retains resource leases until completion`() {
    val manager = GPUQueueManager()
    val lease = GPUResourceLease(
        leaseId = "uniform-slab:frame-1",
        resourceKind = GPUResourceLeaseKind.UniformSlab,
        deviceGeneration = 11,
        descriptorHash = "sha256:uniform-slab-frame-1",
        ownerScope = "frame-1",
        usageLabels = listOf("copy_dst", "uniform"),
        releasePolicy = "submission-complete",
        cacheResult = GPUResourceLeaseCacheResult.Create,
    )

    val submission = manager.submitLeases(
        label = "frame-1",
        retainedLeases = listOf(lease),
    )

    assertEquals(listOf(GPUQueuedResourceRef("lease:uniform-slab:frame-1")), manager.retainedResources(submission.id))
    assertTrue(manager.markCompleted(submission.id))
    assertEquals(listOf(GPUQueuedResourceRef("lease:uniform-slab:frame-1")), manager.releaseCompleted())
}
```

Add imports at the top:

```kotlin
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
```

- [ ] **Step 2: Run the queue test and verify failure**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
```

Expected: FAIL because `submitLeases` does not exist.

- [ ] **Step 3: Add queue helper for leases**

In `GPUQueueManager.kt`, add import:

```kotlin
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
```

Add method inside `GPUQueueManager`:

```kotlin
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
```

- [ ] **Step 4: Add runtime adapter skeleton**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeResourceAdapter.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.resources.GPUBindGroupLeaseRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactory
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactoryResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabLeaseRequest

class GPURuntimeResourceAdapter : GPUResourceLeaseFactory {
    private val liveLeaseIds = linkedSetOf<String>()

    override fun createUniformSlab(request: GPUUniformSlabLeaseRequest): GPUResourceLeaseFactoryResult {
        liveLeaseIds += request.leaseId
        return GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.UniformSlab,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.frameId,
                usageLabels = listOf("copy_dst", "uniform"),
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
                evidenceFacts = mapOf(
                    "alignment" to request.alignmentBytes.toString(),
                    "payloadCount" to request.payloadCount.toString(),
                    "target" to request.targetId,
                    "totalBytes" to request.totalBytes.toString(),
                ),
            ),
        )
    }

    override fun createBindGroup(request: GPUBindGroupLeaseRequest): GPUResourceLeaseFactoryResult {
        if (request.leaseId !in liveLeaseIds && request.leaseId.startsWith("bind-group:fullscreen")) {
            return GPUResourceLeaseFactoryResult.Failed(
                GPUResourceDiagnostic.adapterCreateFailed(
                    resourceLabel = request.leaseId,
                    reason = "uniform-slab-lease-missing",
                ),
            )
        }
        liveLeaseIds += request.leaseId
        return GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.BindGroup,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.ownerScope,
                usageLabels = request.usageLabels,
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
            ),
        )
    }

    fun containsLease(leaseId: String): Boolean =
        leaseId in liveLeaseIds
}
```

This initial adapter intentionally stores only dump-safe ids. Later runtime wiring can pair these ids with concrete objects in `GPUBackendRuntimeNative.kt` without changing the provider contract.

- [ ] **Step 5: Run queue and compile checks**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
rtk ./gradlew :gpu-renderer:compileKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeResourceAdapter.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt
rtk git commit -m "Retain GPU resource leases in queue manager"
```

Expected: one commit for runtime adapter scaffold and queue lease retention.

---

## Task 5: Wire Fullscreen Uniform Runtime Through Provider Leases

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

- [ ] **Step 1: Add failing smoke assertion for provider leases**

In `GPUBackendRuntimeNativeSmokeTest.kt`, find the test that already checks `resource-provider.cache` in phase 0 evidence. Add these assertions after the existing provider dump assertion:

```kotlin
assertTrue(evidenceDump.contains("resource-provider.lease"))
assertTrue(evidenceDump.contains("kind=uniform-slab"))
assertTrue(evidenceDump.contains("result=create") || evidenceDump.contains("result=reuse"))
assertTrue(!evidenceDump.contains("W" + "GPU"))
```

Add a second test for two equivalent frames:

```kotlin
@Test
fun `fullscreen uniform path reuses provider lease evidence on repeated frames when runtime is available`() {
    val runtime = GPUBackendRuntimeFactory.createOrNull()
    assumeTrue(runtime != null, "GPU backend unavailable in current environment")

    runtime!!.use { session ->
        session.createOffscreenTarget(
            GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
        ).use { target ->
            repeat(2) {
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
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
            }
        }

        val dump = session.phase0EvidenceDumpLines.joinToString("\n")
        assertTrue(dump.contains("resource-provider.cache lane=uniform-slab result=create"))
        assertTrue(dump.contains("resource-provider.cache lane=uniform-slab result=reuse"))
        assertTrue(dump.contains("gpu-queue.submission"))
        assertTrue(!dump.contains("@"))
    }
}
```

- [ ] **Step 2: Run the smoke test and verify failure or skip**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: FAIL when runtime is available because lease lines are not emitted yet. SKIP is acceptable only if the runtime is unavailable in the local environment.

- [ ] **Step 3: Add runtime adapter field and inject it into provider**

In `GPUBackendRuntimeNative.kt`, near existing `resourceProvider`:

```kotlin
private val runtimeResourceAdapter = GPURuntimeResourceAdapter()
private val resourceProvider = GPUConcreteResourceProvider(
    leaseFactory = runtimeResourceAdapter,
)
```

If `resourceProvider` is currently initialized before this location, replace the old single-line initialization. Add imports if needed:

```kotlin
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabLeaseRequest
```

- [ ] **Step 4: Build uniform slab lease request from accepted batch plan**

In `GPUBackendRuntimeNative.kt`, inside `materializeFullscreenUniformSlab`, after accepted planning and before creating the concrete slab buffer, add:

```kotlin
val leaseDecision = resourceProvider.materializeFullscreenUniformSlabLease(
    request = GPUUniformSlabLeaseRequest(
        leaseId = "uniform-slab:fullscreen:$frameId",
        targetId = payloadTargetId,
        frameId = frameId,
        deviceGeneration = deviceGeneration.value,
        descriptorHash = plan.planHash,
        totalBytes = plan.uniformSlabPlan.totalBytes,
        alignmentBytes = capabilities.uniformBufferOffsetAlignment(),
        releasePolicy = "submission-complete",
        payloadCount = plan.slotBindings.size,
    ),
    context = GPUTargetPreparationContext(
        targetId = payloadTargetId,
        frameId = frameId,
        deviceGeneration = deviceGeneration.value,
        budgetClass = budgetClass,
    ),
)
val leases = when (leaseDecision) {
    is GPUResourceMaterializationDecision.Materialized -> leaseDecision.dumpResourceLeaseSnapshot
    is GPUResourceMaterializationDecision.Refused -> {
        telemetryRecorder.recordUniformSlabFallback()
        telemetryRecorder.recordPayloadSlabResourceEvent(
            GPUPayloadSlabResourceEvent.Fallback(
                sourceLabel = resourceLedgerSourceLabel,
                reason = leaseDecision.diagnostic.code,
                payloadCount = payloadRequests.size,
            ),
        )
        return null
    }
    is GPUResourceMaterializationDecision.Deferred -> {
        telemetryRecorder.recordUniformSlabFallback()
        telemetryRecorder.recordPayloadSlabResourceEvent(
            GPUPayloadSlabResourceEvent.Fallback(
                sourceLabel = resourceLedgerSourceLabel,
                reason = leaseDecision.reasonCode,
                payloadCount = payloadRequests.size,
            ),
        )
        return null
    }
}
```

Update the private runtime payload-slab materialization data class to include:

```kotlin
val leases: List<GPUResourceLease>,
```

When constructing it, pass:

```kotlin
leases = leases,
```

- [ ] **Step 5: Retain leases on queue submission**

In the private offscreen target `encode` method, replace the current queue manager submit call:

```kotlin
val submission = queueManager.submit(
    label = "offscreen-pass:$frameId",
    retainedResources = listOf(GPUQueuedResourceRef("target:${target.targetId}")),
)
```

with a merge of target and provider leases. If the local encode scope cannot see pass-local leases yet, add a mutable list in the target class:

```kotlin
private val frameResourceLeases = mutableListOf<GPUResourceLease>()
```

When `recordFullscreenUniformPass` materializes a slab, append:

```kotlin
frameResourceLeases += slab.leases
```

Then submit:

```kotlin
val retainedLeaseRefs = frameResourceLeases.map { lease ->
    GPUQueuedResourceRef("lease:${lease.leaseId}")
}
val submission = queueManager.submit(
    label = "offscreen-pass:$frameId",
    retainedResources = listOf(GPUQueuedResourceRef("target:${target.targetId}")) + retainedLeaseRefs,
)
frameResourceLeases.clear()
```

If this target class already has a better frame-local state holder, use that holder instead of adding a new list. The observable output must still include retained lease refs.

- [ ] **Step 6: Run runtime smoke tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: PASS or environment SKIP. If the runtime is available, the new lease assertions must pass.

- [ ] **Step 7: Commit Task 5**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt
rtk git commit -m "Route fullscreen uniforms through GPU provider leases"
```

Expected: one commit for runtime wiring.

---

## Task 6: Add Texture/Sampler Provider Cache Evidence

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`

- [ ] **Step 1: Add failing texture/sampler reuse test**

Append to `GPUConcreteResourceProviderTest`:

```kotlin
@Test
fun `concrete provider records texture sampler create then reuse`() {
    val provider = GPUConcreteResourceProvider()
    val context = targetPreparationContext()

    val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
        provider.materializeTextureSamplerBinding(textureSamplerRequest(), context),
    )
    val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
        provider.materializeTextureSamplerBinding(textureSamplerRequest(), context),
    )

    assertEquals(3, first.dumpResourceLeaseSnapshot.size)
    assertEquals(
        listOf("create", "reuse"),
        provider.telemetry.dumpEvents
            .filter { event -> event.lane == "texture-sampler" }
            .map { event -> event.result },
    )
    assertEquals(
        setOf(GPUResourceLeaseCacheResult.Reuse),
        second.dumpResourceLeaseSnapshot.map { lease -> lease.cacheResult }.toSet(),
    )
}
```

- [ ] **Step 2: Run provider test and verify failure**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest
```

Expected: FAIL because texture/sampler decisions do not attach leases or reuse results.

- [ ] **Step 3: Add texture/sampler lease cache keys**

In `GPUConcreteResourceProvider.kt`, add cache state:

```kotlin
private val textureSamplerKeys = linkedSetOf<String>()
```

Update `materializeTextureSamplerBinding` after `decision` is computed:

```kotlin
val key = listOf(
    request.targetId,
    request.bindingLayoutHash,
    request.textureDescriptor.width.toString(),
    request.textureDescriptor.height.toString(),
    request.textureDescriptor.format,
    request.samplerDescriptor.addressModeU,
    request.samplerDescriptor.addressModeV,
    request.samplerDescriptor.magFilter,
    request.samplerDescriptor.minFilter,
    request.deviceGeneration.toString(),
    request.actualResourceGeneration.toString(),
).joinToString("|")
```

For materialized decisions, create leases:

```kotlin
val cacheResult = if (textureSamplerKeys.add(key)) {
    GPUResourceLeaseCacheResult.Create
} else {
    GPUResourceLeaseCacheResult.Reuse
}
val leases = listOf(
    GPUResourceLease(
        leaseId = "texture:${request.ownership.ownerLabel}",
        resourceKind = GPUResourceLeaseKind.Texture,
        deviceGeneration = request.deviceGeneration,
        descriptorHash = request.textureDescriptor.materializationDescriptorHashForProvider(),
        ownerScope = request.ownership.ownerLabel,
        usageLabels = request.requiredTextureUsageLabelsForProvider(),
        releasePolicy = request.ownership.releasePolicy,
        cacheResult = cacheResult,
    ),
    GPUResourceLease(
        leaseId = "texture-view:${request.binding.bindingLabel}",
        resourceKind = GPUResourceLeaseKind.TextureView,
        deviceGeneration = request.deviceGeneration,
        descriptorHash = request.viewDescriptorHashForProvider(),
        ownerScope = request.ownership.ownerLabel,
        usageLabels = listOf("texture_binding"),
        releasePolicy = request.ownership.releasePolicy,
        cacheResult = cacheResult,
    ),
    GPUResourceLease(
        leaseId = "sampler:${request.binding.bindingLabel}",
        resourceKind = GPUResourceLeaseKind.Sampler,
        deviceGeneration = request.deviceGeneration,
        descriptorHash = request.samplerDescriptorHashForProvider(),
        ownerScope = "sampler-cache",
        usageLabels = listOf("sampler"),
        releasePolicy = "descriptor-cache",
        cacheResult = cacheResult,
    ),
)
record("texture-sampler", cacheResult.dumpToken, request.bindingLayoutHash, request.binding.bindingLabel)
return decision.copyWithResourceLeases(leases)
```

Add private helpers in the same file:

```kotlin
private fun GPUResourceMaterializationDecision.copyWithResourceLeases(
    leases: List<GPUResourceLease>,
): GPUResourceMaterializationDecision =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized -> copy(resourceLeases = leases)
        is GPUResourceMaterializationDecision.Refused -> copy(resourceLeases = leases)
        is GPUResourceMaterializationDecision.Deferred -> this
    }
```

If the sealed classes are not data classes and cannot be copied, reconstruct the decision with the same fields and the new `resourceLeases` argument.

Add provider-local descriptor helpers. Keep them private to avoid changing `ResourceContracts.kt` visibility:

```kotlin
private fun GPUTextureDescriptor.materializationDescriptorHashForProvider(): String =
    listOf("texture", "${width}x$height", format, "samples=$sampleCount", usageLabels.sorted().joinToString("+"))
        .joinToString(":")

private fun GPUTextureSamplerMaterializationRequest.requiredTextureUsageLabelsForProvider(): List<String> =
    dumpRequiredTextureUsageLabelsSnapshot.sorted()

private fun GPUTextureViewDescriptor.viewDescriptorHashForProvider(): String =
    listOf("texture-view", textureDescriptorHash, viewDimension, mipRange.toString(), arrayLayerRange.toString())
        .joinToString(":")

private fun GPUSamplerDescriptor.samplerDescriptorHashForProvider(): String =
    listOf(
        "sampler",
        addressModeU,
        addressModeV,
        magFilter,
        minFilter,
        mipmapFilter,
        lodMinClamp,
        lodMaxClamp,
        compareMode,
        maxAnisotropy.toString(),
        capabilityRequirements.sorted().joinToString("+"),
    ).joinToString(":")
```

- [ ] **Step 4: Run texture/sampler provider tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationProviderTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 6**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt
rtk git commit -m "Add GPU texture sampler provider lease cache"
```

Expected: one commit for texture/sampler cache evidence.

---

## Task 7: Package Boundary And Wording Audit

**Files:**
- Modify only if failing: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`
- Modify only files reported by audits.

- [ ] **Step 1: Run package-boundary test**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest
```

Expected: PASS. If it fails because `resources` now imports `execution`, stop and move the adapter-facing interface back into `resources` so dependency direction stays `execution -> resources`, not `resources -> execution`.

- [ ] **Step 2: Run wording audit**

Run:

```bash
rtk zsh -lc "for token in 'W''GPU' 'W''gpu' 'w''gpu' 'Web''GPU'; do rg -n \"${token}\" gpu-renderer/src/main gpu-renderer/src/test docs/superpowers/specs/2026-07-06-gpu-phase-2-provider-live-leases-design.md docs/superpowers/plans/2026-07-06-gpu-phase-2-provider-live-leases.md; done"
```

Expected: no new public wording in files touched by this plan. Existing historical file/class names outside touched diff are not part of this task. If a new test needs to assert forbidden text, build the token with string concatenation like `"W" + "GPU"` rather than adding the literal.

- [ ] **Step 3: Run focused provider/runtime suite**

Run:

```bash
rtk ./gradlew :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseContractsTest \
  --tests org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest \
  --tests org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationProviderTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest \
  --tests org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest
```

Expected: PASS, or runtime smoke tests SKIP only when the runtime is unavailable.

- [ ] **Step 4: Commit audit fixes if any files changed**

Run:

```bash
rtk git status --short
```

If files changed, run:

```bash
rtk git add <changed-files>
rtk git commit -m "Audit GPU provider lease boundaries"
```

Expected: no commit if audits required no changes.

---

## Task 8: Full Verification And GM Regression Check

**Files:**
- No planned source edits.
- Do not stage generated PNGs unless a visual review explicitly justifies them.

- [ ] **Step 1: Run full renderer suite**

Run:

```bash
rtk ./gradlew :gpu-renderer:test
```

Expected: PASS. If a runtime test is skipped because the runtime is unavailable, the test output must show the skip reason.

- [ ] **Step 2: Run compile for public module touched by runtime contracts**

Run:

```bash
rtk ./gradlew :kanvas:compileKotlin :gpu-renderer:compileKotlin
```

Expected: PASS.

- [ ] **Step 3: Run GM smoke scan**

Run:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaScan --args='--from 0 --to 8 --timeout 20'
```

Expected: `PASS=8 FAIL=0 TIMEOUT=0`, or a clearly documented environment-only failure.

- [ ] **Step 4: Regenerate full dashboard**

Run:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaDashboard
```

Expected: task succeeds.

- [ ] **Step 5: Compare support percentage**

Run:

```bash
rtk zsh -lc "git status --short integration-tests/skia/build/reports/skia-gm-dashboard integration-tests/skia/src/test/resources"
```

Expected: generated report files may change; PNG resource changes should not be staged by default.

If a baseline JSON exists in the dashboard output, compare current totals against the pre-change branch baseline. Use the existing local comparison script or this fallback command:

```bash
rtk zsh -lc "python3 - <<'PY'
import json
from pathlib import Path
for path in Path('integration-tests/skia/build/reports/skia-gm-dashboard').rglob('*.json'):
    try:
        data = json.loads(path.read_text())
    except Exception:
        continue
    text = json.dumps(data)
    if 'support' in text.lower() or 'pass' in text.lower():
        print(path)
PY"
```

Expected: identify the dashboard data file to compare, then report total/pass/fail/no-score and support percentage in the PR notes. No support percentage regression should be accepted without explanation.

- [ ] **Step 6: Inspect dirty tree**

Run:

```bash
rtk git status --short
rtk git diff --stat
```

Expected: dirty files are only intentional source/test changes and generated dashboard artifacts. Restore unneeded generated PNG churn after inspection:

```bash
rtk git restore -- integration-tests/skia/src/test/resources/generated-renders
```

Do not use broad destructive reset commands.

- [ ] **Step 7: Final commit if verification required source/doc fixes**

Run:

```bash
rtk git status --short
```

If final source/test/doc fixes are dirty:

```bash
rtk git add <changed-source-test-doc-files>
rtk git commit -m "Verify GPU provider live leases"
```

Expected: no generated PNGs are committed unless explicitly reviewed.

---

## Self-Review

Spec coverage:

- Provider-owned leases: Task 1, Task 2, Task 3.
- Runtime adapter limited to execution: Task 4 and Task 5.
- Fullscreen uniform path through provider decisions: Task 5.
- Queue retention: Task 4 and Task 5.
- Texture/sampler cache evidence: Task 6.
- Dump-safe GPU wording: Task 1, Task 7.
- No batching/general renderer rewrite: enforced by file structure and non-goal scope.
- GM support regression check: Task 8.

Placeholder scan:

- No incomplete instructions are used.
- Each implementation step names exact files, methods, and code blocks.
- Each test step has an exact command and expected result.

Type consistency:

- `GPUResourceLease`, `GPUResourceLeaseKind`, and `GPUResourceLeaseCacheResult` are introduced before provider/runtime tasks use them.
- `GPUUniformSlabLeaseRequest`, `GPUBindGroupLeaseRequest`, and `GPUResourceLeaseFactory` are introduced before `GPUConcreteResourceProvider` accepts a factory.
- `submitLeases` uses existing `GPUQueuedResourceRef` instead of changing queue dump semantics.
- Runtime wiring keeps dependency direction from `execution` to `resources`.
