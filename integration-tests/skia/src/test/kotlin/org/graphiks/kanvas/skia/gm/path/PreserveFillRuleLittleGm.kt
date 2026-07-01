package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/preservefillrule.cpp::PreserveFillRuleGM(false)`.
 * 20pt star cells, 40 × 40 canvas. Same 2×2 layout as the big variant
 * but at tiny scale to stress the path cache / fill-type interaction.
 * @see https://github.com/google/skia/blob/main/gm/preservefillrule.cpp
 */
class PreserveFillRuleLittleGm : SkiaGm {
    override val name = "preservefillrule_little"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 40.4
    override val width = 40
    override val height = 40

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val starSize = 20f
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
