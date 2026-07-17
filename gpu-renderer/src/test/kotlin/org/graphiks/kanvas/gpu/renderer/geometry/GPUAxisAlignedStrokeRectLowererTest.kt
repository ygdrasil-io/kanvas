package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

class GPUAxisAlignedStrokeRectLowererTest {
    @Test
    fun `centered miter rect stroke lowers to four disjoint analytic bands`() {
        val lowered = assertIs<GPUAxisAlignedStrokeRectLoweringResult.Lowered>(
            GPUAxisAlignedStrokeRectLowerer().lower(request()),
        )

        val route = assertIs<GPUGeometryRoute.Analytic>(lowered.geometryPlan.route)
        assertEquals("analytic-annular-rect.coverage", route.renderStepLabel)
        assertEquals(GPUPixelBounds(45, 33, 275, 167), lowered.outerBounds)
        assertEquals(GPUPixelBounds(51, 39, 269, 161), lowered.innerBounds)
        assertEquals(
            listOf(
                GPUPixelBounds(45, 33, 275, 39),
                GPUPixelBounds(45, 161, 275, 167),
                GPUPixelBounds(45, 39, 51, 161),
                GPUPixelBounds(269, 39, 275, 161),
            ),
            lowered.coverageBands,
        )
        assertEquals("geometry:stroke-rect.analytic", lowered.geometryPlan.diagnostics.single().code)
    }

    @Test
    fun `first slice refuses subpixel width and target overflow explicitly`() {
        val subpixel = assertIs<GPUAxisAlignedStrokeRectLoweringResult.Refused>(
            GPUAxisAlignedStrokeRectLowerer().lower(request(strokeWidth = 5f)),
        )
        assertEquals("unsupported.stroke.rect_subpixel_first_slice", subpixel.diagnostic.code)

        val overflow = assertIs<GPUAxisAlignedStrokeRectLoweringResult.Refused>(
            GPUAxisAlignedStrokeRectLowerer().lower(
                request(pathBounds = GPUPixelBounds(2, 2, 272, 164)),
            ),
        )
        assertEquals("unsupported.stroke.rect_target_overflow", overflow.diagnostic.code)

        val integerOverflow = assertIs<GPUAxisAlignedStrokeRectLoweringResult.Refused>(
            GPUAxisAlignedStrokeRectLowerer().lower(
                request(
                    targetBounds = GPUPixelBounds(0, 0, Int.MAX_VALUE, 200),
                    pathBounds = GPUPixelBounds(Int.MAX_VALUE - 20, 36, Int.MAX_VALUE - 1, 164),
                ),
            ),
        )
        assertEquals("unsupported.stroke.rect_target_overflow", integerOverflow.diagnostic.code)
    }

    private fun request(
        targetBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 320, 200),
        pathBounds: GPUPixelBounds = GPUPixelBounds(48, 36, 272, 164),
        strokeWidth: Float = 6f,
    ) = GPUAxisAlignedStrokeRectLoweringRequest(
        targetBounds = targetBounds,
        pathBounds = pathBounds,
        strokeWidth = strokeWidth,
        pathKey = "path:scene:stroke-rect-outline:rect-outline:v1",
        provenance = "gpu-renderer-scenes",
    )
}
