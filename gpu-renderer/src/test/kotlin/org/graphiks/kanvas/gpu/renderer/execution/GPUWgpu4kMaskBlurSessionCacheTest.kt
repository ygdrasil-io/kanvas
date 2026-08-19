package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Headless session-cache coverage for the prepared top-level mask blur lane (Task 11).
 *
 * The cache is constructed over the same fake-device [GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy]
 * as the sibling suites, so counters, resize, per-format composite pipelines, and close
 * are all driven without a GPU. Pipeline-count pins: 4 invariant pipelines
 * (mask / blur-h / blur-v / style) plus 4 composite pipelines
 * (src-over / src / dst / solid) per scene format.
 */
class GPUWgpu4kMaskBlurSessionCacheTest {

    @Test
    fun `acquire creates invariant and intermediate handles once and reuses across leases`() {
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val cache = GPUWgpu4kMaskBlurSessionCache(native.device)

        val first = cache.acquire(32, 32)
        val second = cache.acquire(32, 32)

        val counters = cache.counters()
        assertEquals(1L, counters.invariantCreations)
        assertEquals(1L, counters.invariantReuses)
        assertEquals(1L, counters.intermediateCreations)
        assertEquals(1L, counters.intermediateReuses)
        // Exactly four invariant pipelines (mask, blur-h, blur-v, style).
        assertEquals(4, native.renderPipelineDescriptors.size)
        assertEquals(4, native.renderPipelineDescriptors.count {
            requireNotNull(it.label).startsWith("Kanvas.session.maskBlur.")
        })
        assertSame(first.invariants.maskPipeline, second.invariants.maskPipeline)
        assertSame(first.intermediates.maskView, second.intermediates.maskView)
        cache.close()
    }

    @Test
    fun `acquire resizes intermediates on local size change`() {
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val cache = GPUWgpu4kMaskBlurSessionCache(native.device)

        val first = cache.acquire(32, 32)
        val resized = cache.acquire(64, 32)

        val counters = cache.counters()
        assertEquals(1L, counters.invariantCreations)
        assertEquals(2L, counters.intermediateCreations)
        assertEquals(64, resized.intermediates.width)
        assertEquals(32, resized.intermediates.height)
        assertNotSame(first.intermediates.maskView, resized.intermediates.maskView)
        // Invariants survive the intermediate resize.
        assertSame(first.invariants.maskPipeline, resized.invariants.maskPipeline)
        assertEquals(4, native.renderPipelineDescriptors.size)
        cache.close()
    }

    @Test
    fun `acquire rejects local mask sizes outside the closed one-to-4096 range`() {
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val cache = GPUWgpu4kMaskBlurSessionCache(native.device)

        assertFailsWith<IllegalArgumentException> { cache.acquire(0, 32) }
        assertFailsWith<IllegalArgumentException> { cache.acquire(32, 0) }
        assertFailsWith<IllegalArgumentException> { cache.acquire(4097, 32) }
        assertFailsWith<IllegalArgumentException> { cache.acquire(32, 4097) }
        cache.close()
    }

    @Test
    fun `composite pipelines are created once per scene format with the four closed pipelines`() {
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val cache = GPUWgpu4kMaskBlurSessionCache(native.device)
        cache.acquire(32, 32)

        val unorm = cache.acquireCompositePipelines(GPUTextureFormat.RGBA8Unorm)
        val unormAgain = cache.acquireCompositePipelines(GPUTextureFormat.RGBA8Unorm)
        val srgb = cache.acquireCompositePipelines(GPUTextureFormat.RGBA8UnormSrgb)

        assertSame(unorm.srcOverPipeline, unormAgain.srcOverPipeline)
        assertSame(unorm.srcPipeline, unormAgain.srcPipeline)
        assertSame(unorm.dstPipeline, unormAgain.dstPipeline)
        assertSame(unorm.solidPipeline, unormAgain.solidPipeline)
        assertNotSame(unorm.srcOverPipeline, srgb.srcOverPipeline)
        assertEquals(GPUTextureFormat.RGBA8Unorm, unorm.sceneFormat)
        assertEquals(GPUTextureFormat.RGBA8UnormSrgb, srgb.sceneFormat)
        // 4 invariant + 4 per-format composite pipelines; the second unorm acquire is cached.
        assertEquals(12, native.renderPipelineDescriptors.size)
        assertEquals(
            listOf(
                "Kanvas.session.maskBlur.composite-src-over.pipeline",
                "Kanvas.session.maskBlur.composite-src.pipeline",
                "Kanvas.session.maskBlur.composite-dst.pipeline",
                "Kanvas.session.maskBlur.solid.pipeline",
            ),
            native.renderPipelineDescriptors.map { it.label }
                .filter { it.contains("maskBlur.composite") || it.contains("maskBlur.solid") }
                .distinct(),
        )
        cache.close()
    }

    @Test
    fun `close is idempotent and closes every cached handle`() {
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val cache = GPUWgpu4kMaskBlurSessionCache(native.device)
        cache.acquire(32, 32)
        cache.acquireCompositePipelines(GPUTextureFormat.RGBA8Unorm)

        cache.close()
        cache.close()

        assertEquals(1, native.closeAttempts("Kanvas.session.maskBlur.maskTexture"))
        assertEquals(1, native.closeAttempts("Kanvas.session.maskBlur.mask.pipeline"))
        assertEquals(1, native.closeAttempts("Kanvas.session.maskBlur.composite-dst.pipeline"))
    }

    @Test
    fun `acquire after close throws`() {
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val cache = GPUWgpu4kMaskBlurSessionCache(native.device)
        cache.close()

        assertFailsWith<IllegalStateException> { cache.acquire(32, 32) }
        assertFailsWith<IllegalStateException> {
            cache.acquireCompositePipelines(GPUTextureFormat.RGBA8Unorm)
        }
    }

    @Test
    fun `counters stay zero until the first acquire`() {
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val cache = GPUWgpu4kMaskBlurSessionCache(native.device)

        assertEquals(GPUMaskBlurNativeCacheCounters(), cache.counters())
        cache.close()
    }
}
