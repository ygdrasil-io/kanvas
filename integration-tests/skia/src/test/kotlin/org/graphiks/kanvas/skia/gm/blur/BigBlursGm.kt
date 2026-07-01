package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bigblurs.cpp`.
 * 65536×65536 rect and rectori (ring) drawn under 4 blur styles
 * with 5 close-up views each (4 corners + centre), 64 px per cell.
 * @see https://github.com/google/skia/blob/main/gm/bigblurs.cpp
 */
class BigBlursGm : SkiaGm {
    override val name = "bigblurs"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 31.0
    override val width = kWidth
    override val height = kHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, kWidth.toFloat(), kHeight.toFloat()),
            Paint(color = Color.fromRGBA(0.867f, 0.867f, 0.867f, 1f)))

        val kBig = 65536f
        val sigma = convertRadiusToSigma(4f)

        val bigRect = Rect(0f, 0f, kBig, kBig)
        val insetRect = Rect(20f, 20f, kBig - 20f, kBig - 20f)

        val rectori = Path {
            moveTo(0f, 0f)
            lineTo(kBig, 0f)
            lineTo(kBig, kBig)
            lineTo(0f, kBig)
            close()
            moveTo(20f, 20f)
            lineTo(20f, kBig - 20f)
            lineTo(kBig - 20f, kBig - 20f)
            lineTo(kBig - 20f, 20f)
            close()
        }

        val kLeftTopPad = 3f * sigma
        val kRightBotPad = kCloseUpSize - 3f * sigma

        val origins = arrayOf(
            Pair(-kLeftTopPad, -kLeftTopPad),
            Pair(kBig - kRightBotPad, -kLeftTopPad),
            Pair(kBig - kRightBotPad, kBig - kRightBotPad),
            Pair(-kLeftTopPad, kBig - kRightBotPad),
            Pair(kBig / 2 - kCloseUpSize / 2, kBig / 2 - kCloseUpSize / 2),
        )

        val outlinePaint = Paint(
            color = Color.RED,
            style = PaintStyle.STROKE,
        )
        val blurPaint = Paint(
            antiAlias = true,
            color = Color.BLACK,
        )

        var desiredX = 0f
        var desiredY = 0f
        val blurStyles = listOf(BlurStyle.NORMAL, BlurStyle.SOLID, BlurStyle.OUTER, BlurStyle.INNER)

        for (i in 0 until 2) {
            for (style in blurStyles) {
                val bp = blurPaint.copy(maskFilter = MaskFilter.Blur(style, sigma))

                for (k in origins.indices) {
                    canvas.save()

                    val clipRect = Rect.fromXYWH(desiredX, desiredY, kCloseUpSize, kCloseUpSize)
                    canvas.clipRect(clipRect)

                    canvas.translate(desiredX - origins[k].first, desiredY - origins[k].second)

                    if (i == 0) {
                        canvas.drawRect(bigRect, bp)
                    } else {
                        canvas.drawPath(rectori, bp)
                    }
                    canvas.restore()
                    canvas.drawRect(clipRect, outlinePaint)

                    desiredX += kCloseUpSize
                }
                desiredX = 0f
                desiredY += kCloseUpSize
            }
        }
    }

    private fun convertRadiusToSigma(radius: Float): Float =
        if (radius > 0f) 0.57735f * radius + 0.5f else 0f

    private companion object {
        const val kCloseUpSize: Float = 64f
        const val kWidth: Int = 5 * 64
        const val kHeight: Int = 2 * 4 * 64
    }
}
