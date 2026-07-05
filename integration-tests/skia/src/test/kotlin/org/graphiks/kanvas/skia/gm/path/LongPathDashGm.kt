package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/longpathdash.cpp`.
 * Tests dashed strokes on long paths with many radial line segments.
 * @see https://github.com/google/skia/blob/main/gm/longpathdash.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class LongPathDashGm : SkiaGm {
    override val name = "longpathdash"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 34.2
    override val width = 612
    override val height = 612

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val lines = Path {
            var x = 32
            while (x < 256) {
                var a = 0.0
                while (a < PI * 2) {
                    val pts0x = 256f + sin(a).toFloat() * x
                    val pts0y = 256f + cos(a).toFloat() * x
                    val pts1x = 256f + sin(a + PI / 3).toFloat() * (x + 64)
                    val pts1y = 256f + cos(a + PI / 3).toFloat() * (x + 64)
                    moveTo(pts0x, pts0y)
                    var i = 0f
                    while (i < 1f) {
                        lineTo(
                            pts0x * (1f - i) + pts1x * i,
                            pts0y * (1f - i) + pts1y * i,
                        )
                        i += 0.05f
                    }
                    a += 0.03141592
                }
                x += 16
            }
        }

        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            pathEffect = PathEffect.Dash(floatArrayOf(1f, 1f), 0f),
            antiAlias = true,
        )
        canvas.translate(50f, 50f)
        canvas.drawPath(lines, paint)
    }
}
