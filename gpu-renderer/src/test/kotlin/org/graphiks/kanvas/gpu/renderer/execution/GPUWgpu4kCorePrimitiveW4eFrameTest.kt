package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4eMaskContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4eMaskResolveAction

/** Public W4e allocation contract: callers cannot silently degrade its ping-pong inventory. */
class GPUWgpu4kCorePrimitiveW4eFrameTest {
    @Test
    fun `public W4e mask continuation keeps its dedicated resolve policy`() {
        val resolved = GPUW4eMaskContinuationRequest(
            maskTargetResourceId = "mask-scratch-4x",
            resolveMaskResourceId = "mask-resolved-1x",
            resolveAction = GPUW4eMaskResolveAction.ResolveCanonical,
        )
        val intermediate = GPUW4eMaskContinuationRequest(
            maskTargetResourceId = "mask-scratch-4x",
            resolveMaskResourceId = null,
            resolveAction = GPUW4eMaskResolveAction.Skip,
        )

        assertEquals("mask-resolved-1x", resolved.resolveMaskResourceId)
        assertEquals(GPUW4eMaskResolveAction.Skip, intermediate.resolveAction)
        assertFailsWith<IllegalArgumentException> {
            GPUW4eMaskContinuationRequest(
                maskTargetResourceId = "mask-scratch-4x",
                resolveMaskResourceId = "mask-scratch-4x",
                resolveAction = GPUW4eMaskResolveAction.ResolveCanonical,
            )
        }
    }

    @Test
    fun `sealed W4e request preserves the exact AA mask inventory`() {
        val request = GPUW4eAttachmentRequest(
            accumulatorCountI32 = 2,
            producerSampleCountI32 = 4,
            requiresProducerDepthStencil = true,
            requiredPhysicalByteCountI64 = 16_384L,
        )

        assertEquals(2, request.accumulatorCountI32)
        assertEquals(4, request.producerSampleCountI32)
        assertEquals(16_384L, request.requiredPhysicalByteCountI64)
    }

    @Test
    fun `public W4e request rejects a mutable single-mask downgrade`() {
        assertFailsWith<IllegalArgumentException> {
            GPUW4eAttachmentRequest(
                accumulatorCountI32 = 1,
                producerSampleCountI32 = 4,
                requiresProducerDepthStencil = true,
                requiredPhysicalByteCountI64 = 8_192L,
            )
        }
    }
}
