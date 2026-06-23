package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheEventResult
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryLedger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUExecutionCacheContractsTest {
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
                    deviceGeneration = GPUDeviceGeneration(6),
                    expectedDeviceGeneration = GPUDeviceGeneration(7),
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

    private fun cacheRequest(
        domain: GPUExecutionCacheDomain,
        keyHash: String = "module-key",
        subjectHash: String = "module-subject",
        deviceGeneration: GPUDeviceGeneration = GPUDeviceGeneration(7),
        expectedDeviceGeneration: GPUDeviceGeneration = GPUDeviceGeneration(7),
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
