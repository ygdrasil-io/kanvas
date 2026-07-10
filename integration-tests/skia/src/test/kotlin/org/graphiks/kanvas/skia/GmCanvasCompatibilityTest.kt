package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GmCanvasCompatibilityTest {
    @Test
    fun `drawColor compatibility API records a full-surface source fill`() {
        val surface = Surface(width = 12, height = 8)
        val canvas = GmCanvas(surface.canvas(), width = 12, height = 8)

        canvas.drawColor(0.25f, 0.5f, 0.75f, 1f, BlendMode.SRC)

        assertEquals(
            listOf(
                DisplayOp.DrawColor(
                    color = Color.fromRGBA(0.25f, 0.5f, 0.75f, 1f),
                    mode = BlendMode.SRC,
                    transform = Matrix33.identity(),
                    clip = org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                ),
            ),
            surface.snapshotOps(),
        )
    }

    @Test
    fun `drawStringAligned compatibility API shifts text by measured width`() {
        val surface = Surface(width = 80, height = 40)
        val canvas = GmCanvas(surface.canvas(), width = 80, height = 40)
        val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
        val font = Font(typeface, size = 16f)
        val text = "aligned"
        val alignment = 0.5f
        val expectedX = 40f - font.measureText(text) * alignment

        canvas.drawStringAligned(text, 40f, 20f, font, Paint(), alignment)

        val op = surface.snapshotOps().filterIsInstance<DisplayOp.DrawText>().single()
        assertEquals(expectedX, op.x)
        assertEquals(20f, op.y)
    }

    @Test
    fun `portableFont compatibility API returns a usable emboldened font`() {
        val font = portableFont(18f, bold = true)

        assertEquals(18f, font.size)
        assertTrue(font.isEmbolden)
        assertNotNull(font.getMetrics())
        assertTrue(font.measureText("portable") > 0f)
    }
}
