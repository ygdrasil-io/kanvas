package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.math.SK_ColorGRAY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GmCanvasTest {
    @Test
    fun `clear records a canvas clear operation`() {
        val surface = Surface(width = 10, height = 10)
        val canvas = GmCanvas(surface.canvas(), width = 10, height = 10)

        canvas.clear(Color.RED)

        assertEquals(listOf(DisplayOp.Clear(Color.RED)), surface.snapshotOps())
    }

    @Test
    fun `clear accepts skia color constants`() {
        val surface = Surface(width = 10, height = 10)
        val canvas = GmCanvas(surface.canvas(), width = 10, height = 10)

        canvas.clear(SK_ColorGRAY)

        assertEquals(listOf(DisplayOp.Clear(Color.fromArgbInt(SK_ColorGRAY))), surface.snapshotOps())
    }
}
