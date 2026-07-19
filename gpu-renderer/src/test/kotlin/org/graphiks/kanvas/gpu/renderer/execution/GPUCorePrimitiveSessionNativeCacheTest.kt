package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.TextureDescriptor
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GPUCorePrimitiveSessionNativeCacheTest {
    @Test
    fun `analytic shape component exposes exact uniform80 dynamic binding`() {
        val descriptor = corePrimitiveBindGroupLayoutDescriptor(
            PRODUCTION_CORE_PRIMITIVE_ANALYTIC_SHAPE_COMPONENT_IDENTITY,
        )

        assertEquals(1, descriptor.entries.size)
        val uniform = requireNotNull(descriptor.entries.single().buffer)
        assertEquals(80uL, uniform.minBindingSize)
        assertTrue(uniform.hasDynamicOffset)
    }

    @Test
    fun `analytic shape cache identity is unique and reused within the twenty four entry ceiling`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val key = analyticShapeProductionKey()
        val first = cache.acquire(key).acquiredHandles()
        val second = cache.acquire(key.copy()).acquiredHandles()
        val direct = cache.acquire(productionKey()).acquiredHandles()

        assertSame(first, second)
        assertEquals(PRODUCTION_CORE_PRIMITIVE_ANALYTIC_SHAPE_COMPONENT_IDENTITY, first.componentIdentity)
        assertNotSame(first.bindGroupLayout, direct.bindGroupLayout)
        assertNotSame(first.shader, direct.shader)
        assertNotSame(first.pipelineLayout, direct.pipelineLayout)
        assertEquals(2, native.pipelineCreationCount)
        assertEquals(GPUCorePrimitiveNativeCacheCounters(2, 1, 0), cache.counters())
        assertEquals(24, CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES)

        cache.close()
    }

    @Test
    fun `coverage mask components expose exact uniform64 and sampled texture layouts`() {
        val producer = corePrimitiveBindGroupLayoutDescriptor(
            PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
        )
        val consumer = corePrimitiveBindGroupLayoutDescriptor(
            PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY,
        )

        assertEquals(1, producer.entries.size)
        assertEquals(0u, producer.entries.single().binding)
        val producerUniform = requireNotNull(producer.entries.single().buffer)
        assertEquals(64uL, producerUniform.minBindingSize)
        assertTrue(producerUniform.hasDynamicOffset)
        assertEquals(2, consumer.entries.size)
        assertEquals(0u, consumer.entries[0].binding)
        val consumerUniform = requireNotNull(consumer.entries[0].buffer)
        assertEquals(64uL, consumerUniform.minBindingSize)
        assertTrue(consumerUniform.hasDynamicOffset)
        assertEquals(1u, consumer.entries[1].binding)
        val texture = requireNotNull(consumer.entries[1].texture)
        assertEquals(GPUTextureSampleType.Float, texture.sampleType)
        assertEquals(GPUTextureViewDimension.TwoD, texture.viewDimension)
        assertEquals(false, texture.multisampled)
    }

    @Test
    fun `coverage mask producer pipelines share one component while consumer is isolated`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val keys = listOf(
            coverageMaskProducerProductionKey(
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
            ),
            coverageMaskProducerProductionKey(
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
            ),
            coverageMaskProducerProductionKey(
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
            ),
            coverageMaskProducerProductionKey(
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
            ),
            coverageMaskConsumerProductionKey(),
        )
        val first = keys.map { key -> cache.acquire(key).acquiredHandles() }
        val reused = keys.map { key -> cache.acquire(key).acquiredHandles() }
        val producers = first.dropLast(1)
        val consumer = first.last()

        first.zip(reused).forEach { (created, reacquired) ->
            assertSame(created.bindGroupLayout, reacquired.bindGroupLayout)
            assertSame(created.shader, reacquired.shader)
            assertSame(created.pipelineLayout, reacquired.pipelineLayout)
            assertSame(created.pipeline, reacquired.pipeline)
        }
        assertEquals(5, first.map { handles -> handles.pipeline }.toSet().size)

        producers.forEach { handles ->
            assertSame(producers.first().bindGroupLayout, handles.bindGroupLayout)
            assertSame(producers.first().shader, handles.shader)
            assertSame(producers.first().pipelineLayout, handles.pipelineLayout)
            assertNotSame(consumer.bindGroupLayout, handles.bindGroupLayout)
            assertNotSame(consumer.shader, handles.shader)
        }
        assertEquals(
            listOf(
                CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_SHADER_IDENTITY,
                CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_SHADER_IDENTITY,
            ),
            native.shaderIdentities,
        )
        assertEquals(2, native.creationCount("component.bindGroupLayout"))
        assertEquals(2, native.creationCount("component.shader"))
        assertEquals(2, native.creationCount("component.pipelineLayout"))
        assertEquals(5, native.pipelineCreationCount)
        assertEquals(GPUCorePrimitiveNativeCacheCounters(5, 5, 0), cache.counters())

        cache.close()
        keys.forEach { key ->
            assertEquals(1, native.closeCount("pipeline:${key.pipelineIdentity.program.name}"))
        }
        assertEquals(2, native.closeCount("component.pipelineLayout"))
        assertEquals(2, native.closeCount("component.shader"))
        assertEquals(2, native.closeCount("component.bindGroupLayout"))

        cache.close()
        keys.forEach { key ->
            assertEquals(1, native.closeCount("pipeline:${key.pipelineIdentity.program.name}"))
        }
        assertEquals(2, native.closeCount("component.pipelineLayout"))
        assertEquals(2, native.closeCount("component.shader"))
        assertEquals(2, native.closeCount("component.bindGroupLayout"))
    }

    @Test
    fun `concrete session frame pool creates and reuses exact public coverage mask resources`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        cache.acquire(
            coverageMaskProducerProductionKey(
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
            ),
        ).acquiredHandles()
        val consumerComponents =
            cache.acquire(coverageMaskConsumerProductionKey()).acquiredHandles()
        val requirement = GPUWgpu4kCorePrimitiveCoverageMaskRequirement(
            width = 63,
            height = 47,
            format = GPUTextureFormat.RGBA8Unorm,
            sampleCount = 1,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
        )
        val request = GPUWgpu4kCorePrimitiveFramePoolRequirements(
            deviceGeneration = GENERATION,
            vertexBytes = 64L,
            indexBytes = 48L,
            uniformBytes = 256L,
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
            coverageMask = requirement,
        )

        val first = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
            cache.acquireFrame(request),
        ).lease
        val firstMask = requireNotNull(first.handles.coverageMask)

        val textureDescriptor = native.textureDescriptors.single()
        assertEquals(63u, textureDescriptor.size.width)
        assertEquals(47u, textureDescriptor.size.height)
        assertEquals(GPUTextureFormat.RGBA8Unorm, textureDescriptor.format)
        assertEquals(1u, textureDescriptor.sampleCount)
        assertEquals(
            GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
            textureDescriptor.usage,
        )
        val producer = native.bindGroupDescriptors.single {
            it.label.contains("coverage-mask-producer")
        }
        val consumer = native.bindGroupDescriptors.single {
            it.label == "Kanvas.session.corePrimitive.framePool.coverageMask.consumerBindGroup"
        }
        assertEquals(1, producer.entries.size)
        assertEquals(2, consumer.entries.size)
        assertEquals(listOf(0u, 1u), consumer.entries.map { it.binding })
        assertSame(consumerComponents.bindGroupLayout, consumer.layout)
        val consumerUniform = assertIs<BufferBinding>(consumer.entries[0].resource)
        assertSame(first.handles.uniformBuffer, consumerUniform.buffer)
        assertEquals(64uL, consumerUniform.size)
        assertSame(firstMask.view, consumer.entries[1].resource)
        first.rollbackBeforeSubmit()

        val reused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
            cache.acquireFrame(request),
        ).lease
        val reusedMask = requireNotNull(reused.handles.coverageMask)
        assertSame(firstMask.texture, reusedMask.texture)
        assertSame(firstMask.view, reusedMask.view)
        assertSame(firstMask.consumerBindGroup, reusedMask.consumerBindGroup)
        assertEquals(1, native.textureDescriptors.size)
        assertEquals(
            GPUCorePrimitiveNativeCacheCounters(
                invariantCreations = 2L,
                coverageMaskTextureCreations = 1L,
                coverageMaskSlotReuses = 1L,
            ),
            cache.counters(),
        )
        reused.rollbackBeforeSubmit()

        cache.close()
        assertEquals(1, native.closeCount("Kanvas.session.corePrimitive.framePool.coverageMask.consumerBindGroup"))
        assertEquals(1, native.closeCount("Kanvas.session.corePrimitive.framePool.coverageMask.view"))
        assertEquals(1, native.closeCount("Kanvas.session.corePrimitive.framePool.coverageMask"))
    }

    @Test
    fun `concrete session frame pool owns and reuses exact target scoped 4x color attachment`() {
        val native = SessionNativeProxy()
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION)
        val key = productionKey().copy(
            pipelineIdentity = productionKey().pipelineIdentity.copy(sampleCount = 4),
        )
        cache.acquire(key).acquiredHandles()
        val msaaRequirement = GPUWgpu4kCorePrimitiveMsaaColorRequirement(
            target = GPUFrameTargetRef("target.core.4x"),
            colorAttachment = GPUTargetIdentity("msaa-color:target.core.4x:9"),
            deviceGeneration = GENERATION,
            targetGeneration = 9L,
            width = 63,
            height = 47,
        )
        val request = GPUWgpu4kCorePrimitiveFramePoolRequirements(
            deviceGeneration = GENERATION,
            vertexBytes = 64L,
            indexBytes = 48L,
            uniformBytes = 256L,
            sampleCount = 4,
            msaaColor = msaaRequirement,
        )

        val first = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
            cache.acquireFrame(request),
        ).lease
        val firstMsaa = requireNotNull(first.handles.msaaColor)
        val descriptor = native.textureDescriptors.single()
        assertEquals(63u, descriptor.size.width)
        assertEquals(47u, descriptor.size.height)
        assertEquals(GPUTextureFormat.RGBA8Unorm, descriptor.format)
        assertEquals(4u, descriptor.sampleCount)
        assertEquals(GPUTextureUsage.RenderAttachment, descriptor.usage)
        assertEquals(msaaRequirement, firstMsaa.requirement)
        first.rollbackBeforeSubmit()

        val reused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
            cache.acquireFrame(request),
        ).lease
        assertSame(firstMsaa.texture, requireNotNull(reused.handles.msaaColor).texture)
        assertSame(firstMsaa.view, requireNotNull(reused.handles.msaaColor).view)
        assertEquals(1, native.textureDescriptors.size)
        assertEquals(1L, cache.counters().msaaColorTextureCreations)
        assertEquals(1L, cache.counters().msaaColorSlotReuses)
        reused.rollbackBeforeSubmit()

        cache.close()
        assertEquals(1, native.closeCount("Kanvas.session.corePrimitive.framePool.msaaColor4x.view"))
        assertEquals(1, native.closeCount("Kanvas.session.corePrimitive.framePool.msaaColor4x"))
    }

    @Test
    fun `cache acquisition exposes no constructible raw invariant handles`() {
        assertTrue(GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired::class.java.isSealed)
        assertTrue(
            GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired::class.java.declaredConstructors.isEmpty(),
        )
        assertFailsWith<ClassNotFoundException> {
            Class.forName(
                "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveInvariantHandles",
            )
        }
    }

    @Test
    fun `prepared pipeline operand exposes no directly forgeable binding policy constructor`() {
        assertFalse(
            GPUPreparedNativeRenderPipelineOperand::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.any { type ->
                    type == GPUPreparedNativeRenderPipelineBindingPolicy::class.java
                }
            },
        )
    }

    @Test
    fun `acquired component identity derives the exact prepared pipeline binding policy`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val dynamicHandles = cache.acquire(productionKey()).acquiredHandles()
        val noBindingsHandles = cache.acquire(clipStencilProducerProductionKey()).acquiredHandles()

        val dynamicOperand = GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
            dynamicHandles,
            GENERATION,
        )
        val noBindingsOperand = GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
            noBindingsHandles,
            GENERATION,
        )

        assertEquals(PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY, dynamicHandles.componentIdentity)
        assertEquals(
            PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY,
            noBindingsHandles.componentIdentity,
        )
        assertSame(dynamicHandles.pipeline, dynamicOperand.pipeline)
        assertSame(noBindingsHandles.pipeline, noBindingsOperand.pipeline)
        assertEquals(
            GPUPreparedNativeRenderPipelineBindingPolicy.BindGroupRequired,
            dynamicOperand.bindingPolicy,
        )
        assertEquals(
            GPUPreparedNativeRenderPipelineBindingPolicy.NoBindings,
            noBindingsOperand.bindingPolicy,
        )

        cache.close()
    }

    @Test
    fun `non indexed draw requires a bind group for an acquired dynamic uniform pipeline`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val dynamicPipeline = GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
            cache.acquire(productionKey()).acquiredHandles(),
            GENERATION,
        )
        val target = GPUPreparedNativeTextureViewOperand(native.textureView("target"), GENERATION)

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 0,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(dynamicPipeline),
                    GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(vertexCount = 3)),
                ),
            )
        }

        cache.close()
    }

    @Test
    fun `non indexed draw accepts no bind group for an acquired no bindings pipeline`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val noBindingsPipeline = GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
            cache.acquire(clipStencilProducerProductionKey()).acquiredHandles(),
            GENERATION,
        )
        val target = GPUPreparedNativeTextureViewOperand(native.textureView("target"), GENERATION)

        GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 0,
            pass = GPUPreparedNativeRenderPassConfig(target),
            commands = listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(noBindingsPipeline),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(vertexCount = 3)),
            ),
        )

        cache.close()
    }

    @Test
    fun `concrete cache shares one component set across two factory accepted pipeline identities`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val direct = testKey("direct")
        val futureStencil = testKey("future-stencil")

        val directHandles = cache.acquire(direct).acquiredHandles()
        val stencilHandles = cache.acquire(futureStencil).acquiredHandles()

        assertSame(directHandles.bindGroupLayout, stencilHandles.bindGroupLayout)
        assertSame(directHandles.shader, stencilHandles.shader)
        assertSame(directHandles.pipelineLayout, stencilHandles.pipelineLayout)
        assertNotSame(directHandles.pipeline, stencilHandles.pipeline)
        assertSame(directHandles, cache.acquire(direct.copy()).acquiredHandles())
        assertSame(stencilHandles, cache.acquire(futureStencil.copy()).acquiredHandles())
        assertEquals(1, native.creationCount("component.bindGroupLayout"))
        assertEquals(1, native.creationCount("component.shader"))
        assertEquals(1, native.creationCount("component.pipelineLayout"))
        assertEquals(2, native.pipelineCreationCount)
        assertEquals(GPUCorePrimitiveNativeCacheCounters(2, 2, 0), cache.counters())

        cache.close()
    }

    @Test
    fun `production cache retains six exact pipelines with shared immutable components`() {
        val native = SessionNativeProxy()
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION)
        val programs = listOf(
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver,
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse,
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil,
        )

        val acquisitions = programs.map { program ->
            program to cache.acquire(productionKey(program)).acquiredHandles()
        }
        val first = acquisitions.first().second

        acquisitions.forEach { (_, handles) ->
            assertSame(first.bindGroupLayout, handles.bindGroupLayout)
            assertSame(first.shader, handles.shader)
            assertSame(first.pipelineLayout, handles.pipelineLayout)
        }
        programs.distinct().forEach { program ->
            val matching = acquisitions.filter { it.first == program }.map { it.second.pipeline }
            assertTrue(matching.all { it === matching.first() })
        }
        assertEquals(6, acquisitions.map { it.second.pipeline }.distinctBy { System.identityHashCode(it) }.size)
        assertEquals(1, native.creationCount("createBindGroupLayout"))
        assertEquals(1, native.creationCount("createShaderModule"))
        assertEquals(1, native.creationCount("createPipelineLayout"))
        assertEquals(6, native.pipelineCreationCount)
        assertEquals(GPUCorePrimitiveNativeCacheCounters(6, 5, 0), cache.counters())

        cache.close()
    }

    @Test
    fun `analytic programs share one uniform64 component set isolated from legacy uniform32`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val legacy = cache.acquire(productionKey()).acquiredHandles()
        val analyticPrograms = listOf(
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectHard,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectHard,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectAA,
        )
        val analytic = analyticPrograms.map { program ->
            cache.acquire(analyticProductionKey(program)).acquiredHandles()
        }

        analytic.forEach { handles ->
            assertSame(analytic.first().bindGroupLayout, handles.bindGroupLayout)
            assertSame(analytic.first().shader, handles.shader)
            assertSame(analytic.first().pipelineLayout, handles.pipelineLayout)
            assertNotSame(legacy.bindGroupLayout, handles.bindGroupLayout)
            assertNotSame(legacy.shader, handles.shader)
            assertNotSame(legacy.pipelineLayout, handles.pipelineLayout)
        }
        assertEquals(2, native.creationCount("component.bindGroupLayout"))
        assertEquals(2, native.creationCount("component.shader"))
        assertEquals(2, native.creationCount("component.pipelineLayout"))
        assertEquals(5, native.pipelineCreationCount)
        assertEquals(GPUCorePrimitiveNativeCacheCounters(5, 0, 0), cache.counters())

        cache.close()
    }

    @Test
    fun `analytic intersection4 reuses one uniform160 pipeline isolated from uniform32 and uniform64`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val legacy = cache.acquire(productionKey()).acquiredHandles()
        val analytic = cache.acquire(
            analyticProductionKey(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA),
        ).acquiredHandles()
        val intersectionKey = analyticIntersection4ProductionKey()
        val intersection = cache.acquire(intersectionKey).acquiredHandles()

        assertSame(intersection, cache.acquire(intersectionKey.copy()).acquiredHandles())
        assertNotSame(legacy.bindGroupLayout, intersection.bindGroupLayout)
        assertNotSame(legacy.shader, intersection.shader)
        assertNotSame(analytic.bindGroupLayout, intersection.bindGroupLayout)
        assertNotSame(analytic.shader, intersection.shader)
        assertEquals(3, native.creationCount("component.bindGroupLayout"))
        assertEquals(3, native.creationCount("component.shader"))
        assertEquals(3, native.creationCount("component.pipelineLayout"))
        assertEquals(3, native.pipelineCreationCount)
        assertEquals(GPUCorePrimitiveNativeCacheCounters(3, 1, 0), cache.counters())

        cache.close()
    }

    @Test
    fun `concrete cache accepts twenty four factory validated pipelines and typed refuses the twenty fifth`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val live = (0 until 24).associate { index ->
            val key = testKey("pipeline-$index")
            key to cache.acquire(key).acquiredHandles()
        }

        val refused = assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused>(
            cache.acquire(testKey("pipeline-24")),
        )

        assertEquals(
            GPUWgpu4kCorePrimitiveSessionCacheRefusal.Saturated(maxEntries = 24),
            refused.reason,
        )
        assertEquals(24, native.pipelineCreationCount)
        live.forEach { (key, handles) -> assertSame(handles, cache.acquire(key).acquiredHandles()) }
        assertEquals(GPUCorePrimitiveNativeCacheCounters(24, 24, 0), cache.counters())
        cache.close()
    }

    @Test
    fun `production cache typed refuses incompatible component and pipeline identities before native creation`() {
        val native = SessionNativeProxy()
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION)
        val canonical = productionKey()
        val incompatibleComponent = canonical.copy(
            componentIdentity = canonical.componentIdentity.copy(shaderIdentity = "shader.stale"),
        )
        val incompatiblePipeline = canonical.copy(
            pipelineIdentity = canonical.pipelineIdentity.copy(sampleCount = 2),
        )

        assertIs<GPUWgpu4kCorePrimitiveSessionCacheRefusal.IncompatibleComponentIdentity>(
            assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused>(
                cache.acquire(incompatibleComponent),
            ).reason,
        )
        assertEquals(
            GPUWgpu4kCorePrimitiveSessionCacheRefusal.UnsupportedPipelineIdentity(
                incompatiblePipeline.pipelineIdentity,
            ),
            assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused>(
                cache.acquire(incompatiblePipeline),
            ).reason,
        )
        assertTrue(native.creationEvents.isEmpty())
        cache.close()
    }

    @Test
    fun `production cache creates and reuses exact direct src over 4x pipeline`() {
        val native = SessionNativeProxy()
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION)
        val key = productionKey().copy(
            pipelineIdentity = productionKey().pipelineIdentity.copy(sampleCount = 4),
        )

        val first = cache.acquire(key).acquiredHandles()
        val second = cache.acquire(key).acquiredHandles()

        assertSame(first, second)
        assertEquals(4, key.pipelineIdentity.sampleCount)
        assertEquals(1, native.pipelineCreationCount)
        assertEquals(GPUCorePrimitiveNativeCacheCounters(1, 1, 0), cache.counters())
        cache.close()
    }

    @Test
    fun `second pipeline creation failure is transactional and keeps the first pipeline reusable`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true }, failPipelineCreationAttempt = 2)
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val firstKey = testKey("first")
        val secondKey = testKey("second")
        val first = cache.acquire(firstKey).acquiredHandles()

        val refused = assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused>(cache.acquire(secondKey))

        assertEquals(
            GPUWgpu4kCorePrimitiveSessionCacheNativeResource.RenderPipeline,
            assertIs<GPUWgpu4kCorePrimitiveSessionCacheRefusal.NativeCreationFailed>(refused.reason).resource,
        )
        assertSame(first, cache.acquire(firstKey).acquiredHandles())
        assertEquals(2, native.pipelineCreationCount)
        assertEquals(1, native.creationCount("component.bindGroupLayout"))
        assertEquals(1, native.creationCount("component.shader"))
        assertEquals(1, native.creationCount("component.pipelineLayout"))
        assertEquals(GPUCorePrimitiveNativeCacheCounters(1, 1, 0), cache.counters())
        cache.close()
    }

    @Test
    fun `first pipeline failure retains blocked component cleanup and retries it before allocation`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true }, failPipelineCreationAttempt = 1)
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val key = testKey("first")
        native.failCloseOnce("component.pipelineLayout")

        val refused = assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused>(cache.acquire(key))

        assertEquals(
            GPUWgpu4kCorePrimitiveSessionCacheNativeResource.RenderPipeline,
            assertIs<GPUWgpu4kCorePrimitiveSessionCacheRefusal.NativeCreationFailed>(refused.reason).resource,
        )
        assertEquals(
            2,
            assertIs<GPUWgpu4kCorePrimitiveSessionCacheRefusal.NativeCreationFailed>(refused.reason)
                .pendingCleanupHandles,
        )
        assertEquals(
            listOf("component.pipelineLayout", "component.shader"),
            native.closeEvents,
        )
        assertEquals(1, native.creationCount("component.bindGroupLayout"))
        assertEquals(1, native.creationCount("component.shader"))
        assertEquals(1, native.creationCount("component.pipelineLayout"))
        assertEquals(1, native.pipelineCreationCount)

        val recovered = cache.acquire(key).acquiredHandles()

        assertEquals(
            listOf(
                "component.pipelineLayout",
                "component.shader",
                "component.pipelineLayout",
                "component.bindGroupLayout",
            ),
            native.closeEvents,
        )
        assertEquals(2, native.creationCount("component.bindGroupLayout"))
        assertEquals(2, native.creationCount("component.shader"))
        assertEquals(2, native.creationCount("component.pipelineLayout"))
        assertEquals(2, native.pipelineCreationCount)
        assertSame(recovered, cache.acquire(key).acquiredHandles())
        assertEquals(GPUCorePrimitiveNativeCacheCounters(1, 1, 0), cache.counters())

        cache.close()
    }

    @Test
    fun `pipeline close failure blocks shared components and retry preserves dependency order`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        cache.acquire(testKey("first")).acquiredHandles()
        cache.acquire(testKey("second")).acquiredHandles()
        native.closeEvents.clear()
        native.failCloseOnce("pipeline:second")

        assertFailsWith<IllegalStateException> { cache.close() }
        assertEquals(listOf("pipeline:second", "pipeline:first"), native.closeEvents)

        cache.close()
        assertEquals(
            listOf(
                "pipeline:second",
                "pipeline:first",
                "pipeline:second",
                "component.pipelineLayout",
                "component.shader",
                "component.bindGroupLayout",
            ),
            native.closeEvents,
        )
        assertEquals(1, native.closeCount("pipeline:first"))
        assertEquals(2, native.closeCount("pipeline:second"))
        assertEquals(1, native.closeCount("component.pipelineLayout"))
        assertEquals(1, native.closeCount("component.shader"))
        assertEquals(1, native.closeCount("component.bindGroupLayout"))
    }

    @Test
    fun `pipeline layout close failure blocks only its bind group layout dependency`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        cache.acquire(testKey("direct")).acquiredHandles()
        native.closeEvents.clear()
        native.failCloseOnce("component.pipelineLayout")

        assertFailsWith<IllegalStateException> { cache.close() }
        assertEquals(
            listOf("pipeline:direct", "component.pipelineLayout", "component.shader"),
            native.closeEvents,
        )

        cache.close()
        assertEquals(
            listOf(
                "pipeline:direct",
                "component.pipelineLayout",
                "component.shader",
                "component.pipelineLayout",
                "component.bindGroupLayout",
            ),
            native.closeEvents,
        )
    }

    @Test
    fun `close and acquire are linearized and acquire refuses once close owns the cache`() {
        val native = SessionNativeProxy(acceptPipelineIdentity = { true })
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION, native)
        val key = testKey("direct")
        cache.acquire(key).acquiredHandles()
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        native.blockClose("pipeline:direct", closeEntered, releaseClose)
        val closeFailure = AtomicReference<Throwable?>()
        val closeThread = thread(name = "core-cache-close") {
            try {
                cache.close()
            } catch (failure: Throwable) {
                closeFailure.set(failure)
            }
        }
        assertTrue(closeEntered.await(5, TimeUnit.SECONDS))
        val acquireResult = AtomicReference<GPUWgpu4kCorePrimitiveSessionCacheAcquire?>()
        val acquireThread = thread(name = "core-cache-acquire") {
            acquireResult.set(cache.acquire(key))
        }

        releaseClose.countDown()
        closeThread.join(5_000)
        acquireThread.join(5_000)

        assertFalse(closeThread.isAlive)
        assertFalse(acquireThread.isAlive)
        closeFailure.get()?.let { throw it }
        assertEquals(
            GPUWgpu4kCorePrimitiveSessionCacheRefusal.Closed,
            assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused>(acquireResult.get()).reason,
        )
        assertEquals(1, native.pipelineCreationCount)
    }

    @Test
    fun `concrete session close drains frame pool before invariants and refuses live or submitted leases`() {
        listOf(false, true).forEach { submitted ->
            val native = SessionNativeProxy()
            val generation = GPUDeviceGenerationID(if (submitted) 42L else 41L)
            val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, generation)
            cache.acquire(productionKey()).acquiredHandles()
            val lease = cache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(generation, 64L, 48L, 512L),
            ).let { assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(it).lease }
            if (submitted) lease.markSubmitted()
            native.closeEvents.clear()

            assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseRefused> { cache.close() }
            assertTrue(native.closeEvents.isEmpty())

            if (submitted) lease.completeSuccessfully() else lease.rollbackBeforeSubmit()
            cache.close()
            assertEquals(
                listOf(
                    "Kanvas.session.corePrimitive.framePool.bindGroup0",
                    "Kanvas.session.corePrimitive.framePool.uniforms",
                    "Kanvas.session.corePrimitive.framePool.indices",
                    "Kanvas.session.corePrimitive.framePool.vertices",
                    "createRenderPipeline",
                    "createPipelineLayout",
                    "createShaderModule",
                    "createBindGroupLayout",
                ),
                native.closeEvents,
            )
        }
    }

    private fun productionKey(
        program: GPUWgpu4kCorePrimitivePipelineProgram =
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver,
    ) = GPUWgpu4kCorePrimitivePipelineCacheKey(
        componentIdentity = GPUWgpu4kCorePrimitiveComponentIdentity(
            shaderIdentity = CORE_PRIMITIVE_NATIVE_SHADER_IDENTITY,
            bindingLayoutIdentity = CORE_PRIMITIVE_NATIVE_BINDING_LAYOUT_IDENTITY,
            vertexLayoutIdentity = CORE_PRIMITIVE_NATIVE_VERTEX_LAYOUT_IDENTITY,
        ),
        pipelineIdentity = GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
            targetFormat = "rgba8unorm",
            sampleCount = 1,
            topology = "triangle-list",
            frontFace = "ccw",
            cullMode = "none",
            program = program,
        ),
    )

    private fun analyticProductionKey(
        program: GPUWgpu4kCorePrimitivePipelineProgram,
    ) = productionKey(program).copy(
        componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY,
    )

    private fun analyticShapeProductionKey() =
        productionKey(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeSrcOver).copy(
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_SHAPE_COMPONENT_IDENTITY,
        )

    private fun analyticIntersection4ProductionKey() =
        productionKey(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipIntersection4).copy(
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY,
        )

    private fun clipStencilProducerProductionKey() =
        productionKey(GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding).copy(
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY,
        )

    private fun coverageMaskProducerProductionKey(
        program: GPUWgpu4kCorePrimitivePipelineProgram,
    ) = productionKey(program).copy(
        componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
    )

    private fun coverageMaskConsumerProductionKey() =
        productionKey(GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest).copy(
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY,
        )

    private fun testKey(identity: String) = productionKey().copy(
        pipelineIdentity = productionKey().pipelineIdentity.copy(targetFormat = "test:$identity"),
    )

    private fun GPUWgpu4kCorePrimitiveSessionCacheAcquire.acquiredHandles() =
        assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired>(this)

    private class SessionNativeProxy(
        private val acceptPipelineIdentity: (GPUWgpu4kCorePrimitiveRenderPipelineIdentity) -> Boolean = {
            it == productionPipelineIdentity()
        },
        private val failPipelineCreationAttempt: Int? = null,
    ) : GPUWgpu4kCorePrimitiveSessionNativeFactory {
        val creationEvents = mutableListOf<String>()
        val closeEvents = mutableListOf<String>()
        private val closeAttempts = mutableMapOf<String, Int>()
        private val closeFailuresRemaining = mutableMapOf<String, Int>()
        private val closeBlocks = mutableMapOf<String, Pair<CountDownLatch, CountDownLatch>>()
        var pipelineCreationCount = 0
            private set
        val shaderIdentities = mutableListOf<String>()
        val bindGroupDescriptors = mutableListOf<BindGroupDescriptor>()
        val textureDescriptors = mutableListOf<TextureDescriptor>()

        val device: GPUDevice = proxy(GPUDevice::class.java) { method, args ->
            when (method.name) {
                "createBuffer" -> createdHandle(
                    (args?.firstOrNull() as BufferDescriptor).label.orEmpty(),
                    method.returnType,
                )
                "createBindGroup" -> {
                    val descriptor = args?.firstOrNull() as BindGroupDescriptor
                    bindGroupDescriptors += descriptor
                    createdHandle(descriptor.label.orEmpty(), method.returnType)
                }
                "createTexture" -> {
                    val descriptor = args?.firstOrNull() as TextureDescriptor
                    textureDescriptors += descriptor
                    texture(descriptor.label.orEmpty())
                }
                "createBindGroupLayout", "createShaderModule", "createPipelineLayout" ->
                    createdHandle(method.name, method.returnType)
                "createRenderPipeline" -> {
                    pipelineCreationCount += 1
                    createdHandle(method.name, method.returnType)
                }
                else -> defaultValue(method.returnType)
            }
        }

        override fun acceptsPipelineIdentity(
            identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
        ): Boolean = acceptPipelineIdentity(identity)

        override fun createBindGroupLayout(
            componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
        ): GPUBindGroupLayout =
            createdHandle("component.bindGroupLayout", GPUBindGroupLayout::class.java)

        override fun createShaderModule(
            componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
            plan: GPUCorePrimitiveNativeShaderPlan,
        ): GPUShaderModule {
            shaderIdentities += componentIdentity.shaderIdentity
            return createdHandle("component.shader", GPUShaderModule::class.java)
        }

        override fun createPipelineLayout(
            componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
            bindGroupLayout: GPUBindGroupLayout,
        ): GPUPipelineLayout =
            createdHandle("component.pipelineLayout", GPUPipelineLayout::class.java)

        override fun createRenderPipeline(
            identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
            shader: GPUShaderModule,
            pipelineLayout: GPUPipelineLayout,
        ): GPURenderPipeline {
            pipelineCreationCount += 1
            creationEvents += "pipeline:${identity.testLabel()}"
            if (pipelineCreationCount == failPipelineCreationAttempt) error("injected pipeline creation failure")
            return handle("pipeline:${identity.testLabel()}", GPURenderPipeline::class.java)
        }

        private fun GPUWgpu4kCorePrimitiveRenderPipelineIdentity.testLabel(): String =
            targetFormat.removePrefix("test:").takeIf { targetFormat.startsWith("test:") }
                ?: program.name

        fun creationCount(label: String): Int = creationEvents.count { it == label }

        fun closeCount(label: String): Int = closeAttempts.getOrDefault(label, 0)

        fun textureView(label: String): GPUTextureView = handle(label, GPUTextureView::class.java)

        private fun texture(label: String): GPUTexture = proxy(GPUTexture::class.java) { method, _ ->
            when (method.name) {
                "createView" -> createdHandle("$label.view", GPUTextureView::class.java)
                "close" -> recordClose(label)
                "toString" -> label
                else -> defaultValue(method.returnType)
            }
        }

        fun failCloseOnce(label: String) {
            closeFailuresRemaining[label] = 1
        }

        fun blockClose(label: String, entered: CountDownLatch, release: CountDownLatch) {
            closeBlocks[label] = entered to release
        }

        private fun <T> createdHandle(label: String, type: Class<T>): T {
            creationEvents += label
            return handle(label, type)
        }

        private fun <T> handle(label: String, type: Class<T>): T = proxy(type) { method, _ ->
            when (method.name) {
                "close" -> recordClose(label)
                "toString" -> label
                else -> defaultValue(method.returnType)
            }
        }

        private fun recordClose(label: String): Nothing? {
            closeEvents += label
            closeAttempts[label] = closeAttempts.getOrDefault(label, 0) + 1
            closeBlocks.remove(label)?.let { (entered, release) ->
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "timed out waiting to release $label close" }
            }
            val failures = closeFailuresRemaining.getOrDefault(label, 0)
            if (failures > 0) {
                closeFailuresRemaining[label] = failures - 1
                error("injected $label close failure")
            }
            return null
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(
            type: Class<T>,
            action: (java.lang.reflect.Method, Array<out Any?>?) -> Any?,
        ): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> action(method, args)
            }
        } as T

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }

        private companion object {
            fun productionPipelineIdentity() = GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
                targetFormat = "rgba8unorm",
                sampleCount = 1,
                topology = "triangle-list",
                frontFace = "ccw",
                cullMode = "none",
                program = GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver,
            )
        }
    }

    private companion object {
        val GENERATION = GPUDeviceGenerationID(41L)
    }
}
