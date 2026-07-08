package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/nested.cpp`.
 * Tests nested path rendering with various shape combinations (rect, round-rect, oval).
 * @see https://github.com/google/skia/blob/main/gm/nested.cpp
 */
class NestedGm : SkiaGm {
    override val name = "nested"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 269
    override val height = 134

    private enum class Shape { RECT, ROUND_RECT, OVAL }

    private fun addShape(path: Path, rect: Rect, shape: Shape) {
        when (shape) {
            Shape.RECT -> path.addRect(rect)
            Shape.ROUND_RECT -> path.addRRect(RRect(rect, 5f))
            Shape.OVAL -> path.addOval(rect)
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rng = Random(0)
        for (y in 0 until height step 10) {
            for (x in 0 until width step 10) {
                val r = Rect.fromXYWH(x.toFloat(), y.toFloat(), 10f, 10f)
                val c = rng.nextInt() or 0xFF000000.toInt()
                canvas.drawRect(r, Paint(color = Color.fromArgbInt(c)))
            }
        }
        val outerRect = Rect.fromXYWH(0f, 0f, 40f, 40f)
        val innerRects = arrayOf(
            Rect.fromLTRB(10f, 10f, 30f, 30f),
            Rect.fromLTRB(0.5f, 18f, 4.5f, 22f),
        )
        val shapePaint = Paint(color = Color.BLACK, antiAlias = true)
        var xOff = 2f; var yOff = 2f
        for (outerShape in Shape.entries) {
            for (innerShape in Shape.entries) {
                for (innerRect in innerRects) {
                    val path = Path { }.apply {
                        addShape(this, outerRect, outerShape)
                        addShape(this, innerRect, innerShape)
                    }
                    canvas.save()
                    canvas.translate(xOff, yOff)
                    canvas.drawPath(path, shapePaint)
                    canvas.restore()
                    xOff += 45f
                }
            }
            xOff = 2f; yOff += 45f
        }
    }
}
