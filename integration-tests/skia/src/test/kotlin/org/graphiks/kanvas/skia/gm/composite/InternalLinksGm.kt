package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/internal_links.cpp::InternalLinksGM` (700 x 500, gray BG).
 * Draws labeled rects that exercise annotation calls (no-ops on raster).
 * @see https://github.com/google/skia/blob/main/gm/internal_links.cpp
 */
class InternalLinksGm : SkiaGm {
    override val name = "internal_links"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 700
    override val height = 500

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bg = Color.fromRGBA(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = bg))

        // Panel 1 - "Link to A"
        canvas.save()
        canvas.translate(100f, 100f)
        drawLabeledRect(canvas, "Link to A", 0f, 0f)
        canvas.restore()

        // Panel 2 - "Target A"
        canvas.save()
        canvas.translate(200f, 200f)
        drawLabeledRect(canvas, "Target A", 100f, 50f)
        canvas.restore()
    }

    private fun drawLabeledRect(canvas: GmCanvas, text: String, x: Float, y: Float) {
        canvas.drawRect(Rect.fromXYWH(x, y, 50f, 20f), Paint(color = Color.BLUE))
        val font = Font(typeface, size = 25f)
        canvas.drawString(text, x, y, font, Paint(color = Color.BLACK))
    }
}
