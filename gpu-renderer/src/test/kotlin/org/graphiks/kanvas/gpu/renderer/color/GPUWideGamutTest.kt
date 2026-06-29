package org.graphiks.kanvas.gpu.renderer.color

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GPUWideGamutTest {

    @Test
    fun `wide-gamut plan accepts Display P3 conversion to sRGB`() {
        val plan = GPUWideGamutWorkingSpacePlan.forPrimaries(GPUWideGamutPrimaries.DisplayP3)
        val conversion = plan.srgbConversion()
        assertTrue { conversion.matrix.size >= 9 }
    }

    @Test
    fun `wide-gamut plan accepts Adobe RGB conversion`() {
        val plan = GPUWideGamutWorkingSpacePlan.forPrimaries(GPUWideGamutPrimaries.AdobeRGB)
        val conversion = plan.srgbConversion()
        assertTrue { conversion.matrix.isNotEmpty() }
    }

    @Test
    fun `wide-gamut plan accepts Rec2020 conversion`() {
        val plan = GPUWideGamutWorkingSpacePlan.forPrimaries(GPUWideGamutPrimaries.Rec2020)
        val conversion = plan.srgbConversion()
        assertTrue { conversion.matrix.isNotEmpty() }
    }

    @Test
    fun `wide-gamut route accepts all defined primaries`() {
        for (primaries in GPUWideGamutPrimaries.entries) {
            val plan = GPUWideGamutWorkingSpacePlan.forPrimaries(primaries)
            val route = plan.analyze()
            assertIs<GPUWideGamutRoute.Accepted>(route)
        }
    }

    @Test
    fun `wide-gamut route refuses unsupported transfer function`() {
        val plan = GPUWideGamutWorkingSpacePlan(
            primaries = GPUWideGamutPrimaries.DisplayP3,
            transferFunction = GPUHdrTransferFunction.PQ,
            intermediateFormat = GPUWideGamutIntermediateFormat.rgba16float,
        )
        val route = plan.analyze()
        assertIs<GPUWideGamutRoute.Refused>(route)
        assertEquals("unsupported.color.wide_gamut_working_space", route.diagnostic.code)
    }

    @Test
    fun `intermediate format produces correct descriptor`() {
        assertEquals("rgba16float", GPUWideGamutIntermediateFormat.rgba16float.descriptor)
        assertEquals("rgba32float", GPUWideGamutIntermediateFormat.rgba32float.descriptor)
    }
}
