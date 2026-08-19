package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class GPUWgpu4kSessionCacheOwnershipTest {
    @TestFactory
    fun `failed cache entry construction retains setup handles for adapter retry`(): List<DynamicTest> =
        listOf(
            "destination-copy" to { fixture: FailingCacheSetupDevice ->
                GPUWgpu4kDestinationCopySessionCache(fixture.device).also { cache ->
                    assertFailsWith<IllegalStateException> {
                        cache.acquire(listOf(GPUDestinationCopySnapshotSpec(4, 4)))
                    }
                }
            },
            "registered-uniform-ephemeral" to { fixture: FailingCacheSetupDevice ->
                GPUWgpu4kRegisteredUniformRectSessionCache(fixture.device).also { cache ->
                    assertFailsWith<IllegalStateException> {
                        cache.acquire(
                            GPUWgpu4kRegisteredUniformRectPipelineCacheKey(
                                GPURegisteredUniformProgram.SolidColor,
                                "rgba8unorm",
                                1,
                            ),
                        )
                    }
                }
            },
            "separable-blur" to { fixture: FailingCacheSetupDevice ->
                GPUWgpu4kSeparableBlurRectSessionCache(fixture.device).also { cache ->
                    assertFailsWith<IllegalStateException> { cache.acquire(4, 4) }
                }
            },
            "surface-blit" to { fixture: FailingCacheSetupDevice ->
                GPUWgpu4kSurfaceBlitSessionCache(
                    fixture.device,
                    sourceViewProvider = { fakeNative("surface-source") },
                ).also { cache ->
                    assertFailsWith<IllegalStateException> {
                        cache.acquire(GPUTextureFormat.RGBA8Unorm)
                    }
                }
            },
        ).map { (name, createFailedCache) ->
            DynamicTest.dynamicTest(name) {
                val fixture = FailingCacheSetupDevice(
                    failTextureViewCreation = name == "destination-copy",
                )
                val cache = createFailedCache(fixture)
                assertEquals(1, fixture.closeAttempts)

                assertFailsWith<IllegalStateException> { cache.close() }
                assertEquals(2, fixture.closeAttempts)

                val adapter = GPURuntimeResourceAdapter()
                assertTrue(adapter.quarantinePreRegistrationCloseOwner(cache))
                adapter.close()
                assertEquals(3, fixture.closeAttempts)
                assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
            }
        }
}

private class FailingCacheSetupDevice(
    private val failTextureViewCreation: Boolean,
) {
    var closeAttempts: Int = 0
        private set
    private var deviceCreationCalls = 0
    private var closeFailuresRemaining = 2

    val device: GPUDevice = proxy(GPUDevice::class.java) { methodName, returnType ->
        when {
            methodName.startsWith("create") -> {
                deviceCreationCalls += 1
                if (deviceCreationCalls > 1) error("setup creation failed")
                setupHandle(returnType)
            }
            methodName == "toString" -> "FailingCacheSetupDevice"
            else -> error("Unexpected fake device call: $methodName")
        }
    }

    private fun setupHandle(type: Class<*>): Any = proxy(type) { methodName, _ ->
        when (methodName) {
            "createView" -> if (failTextureViewCreation) {
                error("texture view creation failed")
            } else {
                error("Unexpected texture view creation")
            }
            "close" -> {
                closeAttempts += 1
                if (closeFailuresRemaining > 0) {
                    closeFailuresRemaining -= 1
                    error("setup close failed")
                }
                Unit
            }
            "getLabel" -> "failing-cache-setup-handle"
            "setLabel" -> Unit
            "toString" -> "FailingCacheSetupHandle"
            else -> error("Unexpected fake setup handle call: $methodName")
        }
    }
}

private fun <T : Any> proxy(
    type: Class<T>,
    invocation: (String, Class<*>) -> Any?,
): T = type.cast(
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
        when (method.name) {
            "equals" -> proxy === arguments?.singleOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            else -> invocation(method.name, method.returnType)
        }
    },
)

private inline fun <reified T : Any> fakeNative(label: String): T = proxy(T::class.java) { methodName, _ ->
    when (methodName) {
        "getLabel" -> label
        "setLabel", "close" -> Unit
        "toString" -> "FakeNative($label)"
        else -> error("Unexpected fake native call: $methodName")
    }
}
