package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Public W4e allocation contract: callers cannot silently degrade its ping-pong inventory. */
class GPUWgpu4kCorePrimitiveW4eFrameTest {
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
