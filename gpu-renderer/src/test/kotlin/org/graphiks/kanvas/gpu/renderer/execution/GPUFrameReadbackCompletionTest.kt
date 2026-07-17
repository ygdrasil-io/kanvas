package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferMapState
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUSize64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUFrameReadbackCompletionTest {
    @Test
    fun `wgpu4k mapper maps once off thread and exposes one exact mapped range`() {
        val output = readbackOutput("readback.mapper-success")
        val mappedBytes = ByteArray(output.layout.totalBufferBytes.toInt()) { index -> index.toByte() }
        val buffer = RecordingGPUBuffer(mappedBytes = mappedBytes)
        val operand = readbackOperand(buffer)
        val delivery = AtomicReference<GPUFrameNativeReadbackMapDelivery>()
        val delivered = CountDownLatch(1)
        val renderThread = Thread.currentThread().name
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wgpu4k-readback-test")
        }

        try {
            val arm = GPUWgpu4kNativeReadbackMapper(executor).map(output, operand) {
                delivery.set(it)
                delivered.countDown()
            }

            assertIs<GPUFrameReadbackMapArmResult.Armed>(arm)
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            val mapped = assertIs<GPUFrameNativeReadbackMapDelivery.Mapped>(delivery.get())
            assertContentEquals(mappedBytes, mapped.range.copyBytesFromZero())
            mapped.range.unmap()

            assertEquals(1, buffer.mapCalls)
            assertEquals(GPUMapMode.Read, buffer.lastMapMode)
            assertEquals(0uL, buffer.lastMapOffset)
            assertEquals(output.layout.totalBufferBytes.toULong(), buffer.lastMapSize)
            assertEquals(1, buffer.rangeCalls)
            assertEquals(0uL, buffer.lastRangeOffset)
            assertEquals(output.layout.totalBufferBytes.toULong(), buffer.lastRangeSize)
            assertEquals(1, buffer.unmapCalls)
            assertNotEquals(renderThread, buffer.mapThreadName)
            assertTrue(requireNotNull(buffer.mapThreadName).startsWith("wgpu4k-readback-test"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `wgpu4k mapper failure never reads a mapped range`() {
        val output = readbackOutput("readback.mapper-failure")
        val buffer = RecordingGPUBuffer(mapFailure = IllegalStateException("native map failed"))
        val delivered = CountDownLatch(1)
        val delivery = AtomicReference<GPUFrameNativeReadbackMapDelivery>()
        val executor = Executors.newSingleThreadExecutor()

        try {
            val arm = GPUWgpu4kNativeReadbackMapper(executor).map(output, readbackOperand(buffer)) {
                delivery.set(it)
                delivered.countDown()
            }

            assertIs<GPUFrameReadbackMapArmResult.Armed>(arm)
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            val failed = assertIs<GPUFrameNativeReadbackMapDelivery.Failed>(delivery.get())
            assertEquals("failed.frame-readback.map", failed.diagnostic.code.value)
            assertEquals(GPUFrameReadbackMapFailureSafety.Quarantine, failed.safety)
            assertEquals(1, buffer.mapCalls)
            assertEquals(0, buffer.rangeCalls)
            assertEquals(0, buffer.unmapCalls)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `wgpu4k mapper refuses when off-thread scheduling is rejected`() {
        val output = readbackOutput("readback.executor-rejected")
        val buffer = RecordingGPUBuffer()
        val mapper = GPUWgpu4kNativeReadbackMapper {
            throw RejectedExecutionException("executor closed")
        }
        var delivered = false

        val result = mapper.map(output, readbackOperand(buffer)) { delivered = true }

        val refused = assertIs<GPUFrameReadbackMapArmResult.Refused>(result)
        assertEquals("failed.frame-readback.mapping-executor", refused.diagnostic.code.value)
        assertFalse(delivered)
        assertEquals(0, buffer.mapCalls)
        assertEquals(0, buffer.rangeCalls)
        assertEquals(0, buffer.unmapCalls)
    }

    @Test
    fun `wgpu4k mapper refuses mismatched layout and non-output ownership before mapping`() {
        val output = readbackOutput("readback.invalid-native")
        val buffer = RecordingGPUBuffer()
        val valid = readbackOperand(buffer)
        val invalidLayout = GPUPreparedNativeScopeOperand.Readback(
            sourceStepIndex = valid.sourceStepIndex,
            source = valid.source,
            destination = valid.destination,
            layout = GPUPreparedNativeReadbackLayout(
                originX = 1,
                originY = valid.layout.originY,
                width = valid.layout.width,
                height = valid.layout.height,
                bytesPerRow = valid.layout.bytesPerRow,
                rowsPerImage = valid.layout.rowsPerImage,
                bufferOffset = valid.layout.bufferOffset,
                mappedSize = valid.layout.mappedSize,
                format = valid.layout.format,
            ),
        )
        val borrowed = GPUPreparedNativeScopeOperand.Readback(
            sourceStepIndex = valid.sourceStepIndex,
            source = valid.source,
            destination = GPUPreparedNativeBufferOperand(
                buffer,
                valid.destination.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            ),
            layout = valid.layout,
        )
        val executor = Executors.newSingleThreadExecutor()

        try {
            val mapper = GPUWgpu4kNativeReadbackMapper(executor)
            val layoutResult = mapper.map(output, invalidLayout) { error("must not deliver") }
            val ownershipResult = mapper.map(output, borrowed) { error("must not deliver") }

            assertEquals(
                "invalid.frame-readback.native-layout",
                assertIs<GPUFrameReadbackMapArmResult.Refused>(layoutResult).diagnostic.code.value,
            )
            assertEquals(
                "invalid.frame-readback.native-ownership",
                assertIs<GPUFrameReadbackMapArmResult.Refused>(ownershipResult).diagnostic.code.value,
            )
            assertEquals(0, buffer.mapCalls)
            assertEquals(0, buffer.rangeCalls)
            assertEquals(0, buffer.unmapCalls)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun readbackOutput(requestId: String): GPUPreparedReadbackOutput =
        GPUFrameCoreTestFixture.preparedFrame(
            readbackRequestId = GPUReadbackRequestID(requestId),
        ).resources.outputOwnedReadbacks.single()

    private fun readbackOperand(buffer: GPUBuffer): GPUPreparedNativeScopeOperand.Readback {
        val base = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
            .scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
            .single()
        return GPUPreparedNativeScopeOperand.Readback(
            sourceStepIndex = base.sourceStepIndex,
            source = base.source,
            destination = GPUPreparedNativeBufferOperand(
                buffer,
                base.destination.deviceGeneration,
                GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
            ),
            layout = base.layout,
        )
    }

    private class RecordingGPUBuffer(
        private val mappedBytes: ByteArray = ByteArray(784),
        private val mapFailure: Throwable? = null,
    ) : GPUBuffer {
        override var label: String = "readback.test"
        override val size: ULong = mappedBytes.size.toULong()
        override val usage: Set<GPUBufferUsage> = setOf(GPUBufferUsage.MapRead, GPUBufferUsage.CopyDst)
        override val mapState: GPUBufferMapState = GPUBufferMapState.Unmapped

        var mapCalls = 0
            private set
        var rangeCalls = 0
            private set
        var unmapCalls = 0
            private set
        var lastMapMode: GPUMapMode? = null
            private set
        var lastMapOffset: GPUSize64? = null
            private set
        var lastMapSize: GPUSize64? = null
            private set
        var lastRangeOffset: GPUSize64? = null
            private set
        var lastRangeSize: GPUSize64? = null
            private set
        var mapThreadName: String? = null
            private set

        override suspend fun mapAsync(
            mode: GPUMapMode,
            offset: GPUSize64,
            size: GPUSize64?,
        ): Result<Unit> {
            mapCalls += 1
            lastMapMode = mode
            lastMapOffset = offset
            lastMapSize = size
            mapThreadName = Thread.currentThread().name
            return mapFailure?.let(Result.Companion::failure) ?: Result.success(Unit)
        }

        override fun getMappedRange(offset: GPUSize64, size: GPUSize64?): ArrayBuffer {
            rangeCalls += 1
            lastRangeOffset = offset
            lastRangeSize = size
            return ArrayBuffer.of(mappedBytes.copyOf())
        }

        override fun unmap() {
            unmapCalls += 1
        }

        override fun close() = Unit
    }
}
