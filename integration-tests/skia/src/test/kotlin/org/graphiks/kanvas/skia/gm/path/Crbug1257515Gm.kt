package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_1257515.cpp` (1139 x 400).
 * Two long polylines under translate + scale(2, 2).
 * @see https://github.com/google/skia/blob/main/gm/crbug_1257515.cpp
 */
class Crbug1257515Gm : SkiaGm {
    override val name = "crbug_1257515"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 62.5
    override val width = 1139
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var paint = Paint(
            color = Color.RED,
            strokeWidth = 2f,
            style = PaintStyle.STROKE,
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.ROUND,
            antiAlias = true,
        )

        val red = Path {
            moveTo(45.125f, 102.53701800000002f)
            lineTo(135.375f, 162.666156f)
            lineTo(225.625f, 116.622276f)
            lineTo(315.875f, 121.52087700000001f)
            lineTo(406.125f, 134.632899f)
            lineTo(496.375f, 192.317736f)
            lineTo(586.625f, 138.82944899999998f)
            lineTo(676.875f, 234.212031f)
            lineTo(767.125f, 207.082926f)
            lineTo(857.375f, 128.083857f)
            lineTo(947.625f, 127.95689999999999f)
            lineTo(1037.875f, 113.956785f)
        }
        canvas.save()
        canvas.translate(-50f, -200f)
        canvas.scale(2f, 2f)
        canvas.drawPath(red, paint)
        canvas.restore()

        val blue = Path {
            moveTo(128.5307f, 587.5728f)
            lineTo(232.4748f, 617.037f)
            lineTo(335.4189f, 624.8472f)
            lineTo(438.3631f, 630.5933f)
            lineTo(541.3073f, 625.1138f)
            lineTo(644.2513f, 626.8717f)
            lineTo(747.1955f, 629.9542f)
            lineTo(850.1396f, 629.6956f)
            lineTo(953.0838f, 616.4909f)
            lineTo(1056.028f, 613.8181f)
        }
        paint = paint.copy(
            color = Color.fromRGBA(47f / 255f, 136f / 255f, 1f),
            strokeWidth = 3f,
            strokeCap = StrokeCap.BUTT,
            strokeJoin = StrokeJoin.BEVEL,
            strokeMiter = 10f,
        )

        canvas.save()
        canvas.translate(-300f, -900f)
        canvas.scale(2f, 2f)
        canvas.drawPath(blue, paint)
        canvas.restore()
    }
}
