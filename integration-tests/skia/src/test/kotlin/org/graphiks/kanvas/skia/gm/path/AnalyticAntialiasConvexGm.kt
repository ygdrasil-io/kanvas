package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/aaa.cpp::analytic_antialias_convex`.
 * Stresses analytic-AA convex fill with rotated rects, thin rects,
 * degenerate cubic, and skbug regression cases.
 * @see https://github.com/google/skia/blob/main/gm/aaa.cpp
 */
class AnalyticAntialiasConvexGm : SkiaGm {
    override val name = "analytic_antialias_convex"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 88.8
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true, color = Color.RED)
        canvas.drawRect(Rect(0f, 0f, 800f, 800f), Paint(color = Color.WHITE))

        canvas.drawRect(Rect(20f, 20f, 200f, 200f), paint)

        var y = 200f

        canvas.save()
        canvas.translate(0f, y)
        canvas.drawRect(Rect(20f, 20f, 20.2f, 200f), paint)
        canvas.drawRect(Rect(20f, 200f, 200f, 200.1f), paint)
        canvas.drawCircle(100f, 100f, 30f, paint)
        canvas.restore()

        val path1 = Path {
            moveTo(Float.fromBits(0x429b9d5c), Float.fromBits(0x4367a041))
            cubicTo(
                Float.fromBits(0x429b9d71), Float.fromBits(0x4367a022),
                Float.fromBits(0x429b9d64), Float.fromBits(0x4367a009),
                Float.fromBits(0x429b9d50), Float.fromBits(0x43679ff2),
            )
            lineTo(Float.fromBits(0x429b9d5c), Float.fromBits(0x4367a041))
            close()
        }
        canvas.drawPath(path1, paint)

        y += 200f
        canvas.save()
        canvas.translate(0f, y)
        val path2 = Path {
            moveTo(1.98009784f, 9.0162744f)
            lineTo(47.843992f, 10.1922744f)
            lineTo(47.804008f, 11.7597256f)
            lineTo(1.93990216f, 10.5837256f)
        }
        canvas.drawPath(path2, paint)
        canvas.restore()

        val path3 = Path {
            moveTo(700f, 266f)
            lineTo(710f, 266f)
            lineTo(710f, 534f)
            lineTo(700f, 534f)
        }
        canvas.drawPath(path3, paint)
    }
}
