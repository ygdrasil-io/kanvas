package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/circulararcs.cpp::bug406747427`.
 * Three drawArc calls with kRound_Cap and stroke widths that exceed the
 * arc's radius, exercising a stroker round-cap bug.
 * @see https://github.com/google/skia/blob/main/gm/circulararcs.cpp
 */
class Bug406747427Gm : SkiaGm {
    override val name = "bug406747427"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeCap = StrokeCap.ROUND,
        )

        canvas.drawArc(
            Rect.fromXYWH(100f, 40f, 50f, 50f), 45f, 275f, useCenter = false,
            paint = paint.copy(color = Color.fromRGBA(1f, 0f, 0f, 1f), strokeWidth = 50f),
        )

        canvas.drawArc(
            Rect.fromXYWH(100f, 140f, 50f, 50f), 45f, 275f, useCenter = false,
            paint = paint.copy(color = Color.fromRGBA(0f, 0f, 1f, 1f), strokeWidth = 48f),
        )

        canvas.drawArc(
            Rect.fromXYWH(100f, 280f, 50f, 50f), 45f, 275f, useCenter = false,
            paint = paint.copy(color = Color.fromRGBA(0f, 1f, 0f, 1f), strokeWidth = 80f),
        )
    }
}
