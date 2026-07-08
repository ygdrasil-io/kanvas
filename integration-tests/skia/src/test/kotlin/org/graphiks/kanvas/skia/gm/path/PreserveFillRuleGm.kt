package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/preservefillrule.cpp`.
 * Tests fill-rule preservation with star polygons using WINDING and EVEN_ODD.
 * @see https://github.com/google/skia/blob/main/gm/preservefillrule.cpp
 */
class PreserveFillRuleGm : SkiaGm {
    override val name = "preservefillrule"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val starSize = 200f
        val cx = starSize / 2f; val cy = starSize / 2f
        val paint = Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f), antiAlias = true)
        for (row in 0..1) {
            for (col in 0..1) {
                val n = if (col == 0) 7 else 5
                val path = Path {
                    for (i in 0 until 2 * n) {
                        val angle = (i * PI.toFloat() / n) - PI.toFloat() / 2f
                        val r = if (i % 2 == 0) 1f else 0.5f
                        val x = cx + (starSize / 2f) * r * cos(angle)
                        val y = cy + (starSize / 2f) * r * sin(angle)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }.also {
                    it.fillType = if (row == 0) FillType.WINDING else FillType.EVEN_ODD
                }
                canvas.save()
                canvas.translate(col * starSize, row * starSize)
                canvas.drawPath(path, paint)
                canvas.restore()
            }
        }
    }
}
