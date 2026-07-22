package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheEventResult
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryLedger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class GPUExecutionCacheContractsTest {
    @TestFactory
    fun `every execution cache domain retains failed disposal until close retry`(): List<DynamicTest> =
        GPUExecutionCacheDomain.entries.map { domain ->
            DynamicTest.dynamicTest(domain.telemetryDomain) {
                val probe = RetryingCloseProbe(closeFailuresRemaining = 1)
                val cache = GPUExecutionObjectCache(
                    domain = domain,
                    dispose = RetryingCloseProbe::close,
                )
                val request = cacheRequest(domain = domain)
                cache.getOrCreate(request) { probe }

                val incomplete = assertFailsWith<GPUOwnedNativeCloseIncompleteException> {
                    cache.close()
                }
                assertEquals("execution-cache-${domain.telemetryDomain}", incomplete.ownerLabel)
                assertEquals(1, incomplete.remainingOwnerCount)
                assertFailsWith<IllegalStateException> {
                    cache.getOrCreate(request) { RetryingCloseProbe() }
                }

                cache.close()
                cache.close()

                assertEquals(2, probe.closeAttempts)
                assertEquals(1, probe.successfulCloses)
            }
        }

    @Test
    fun `failed execution cache eviction retains the same entry for retry`() {
        val probe = RetryingCloseProbe(closeFailuresRemaining = 1)
        val cache = GPUExecutionObjectCache(
            domain = GPUExecutionCacheDomain.Module,
            dispose = RetryingCloseProbe::close,
        )
        val request = cacheRequest(domain = GPUExecutionCacheDomain.Module)
        cache.getOrCreate(request) { probe }

        assertFailsWith<IllegalStateException> { cache.evict(request) }
        val retained = assertIs<GPUExecutionCacheDecision.Ready<RetryingCloseProbe>>(
            cache.getOrCreate(request) { error("failed eviction must retain the existing entry") },
        )
        assertSame(probe, retained.handle)

        cache.evict(request)
        assertEquals(2, probe.closeAttempts)
        assertEquals(1, probe.successfulCloses)
    }

    @Test
    fun `runtime downstream teardown stops queue and window after persistent execution cache failure`() {
        val events = mutableListOf<String>()
        val queueCompletion = RuntimeCloseProbe("queue-completion", events)
        val executionCaches = RuntimeCloseProbe("execution-cache", events, closeFailuresRemaining = 2)
        val queueManager = RuntimeCloseProbe("queue-manager", events)
        val windowBackend = RuntimeCloseProbe("window-backend", events)
        val teardown = GPUBackendRuntimeDownstreamTeardown(
            queueCompletion = queueCompletion,
            executionCaches = executionCaches,
            queueManager = queueManager,
            windowBackend = windowBackend,
        )

        assertFailsWith<GPUOwnedNativeCloseIncompleteException> { teardown.close() }
        assertFailsWith<GPUOwnedNativeCloseIncompleteException> { teardown.close() }
        assertEquals(listOf("queue-completion"), events)
        assertEquals(0, queueManager.closeAttempts)
        assertEquals(0, windowBackend.closeAttempts)

        teardown.close()
        teardown.close()

        assertEquals(
            listOf("queue-completion", "execution-cache", "queue-manager", "window-backend"),
            events,
        )
        assertEquals(3, executionCaches.closeAttempts)
        listOf(queueCompletion, executionCaches, queueManager, windowBackend).forEach { owner ->
            assertEquals(1, owner.successfulCloses)
        }
    }

    @Test
    fun `execution cache records miss create and hit without leaking backend handles`() {
        val cache = GPUExecutionObjectCache<String>(domain = GPUExecutionCacheDomain.Module)
        val request = cacheRequest(domain = GPUExecutionCacheDomain.Module)

        val cold = assertIs<GPUExecutionCacheDecision.Ready<String>>(
            cache.getOrCreate(request) { "GPUShaderModule@secret-handle" },
        )
        val warm = assertIs<GPUExecutionCacheDecision.Ready<String>>(
            cache.getOrCreate(request) { error("warm lookup must not recreate") },
        )

        assertEquals("GPUShaderModule@secret-handle", cold.handle)
        assertEquals(cold.handle, warm.handle)
        assertEquals(
            listOf(GPUCacheEventResult.Miss, GPUCacheEventResult.Create),
            cold.cacheEvents.map { event -> event.result },
        )
        assertEquals(listOf(GPUCacheEventResult.Hit), warm.cacheEvents.map { event -> event.result })

        val ledger = (cold.cacheEvents + warm.cacheEvents).fold(GPUTelemetryLedger.empty()) { current, event ->
            current.recordCacheEvent(event)
        }
        val module = ledger.cacheTelemetry.single { telemetry -> telemetry.cacheName == "module" }
        assertEquals(1L, module.misses)
        assertEquals(1L, module.creations)
        assertEquals(1L, module.hits)

        val dump = (cold.dumpLines() + warm.dumpLines()).joinToString("\n")
        assertFalse(dump.contains("GPUShaderModule@secret-handle"))
        assertFalse(dump.contains("productRouteActivated=true"))
        assertEquals(
            listOf(
                "execution.cache domain=module result=miss key=module-key subject=module-subject deviceGeneration=7 owner=GPUResourceProvider releaseBlocking=false productRouteActivated=false",
                "execution.cache domain=module result=create key=module-key subject=module-subject deviceGeneration=7 owner=GPUResourceProvider releaseBlocking=false productRouteActivated=false",
            ),
            cold.dumpLines(),
        )
        assertEquals(
            listOf(
                "execution.cache domain=module result=hit key=module-key subject=module-subject deviceGeneration=7 owner=GPUResourceProvider releaseBlocking=false productRouteActivated=false",
            ),
            warm.dumpLines(),
        )
    }

    @Test
    fun `execution cache refuses stale generation and creation failure with telemetry`() {
        val cache = GPUExecutionObjectCache<String>(domain = GPUExecutionCacheDomain.RenderPipeline)
        val stale = assertIs<GPUExecutionCacheDecision.Refused>(
            cache.getOrCreate(
                cacheRequest(
                    domain = GPUExecutionCacheDomain.RenderPipeline,
                    deviceGeneration = GPUDeviceGenerationID(6),
                    expectedDeviceGeneration = GPUDeviceGenerationID(7),
                ),
            ) { "GPURenderPipeline@stale" },
        )
        val failed = assertIs<GPUExecutionCacheDecision.Refused>(
            cache.getOrCreate(cacheRequest(domain = GPUExecutionCacheDomain.RenderPipeline)) {
                error("wgpu validation rejected render pipeline")
            },
        )

        assertEquals("unsupported.execution.cache_device_generation_stale", stale.diagnosticCode)
        assertEquals(listOf(GPUCacheEventResult.StaleGeneration), stale.cacheEvents.map { it.result })
        assertEquals("unsupported.execution.cache_create_failed", failed.diagnosticCode)
        assertEquals(
            listOf(GPUCacheEventResult.Miss, GPUCacheEventResult.Failure),
            failed.cacheEvents.map { it.result },
        )
        val dump = (stale.dumpLines() + failed.dumpLines()).joinToString("\n")
        assertFalse(dump.contains("GPURenderPipeline@stale"))
        assertFalse(dump.contains("wgpu validation rejected render pipeline"))
        assertFalse(dump.contains("productRouteActivated=true"))
    }

    @Test
    fun `execution cache evicts by key and reports bind group layout materialization`() {
        val cache = GPUExecutionObjectCache<String>(domain = GPUExecutionCacheDomain.BindGroupLayout)
        val request = cacheRequest(
            domain = GPUExecutionCacheDomain.BindGroupLayout,
            keyHash = "layout-key",
            subjectHash = "layout-subject",
        )

        cache.getOrCreate(request) { "GPUBindGroupLayout@secret" }
        val evicted = cache.evict(request)
        val recreated = assertIs<GPUExecutionCacheDecision.Ready<String>>(
            cache.getOrCreate(request) { "GPUBindGroupLayout@secret-2" },
        )

        assertEquals(listOf(GPUCacheEventResult.Evict), evicted.cacheEvents.map { it.result })
        assertEquals(
            listOf(GPUCacheEventResult.Miss, GPUCacheEventResult.Create),
            recreated.cacheEvents.map { it.result },
        )
        val ledger = (evicted.cacheEvents + recreated.cacheEvents).fold(GPUTelemetryLedger.empty()) { current, event ->
            current.recordCacheEvent(event)
        }
        val layout = ledger.cacheTelemetry.single { telemetry -> telemetry.cacheName == "bind-group-layout" }
        assertEquals(1L, layout.evictions)
        assertEquals(1L, layout.creations)
    }

    @Test
    fun `execution cache records pipeline layout materialization without leaking handles`() {
        val cache = GPUExecutionObjectCache<String>(domain = GPUExecutionCacheDomain.PipelineLayout)
        val request = cacheRequest(
            domain = GPUExecutionCacheDomain.PipelineLayout,
            keyHash = "pipeline-layout-key",
            subjectHash = "pipeline-layout-subject",
        )

        val created = assertIs<GPUExecutionCacheDecision.Ready<String>>(
            cache.getOrCreate(request) { "GPUPipelineLayout@secret" },
        )

        val ledger = created.cacheEvents.fold(GPUTelemetryLedger.empty()) { current, event ->
            current.recordCacheEvent(event)
        }
        val layout = ledger.cacheTelemetry.single { telemetry -> telemetry.cacheName == "pipeline-layout" }
        assertEquals(1L, layout.misses)
        assertEquals(1L, layout.creations)
        assertFalse(created.dumpLines().joinToString("\n").contains("GPUPipelineLayout@secret"))
    }

    @Test
    fun `execution cache disposes backend handle when evicted`() {
        val cache = GPUExecutionObjectCache<CloseProbe>(
            domain = GPUExecutionCacheDomain.Module,
            dispose = CloseProbe::close,
        )
        val request = cacheRequest(domain = GPUExecutionCacheDomain.Module)
        val created = assertIs<GPUExecutionCacheDecision.Ready<CloseProbe>>(
            cache.getOrCreate(request) { CloseProbe() },
        )

        cache.evict(request)

        assertTrue(created.handle.closed)
    }

    private class CloseProbe : AutoCloseable {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
        }
    }

    private class RetryingCloseProbe(
        private var closeFailuresRemaining: Int = 0,
    ) {
        var closeAttempts: Int = 0
            private set
        var successfulCloses: Int = 0
            private set

        fun close() {
            closeAttempts += 1
            if (closeFailuresRemaining > 0) {
                closeFailuresRemaining -= 1
                error("execution cache dispose failed")
            }
            check(successfulCloses == 0) { "execution cache entry disposed more than once" }
            successfulCloses += 1
        }
    }

    private class RuntimeCloseProbe(
        private val label: String,
        private val events: MutableList<String>,
        private var closeFailuresRemaining: Int = 0,
    ) : AutoCloseable {
        var closeAttempts: Int = 0
            private set
        var successfulCloses: Int = 0
            private set

        override fun close() {
            closeAttempts += 1
            if (closeFailuresRemaining > 0) {
                closeFailuresRemaining -= 1
                error("$label close failed")
            }
            check(successfulCloses == 0) { "$label closed more than once" }
            successfulCloses += 1
            events += label
        }
    }

    private fun cacheRequest(
        domain: GPUExecutionCacheDomain,
        keyHash: String = "module-key",
        subjectHash: String = "module-subject",
        deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(7),
        expectedDeviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(7),
    ): GPUExecutionCacheRequest =
        GPUExecutionCacheRequest(
            domain = domain,
            keyHash = keyHash,
            subjectHash = subjectHash,
            deviceGeneration = deviceGeneration,
            expectedDeviceGeneration = expectedDeviceGeneration,
            ownerScope = "GPUResourceProvider",
        )
}
