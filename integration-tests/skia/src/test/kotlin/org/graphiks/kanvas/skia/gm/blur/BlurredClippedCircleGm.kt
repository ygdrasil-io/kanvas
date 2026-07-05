package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
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
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurredclippedcircle.cpp`.
 * Precision artifacts in nested clips culminating in clipRRect difference.
 * @see https://github.com/google/skia/blob/main/gm/blurredclippedcircle.cpp
 */
class BlurredClippedCircleGm : SkiaGm {
    override val name = "blurredclippedcircle"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 85.6
    override val width = kWidth
    override val height = kHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xCC / 255f, 0xCC / 255f, 0xCC / 255f, 1f)

        canvas.scale(2f, 2f)

        val whitePaint = Paint(
            color = Color.WHITE,
            blendMode = BlendMode.SRC,
        )

        canvas.save()
        val clipRect1 = Rect.fromLTRB(0f, 0f, kWidth.toFloat(), kHeight.toFloat())
        canvas.clipRect(clipRect1)

        canvas.save()
        canvas.clipRect(clipRect1)
        canvas.drawRect(clipRect1, whitePaint)

        canvas.save()
        val clipRect2 = Rect.fromLTRB(8f, 8f, 288f, 288f)
        canvas.clipRect(clipRect2)

        val r = Rect.fromLTRB(4f, 4f, 292f, 292f)
        val path = Path { }.apply { addOval(r) }

        val paint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1.366025f),
            colorFilter = ColorFilter.Blend(Color.RED, BlendMode.SRC_IN),
        )
        canvas.drawPath(path, paint)
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }

    private companion object {
        const val kWidth: Int = 1164
        const val kHeight: Int = 802
    }
}
