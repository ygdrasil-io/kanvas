package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUBackendRuntimeCompositeOwnershipTest {
    @Test
    fun `real execution cache composite blocks every dependency behind an incomplete render pipeline tier`() {
        val events = mutableListOf<String>()
        val generation = GPUDeviceGenerationID(91)
        val module = populatedNativeCache(
            GPUExecutionCacheDomain.Module,
            GPUShaderModule::class.java,
            generation,
            RetryingNativeClose("module", events),
        )
        val bindGroupLayout = populatedNativeCache(
            GPUExecutionCacheDomain.BindGroupLayout,
            GPUBindGroupLayout::class.java,
            generation,
            RetryingNativeClose("bindGroupLayout", events),
        )
        val pipelineLayout = populatedNativeCache(
            GPUExecutionCacheDomain.PipelineLayout,
            GPUPipelineLayout::class.java,
            generation,
            RetryingNativeClose("pipelineLayout", events),
        )
        val renderPipelineProbe = RetryingNativeClose(
            label = "renderPipeline",
            events = events,
            closeFailuresRemaining = 2,
        )
        val renderPipeline = populatedNativeCache(
            GPUExecutionCacheDomain.RenderPipeline,
            GPURenderPipeline::class.java,
            generation,
            renderPipelineProbe,
        )
        val caches = WgpuExecutionCaches(
            deviceGeneration = generation,
            moduleCache = module.cache,
            bindGroupLayoutCache = bindGroupLayout.cache,
            pipelineLayoutCache = pipelineLayout.cache,
            renderPipelineCache = renderPipeline.cache,
        )

        repeat(2) {
            assertFailsWith<GPUOwnedNativeCloseIncompleteException> { caches.close() }
            assertEquals(emptyList(), events)
            assertEquals(0, pipelineLayout.probe.closeAttempts)
            assertEquals(0, bindGroupLayout.probe.closeAttempts)
            assertEquals(0, module.probe.closeAttempts)
        }

        caches.close()
        caches.close()

        assertEquals(
            listOf("renderPipeline", "pipelineLayout", "bindGroupLayout", "module"),
            events,
        )
        assertEquals(3, renderPipelineProbe.closeAttempts)
        listOf(renderPipeline, pipelineLayout, bindGroupLayout, module).forEach { owner ->
            assertEquals(1, owner.probe.successfulCloses)
        }
    }

    @Test
    fun `real window surface teardown retains queue and runtime behind incomplete execution caches`() {
        val events = mutableListOf<String>()
        val generation = GPUDeviceGenerationID(92)
        val module = populatedNativeCache(
            GPUExecutionCacheDomain.Module,
            GPUShaderModule::class.java,
            generation,
            RetryingNativeClose("module", events),
        )
        val bindGroupLayout = populatedNativeCache(
            GPUExecutionCacheDomain.BindGroupLayout,
            GPUBindGroupLayout::class.java,
            generation,
            RetryingNativeClose("bindGroupLayout", events),
        )
        val pipelineLayout = populatedNativeCache(
            GPUExecutionCacheDomain.PipelineLayout,
            GPUPipelineLayout::class.java,
            generation,
            RetryingNativeClose("pipelineLayout", events),
        )
        val renderPipelineProbe = RetryingNativeClose(
            label = "renderPipeline",
            events = events,
            closeFailuresRemaining = 2,
        )
        val renderPipeline = populatedNativeCache(
            GPUExecutionCacheDomain.RenderPipeline,
            GPURenderPipeline::class.java,
            generation,
            renderPipelineProbe,
        )
        val caches = WgpuExecutionCaches(
            deviceGeneration = generation,
            moduleCache = module.cache,
            bindGroupLayoutCache = bindGroupLayout.cache,
            pipelineLayoutCache = pipelineLayout.cache,
            renderPipelineCache = renderPipeline.cache,
        )
        val queueCompletion = RetryingNativeClose("queueCompletion", events)
        val adapter = RetryingNativeClose("adapter", events)
        val queueManager = RetryingNativeClose("queueManager", events)
        val runtime = RetryingNativeClose("runtime", events)
        val teardown = WgpuWindowSurfaceTeardown(
            queueCompletion = queueCompletion,
            adapter = adapter,
            executionCaches = caches,
            queueManager = queueManager,
            runtime = runtime,
        )

        repeat(2) {
            assertFailsWith<GPUOwnedNativeCloseIncompleteException> { teardown.close() }
            assertEquals(listOf("queueCompletion", "adapter"), events)
            assertEquals(0, queueManager.closeAttempts)
            assertEquals(0, runtime.closeAttempts)
        }

        teardown.close()
        teardown.close()

        assertEquals(
            listOf(
                "queueCompletion",
                "adapter",
                "renderPipeline",
                "pipelineLayout",
                "bindGroupLayout",
                "module",
                "queueManager",
                "runtime",
            ),
            events,
        )
        assertEquals(3, renderPipelineProbe.closeAttempts)
        listOf(queueCompletion, adapter, queueManager, runtime).forEach { owner ->
            assertEquals(1, owner.successfulCloses)
        }
    }

    private data class PopulatedNativeCache<T : AutoCloseable>(
        val cache: GPUExecutionObjectCache<T>,
        val probe: RetryingNativeClose,
    )

    private fun <T : AutoCloseable> populatedNativeCache(
        domain: GPUExecutionCacheDomain,
        type: Class<T>,
        generation: GPUDeviceGenerationID,
        probe: RetryingNativeClose,
    ): PopulatedNativeCache<T> {
        val cache = GPUExecutionObjectCache<T>(domain = domain, dispose = { it.close() })
        cache.getOrCreate(
            GPUExecutionCacheRequest(
                domain = domain,
                keyHash = "${domain.telemetryDomain}-key",
                subjectHash = "${domain.telemetryDomain}-subject",
                deviceGeneration = generation,
                expectedDeviceGeneration = generation,
                ownerScope = "WgpuExecutionCaches",
            ),
        ) {
            nativeCloseProxy(type, probe)
        }
        return PopulatedNativeCache(cache, probe)
    }

    private class RetryingNativeClose(
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

    private fun <T : AutoCloseable> nativeCloseProxy(
        type: Class<T>,
        probe: RetryingNativeClose,
    ): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
            when (method.name) {
                "close" -> probe.close()
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "getLabel" -> "composite-close-proxy"
                "setLabel" -> Unit
                "toString" -> "CompositeCloseProxy(${type.simpleName})"
                else -> error("Unexpected ${type.simpleName} call: ${method.name}")
            }
        },
    )
}
