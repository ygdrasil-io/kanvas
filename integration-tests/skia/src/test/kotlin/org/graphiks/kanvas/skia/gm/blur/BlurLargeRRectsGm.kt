package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/blurroundrect.cpp` (large rrects variant).
 *  Tests blur mask filter on large rounded rectangles — draws filled
 *  and stroked rounded rects with various blur styles.
 *  @see https://github.com/google/skia/blob/main/gm/blurroundrect.cpp
 */
class BlurLargeRRectsGm : SkiaGm {
    override val name = "blur_large_rrects"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sigma = 0.57735f * 20f + 0.5f
        val paint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma),
        )
        val rect = Rect.fromLTRB(5f, -20000f, 240f, 25f)
        val rrect = RRect(rect, 40f)
        for (i in 0 until 4) {
            val r = if ((i and 1) != 0) 0xFF else 0
            val g = if ((i and 2) != 0) 0xFF else 0
            val b = if (i < 2) 0xFF else 0
            canvas.drawRRect(rrect, paint.copy(
                color = Color.fromRGBA(r / 255f, g / 255f, b / 255f, 1f),
            ))
            canvas.translate(150f, 150f)
            canvas.rotate(90f)
            canvas.translate(-150f, -150f)
        }
    }
}
