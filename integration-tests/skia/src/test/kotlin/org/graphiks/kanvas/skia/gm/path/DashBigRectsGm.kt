package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.random.Random

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(dashbigrects, …)` (256 × 256).
 * Draws 11 concentric dashed rects at exponentially increasing sizes (from
 * 4 × kOnOffInterval up to 1 000 000 000 × kOnOffInterval). Each rect
 * is given a random 16-bit-565-quantised colour.
 * The extremely large rect dimensions stress the dash counter arithmetic
 * at IEEE float precision limits — each width/height is chosen as
 * N * kOnOffInterval + kOnOffInterval/2 so the first and last dash
 * segments are always half-length, forcing the edge-clip logic.
 * Reference image: dashbigrects.png, 256 × 256, black BG.
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class DashBigRectsGm : SkiaGm {
    override val name = "dashbigrects"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(0)

        val kHalfStrokeWidth = 8
        val kOnOffInterval = 2 * kHalfStrokeWidth

        // Clear to black - use a fill rect
        canvas.drawRect(
            Rect.fromLTRB(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.BLACK)
        )

        var paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = (2 * kHalfStrokeWidth).toFloat(),
            strokeCap = StrokeCap.BUTT,
            pathEffect = PathEffect.Dash(
                floatArrayOf(kOnOffInterval.toFloat(), kOnOffInterval.toFloat()), 0f
            )
        )

        val gWidthHeights = floatArrayOf(
            1_000_000_000f * kOnOffInterval + kOnOffInterval / 2f,
            1_000_000f    * kOnOffInterval + kOnOffInterval / 2f,
            1_000f        * kOnOffInterval + kOnOffInterval / 2f,
            100f          * kOnOffInterval + kOnOffInterval / 2f,
            10f           * kOnOffInterval + kOnOffInterval / 2f,
            9f            * kOnOffInterval + kOnOffInterval / 2f,
            8f            * kOnOffInterval + kOnOffInterval / 2f,
            7f            * kOnOffInterval + kOnOffInterval / 2f,
            6f            * kOnOffInterval + kOnOffInterval / 2f,
            5f            * kOnOffInterval + kOnOffInterval / 2f,
            4f            * kOnOffInterval + kOnOffInterval / 2f,
        )

        for (i in gWidthHeights.indices) {
            val colorInt = rand.nextInt() or (0xFF shl 24)
            val r = (colorInt shr 16 and 0xFF) / 255f
            val g = (colorInt shr 8 and 0xFF) / 255f
            val b = (colorInt and 0xFF) / 255f
            paint = paint.copy(color = Color.fromRGBA(r, g, b, 1f))
            val offset = (2 * i * kHalfStrokeWidth + kHalfStrokeWidth).toFloat()
            canvas.drawRect(
                Rect.fromXYWH(offset, offset, gWidthHeights[i], gWidthHeights[i]),
                paint,
            )
        }
    }
}
