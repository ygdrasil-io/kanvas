package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.geometry.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Port of Skia's `gm/batchedconvexpaths.cpp`.
 *  Tests batched convex path rendering — draws many convex polygon
 *  paths (triangles, pentagons, stars) at various positions.
 *  @see https://github.com/google/skia/blob/main/gm/batchedconvexpaths.cpp
 */
class BatchedConvexPathsGm : SkiaGm {
    override val name = "batchedconvexpaths"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 30.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f)

        for (i in 0 until 10) {
            canvas.save()
            val numPoints = (i + 3) * 3
            val path = Path { }
            path.moveTo(1f, 0f)
            var j = 1
            while (j < numPoints) {
                val k2pi = (PI * 2.0).toFloat()
                val a1 = j.toFloat() / numPoints * k2pi
                val a2 = (j + 1).toFloat() / numPoints * k2pi
                val a3 = (j + 2).toFloat() / numPoints * k2pi
                val ex: Float
                val ey: Float
                if (j + 2 == numPoints) {
                    ex = 1f
                    ey = 0f
                } else {
                    ex = cos(a3.toDouble()).toFloat()
                    ey = sin(a3.toDouble()).toFloat()
                }
                path.cubicTo(
                    cos(a1.toDouble()).toFloat(), sin(a1.toDouble()).toFloat(),
                    cos(a2.toDouble()).toFloat(), sin(a2.toDouble()).toFloat(),
                    ex, ey,
                )
                j += 3
            }

            val scale = (256 - i * 24).toFloat()
            canvas.translate(scale + (256f - scale) * 0.33f, scale + (256f - scale) * 0.33f)
            canvas.scale(scale, scale)

            val raw = ((i + 123458383) * 285018463) or (0xff808080.toInt())
            val r = ((raw ushr 16) and 0xFF) / 255f
            val g = ((raw ushr 8) and 0xFF) / 255f
            val b = (raw and 0xFF) / 255f

            val paint = Paint(color = Color.fromRGBA(r, g, b, 0.3f), antiAlias = true)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}
