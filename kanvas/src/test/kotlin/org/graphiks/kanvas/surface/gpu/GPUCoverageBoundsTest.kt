package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GPUCoverageBoundsTest {
    @Test
    fun `coverage scissor expands fractional thin rect to touched pixels`() {
        val scissor = coverageScissor(
            bounds = GPUBounds(17.125f, 1f, 17.5f, 21f),
            clipBounds = GPUBounds(0f, 0f, 240f, 320f),
            surfaceWidth = 240,
            surfaceHeight = 320,
        )

        assertEquals(GPUCoverageScissor(x = 17, y = 1, width = 1, height = 20), scissor)
    }

    @Test
    fun `coverage scissor includes both pixels crossed by fractional bounds`() {
        val scissor = coverageScissor(
            bounds = GPUBounds(17.75f, 1f, 18.25f, 21f),
            clipBounds = GPUBounds(0f, 0f, 240f, 320f),
            surfaceWidth = 240,
            surfaceHeight = 320,
        )

        assertEquals(GPUCoverageScissor(x = 17, y = 1, width = 2, height = 20), scissor)
    }

    @Test
    fun `coverage scissor clips fractional bounds at the surface edge`() {
        val scissor = coverageScissor(
            bounds = GPUBounds(-0.25f, 318.75f, 0.5f, 320.25f),
            clipBounds = GPUBounds(-10f, -10f, 250f, 330f),
            surfaceWidth = 240,
            surfaceHeight = 320,
        )

        assertEquals(GPUCoverageScissor(x = 0, y = 318, width = 1, height = 2), scissor)
    }

    @Test
    fun `coverage scissor returns null for bounds beyond the surface`() {
        val scissor = coverageScissor(
            bounds = GPUBounds(241f, 321f, 242f, 322f),
            clipBounds = GPUBounds(0f, 0f, 250f, 330f),
            surfaceWidth = 240,
            surfaceHeight = 320,
        )

        assertNull(scissor)
    }

    @Test
    fun `coverage scissor returns null for clip-disjoint bounds`() {
        val scissor = coverageScissor(
            bounds = GPUBounds(10f, 10f, 20f, 20f),
            clipBounds = GPUBounds(30f, 30f, 40f, 40f),
            surfaceWidth = 240,
            surfaceHeight = 320,
        )

        assertNull(scissor)
    }
}
