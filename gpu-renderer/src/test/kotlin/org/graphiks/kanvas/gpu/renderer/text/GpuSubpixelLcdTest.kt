package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GpuSubpixelLcdTest {

    @Test
    fun `RGB horizontal pixel geometry has correct component order`() {
        val plan = GPUSubpixelLCDPlan.create(
            pixelGeometry = GPUPixelGeometry.RGBHorizontal,
            r = 0.8f, g = 0.6f, b = 0.4f,
        )
        assertEquals(0.8f, plan.perComponentMask.rComponent)
        assertEquals(0.6f, plan.perComponentMask.gComponent)
        assertEquals(0.4f, plan.perComponentMask.bComponent)
    }

    @Test
    fun `BGR horizontal pixel geometry swaps red and blue`() {
        val plan = GPUSubpixelLCDPlan.create(
            pixelGeometry = GPUPixelGeometry.BGRHorizontal,
            r = 0.8f, g = 0.6f, b = 0.4f,
        )
        assertEquals(GPUPixelGeometry.BGRHorizontal, plan.pixelGeometry)
    }

    @Test
    fun `vertical pixel geometries are accepted`() {
        for (geo in listOf(GPUPixelGeometry.VRGBVertical, GPUPixelGeometry.VBGRVertical)) {
            val plan = GPUSubpixelLCDPlan.create(geo, 0.5f, 0.5f, 0.5f)
            assertEquals(geo, plan.pixelGeometry)
        }
    }

    @Test
    fun `subpixel LCD plan produces valid render step`() {
        val plan = GPUSubpixelLCDPlan.create(GPUPixelGeometry.RGBHorizontal, 1f, 1f, 1f)
        assertEquals("subpixel_lcd_rgb", plan.renderStep.modulation.modulation)
        assertEquals("subpixel_lcd_main", plan.renderStep.wgslModule.entryPoint)
    }

    @Test
    fun `coverage mask components are clamped to valid range`() {
        val mask = GPUSubpixelCoverageMask(
            atlasEntry = org.graphiks.kanvas.gpu.renderer.geometry.GPUAtlasEntryRef("glyph_42"),
            rComponent = 1f, gComponent = 0f, bComponent = 0.5f,
        )
        assertTrue { mask.rComponent in 0f..1f }
        assertTrue { mask.gComponent in 0f..1f }
        assertTrue { mask.bComponent in 0f..1f }
    }

    @Test
    fun `all pixel geometry values are valid`() {
        assertEquals(4, GPUPixelGeometry.entries.size)
        for (geo in GPUPixelGeometry.entries) {
            assertTrue { geo.name.isNotEmpty() }
        }
    }

    @Test
    fun `subpixel LCD route accepts rgba8unorm opaque with pixel geometry`() {
        val plan = GPUSubpixelLCDPlan.create(GPUPixelGeometry.RGBHorizontal, 1f, 1f, 1f)
        val ctx = GPUSubpixelLCDRouteContext(
            targetFormat = "rgba8unorm",
            pixelGeometryKnown = true,
            destinationOpaque = true,
            destinationReadAvailable = false,
        )
        val decision = GPUSubpixelLCDRouteContext.decide(ctx, plan)
        assertIs<GPUSubpixelLCDRouteDecision.Accepted>(decision)
    }

    @Test
    fun `subpixel LCD route refuses unknown pixel geometry`() {
        val plan = GPUSubpixelLCDPlan.create(GPUPixelGeometry.RGBHorizontal, 1f, 1f, 1f)
        val ctx = GPUSubpixelLCDRouteContext(
            targetFormat = "rgba8unorm",
            pixelGeometryKnown = false,
            destinationOpaque = true,
            destinationReadAvailable = false,
        )
        val decision = GPUSubpixelLCDRouteContext.decide(ctx, plan)
        assertIs<GPUSubpixelLCDRouteDecision.Refused>(decision)
        assertEquals(GPUTextDiagnosticCodes.SUBPIXEL_PIXEL_GEOMETRY, decision.diagnostic.code)
        assertTrue(decision.diagnostic.terminal)
    }

    @Test
    fun `subpixel LCD route refuses incompatible target format`() {
        val plan = GPUSubpixelLCDPlan.create(GPUPixelGeometry.RGBHorizontal, 1f, 1f, 1f)
        val ctx = GPUSubpixelLCDRouteContext(
            targetFormat = "bgra8unorm",
            pixelGeometryKnown = true,
            destinationOpaque = true,
            destinationReadAvailable = false,
        )
        val decision = GPUSubpixelLCDRouteContext.decide(ctx, plan)
        assertIs<GPUSubpixelLCDRouteDecision.Refused>(decision)
        assertEquals(GPUTextDiagnosticCodes.SUBPIXEL_TARGET_FORMAT, decision.diagnostic.code)
        assertTrue(decision.diagnostic.terminal)
    }

    @Test
    fun `subpixel LCD route refuses translucent without destination read`() {
        val plan = GPUSubpixelLCDPlan.create(GPUPixelGeometry.RGBHorizontal, 1f, 1f, 1f)
        val ctx = GPUSubpixelLCDRouteContext(
            targetFormat = "rgba8unorm",
            pixelGeometryKnown = true,
            destinationOpaque = false,
            destinationReadAvailable = false,
        )
        val decision = GPUSubpixelLCDRouteContext.decide(ctx, plan)
        assertIs<GPUSubpixelLCDRouteDecision.Refused>(decision)
        assertEquals(GPUTextDiagnosticCodes.DESTINATION_READ_UNACCEPTED, decision.diagnostic.code)
    }
}
