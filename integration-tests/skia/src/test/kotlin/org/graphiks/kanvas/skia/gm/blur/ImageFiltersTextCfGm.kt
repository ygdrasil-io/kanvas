package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Placeholder GM sketching primitives for image-filter + color-filter text rendering. */
class ImageFiltersTextCfGm : SkiaGm {
    override val name = "imagefilterstext_cf"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        canvas.drawRect(Rect(50f, 50f, 200f, 100f), Paint(color = Color.RED))
        canvas.drawRect(Rect(100f, 80f, 250f, 130f), Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.5f)))
        canvas.drawRect(Rect(300f, 50f, 450f, 100f), Paint(color = Color.GREEN))
    }
}
