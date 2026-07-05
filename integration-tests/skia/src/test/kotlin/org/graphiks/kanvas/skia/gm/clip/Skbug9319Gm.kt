package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/skbug_9319.cpp` (256 × 512).
 *
 * Reproduces a GPU rect-blur bug where the outer portion was too dark
 * for very small sigmas. Uses [ClipOp.DIFFERENCE] clips with rect and
 * rrect shapes followed by blurred draws.
 * @see https://github.com/google/skia/blob/main/gm/skbug_9319.cpp
 */
class Skbug9319Gm : SkiaGm {
    override val name = "skbug_9319"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val p = Paint(
            antiAlias = true,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 0.5f),
        )

        val r = Rect.fromXYWH(10f, 10f, 100f, 100f)

        canvas.save()
        canvas.clipPath(Path { }.apply { addRect(r) }, ClipOp.DIFFERENCE)
        canvas.drawRect(r, p)
        canvas.restore()

        canvas.translate(0f, 120f)

        canvas.save()
        val rr = RRect(r, 0.1f)
        canvas.clipRRect(rr, ClipOp.DIFFERENCE)
        canvas.drawRRect(rr, p)
        canvas.restore()
    }
}
