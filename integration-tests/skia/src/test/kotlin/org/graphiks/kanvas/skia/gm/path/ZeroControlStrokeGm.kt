package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/strokes.cpp::Strokes5GM` (`zero_control_stroke`, 400 x 800).
 * Stress test for the stroker on curves with degenerate tangents at t=0 or t=1.
 * Note: conicTo is not available in the new Path API; conics are drawn as quads.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */
class ZeroControlStrokeGm : SkiaGm {
    override val name = "zero_control_stroke"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val p = Paint(
            color = Color.RED,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 40f,
            strokeCap = StrokeCap.BUTT,
        )

        canvas.drawPath(
            Path {
                moveTo(157.474f, 111.753f)
                cubicTo(128.5f, 111.5f, 35.5f, 29.5f, 35.5f, 29.5f)
            },
            p,
        )
        canvas.drawPath(
            Path { moveTo(250f, 50f); quadTo(280f, 80f, 280f, 80f) },
            p,
        )
        canvas.drawPath(
            Path { moveTo(150f, 50f); quadTo(180f, 80f, 180f, 80f) },
            p,
        )

        canvas.drawPath(
            Path {
                moveTo(157.474f, 311.753f)
                cubicTo(157.474f, 311.753f, 85.5f, 229.5f, 35.5f, 229.5f)
            },
            p,
        )
        canvas.drawPath(
            Path { moveTo(280f, 250f); quadTo(280f, 250f, 310f, 280f) },
            p,
        )
        canvas.drawPath(
            Path { moveTo(180f, 250f); quadTo(180f, 250f, 210f, 280f) },
            p,
        )
    }
}
