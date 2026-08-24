package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUCommandBuffer
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class GPUWgpu4kFrameEncodingBackendOwnershipTest {
    @Test
    fun `submission callback records each successful queue submission and not a failed submit`() {
        val fixture = SubmissionCallbackFixture()
        var recordedSubmissions = 0
        val backend = GPUWgpu4kFrameEncodingBackend(
            deviceGeneration = GPUDeviceGenerationID(82),
            device = fixture.device,
            queue = fixture.queue,
            onSubmission = { recordedSubmissions += 1 },
        )
        try {
            backend.submit(backend.createCommandEncoder("successful-submit").finish())
            assertEquals(1, recordedSubmissions)

            fixture.failNextSubmit = true
            assertFailsWith<IllegalStateException> {
                backend.submit(backend.createCommandEncoder("failed-submit").finish())
            }
            assertEquals(1, recordedSubmissions)
        } finally {
            backend.close()
        }
    }

    @Test
    fun `texture upload forwards padded bytes and logical extent to writeTexture`() {
        val calls = mutableListOf<List<Any?>>()
        val queue = nativeProxy(GPUQueue::class.java) { methodName, _, arguments ->
            when (methodName) {
                "writeTexture" -> {
                    calls += arguments.orEmpty().toList()
                    Unit
                }
                "toString" -> "TextureUploadQueue"
                else -> error("Unexpected fake queue call: $methodName")
            }
        }
        val layout = preparedImageUploadLayoutForTest()
        val data = GPUPreparedNativeUploadData(
            GPUPreparedNativeOperandKey(
                GPUPreparedNativeOperandRole.UploadSource,
                GPUPreparedNativeOperandKind.Buffer,
                gpuPreparedNativeBindingKey("prepared-image-upload-data:staging"),
            ),
            layout.bytesForUpload(),
        )

        encodePreparedImageTextureUpload(
            queue,
            GPUPreparedNativeScopeOperand.TextureUpload(
                sourceStepIndex = 1,
                data = data,
                destination = fakeNativeTextureOperand(GPUDeviceGenerationID(9)),
                destinationKey = GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.UploadDestination,
                    GPUPreparedNativeOperandKind.Texture,
                    gpuPreparedNativeBindingKey("GPUFrameTextureRef:image@1"),
                ),
                layout = layout,
            ),
        )

        assertEquals(1, calls.size)
        assertContentEquals(layout.bytesForUpload(), data.bytes())
        assertEquals(256L, layout.bytesPerRow)
        assertEquals(1, layout.width)
        assertEquals(1, layout.height)
    }

    @Test
    fun `uniform upload writes the bound buffer at its exact offset before its consumers`() {
        val calls = mutableListOf<List<Any?>>()
        val queue = nativeProxy(GPUQueue::class.java) { methodName, _, arguments ->
            when {
                methodName.startsWith("writeBuffer") -> {
                    calls += arguments.orEmpty().toList()
                    Unit
                }
                methodName == "toString" -> "UniformUploadQueue"
                else -> error("Unexpected fake queue call: $methodName")
            }
        }
        val buffer = nativeProxy(GPUBuffer::class.java) { methodName, _, _ ->
            when (methodName) {
                "close" -> Unit
                "getLabel" -> "prepared-image-uniform-buffer"
                "setLabel" -> Unit
                "toString" -> "PreparedImageUniformBuffer"
                else -> error("Unexpected fake buffer call: $methodName")
            }
        }
        val bytes = ByteArray(112) { (it + 1).toByte() }
        val upload = GPUPreparedNativeBufferUpload(
            data = GPUPreparedNativeUploadData(
                GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.UploadSource,
                    GPUPreparedNativeOperandKind.Buffer,
                    gpuPreparedNativeBindingKey("prepared-image-uniform-data:uniform"),
                ),
                bytes,
            ),
            destination = GPUPreparedNativeBufferOperand(
                buffer,
                GPUDeviceGenerationID(9),
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                byteCapacity = 512L,
            ),
            destinationKey = GPUPreparedNativeOperandKey(
                GPUPreparedNativeOperandRole.UploadDestination,
                GPUPreparedNativeOperandKind.Buffer,
                gpuPreparedNativeBindingKey("GPUFrameBufferRef:uniform"),
            ),
            destinationOffset = 256L,
            consumerSourceStepIndices = listOf(7),
        )

        encodePreparedImageUniformUpload(queue, upload)

        assertEquals(1, calls.size)
        assertSame(buffer, calls.single()[0])
        assertEquals(256L, calls.single()[1])
        assertContentEquals(bytes, upload.data.bytes())
        assertEquals(listOf(7), upload.consumerSourceStepIndices)
    }

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

        val device: GPUDevice = nativeProxy(GPUDevice::class.java) { methodName, _, _ ->
            when (methodName) {
                "createCommandEncoder" -> encoder()
                "toString" -> "EncodingBackendFixtureDevice"
                else -> error("Unexpected fake device call: $methodName")
            }
        }

        val queue: GPUQueue = nativeProxy(GPUQueue::class.java) { methodName, _, _ ->
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
            return nativeProxy(GPUCommandEncoder::class.java) { methodName, _, _ ->
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

    private class SubmissionCallbackFixture {
        var failNextSubmit = false

        val device: GPUDevice = nativeProxy(GPUDevice::class.java) { methodName, _, _ ->
            when (methodName) {
                "createCommandEncoder" -> nativeProxy(GPUCommandEncoder::class.java) { encoderMethod, _, _ ->
                    when (encoderMethod) {
                        "finish" -> nativeProxy(GPUCommandBuffer::class.java) { bufferMethod, _, _ ->
                            when (bufferMethod) {
                                "close", "setLabel" -> Unit
                                "getLabel" -> "submission-callback-command-buffer"
                                "toString" -> "SubmissionCallbackCommandBuffer"
                                else -> error("Unexpected fake command-buffer call: $bufferMethod")
                            }
                        }
                        "close", "setLabel" -> Unit
                        "getLabel" -> "submission-callback-command-encoder"
                        "toString" -> "SubmissionCallbackCommandEncoder"
                        else -> error("Unexpected fake command-encoder call: $encoderMethod")
                    }
                }
                "toString" -> "SubmissionCallbackDevice"
                else -> error("Unexpected fake device call: $methodName")
            }
        }

        val queue: GPUQueue = nativeProxy(GPUQueue::class.java) { methodName, _, _ ->
            when (methodName) {
                "submit" -> {
                    if (failNextSubmit) {
                        failNextSubmit = false
                        throw IllegalStateException("fake queue submit failure")
                    }
                    Unit
                }
                "toString" -> "SubmissionCallbackQueue"
                else -> error("Unexpected fake queue call: $methodName")
            }
        }
    }

    private fun <T : Any> closeableNative(
        type: Class<T>,
        close: RetryingNativeClose,
    ): T = nativeProxy(type) { methodName, _, _ ->
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
    invocation: (String, Class<*>, Array<out Any?>?) -> Any?,
): T = type.cast(
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
        when (method.name) {
            "equals" -> proxy === arguments?.singleOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            else -> invocation(method.name, method.returnType, arguments)
        }
    },
)
