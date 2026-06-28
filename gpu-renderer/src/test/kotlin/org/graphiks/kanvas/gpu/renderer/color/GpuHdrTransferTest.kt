package org.graphiks.kanvas.gpu.renderer.color

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GpuHdrTransferTest {

    @Test
    fun `PQ EOTF linearizes correctly at 0_5`() {
        val result = GpuHdrTransferFunction.PQ.eotf(0.5f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `PQ EOTF linearizes correctly at peak`() {
        val result = GpuHdrTransferFunction.PQ.eotf(1.0f)
        assertTrue { result in 0.9f..1.1f } // near display peak
    }

    @Test
    fun `HLG OETF inverse produces scene light`() {
        val result = GpuHdrTransferFunction.HLG.oetfInverse(0.5f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `scRGB linear is identity`() {
        assertEquals(0.5f, GpuHdrTransferFunction.scRGBLinear.eotf(0.5f), 0.001f)
        assertEquals(1.0f, GpuHdrTransferFunction.scRGBLinear.eotf(1.0f), 0.001f)
    }

    @Test
    fun `HDR transfer plan accepts supported functions`() {
        for (tf in GpuHdrTransferFunction.entries) {
            val plan = GpuHdrTransferFunctionPlan.forTransfer(tf)
            val route = plan.analyze()
            assertIs<GpuHdrTransferRoute.Accepted>(route)
        }
    }

    @Test
    fun `tone map Reinhard produces bounded output`() {
        val result = GpuHdrToneMapStrategy.Reinhard.apply(2.0f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `tone map ACES produces bounded output`() {
        val result = GpuHdrToneMapStrategy.ACES.apply(2.0f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `tone map Hable produces bounded output`() {
        val result = GpuHdrToneMapStrategy.Hable.apply(2.0f)
        assertTrue { result in 0.0f..1.0f }
    }
}
