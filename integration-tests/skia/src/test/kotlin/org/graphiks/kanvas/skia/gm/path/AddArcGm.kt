package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.random.Random

/**
 * Port of Skia's `gm/addarc.cpp:AddArcGM`.
 * Concentric stroked open arcs of 345° sweep, randomly rotated and
 * insets by strokeWidth + 4 per iteration, until the rect would no
 * longer fit two stroke widths across. The path is built via
 * canvas.drawArc (oval + start + sweep) — exercising the
 * cubic-Bézier arc emitter end-to-end.
 * @see https://github.com/google/skia/blob/main/gm/addarc.cpp
 */
class AddArcGm : SkiaGm {
    override val name = "addarc"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1040
    override val height = 1040

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(20f, 20f)

        var r = Rect.fromLTRB(0f, 0f, 1000f, 1000f)

        var paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 15f
        )

        val inset = paint.strokeWidth + 4f
        val sweepAngle = 345f
        val rand = Random(0)

        var sign = 1f
        while (r.width > paint.strokeWidth * 3f) {
            val rInt = rand.nextInt(256)
            val gInt = rand.nextInt(256)
            val bInt = rand.nextInt(255)
            paint = paint.copy(color = Color.fromRGBA(rInt / 255f, gInt / 255f, bInt / 255f, 1f))
            val startAngle = rand.nextFloat() * 360f

            canvas.drawArc(r, startAngle, sweepAngle, useCenter = false, paint = paint)

            r = Rect.fromLTRB(
                r.left + inset, r.top + inset,
                r.right - inset, r.bottom - inset
            )
            sign = -sign
        }
    }
}
