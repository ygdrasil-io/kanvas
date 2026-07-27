package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePipelineKey

class GPUPreparedImageSessionCacheTest {
    @Test
    fun `generation mismatch refuses before lookup or handle creation without mutating the cache`() {
        val generation7 = GPUDeviceGenerationID(7)
        val generation8 = GPUDeviceGenerationID(8)
        val native = TrackingDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(native.device, generation7)

        val first = assertIs<GPUPreparedImageCacheAcquire.Ready>(
            cache.acquire(PIPELINE_KEY, generation7),
        )
        val generation7Handles = native.handles.toList()
        assertEquals(4, generation7Handles.size)

        val refused = assertIs<GPUPreparedImageCacheAcquire.Refused>(
            cache.acquire(PIPELINE_KEY, generation8),
        )

        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_GENERATION, refused.code)
        assertContains(refused.message, "expected=7")
        assertContains(refused.message, "actual=8")
        assertEquals(generation7, cache.deviceGeneration)
        assertEquals(generation7Handles, native.handles)
        assertSame(
            first.pipeline,
            assertIs<GPUPreparedImageCacheAcquire.Ready>(
                cache.acquire(PIPELINE_KEY, generation7),
            ).pipeline,
        )
        assertEquals(generation7Handles, native.handles)
        assertTrue(generation7Handles.all { it.closeCalls == 0 })

        cache.close()
        cache.close()

        assertTrue(generation7Handles.all { it.closeCalls == 1 })
    }

    @Test
    fun `replacement generation constructs a separate cache only after the old cache closes`() {
        val generation7 = GPUDeviceGenerationID(7)
        val generation8 = GPUDeviceGenerationID(8)
        val native7 = TrackingDevice()
        val cache7 = GPUWgpu4kPreparedImageSessionCache(native7.device, generation7)
        assertIs<GPUPreparedImageCacheAcquire.Ready>(cache7.acquire(PIPELINE_KEY, generation7))

        cache7.close()

        val native8 = TrackingDevice()
        val cache8 = GPUWgpu4kPreparedImageSessionCache(native8.device, generation8)
        val replacement = assertIs<GPUPreparedImageCacheAcquire.Ready>(
            cache8.acquire(PIPELINE_KEY, generation8),
        )

        assertEquals(generation8, cache8.deviceGeneration)
        assertEquals(generation8, replacement.pipeline.deviceGeneration)
        assertEquals(4, native7.handles.size)
        assertEquals(4, native8.handles.size)
        assertTrue(native7.handles.all { it.closeCalls == 1 })
        assertTrue(native8.handles.all { it.closeCalls == 0 })

        cache8.close()

        assertTrue(native8.handles.all { it.closeCalls == 1 })
    }

    @Test
    fun `supported alias batch preserves raw mapping and drives one native descriptor`() {
        val generation = GPUDeviceGenerationID(7)
        val native = TrackingDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(native.device, generation)
        val aliases = listOf(
            PIPELINE_KEY,
            PIPELINE_KEY.copy(
                destinationBlendState = "SrcOver",
                targetFormat = "rgba8unorm",
            ),
            PIPELINE_KEY.copy(
                destinationBlendState = "src_over",
                targetFormat = "RGBA8UNORM",
            ),
        )

        val ready = assertIs<GPUPreparedImageCacheBatchAcquire.Ready>(
            cache.acquireBatch(aliases, generation),
        )
        val pipelines = aliases.map(ready.pipelinesByKey::getValue)

        assertEquals(aliases, ready.pipelinesByKey.keys.toList())
        assertTrue(pipelines.all { it === pipelines.first() })
        assertEquals(4, native.handles.size)
        val descriptor = native.renderPipelineDescriptors.single()
        assertTrue(
            native.handles.single { it.label == "createPipelineLayout" }.native ===
                descriptor.layout,
        )
        val target = requireNotNull(descriptor.fragment).targets.single()
        assertEquals(GPUTextureFormat.RGBA8Unorm, target.format)
        val blend = requireNotNull(target.blend)
        assertEquals(GPUBlendOperation.Add, blend.color.operation)
        assertEquals(GPUBlendFactor.One, blend.color.srcFactor)
        assertEquals(GPUBlendFactor.OneMinusSrcAlpha, blend.color.dstFactor)
        assertEquals(GPUBlendOperation.Add, blend.alpha.operation)
        assertEquals(GPUBlendFactor.One, blend.alpha.srcFactor)
        assertEquals(GPUBlendFactor.OneMinusSrcAlpha, blend.alpha.dstFactor)

        cache.close()
        cache.close()

        assertTrue(native.handles.all { it.closeCalls == 1 })
    }

    @Test
    fun `valid then unsupported batch refuses before any native handle creation`() {
        val generation = GPUDeviceGenerationID(7)
        val native = TrackingDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(native.device, generation)

        val refused = assertIs<GPUPreparedImageCacheBatchAcquire.Refused>(
            cache.acquireBatch(
                listOf(
                    PIPELINE_KEY,
                    PIPELINE_KEY.copy(targetFormat = "BGRA8Unorm"),
                ),
                generation,
            ),
        )

        assertEquals(GPUPreparedImageRefusalCodes.PIXEL_FORMAT, refused.code)
        assertContains(refused.message, "BGRA8Unorm")
        assertTrue(native.handles.isEmpty())

        cache.close()
    }

    @Test
    fun `unsupported descriptor axes refuse before any native handle creation`() {
        val generation = GPUDeviceGenerationID(7)
        val native = TrackingDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(native.device, generation)

        val formatRefusal = assertIs<GPUPreparedImageCacheAcquire.Refused>(
            cache.acquire(PIPELINE_KEY.copy(targetFormat = "BGRA8Unorm"), generation),
        )
        val blendRefusal = assertIs<GPUPreparedImageCacheAcquire.Refused>(
            cache.acquire(PIPELINE_KEY.copy(destinationBlendState = "multiply"), generation),
        )
        val layoutRefusal = assertIs<GPUPreparedImageCacheAcquire.Refused>(
            cache.acquire(PIPELINE_KEY.copy(bindingLayoutHash = "foreign-layout"), generation),
        )

        assertEquals(GPUPreparedImageRefusalCodes.PIXEL_FORMAT, formatRefusal.code)
        assertContains(formatRefusal.message, "BGRA8Unorm")
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, blendRefusal.code)
        assertContains(blendRefusal.message, "multiply")
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, layoutRefusal.code)
        assertContains(layoutRefusal.message, "foreign-layout")
        assertTrue(native.handles.isEmpty())

        cache.close()
    }

    private class TrackingDevice {
        val handles = mutableListOf<TrackedHandle>()
        val renderPipelineDescriptors = mutableListOf<RenderPipelineDescriptor>()
        val device: GPUDevice = proxy(GPUDevice::class.java) { methodName, returnType, args ->
            when (methodName) {
                "createBindGroupLayout",
                "createShaderModule",
                "createPipelineLayout",
                -> track(methodName, returnType)
                "createRenderPipeline" -> {
                    renderPipelineDescriptors += args?.firstOrNull() as RenderPipelineDescriptor
                    track(methodName, returnType)
                }
                else -> defaultValue(returnType)
            }
        }

        private fun track(label: String, type: Class<*>): Any {
            val tracked = TrackedHandle(label)
            val native = proxy(type) { methodName, returnType, args ->
                when (methodName) {
                    "close" -> {
                        tracked.closeCalls += 1
                        Unit
                    }
                    "toString" -> label
                    "hashCode" -> System.identityHashCode(tracked)
                    "equals" -> tracked.native === args?.singleOrNull()
                    else -> defaultValue(returnType)
                }
            }
            tracked.native = native
            handles += tracked
            return native
        }
    }

    private class TrackedHandle(val label: String) {
        lateinit var native: Any
        var closeCalls: Int = 0

        override fun toString(): String = "$label(closeCalls=$closeCalls)"
    }

    private companion object {
        val PIPELINE_KEY = GPUPreparedImagePipelineKey(
            destinationBlendState = "src-over",
            targetFormat = "RGBA8Unorm",
            bindingLayoutHash = "prepared-image.group0.dynamic-uniform-texture-sampler.v1",
        )

        private fun <T> proxy(
            type: Class<T>,
            handler: (methodName: String, returnType: Class<*>, args: Array<out Any?>?) -> Any?,
        ): T = type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                handler(method.name, method.returnType, args)
            },
        )

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Void.TYPE -> Unit
            else -> null
        }
    }
}
