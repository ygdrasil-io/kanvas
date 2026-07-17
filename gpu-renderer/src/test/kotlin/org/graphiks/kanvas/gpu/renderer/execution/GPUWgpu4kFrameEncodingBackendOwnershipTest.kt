package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUCommandBuffer
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class GPUWgpu4kFrameEncodingBackendOwnershipTest {
    @TestFactory
    fun `encoding backend retains every failed native ledger until retry succeeds`(): List<DynamicTest> =
        EncodingLedgerRoute.entries.map { route ->
            DynamicTest.dynamicTest(route.name) {
                val closeFailuresBeforeSuccess = if (route.isQuarantineRoute) 2 else 1
                val target = RetryingNativeClose(route.name, closeFailuresBeforeSuccess)
                val fixture = EncodingBackendFixture(route, target)
                val backend = GPUWgpu4kFrameEncodingBackend(
                    deviceGeneration = GPUDeviceGenerationID(81),
                    device = fixture.device,
                    queue = fixture.queue,
                )

                fixture.populate(backend)

                val incomplete = assertFailsWith<GPUOwnedNativeCloseIncompleteException> {
                    backend.close()
                }
                assertEquals("frame-encoding", incomplete.ownerLabel)
                assertEquals(1, incomplete.remainingOwnerCount)
                assertFailsWith<IllegalStateException> {
                    backend.createCommandEncoder("after-close-request")
                }

                backend.close()
                backend.close()

                assertEquals(closeFailuresBeforeSuccess + 1, target.closeAttempts)
                assertEquals(1, target.successfulCloses)
            }
        }

    private enum class EncodingLedgerRoute {
        LiveEncoder,
        CommandBuffer,
        QuarantinedEncoder,
        QuarantinedCommandBuffer,
        ;

        val isQuarantineRoute: Boolean
            get() = this == QuarantinedEncoder || this == QuarantinedCommandBuffer
    }

    private inner class EncodingBackendFixture(
        private val route: EncodingLedgerRoute,
        private val target: RetryingNativeClose,
    ) {
        private var encoderOrdinal = 0

        val device: GPUDevice = nativeProxy(GPUDevice::class.java) { methodName, _ ->
            when (methodName) {
                "createCommandEncoder" -> encoder()
                "toString" -> "EncodingBackendFixtureDevice"
                else -> error("Unexpected fake device call: $methodName")
            }
        }

        val queue: GPUQueue = nativeProxy(GPUQueue::class.java) { methodName, _ ->
            when (methodName) {
                "toString" -> "EncodingBackendFixtureQueue"
                else -> error("Unexpected fake queue call: $methodName")
            }
        }

        fun populate(backend: GPUWgpu4kFrameEncodingBackend) {
            val encoder = backend.createCommandEncoder("ownership-${route.name}")
            when (route) {
                EncodingLedgerRoute.LiveEncoder -> Unit
                EncodingLedgerRoute.CommandBuffer -> encoder.finish()
                EncodingLedgerRoute.QuarantinedEncoder -> encoder.discard()
                EncodingLedgerRoute.QuarantinedCommandBuffer -> backend.discard(encoder.finish())
            }
        }

        private fun encoder(): GPUCommandEncoder {
            encoderOrdinal += 1
            val encoderClose = if (
                route == EncodingLedgerRoute.LiveEncoder || route == EncodingLedgerRoute.QuarantinedEncoder
            ) {
                target
            } else {
                RetryingNativeClose("non-target-encoder-$encoderOrdinal", 0)
            }
            val commandBufferClose = if (
                route == EncodingLedgerRoute.CommandBuffer || route == EncodingLedgerRoute.QuarantinedCommandBuffer
            ) {
                target
            } else {
                RetryingNativeClose("non-target-buffer-$encoderOrdinal", 0)
            }
            val commandBuffer = closeableNative(GPUCommandBuffer::class.java, commandBufferClose)
            return nativeProxy(GPUCommandEncoder::class.java) { methodName, _ ->
                when (methodName) {
                    "finish" -> commandBuffer
                    "close" -> encoderClose.close()
                    "getLabel" -> "encoder-$encoderOrdinal"
                    "setLabel" -> Unit
                    "toString" -> "FakeEncoder($encoderOrdinal)"
                    else -> error("Unexpected fake encoder call: $methodName")
                }
            }
        }
    }

    private class RetryingNativeClose(
        private val label: String,
        private var closeFailuresRemaining: Int,
    ) {
        var closeAttempts: Int = 0
            private set
        var successfulCloses: Int = 0
            private set

        fun close() {
            closeAttempts += 1
            if (closeFailuresRemaining > 0) {
                closeFailuresRemaining -= 1
                error("$label close failed")
            }
            check(successfulCloses == 0) { "$label closed more than once" }
            successfulCloses += 1
        }
    }

    private fun <T : Any> closeableNative(
        type: Class<T>,
        close: RetryingNativeClose,
    ): T = nativeProxy(type) { methodName, _ ->
        when (methodName) {
            "close" -> close.close()
            "getLabel" -> "closeable-native"
            "setLabel" -> Unit
            "toString" -> "CloseableNative"
            else -> error("Unexpected fake closeable call: $methodName")
        }
    }
}

private fun <T : Any> nativeProxy(
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
