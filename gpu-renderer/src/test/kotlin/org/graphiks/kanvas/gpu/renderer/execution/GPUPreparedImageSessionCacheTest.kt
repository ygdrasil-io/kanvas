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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePipelineKey
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageRouteCapability

class GPUPreparedImageSessionCacheTest {
    @Test
    fun `invalid WGSL crosses the real cache boundary without creating native handles`() {
        val generation = GPUDeviceGenerationID(6)
        val native = TrackingDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            device = native.device,
            deviceGeneration = generation,
            shaderSource = "@fragment fn broken(",
        )

        val refused = assertIs<GPUPreparedImageCacheAcquire.Refused>(
            cache.acquire(PIPELINE_KEY, generation),
        )

        assertEquals(GPUPreparedImageRefusalCodes.WGSL_VALIDATION, refused.code)
        assertTrue(native.handles.isEmpty())
        cache.close()
    }

    @Test
    fun `failed invariant rollback retains pending ownership and closes it on retry`() {
        val generation = GPUDeviceGenerationID(6)
        val counters = GPUPreparedImageNativeCounterRecorder()
        val native = TrackingDevice(
            failCloseOnceFor = "createBindGroupLayout",
            failCreateFor = "createPipelineLayout",
        )
        val cache = GPUWgpu4kPreparedImageSessionCache(
            native.device,
            generation,
            counters,
        )

        val creationFailure = assertFailsWith<IllegalStateException> {
            cache.acquire(PIPELINE_KEY, generation)
        }

        assertEquals("createPipelineLayout creation failed", creationFailure.message)
        assertEquals(
            listOf(
                "createShaderModule#1:success",
                "createBindGroupLayout#1:failure",
            ),
            native.closeEvents,
        )
        val closingFailure = assertFailsWith<IllegalStateException> {
            cache.acquire(PIPELINE_KEY, generation)
        }
        assertContains(closingFailure.message.orEmpty(), "cache is closing")

        cache.close()
        cache.close()

        assertEquals(
            listOf(
                "createShaderModule#1:success",
                "createBindGroupLayout#1:failure",
                "createBindGroupLayout#2:success",
            ),
            native.closeEvents,
        )
        assertEquals(
            mapOf(
                "createBindGroupLayout" to 2,
                "createShaderModule" to 1,
            ),
            native.handles.associate { it.label to it.closeCalls },
        )
        assertEquals(0L, counters.snapshot().pipelineCreations)
        assertEquals(0L, counters.snapshot().pipelineReuses)
    }

    @Test
    fun `successful invariant rollback keeps the cache active without double close`() {
        val generation = GPUDeviceGenerationID(6)
        val native = TrackingDevice(failCreateFor = "createShaderModule")
        val cache = GPUWgpu4kPreparedImageSessionCache(native.device, generation)

        assertFailsWith<IllegalStateException> {
            cache.acquire(PIPELINE_KEY, generation)
        }

        assertEquals(
            listOf("createBindGroupLayout#1:success"),
            native.closeEvents,
        )
        val empty = assertIs<GPUPreparedImageCacheBatchAcquire.Ready>(
            cache.acquireBatch(emptyList(), generation),
        )
        assertTrue(empty.pipelinesByKey.isEmpty())

        cache.close()

        assertEquals(
            mapOf("createBindGroupLayout" to 1),
            native.handles.associate { it.label to it.closeCalls },
        )
    }

    @Test
    fun `failed native pipeline creation does not increment prepared image counters`() {
        val generation = GPUDeviceGenerationID(6)
        val counters = GPUPreparedImageNativeCounterRecorder()
        val native = TrackingDevice(failCreateFor = "createRenderPipeline")
        val cache = GPUWgpu4kPreparedImageSessionCache(
            native.device,
            generation,
            counters,
        )

        assertFailsWith<IllegalStateException> {
            cache.acquire(PIPELINE_KEY, generation)
        }

        assertEquals(0L, counters.snapshot().pipelineCreations)
        assertEquals(0L, counters.snapshot().pipelineReuses)
        cache.close()
    }

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
    fun `close failure retains only pending handles and retries without closing dependencies early`() {
        val generation = GPUDeviceGenerationID(7)
        val native = TrackingDevice(failCloseOnceFor = "createPipelineLayout")
        val cache = GPUWgpu4kPreparedImageSessionCache(native.device, generation)
        assertIs<GPUPreparedImageCacheAcquire.Ready>(cache.acquire(PIPELINE_KEY, generation))

        val incomplete = assertFailsWith<GPUOwnedNativeCloseIncompleteException> {
            cache.close()
        }

        assertEquals(3, incomplete.remainingOwnerCount)
        assertEquals(
            listOf(
                "createRenderPipeline#1:success",
                "createPipelineLayout#1:failure",
            ),
            native.closeEvents,
        )
        assertFailsWith<IllegalStateException> {
            cache.acquireBatch(emptyList(), generation)
        }

        cache.close()
        cache.close()

        assertEquals(
            listOf(
                "createRenderPipeline#1:success",
                "createPipelineLayout#1:failure",
                "createPipelineLayout#2:success",
                "createShaderModule#1:success",
                "createBindGroupLayout#1:success",
            ),
            native.closeEvents,
        )
        assertEquals(
            mapOf(
                "createBindGroupLayout" to 1,
                "createShaderModule" to 1,
                "createPipelineLayout" to 2,
                "createRenderPipeline" to 1,
            ),
            native.handles.associate { it.label to it.closeCalls },
        )
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
                targetFormat = "rgba8unorm-srgb",
            ),
            PIPELINE_KEY.copy(
                destinationBlendState = "src_over",
                targetFormat = "RGBA8UNORMSRGB",
            ),
            PIPELINE_KEY.copy(
                destinationBlendState =
                    "fixed:SRC_OVER:None:one_isa:one:one-minus-src-alpha:" +
                        "add:one:one-minus-src-alpha:add:rgba",
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
        assertEquals(GPUTextureFormat.RGBA8UnormSrgb, target.format)
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
    fun `batch keeps bounded capability separate from generic native pipeline`() {
        val generation = GPUDeviceGenerationID(7)
        val native = TrackingDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(native.device, generation)
        val generic = PIPELINE_KEY.copy(
            routeCapability = GPUPreparedImageRouteCapability.GenericNative,
        )
        val bounded = PIPELINE_KEY.copy(
            routeCapability = GPUPreparedImageRouteCapability.BoundedNearest1To1,
        )

        val ready = assertIs<GPUPreparedImageCacheBatchAcquire.Ready>(
            cache.acquireBatch(listOf(generic, bounded), generation),
        )

        assertNotSame(
            ready.pipelinesByKey.getValue(generic),
            ready.pipelinesByKey.getValue(bounded),
        )
        assertEquals(5, native.handles.size)
        cache.close()
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

        assertEquals(GPUPreparedImageRefusalCodes.IMAGE_PROFILE_CONVERSION, refused.code)
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

        assertEquals(GPUPreparedImageRefusalCodes.IMAGE_PROFILE_CONVERSION, formatRefusal.code)
        assertContains(formatRefusal.message, "BGRA8Unorm")
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, blendRefusal.code)
        assertContains(blendRefusal.message, "multiply")
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, layoutRefusal.code)
        assertContains(layoutRefusal.message, "foreign-layout")
        assertTrue(native.handles.isEmpty())

        cache.close()
    }

    private class TrackingDevice(
        private val failCloseOnceFor: String? = null,
        private val failCreateFor: String? = null,
    ) {
        val handles = mutableListOf<TrackedHandle>()
        val closeEvents = mutableListOf<String>()
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
            if (label == failCreateFor) error("$label creation failed")
            val tracked = TrackedHandle(
                label = label,
                closeFailuresRemaining = if (label == failCloseOnceFor) 1 else 0,
            )
            val native = proxy(type) { methodName, returnType, args ->
                when (methodName) {
                    "close" -> {
                        tracked.closeCalls += 1
                        val event = "$label#${tracked.closeCalls}"
                        if (tracked.closeFailuresRemaining > 0) {
                            tracked.closeFailuresRemaining -= 1
                            closeEvents += "$event:failure"
                            error("$label close failed")
                        }
                        closeEvents += "$event:success"
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

    private class TrackedHandle(
        val label: String,
        var closeFailuresRemaining: Int,
    ) {
        lateinit var native: Any
        var closeCalls: Int = 0

        override fun toString(): String = "$label(closeCalls=$closeCalls)"
    }

    private companion object {
        val PIPELINE_KEY = GPUPreparedImagePipelineKey(
            destinationBlendState = "src-over",
            targetFormat = "RGBA8UnormSrgb",
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
