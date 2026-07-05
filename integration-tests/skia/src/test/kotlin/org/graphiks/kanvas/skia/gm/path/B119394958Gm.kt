package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/b_119394958.cpp`.
 * Repro for Android GPU bug: stroked round-cap arc batched with filled circle.
 * @see https://github.com/google/skia/blob/main/gm/b_119394958.cpp
 */
class B119394958Gm : SkiaGm {
    override val name = "b_119394958"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 27.9
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawCircle(50f, 50f, 45f, Paint(color = Color.BLUE, antiAlias = true))

        canvas.drawCircle(
            50f, 50f, 35f,
            Paint(
                color = Color.GREEN,
                style = PaintStyle.STROKE,
                strokeWidth = 5f,
                antiAlias = true,
            ),
        )

        canvas.drawArc(
            Rect.fromLTRB(30f, 30f, 70f, 70f),
            0f, 110f, false,
            Paint(
                color = Color.RED,
                style = PaintStyle.STROKE,
                strokeWidth = 5f,
                strokeCap = StrokeCap.ROUND,
                antiAlias = true,
            ),
        )
    }
}
