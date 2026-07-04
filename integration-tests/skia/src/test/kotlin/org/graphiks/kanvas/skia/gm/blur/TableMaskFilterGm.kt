package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/tablemaskfilter.cpp`.
 * Builds a 256-entry alpha LUT mask filter that halves AA coverage but preserves opaque pixels.
 * @see https://github.com/google/skia/blob/main/gm/tablemaskfilter.cpp
 */
class TableMaskFilterGm : SkiaGm {
    override val name = "tablemaskfilter"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val table = UByteArray(256) { 128u }
        table[255] = 255u

        val paint = Paint(
            style = PaintStyle.FILL,
            color = Color.BLACK,
            maskFilter = MaskFilter.Table(table),
        )

        val bounds = Rect(38f, 38f, 218f, 218f)

        // Rect (CW) + oval approximated as CCW polygon — opposite winding creates a hole
        val cx = (bounds.left + bounds.right) / 2f
        val cy = (bounds.top + bounds.bottom) / 2f
        val rx = bounds.width / 2f
        val ry = bounds.height / 2f
        val path = Path { }.apply {
            addRect(bounds)
            val steps = 64
            moveTo(cx + rx, cy)
            for (i in 1..steps) {
                val a = (i.toFloat() / steps) * 2f * PI.toFloat()
                lineTo(cx + rx * cos(a), cy + ry * sin(a))
            }
            close()
        }

        canvas.drawPath(path, paint)
    }
}
