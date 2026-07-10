package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/skbug1719.cpp` (300 × 100).
 *
 * Reproduces a clip + blur + colorFilter precision bug.
 * A round-rect clipPath is intersected, then a slightly-larger
 * nested-rect path is drawn through it with a tiny-sigma blur and
 * a colorFilter that re-tints the result.
 * @see https://github.com/google/skia/blob/main/gm/skbug1719.cpp
 */
class Skbug1719Gm : SkiaGm {
    override val name = "skbug1719"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0x30 / 255f, 0x30 / 255f, 0x30 / 255f)

        canvas.translate(-820f, -650f)

        val clipPath = Path {
            moveTo(832f, 654f)
            lineTo(1034f, 654f)
            cubicTo(1038.4183f, 654f, 1042f, 657.58173f, 1042f, 662f)
            lineTo(1042f, 724f)
            cubicTo(1042f, 728.41827f, 1038.4183f, 732f, 1034f, 732f)
            lineTo(832f, 732f)
            cubicTo(827.58173f, 732f, 824f, 728.41827f, 824f, 724f)
            lineTo(824f, 662f)
            cubicTo(824f, 657.58173f, 827.58173f, 654f, 832f, 654f)
            close()
        }

        val drawPath = Path { }
        drawPath.fillType = FillType.EVEN_ODD
        drawPath.moveTo(823f, 653f)
        drawPath.lineTo(1043f, 653f)
        drawPath.lineTo(1043f, 733f)
        drawPath.lineTo(823f, 733f)
        drawPath.lineTo(823f, 653f)
        drawPath.close()
        drawPath.moveTo(832f, 654f)
        drawPath.lineTo(1034f, 654f)
        drawPath.cubicTo(1038.4183f, 654f, 1042f, 657.58173f, 1042f, 662f)
        drawPath.lineTo(1042f, 724f)
        drawPath.cubicTo(1042f, 728.41827f, 1038.4183f, 732f, 1034f, 732f)
        drawPath.lineTo(832f, 732f)
        drawPath.cubicTo(827.58173f, 732f, 824f, 728.41827f, 824f, 724f)
        drawPath.lineTo(824f, 662f)
        drawPath.cubicTo(824f, 657.58173f, 827.58173f, 654f, 832f, 654f)
        drawPath.close()

        val paint = Paint(
            antiAlias = true,
            color = Color.BLACK,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 0.78867501f),
            colorFilter = ColorFilter.Blend(Color(0xBFFFFFFFu), BlendMode.SRC_IN),
        )

        canvas.clipPath(clipPath, antiAlias = true)
        canvas.drawPath(drawPath, paint)
    }
}
