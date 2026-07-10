package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AAXfermodesPortParityTest {
    @Test
    fun `upstream mode order matches SkBlendMode C++ parity order`() {
        assertEquals(
            expectedSkiaBlendModes,
            AAXfermodesGm.upstreamBlendModes,
        )
    }

    @Test
    fun `SCREEN is the coefficient cutoff`() {
        assertEquals(15, AAXfermodesGm.upstreamCoeffSplit)
        assertEquals(BlendMode.SCREEN, AAXfermodesGm.upstreamBlendModes[AAXfermodesGm.upstreamCoeffSplit - 1])
        assertEquals(BlendMode.OVERLAY, AAXfermodesGm.upstreamBlendModes[AAXfermodesGm.upstreamCoeffSplit])
    }

    @Test
    fun `upstream geometry constants match the C++ GM`() {
        assertEquals(22, AAXfermodesGm.kShapeSize)
        assertEquals(36, AAXfermodesGm.kShapeSpacing)
        assertEquals(48, AAXfermodesGm.kShapeTypeSpacing)
        assertEquals(192, AAXfermodesGm.kPaintSpacing)
        assertEquals(66, AAXfermodesGm.kLabelSpacing)
        assertEquals(18, AAXfermodesGm.kMargin)
        assertEquals(27, AAXfermodesGm.kTitleSpacing)
        assertEquals(22, AAXfermodesGm.kSubtitleSpacing)
    }

    private val expectedSkiaBlendModes = listOf(
        BlendMode.CLEAR,
        BlendMode.SRC,
        BlendMode.DST,
        BlendMode.SRC_OVER,
        BlendMode.DST_OVER,
        BlendMode.SRC_IN,
        BlendMode.DST_IN,
        BlendMode.SRC_OUT,
        BlendMode.DST_OUT,
        BlendMode.SRC_ATOP,
        BlendMode.DST_ATOP,
        BlendMode.XOR,
        BlendMode.PLUS,
        BlendMode.MODULATE,
        BlendMode.SCREEN,
        BlendMode.OVERLAY,
        BlendMode.DARKEN,
        BlendMode.LIGHTEN,
        BlendMode.COLOR_DODGE,
        BlendMode.COLOR_BURN,
        BlendMode.HARD_LIGHT,
        BlendMode.SOFT_LIGHT,
        BlendMode.DIFFERENCE,
        BlendMode.EXCLUSION,
        BlendMode.MULTIPLY,
        BlendMode.HUE,
        BlendMode.SATURATION,
        BlendMode.COLOR,
        BlendMode.LUMINOSITY,
    )
}
