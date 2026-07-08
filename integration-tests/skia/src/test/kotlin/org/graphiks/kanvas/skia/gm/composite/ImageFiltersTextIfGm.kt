package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/** Tests text rendering describing color and image filter compositing modes. */
class ImageFiltersTextIfGm : SkiaGm {
    override val name = "imagefilterstext_if"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val font = org.graphiks.kanvas.text.Font(Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!, 36f)
        canvas.drawString("Image Filters Test", 20f, 60f, font, Paint(color = Color.BLACK))
        val smallFont = org.graphiks.kanvas.text.Font(Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!, 24f)
        canvas.drawString("Color filters affect rendering", 20f, 110f, smallFont, Paint(color = Color.fromRGBA(0.5f, 0f, 0f, 1f)))
        canvas.drawString("by modifying pixel colors", 20f, 150f, smallFont, Paint(color = Color.fromRGBA(0f, 0f, 0.5f, 1f)))
        canvas.drawString("in various compositing modes", 20f, 190f, smallFont, Paint(color = Color.fromRGBA(0f, 0.5f, 0f, 1f)))
    }
}
