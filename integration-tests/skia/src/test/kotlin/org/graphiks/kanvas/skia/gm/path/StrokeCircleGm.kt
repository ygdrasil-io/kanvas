package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/addarc.cpp::StrokeCircleGM`.
 * Concentric stroked ovals at decreasing size (24×24 down to ~1.5×1.5
 * source-units) drawn under a `scale(20, 20)` CTM. Stroke width is
 * 0.5 source-units (= 10 device-px), and each iteration insets the
 * rect by `delta = 0.75`.
 * @see https://github.com/google/skia/blob/main/gm/addarc.cpp
 */
class StrokeCircleGm : SkiaGm {
    override val name = "strokecircle"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 25.0
    override val width = 520
    override val height = 520

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(20f, 20f)
        canvas.translate(13f, 13f)

        val strokeWidth = 0.5f
        val delta = strokeWidth * 3f / 2f
        var r = Rect.fromXYWH(-12f, -12f, 24f, 24f)
        val rand = Random(0)

        while (r.width > strokeWidth * 2f) {
            canvas.save()
            val raw = rand.nextInt()
            val paint = Paint(
                antiAlias = true,
                style = PaintStyle.STROKE,
                strokeWidth = strokeWidth,
                color = fromInt(raw or (0xFF000000.toInt())),
            )
            canvas.drawOval(r, paint)
            canvas.restore()
            r = Rect.fromLTRB(
                r.left + delta, r.top + delta,
                r.right - delta, r.bottom - delta,
            )
        }
    }

    private fun fromInt(c: Int): org.graphiks.kanvas.types.Color =
        org.graphiks.kanvas.types.Color.fromRGBA(
            ((c ushr 16) and 0xFF) / 255f,
            ((c ushr 8) and 0xFF) / 255f,
            (c and 0xFF) / 255f,
            1f,
        )
}
