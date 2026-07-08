package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests text rendering with color filters in a composite context. */
class ImageFiltersTextCfGm : SkiaGm {
    override val name = "imagefilterstext_cf"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val font = org.graphiks.kanvas.text.Font(Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!, 48f)
        canvas.drawString("Color Filter", 20f, 80f, font, Paint(color = Color.RED))
        canvas.drawString("Image Filter", 20f, 160f, font, Paint(color = Color.BLUE))
        canvas.drawRect(Rect.fromXYWH(20f, 180f, 200f, 50f),
            Paint(color = Color.fromRGBA(0f, 1f, 0f, 0.5f)))
    }
}
