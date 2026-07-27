package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUDevice
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePipelineKey

class GPUBackendRuntimePreparedImageCacheLifecycleTest {
    @Test
    fun `runtime tears down prepared image cache before device caches and target`() {
        val generation = GPUDeviceGenerationID(7)
        val imageCache = GPUWgpu4kPreparedImageSessionCache(
            inertDevice("generation-7"),
            generation,
        )
        val events = mutableListOf<String>()
        val teardown = GPUPreparedSceneChildTeardown(
            ownerTiers = preparedSceneChildOwnerTiers(
                activityOwners = listOf(
                    AutoCloseable {
                        assertIs<GPUPreparedImageCacheBatchAcquire.Ready>(
                            imageCache.acquireBatch(emptyList(), generation),
                        )
                        events += "activity"
                    },
                ),
                preparedImageCache = imageCache,
                deviceCacheOwners = listOf(
                    AutoCloseable {
                        assertFailsWith<IllegalStateException> {
                            imageCache.acquireBatch(emptyList(), generation)
                        }
                        events += "device-cache"
                    },
                ),
                target = AutoCloseable {
                    assertFailsWith<IllegalStateException> {
                        imageCache.acquireBatch(emptyList(), generation)
                    }
                    events += "target"
                },
            ),
            releaseLease = AutoCloseable { events += "lease" },
        )

        teardown.close()
        teardown.close()

        assertEquals(listOf("activity", "device-cache", "target", "lease"), events)
    }

    @Test
    fun `runtime retries an incomplete image cache before closing dependent caches and target`() {
        val generation = GPUDeviceGenerationID(7)
        val events = mutableListOf<String>()
        val native = RetryingPreparedImageDevice(
            events = events,
            failCloseOnceFor = "createPipelineLayout",
        )
        val imageCache = GPUWgpu4kPreparedImageSessionCache(native.device, generation)
        assertIs<GPUPreparedImageCacheAcquire.Ready>(
            imageCache.acquire(PREPARED_IMAGE_PIPELINE_KEY, generation),
        )
        val teardown = GPUPreparedSceneChildTeardown(
            ownerTiers = preparedSceneChildOwnerTiers(
                activityOwners = listOf(AutoCloseable { events += "activity" }),
                preparedImageCache = imageCache,
                deviceCacheOwners = listOf(AutoCloseable { events += "device-cache" }),
                target = AutoCloseable { events += "target" },
            ),
            releaseLease = AutoCloseable { events += "lease" },
        )

        val incomplete = assertFailsWith<GPUOwnedNativeCloseIncompleteException> {
            teardown.close()
        }

        assertEquals(1, incomplete.remainingOwnerCount)
        assertEquals(
            listOf(
                "activity",
                "createRenderPipeline#1:success",
                "createPipelineLayout#1:failure",
            ),
            events,
        )

        teardown.close()
        teardown.close()

        assertEquals(
            listOf(
                "activity",
                "createRenderPipeline#1:success",
                "createPipelineLayout#1:failure",
                "createPipelineLayout#2:success",
                "createShaderModule#1:success",
                "createBindGroupLayout#1:success",
                "device-cache",
                "target",
                "lease",
            ),
            events,
        )
    }

    @Test
    fun `child registry constructs generation 8 cache only after generation 7 child teardown`() {
        val events = mutableListOf<String>()
        val generation7 = GPUDeviceGenerationID(7)
        val native7 = RetryingPreparedImageDevice(
            events = events,
            failCloseOnceFor = "never",
            eventPrefix = "g7",
        )
        val device7 = native7.device
        val cache7 = GPUWgpu4kPreparedImageSessionCache(
            device7,
            generation7,
        )
        val acquired7 = assertIs<GPUPreparedImageCacheAcquire.Ready>(
            cache7.acquire(PREPARED_IMAGE_PIPELINE_KEY, generation7),
        )
        val pipeline7 = acquired7.pipeline.pipeline
        val generation8 = GPUDeviceGenerationID(8)
        val native8 = RetryingPreparedImageDevice(
            events = events,
            failCloseOnceFor = "never",
            eventPrefix = "g8",
        )
        val device8 = native8.device
        assertNotSame(device7, device8)
        var cache8: GPUWgpu4kPreparedImageSessionCache? = null
        var acquired8: GPUPreparedImageCacheAcquire.Ready? = null
        lateinit var registry: GPUPreparedSceneChildRegistry
        registry = GPUPreparedSceneChildRegistry {
            events += "parent-teardown"
            assertFailsWith<IllegalStateException> {
                cache7.acquire(PREPARED_IMAGE_PIPELINE_KEY, generation7)
            }
            cache8 = GPUWgpu4kPreparedImageSessionCache(device8, generation8)
            events += "construct:g8"
            acquired8 = assertIs<GPUPreparedImageCacheAcquire.Ready>(
                requireNotNull(cache8).acquire(PREPARED_IMAGE_PIPELINE_KEY, generation8),
            )
            events += "acquire:g8"
        }
        val lease = registry.reserve()
        val teardown7 = GPUPreparedSceneChildTeardown(
            ownerTiers = preparedSceneChildOwnerTiers(
                activityOwners = listOf(AutoCloseable { events += "activity:g7" }),
                preparedImageCache = cache7,
                deviceCacheOwners = listOf(
                    AutoCloseable {
                        assertFailsWith<IllegalStateException> {
                            cache7.acquire(PREPARED_IMAGE_PIPELINE_KEY, generation7)
                        }
                        assertEquals(1, native7.closeAttemptsFor("createRenderPipeline"))
                        events += "cache-closed:g7"
                    },
                ),
                target = AutoCloseable { events += "target:g7" },
            ),
            releaseLease = AutoCloseable {
                events += "lease:g7"
                lease.close()
            },
        )
        val child7 = GPUPreparedSceneFrameSession(
            deviceGeneration = generation7,
            coordinatorFactory = GPUFrameCoordinatorFactory { _, _ ->
                error("replacement barrier test must not render")
            },
            closeAction = teardown7::close,
        )
        lease.bind(child7)
        events.clear()

        registry.close()
        registry.close()

        assertEquals(
            listOf(
                "activity:g7",
                "g7:createRenderPipeline#1:success",
                "g7:createPipelineLayout#1:success",
                "g7:createShaderModule#1:success",
                "g7:createBindGroupLayout#1:success",
                "cache-closed:g7",
                "target:g7",
                "lease:g7",
                "parent-teardown",
                "construct:g8",
                "g8:createBindGroupLayout:create",
                "g8:createShaderModule:create",
                "g8:createPipelineLayout:create",
                "g8:createRenderPipeline:create",
                "acquire:g8",
            ),
            events,
        )
        assertFailsWith<IllegalStateException> {
            cache7.acquire(PREPARED_IMAGE_PIPELINE_KEY, generation7)
        }
        assertEquals(1, native7.closeAttemptsFor("createRenderPipeline"))

        val replacement = requireNotNull(cache8)
        val replacementPipeline = requireNotNull(acquired8).pipeline.pipeline
        assertNotSame(pipeline7, replacementPipeline)
        assertEquals(generation8, replacement.deviceGeneration)
        val stale = assertIs<GPUPreparedImageCacheAcquire.Refused>(
            replacement.acquire(PREPARED_IMAGE_PIPELINE_KEY, generation7),
        )
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_GENERATION, stale.code)
        val reacquired8 = assertIs<GPUPreparedImageCacheAcquire.Ready>(
            replacement.acquire(PREPARED_IMAGE_PIPELINE_KEY, generation8),
        )
        assertSame(replacementPipeline, reacquired8.pipeline.pipeline)
        assertTrue(events.none { it == "g7:createRenderPipeline:create" })

        replacement.close()
    }

    private fun inertDevice(label: String): GPUDevice {
        lateinit var proxy: GPUDevice
        proxy = GPUDevice::class.java.cast(
            Proxy.newProxyInstance(
                GPUDevice::class.java.classLoader,
                arrayOf(GPUDevice::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "toString" -> label
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.singleOrNull()
                    else -> error("Unexpected native call ${method.name}")
                }
            },
        )
        return proxy
    }

    private class RetryingPreparedImageDevice(
        private val events: MutableList<String>,
        private val failCloseOnceFor: String,
        private val eventPrefix: String = "",
    ) {
        private val closeAttempts = mutableMapOf<String, Int>()
        private var failurePending = true

        val device: GPUDevice

        init {
            lateinit var nativeDevice: GPUDevice
            nativeDevice = proxy(GPUDevice::class.java) { methodName, returnType, args ->
                when {
                    methodName.startsWith("create") -> {
                        if (eventPrefix.isNotEmpty()) {
                            events += "$eventPrefix:$methodName:create"
                        }
                        nativeHandle(methodName, returnType)
                    }
                    methodName == "toString" -> "RetryingPreparedImageDevice"
                    methodName == "hashCode" -> System.identityHashCode(nativeDevice)
                    methodName == "equals" -> nativeDevice === args?.singleOrNull()
                    else -> defaultValue(returnType)
                }
            }
            device = nativeDevice
        }

        private fun nativeHandle(label: String, type: Class<*>): Any {
            lateinit var handle: Any
            handle = proxy(type) { methodName, returnType, args ->
                when (methodName) {
                    "close" -> {
                        val attempt = closeAttempts.getOrDefault(label, 0) + 1
                        closeAttempts[label] = attempt
                        val event = "$label#$attempt"
                        if (label == failCloseOnceFor && failurePending) {
                            failurePending = false
                            events += event.withPrefix(":failure")
                            error("$label close failed")
                        }
                        events += event.withPrefix(":success")
                        Unit
                    }
                    "toString" -> label
                    "hashCode" -> System.identityHashCode(handle)
                    "equals" -> handle === args?.singleOrNull()
                    else -> defaultValue(returnType)
                }
            }
            return handle
        }

        fun closeAttemptsFor(label: String): Int = closeAttempts.getOrDefault(label, 0)

        private fun String.withPrefix(suffix: String): String =
            if (eventPrefix.isEmpty()) {
                "$this$suffix"
            } else {
                "$eventPrefix:$this$suffix"
            }
    }

    private companion object {
        val PREPARED_IMAGE_PIPELINE_KEY = GPUPreparedImagePipelineKey(
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
