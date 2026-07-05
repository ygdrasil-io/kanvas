package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/inversepaths.cpp::inverse_windingmode_filters` (256 x 100).
 * Probes interaction between blur mask filter and inverse-fill path types
 * under tight clipRects.
 * @see https://github.com/google/skia/blob/main/gm/inversepaths.cpp
 */
class InverseWindingmodeFiltersGm : SkiaGm {
    override val name = "inverse_windingmode_filters"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path { }.apply {
            addRect(Rect.fromLTRB(10f, 10f, 30f, 30f))
            addRect(Rect.fromLTRB(20f, 20f, 40f, 40f))
            addRect(Rect.fromLTRB(10f, 60f, 30f, 80f))
            addRect(Rect.fromLTRB(20f, 70f, 40f, 90f))
        }

        val strokePaint = Paint(style = PaintStyle.STROKE)
        val clipRect = Rect.fromLTRB(0f, 0f, 51f, 99f)
        canvas.drawPath(path, strokePaint)

        val fillPaint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
        )

        for (fillType in listOf(
            FillType.WINDING,
            FillType.EVEN_ODD,
            FillType.INVERSE_WINDING,
            FillType.INVERSE_EVEN_ODD,
        )) {
            canvas.translate(51f, 0f)
            canvas.save()
            canvas.clipRect(clipRect)
            path.fillType = fillType
            canvas.drawPath(path, fillPaint)
            canvas.restore()
            val clipPaint = Paint(
                color = Color.RED,
                style = PaintStyle.STROKE,
                strokeWidth = 1f,
            )
            canvas.drawRect(clipRect, clipPaint)
        }
    }
}
