package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.PointMode
import kotlin.random.Random

/** Port of Skia's `gm/points.cpp` (mask-filter variant).
 *  Tests point rendering with mask filters — draws random coloured
 *  points with blur mask filter and various stroke caps.
 *  @see https://github.com/google/skia/blob/main/gm/points.cpp
 */
class PointsMaskFilterGm : SkiaGm {
    override val name = "points_maskfilter"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val n = 30
        val rand = Random(0)
        val pts = List(n) {
            Point2F32(rand.nextFloat() * 220f + 18f, rand.nextFloat() * 220f + 18f)
        }

        val sigma = 0.57735f * 6f + 0.5f
        val mf = MaskFilter.Blur(BlurStyle.NORMAL, sigma)
        val caps = listOf(StrokeCap.SQUARE, StrokeCap.ROUND)

        for (cap in caps) {
            canvas.drawPoints(
                PointMode.POINTS, pts,
                Paint(
                    antiAlias = true,
                    style = PaintStyle.STROKE,
                    strokeWidth = 10f,
                    strokeCap = cap,
                    maskFilter = mf,
                    color = ColorARGB.Black,
                ),
            )
            canvas.drawPoints(
                PointMode.POINTS, pts,
                Paint(
                    antiAlias = true,
                    style = PaintStyle.STROKE,
                    strokeWidth = 10f,
                    strokeCap = cap,
                    color = ColorARGB.Red,
                ),
            )
            canvas.translate(256f, 0f)
        }
    }
}
