package org.graphiks.kanvas.paint

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.DiagnosticLevel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class EnumValuesTest {
    @Test
    fun `BlendMode has correct count`() {
        assertTrue(BlendMode.entries.size >= 28)
    }

    @Test
    fun `PaintStyle has 3 values`() {
        assertEquals(3, PaintStyle.entries.size)
        assertTrue(PaintStyle.entries.contains(PaintStyle.STROKE_AND_FILL))
    }

    @Test
    fun `TileMode has 4 values`() {
        assertEquals(4, TileMode.entries.size)
    }

    @Test
    fun `FillType has 4 values`() {
        assertEquals(4, FillType.entries.size)
    }

    @Test
    fun `DiagnosticLevel has 3 values`() {
        assertEquals(3, DiagnosticLevel.entries.size)
    }

    @Test
    fun `BlendFactor has common values`() {
        assertTrue(org.graphiks.kanvas.pipeline.BlendFactor.entries.size >= 10)
    }
}
