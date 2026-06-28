package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GpuSubpixelLcdTest {

    @Test
    fun `RGB horizontal pixel geometry has correct component order`() {
        val plan = GpuSubpixelLcdPlan.create(
            pixelGeometry = GpuPixelGeometry.RGBHorizontal,
            r = 0.8f, g = 0.6f, b = 0.4f,
        )
        assertEquals(0.8f, plan.coverageMask.rComponent)
        assertEquals(0.6f, plan.coverageMask.gComponent)
        assertEquals(0.4f, plan.coverageMask.bComponent)
    }

    @Test
    fun `BGR horizontal pixel geometry swaps red and blue`() {
        val plan = GpuSubpixelLcdPlan.create(
            pixelGeometry = GpuPixelGeometry.BGRHorizontal,
            r = 0.8f, g = 0.6f, b = 0.4f,
        )
        assertEquals(GpuPixelGeometry.BGRHorizontal, plan.pixelGeometry)
    }

    @Test
    fun `vertical pixel geometries are accepted`() {
        for (geo in listOf(GpuPixelGeometry.VRGBVertical, GpuPixelGeometry.VBGRVertical)) {
            val plan = GpuSubpixelLcdPlan.create(geo, 0.5f, 0.5f, 0.5f)
            assertEquals(geo, plan.pixelGeometry)
        }
    }

    @Test
    fun `subpixel LCD plan produces valid render step`() {
        val plan = GpuSubpixelLcdPlan.create(GpuPixelGeometry.RGBHorizontal, 1f, 1f, 1f)
        assertEquals("subpixel_lcd_rgb", plan.renderStep.modulation)
    }

    @Test
    fun `coverage mask components are clamped to valid range`() {
        val mask = GpuSubpixelCoverageMask(
            atlasEntry = "glyph_42",
            rComponent = 1.5f, gComponent = -0.5f, bComponent = 0.5f,
        )
        assertTrue { mask.rComponent in 0f..1f }
        assertTrue { mask.gComponent in 0f..1f }
        assertTrue { mask.bComponent in 0f..1f }
    }

    @Test
    fun `all pixel geometry values are valid`() {
        assertEquals(4, GpuPixelGeometry.entries.size)
        for (geo in GpuPixelGeometry.entries) {
            assertTrue { geo.name.isNotEmpty() }
        }
    }
}
