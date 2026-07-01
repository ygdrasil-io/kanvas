package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
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
 * Port of Skia's `gm/emboss.cpp`.
 * Walks a small image-and-shape sequence to exercise the emboss mask filter pipeline.
 * @see https://github.com/google/skia/blob/main/gm/emboss.cpp
 */
class EmbossGm : SkiaGm {
    override val name = "emboss"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 61.0
    override val width = 600
    override val height = 120

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawCircle(50f, 50f, 50f, Paint(color = Color.BLACK))
        canvas.translate(110f, 0f)

        canvas.drawCircle(50f, 50f, 50f, Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, blurRadius(3f)),
        ))
        canvas.translate(110f, 0f)

        canvas.drawCircle(50f, 50f, 50f, Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, blurRadius(3f)),
            colorFilter = ColorFilter.Blend(Color.fromRGBA(1f, 0f, 0f, 1f), BlendMode.SRC_ATOP),
        ))
        canvas.translate(110f, 0f)

        canvas.drawCircle(50f, 50f, 30f, Paint(
            color = Color.BLUE,
            style = PaintStyle.STROKE,
            strokeWidth = 10f,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, blurRadius(4f)),
        ))
        canvas.translate(110f, 0f)

        canvas.drawRect(Rect.fromXYWH(0f, 40f, 50f, 30f), Paint(color = Color.RED))
        canvas.drawRect(Rect.fromXYWH(0f, 80f, 50f, 30f), Paint(color = Color.GREEN))
    }

    private companion object {
        fun blurRadius(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
