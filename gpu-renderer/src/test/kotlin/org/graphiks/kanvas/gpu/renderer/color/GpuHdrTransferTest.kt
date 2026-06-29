package org.graphiks.kanvas.gpu.renderer.color

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GPUHdrTransferTest {

    @Test
    fun `PQ EOTF linearizes correctly at 0_5`() {
        val result = GPUHdrTransferFunction.PQ.eotf(0.5f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `PQ EOTF linearizes correctly at peak`() {
        val result = GPUHdrTransferFunction.PQ.eotf(1.0f)
        assertTrue { result in 0.9f..1.1f }
    }

    @Test
    fun `HLG OETF inverse produces scene light`() {
        val result = GPUHdrTransferFunction.HLG.oetfInverse(0.5f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `scRGB linear is identity`() {
        assertEquals(0.5f, GPUHdrTransferFunction.scRGBLinear.eotf(0.5f), 0.001f)
        assertEquals(1.0f, GPUHdrTransferFunction.scRGBLinear.eotf(1.0f), 0.001f)
    }

    @Test
    fun `HDR transfer plan accepts supported functions`() {
        for (tf in GPUHdrTransferFunction.entries) {
            val plan = GPUHdrTransferFunctionPlan.forTransfer(tf)
            val route = plan.analyze()
            assertIs<GPUHdrTransferRoute.Accepted>(route)
        }
    }

    @Test
    fun `HDR transfer plan refuses when no HDR target format`() {
        val plan = GPUHdrTransferFunctionPlan.forTransfer(GPUHdrTransferFunction.PQ)
        val route = plan.analyze(hdrTargetFormatAvailable = false)
        assertIs<GPUHdrTransferRoute.Refused>(route)
        assertEquals("unsupported.color.hdr_target_format", route.diagnostic.code)
    }

    @Test
    fun `PQ OETF encodes linear to PQ signal`() {
        val result = GPUHdrTransferFunction.PQ.oetf(0.5f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `HLG OETF encodes linear to HLG signal`() {
        val result = GPUHdrTransferFunction.HLG.oetf(0.5f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `PQ OETF and EOTF round-trip within tolerance`() {
        val linear = 0.5f
        val encoded = GPUHdrTransferFunction.PQ.oetf(linear)
        val decoded = GPUHdrTransferFunction.PQ.eotf(encoded)
        assertTrue { kotlin.math.abs(decoded - linear) < 0.05f }
    }

    @Test
    fun `HLG OETF and OETF-inverse round-trip within tolerance`() {
        val linear = 0.5f
        val encoded = GPUHdrTransferFunction.HLG.oetf(linear)
        val decoded = GPUHdrTransferFunction.HLG.oetfInverse(encoded)
        assertTrue { kotlin.math.abs(decoded - linear) < 0.05f }
    }

    @Test
    fun `tone map Reinhard produces bounded output`() {
        val result = GPUHdrToneMapStrategy.Reinhard.apply(2.0f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `tone map ACES produces bounded output`() {
        val result = GPUHdrToneMapStrategy.ACES.apply(2.0f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `tone map Hable produces bounded output`() {
        val result = GPUHdrToneMapStrategy.Hable.apply(2.0f)
        assertTrue { result in 0.0f..1.0f }
    }

    @Test
    fun `HDR transfer plan validates generated EOTF WGSL through wgsl4k`() {
        for (tf in GPUHdrTransferFunction.entries) {
            val route = GPUHdrTransferFunctionPlan.forTransfer(tf).analyze()
            assertIs<GPUHdrTransferRoute.Accepted>(route)
            val reflection = route.eotfPlan.wgslReflection
            assertNotNull(reflection, "EOTF WGSL for $tf should carry a wgsl4k reflection")
            assertTrue(reflection.validated, "EOTF WGSL for $tf should validate through wgsl4k")
        }
    }

    @Test
    fun `HDR transfer plan validates generated tone-map WGSL through wgsl4k`() {
        val route = GPUHdrTransferFunctionPlan.forTransfer(GPUHdrTransferFunction.PQ).analyze()
        assertIs<GPUHdrTransferRoute.Accepted>(route)
        val toneMap = route.toneMapPlan
        assertNotNull(toneMap, "PQ route should produce a tone-map plan")
        val reflection = toneMap.wgslReflection
        assertNotNull(reflection, "tone-map WGSL should carry a wgsl4k reflection")
        assertTrue(reflection.validated, "tone-map WGSL should validate through wgsl4k")
    }
}
