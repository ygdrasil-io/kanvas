package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/overdrawcanvas.cpp` (overdraw_text_xform).
 * Draws two text grids: left with absolute coords, right with CTM transforms.
 * Skips the SkOverdrawCanvas wrapper and A8 offscreen surface.
 * @see https://github.com/google/skia/blob/main/gm/overdrawcanvas.cpp
 */
class OverdrawTextXformGm : SkiaGm {
    override val name = "overdraw_text_xform"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 50.0
    override val width = 512
    override val height = 512

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.BLACK),
        )

        val font = Font(typeface, size = 12f)
        val text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        for (n in 1..20) {
            val y = n * 20f
            canvas.drawString(text, 10f, y, font, Paint(color = Color.WHITE))
        }

        for (n in 1..20) {
            val y = n * 20f
            canvas.save()
            canvas.translate(256f + 10f, y)
            canvas.drawString(text, 0f, 0f, font, Paint(color = Color.WHITE))
            canvas.restore()
        }
    }
}
