package org.graphiks.kanvas.gpu.renderer.color

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GpuWideGamutTest {

    @Test
    fun `wide-gamut plan accepts Display P3 conversion to sRGB`() {
        val plan = GpuWideGamutWorkingSpacePlan.forPrimaries(GpuWideGamutPrimaries.DisplayP3)
        val conversion = plan.srgbConversion()
        assertTrue { conversion.matrix.size >= 9 } // 3x3 matrix
    }

    @Test
    fun `wide-gamut plan accepts Adobe RGB conversion`() {
        val plan = GpuWideGamutWorkingSpacePlan.forPrimaries(GpuWideGamutPrimaries.AdobeRGB)
        val conversion = plan.srgbConversion()
        assertTrue { conversion.matrix.isNotEmpty() }
    }

    @Test
    fun `wide-gamut plan accepts Rec2020 conversion`() {
        val plan = GpuWideGamutWorkingSpacePlan.forPrimaries(GpuWideGamutPrimaries.Rec2020)
        val conversion = plan.srgbConversion()
        assertTrue { conversion.matrix.isNotEmpty() }
    }

    @Test
    fun `wide-gamut route accepts all defined primaries`() {
        for (primaries in GpuWideGamutPrimaries.entries) {
            val plan = GpuWideGamutWorkingSpacePlan.forPrimaries(primaries)
            val route = plan.analyze()
            assertIs<GpuWideGamutRoute.Accepted>(route)
        }
    }

    @Test
    fun `wide-gamut route refuses unsupported transfer function`() {
        val plan = GpuWideGamutWorkingSpacePlan(
            primaries = GpuWideGamutPrimaries.DisplayP3,
            transferFunction = "SMPTE_ST_2084_HDR",
            intermediateFormat = GpuWideGamutIntermediateFormat.rgba16float,
        )
        val route = plan.analyze()
        assertIs<GpuWideGamutRoute.Refused>(route)
        assertEquals("unsupported.color.wide_gamut_transfer", route.diagnostic.code)
    }

    @Test
    fun `intermediate format produces correct descriptor`() {
        assertEquals("rgba16float", GpuWideGamutIntermediateFormat.rgba16float.descriptor)
        assertEquals("rgba32float", GpuWideGamutIntermediateFormat.rgba32float.descriptor)
    }
}
