package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/preservefillrule.cpp::PreserveFillRuleGM(true)`.
 * 200pt star cells, 400 × 400 canvas. Lays out four star paths in a
 * 2×2 grid (7-pt winding / 5-pt winding / 7-pt even-odd / 5-pt even-odd)
 * to verify fill-type propagation through the rendering pipeline.
 * @see https://github.com/google/skia/blob/main/gm/preservefillrule.cpp
 */
class PreserveFillRuleBigGm : SkiaGm {
    override val name = "preservefillrule_big"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 47.7
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val starSize = 200f
        val cx = starSize / 2f
        val cy = starSize / 2f
        val r = starSize / 2f

        canvas.drawColor(1f, 1f, 1f)

        val paint = Paint(antiAlias = true, color = Color.GREEN)

        canvas.drawPath(makeStar(cx, cy, r, 7, FillType.WINDING), paint)

        canvas.save()
        canvas.translate(starSize, 0f)
        canvas.drawPath(makeStar(cx, cy, r, 5, FillType.WINDING), paint)
        canvas.restore()

        canvas.save()
        canvas.translate(0f, starSize)
        canvas.drawPath(makeStar(cx, cy, r, 7, FillType.EVEN_ODD), paint)
        canvas.restore()

        canvas.save()
        canvas.translate(starSize, starSize)
        canvas.drawPath(makeStar(cx, cy, r, 5, FillType.EVEN_ODD), paint)
        canvas.restore()
    }

    private fun makeStar(cx: Float, cy: Float, radius: Float, numPts: Int, fillType: FillType): Path {
        val step = 2
        val path = Path {
            moveTo(cx, cy - radius)
            for (i in 1 until numPts) {
                val idx = i * step % numPts
                val theta = idx * 2f * PI.toFloat() / numPts + PI.toFloat() / 2f
                val x = cx + radius * cos(theta)
                val y = cy - radius * sin(theta)
                lineTo(x, y)
            }
            close()
        }
        path.fillType = fillType
        return path
    }
}
