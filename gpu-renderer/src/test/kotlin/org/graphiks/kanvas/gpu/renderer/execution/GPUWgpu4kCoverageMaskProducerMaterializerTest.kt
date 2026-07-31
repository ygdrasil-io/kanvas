package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.validateCoverageMaskProducerUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep

class GPUWgpu4kCoverageMaskProducerMaterializerTest {
    @Test
    fun `common producer seal rejects missing extra and reordered packets`() {
        val producerPackets = sealedCoverageMaskProducerPackets()
        val seal = requireNotNull(
            producerPackets.first().coverageMaskProducerUniformSlabSeal,
        )

        assertTrue(validateCoverageMaskProducerUniformSlabSeal(producerPackets, seal))
        assertFalse(validateCoverageMaskProducerUniformSlabSeal(producerPackets.dropLast(1), seal))
        assertFalse(validateCoverageMaskProducerUniformSlabSeal(producerPackets + producerPackets.first(), seal))
        assertFalse(validateCoverageMaskProducerUniformSlabSeal(producerPackets.reversed(), seal))
    }

    @Test
    fun `producer helper preserves mixed and Core envelope parity with one upload and lifecycle`() {
        listOf(
            Mode(
                name = "mixed",
                vertexBytes = 1L,
                indexBytes = 1L,
                consumerBindGroupRequired = false,
            ),
            Mode(
                name = "core",
                vertexBytes = 128L,
                indexBytes = 64L,
                consumerBindGroupRequired = true,
            ),
        ).forEach { mode ->
            val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
            val generation = GPUDeviceGenerationID(41L)
            val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, generation)
            val seal = sealedCoverageMaskSlab()
            if (mode.consumerBindGroupRequired) {
                assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired>(
                    cache.acquire(
                        GPUWgpu4kCorePrimitivePipelineCacheKey(
                            PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY,
                            GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
                                targetFormat = "rgba8unorm",
                                sampleCount = 1,
                                topology = "triangle-list",
                                frontFace = "ccw",
                                cullMode = "none",
                                program =
                                    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest,
                            ),
                        ),
                    ),
                )
            }
            val offsets = seal.producerSlots.map { producer ->
                requireNotNull(seal.plan.slots.getOrNull(producer.slotIndex)).alignedOffset
            }
            val snapshot = seal.packedBytesSnapshot()

            val result = GPUWgpu4kCoverageMaskProducerMaterializer(
                native.queue,
                cache,
                limits,
            ).materialize(
                GPUWgpu4kCoverageMaskProducerRequest.borrowSealed(
                    uniformSlabSeal = seal.producerUniformSlabSeal,
                    scopes = listOf(
                        GPUWgpu4kCoverageMaskProducerScope(3, seal.producerSlots.indices.toList()),
                    ),
                    deviceGeneration = generation,
                    resourceEnvelope = GPUWgpu4kCoverageMaskResourceEnvelope.borrowBuilderPacked(
                        vertexBytes = mode.vertexBytes,
                        indexBytes = mode.indexBytes,
                        uniformSlabSeal = seal.producerUniformSlabSeal,
                        coverageMaskConsumerBindGroupRequired =
                            mode.consumerBindGroupRequired,
                    ),
                ),
            )

            val ready = assertIs<GPUWgpu4kCoverageMaskProducerMaterialization.Ready>(
                result,
                (result as? GPUWgpu4kCoverageMaskProducerMaterialization.Refused)?.let {
                    "${mode.name}: ${it.code}: ${it.message}"
                } ?: mode.name,
            )
            assertEquals(1, ready.scopeOperands.size, mode.name)
            assertEquals(
                offsets,
                ready.scopeOperands.single().commands
                    .filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                    .map { command -> command.dynamicOffsets.single() },
                mode.name,
            )
            assertEquals(1, native.writeBufferCalls.size, mode.name)
            assertEquals(seal.plan.totalBytes.toULong(), native.writeBufferCalls.single().size, mode.name)
            assertContentEquals(snapshot, native.writeBufferCalls.single().snapshot, mode.name)
            assertEquals(
                1,
                native.createdHandles(
                    "Kanvas.session.corePrimitive.framePool.coverageMask",
                ).size,
                mode.name,
            )
            assertTrue(
                requireNotNull(ready.borrowedResources.vertexBuffer.byteCapacity) >=
                    mode.vertexBytes,
                mode.name,
            )
            assertTrue(
                requireNotNull(ready.borrowedResources.indexBuffer.byteCapacity) >=
                    mode.indexBytes,
                mode.name,
            )
            assertTrue(
                requireNotNull(ready.borrowedResources.uniformBuffer.byteCapacity) >=
                    seal.plan.totalBytes,
                mode.name,
            )
            if (mode.consumerBindGroupRequired) {
                assertNotNull(ready.borrowedResources.consumerBindGroup, mode.name)
            } else {
                assertNull(ready.borrowedResources.consumerBindGroup, mode.name)
            }
            assertIs<GPUPreparedNativeFrameLeaseTransition.Applied>(
                ready.leaseLifecycle.releaseBeforeSubmit(),
                mode.name,
            )
            assertIs<GPUPreparedNativeFrameLeaseTransition.Refused>(
                ready.leaseLifecycle.releaseBeforeSubmit(),
                mode.name,
            )
            cache.close()
        }
    }

    @Test
    fun `producer helper refuses a forged ABI64 slice before checkout or upload`() {
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val generation = GPUDeviceGenerationID(42L)
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, generation)
        val seal = sealedCoverageMaskSlab()
        val forgedBytes = seal.packedBytesSnapshot().apply {
            val secondProducer = seal.producerSlots[1]
            val offset = requireNotNull(seal.plan.slots.getOrNull(secondProducer.slotIndex))
                .alignedOffset.toInt()
            this[offset] = (this[offset].toInt() xor 0x01).toByte()
        }
        val forgedSeal = GPUCorePrimitiveCoverageMaskUniformSlabSeal(
            plan = seal.plan,
            preparedRoute = seal.preparedRoute,
            contentKey = seal.contentKey,
            planCanonicalIdentity = seal.planCanonicalIdentity,
            maskResource = seal.maskResource,
            producerSlots = seal.producerSlots,
            consumerSlots = seal.consumerSlots,
            packedBytes = forgedBytes,
            maskBounds = seal.maskBounds,
            orderingToken = seal.orderingToken,
        )

        val result = GPUWgpu4kCoverageMaskProducerMaterializer(
            native.queue,
            cache,
            limits,
        ).materialize(
            GPUWgpu4kCoverageMaskProducerRequest.borrowSealed(
                uniformSlabSeal = seal.producerUniformSlabSeal,
                scopes = listOf(GPUWgpu4kCoverageMaskProducerScope(3, listOf(0, 1))),
                deviceGeneration = generation,
                resourceEnvelope = GPUWgpu4kCoverageMaskResourceEnvelope.borrowBuilderPacked(
                    1L,
                    1L,
                    forgedSeal.producerUniformSlabSeal,
                    false,
                ),
            ),
        )

        assertEquals(
            "invalid.native.coverage-mask.uniform-abi",
            assertIs<GPUWgpu4kCoverageMaskProducerMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), native.writeBufferCalls)
        assertEquals(
            emptyList(),
            native.createdHandles("Kanvas.session.corePrimitive.framePool.coverageMask"),
        )
        cache.close()
    }

    private fun sealedCoverageMaskSlab(): GPUCorePrimitiveCoverageMaskUniformSlabSeal =
        GPUFramePreflighterTest().preparedCoverageMaskFramePlan().steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .mapNotNull { packet ->
                packet.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal
            }
            .first()

    private fun sealedCoverageMaskProducerPackets(): List<GPUDrawPacket> =
        GPUFramePreflighterTest().preparedCoverageMaskFramePlan().steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .filter { packet -> packet.coverageMaskProducerUniformSlabSeal != null }

    private data class Mode(
        val name: String,
        val vertexBytes: Long,
        val indexBytes: Long,
        val consumerBindGroupRequired: Boolean,
    )

    private companion object {
        val limits = GPULimits(
            maxTextureDimension2D = 8_192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        )
    }
}
