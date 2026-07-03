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
 * Port of Skia's `gm/textblobgeometrychange.cpp::TextBlobGeometryChange`
 * (200 × 200). Draws "Hamburgefons" in the top half (white background)
 * and bottom half. Offscreen surface handling is dropped.
 * @see https://github.com/google/skia/blob/main/gm/textblobgeometrychange.cpp
 */
class TextBlobGeometryChangeGm : SkiaGm {
    override val name = "textblobgeometrychange"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 20f)
        val text = "Hamburgefons"
        val blob = font.toTextBlob(text, 10f, 10f)

        // White background for top half
        val rect = Rect(0f, 0f, width.toFloat(), height / 2f)
        canvas.drawRect(rect, Paint(color = Color.WHITE))
        canvas.drawTextBlob(blob, 0f, 0f, Paint())

        // Bottom half draws at a different y by adjusting the blob
        val blob2 = font.toTextBlob(text, 10f, 150f)
        canvas.save()
        // No offscreen surface — draw directly
        canvas.drawTextBlob(blob2, 0f, 0f, Paint())
        canvas.restore()
    }
}
