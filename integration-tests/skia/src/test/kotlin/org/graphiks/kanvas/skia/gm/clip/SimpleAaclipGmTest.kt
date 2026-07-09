package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SimpleAaclipGmTest {
    @Test
    fun `port keeps Skia background and clip geometry`() {
        val gm = SimpleAaclipRectGm()
        val surface = Surface(width = gm.width, height = gm.height)

        gm.draw(GmCanvas(surface.canvas(), gm.width, gm.height), gm.width, gm.height)

        val rects = surface.snapshotOps().filterIsInstance<DisplayOp.DrawRect>()
        assertEquals(Rect.fromLTRB(0f, 0f, 500f, 240f), rects.first().rect)
        assertEquals(Color(0xFFDDDDDDu), rects.first().paint.color)

        val outlines = rects.filter { it.paint.style == PaintStyle.STROKE }
        assertEquals(Rect.fromLTRB(100.65f, 100.65f, 150.65f, 150.65f), outlines[0].rect)
        assertEquals(Rect.fromLTRB(130.65f, 130.65f, 170.65f, 170.65f), outlines[1].rect)
    }
}
