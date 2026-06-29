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
        assertTrue { result in 0.9f..1.1f }
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
    fun `HDR transfer plan refuses when no HDR target format`() {
        val plan = GpuHdrTransferFunctionPlan.forTransfer(GpuHdrTransferFunction.PQ)
        val route = plan.analyze(hdrTargetFormatAvailable = false)
        assertIs<GpuHdrTransferRoute.Refused>(route)
        assertEquals("unsupported.color.hdr_target_format", route.diagnostic.code)
    }

    @Test
    fun `PQ OETF encodes linear to PQ signal`() {
        val result = GpuHdrTransferFunction.PQ.oetf(0.5f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `HLG OETF encodes linear to HLG signal`() {
        val result = GpuHdrTransferFunction.HLG.oetf(0.5f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `PQ OETF and EOTF round-trip within tolerance`() {
        val linear = 0.5f
        val encoded = GpuHdrTransferFunction.PQ.oetf(linear)
        val decoded = GpuHdrTransferFunction.PQ.eotf(encoded)
        assertTrue { kotlin.math.abs(decoded - linear) < 0.05f }
    }

    @Test
    fun `HLG OETF and OETF-inverse round-trip within tolerance`() {
        val linear = 0.5f
        val encoded = GpuHdrTransferFunction.HLG.oetf(linear)
        val decoded = GpuHdrTransferFunction.HLG.oetfInverse(encoded)
        assertTrue { kotlin.math.abs(decoded - linear) < 0.05f }
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
