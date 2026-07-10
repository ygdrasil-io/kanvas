package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.floor

/**
 * Port of Skia's `gm/textblobblockreordering.cpp::TextBlobBlockReordering`
 * (275 × 200). Draws "AB" at 56pt alias 3× — middle draw uses kSrcIn.
 * @see https://github.com/google/skia/blob/main/gm/textblobblockreordering.cpp
 */
class TextBlobBlockReorderingGm : SkiaGm {
    override val name = "textblobblockreordering"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.MEDIUM
    override val minSimilarity = 0.0
    override val width = 275
    override val height = 200

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 56f)
        val text = "AB"
        val blob = font.toTextBlob(text, 0f, 0f)
        val textWidth = font.measureText(text)
        val yOffset = font.size * 1.2f
        val xDelta = floor(textWidth).toInt()
        val yDelta = floor(yOffset).toInt() + 20

        canvas.drawColor(r = 0.5f, g = 0.5f, b = 0.5f) // SK_ColorGRAY
        canvas.translate(10f, 40f)

        val paint = Paint()
        canvas.drawTextBlob(blob, 0f, 0f, paint)

        canvas.translate(xDelta.toFloat(), yDelta.toFloat())

        val redPaint = Paint(color = Color.RED)
        val bounds = Rect(0f, -yOffset, textWidth, 0f)
        canvas.drawRect(bounds, redPaint)
        val srcInPaint = Paint(blendMode = BlendMode.SRC_IN)
        canvas.drawTextBlob(blob, 0f, 0f, srcInPaint)

        canvas.translate(xDelta.toFloat(), yDelta.toFloat())
        canvas.drawTextBlob(blob, 0f, 0f, paint)
    }
}
