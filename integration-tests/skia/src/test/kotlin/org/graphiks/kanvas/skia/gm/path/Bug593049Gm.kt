package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/arcto.cpp` DEF_SIMPLE_GM(bug593049, ...).
 * A single half-arc starting at a non-axis-aligned point, stroked with
 * kRound_Cap, testing the stroker with wide round caps on arcs.
 * @see https://github.com/google/skia/blob/main/gm/arcto.cpp
 */
class Bug593049Gm : SkiaGm {
    override val name = "bug593049"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(111f, 0f)

        val yOffset = 122.88f
        val radius = 61.44f
        val oval = Rect.fromXYWH(-radius, yOffset - radius, 2f * radius, 2f * radius)

        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeCap = StrokeCap.ROUND,
            strokeWidth = 15.36f,
        )

        canvas.drawArc(oval, 225f, 90f, useCenter = false, paint = paint)
    }
}
