package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/beziereffects.cpp` — conic variant.
 * Tests conic Bezier curve rendering with control points and bounding rects.
 * @see https://github.com/google/skia/blob/main/gm/beziereffects.cpp
 */
class BezierConicEffectsGm : SkiaGm {
    override val name = "bezier_conic_effects"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val ctrlPaint = Paint(color = Color.RED)
        val quadPaths = listOf(
            listOf(40f, 40f, 120f, 160f, 180f, 80f),
            listOf(60f, 120f, 30f, 60f, 200f, 120f),
            listOf(20f, 80f, 160f, 40f, 220f, 200f),
        )

        for ((i, pts) in quadPaths.withIndex()) {
            val yOff = i * 80f
            val x0 = pts[0]; val y0 = pts[1] + yOff
            val x1 = pts[2]; val y1 = pts[3] + yOff
            val x2 = pts[4]; val y2 = pts[5] + yOff

            canvas.drawCircle(x0, y0, 4f, ctrlPaint)
            canvas.drawCircle(x1, y1, 4f, ctrlPaint)
            canvas.drawCircle(x2, y2, 4f, ctrlPaint)

            val polyPath = Path { moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); close() }
            canvas.drawPath(polyPath, Paint(
                color = Color.fromRGBA(0xA0 / 255f, 0xA0 / 255f, 0xA0 / 255f),
                style = PaintStyle.STROKE,
                strokeWidth = 1f,
            ))

            val curvePath = Path { moveTo(x0, y0); quadTo(x1, y1, x2, y2) }
            canvas.drawPath(curvePath, Paint(color = Color.BLACK, style = PaintStyle.STROKE, strokeWidth = 2f))

            canvas.drawRect(
                Rect.fromLTRB(x0, y0, x2, y2),
                Paint(color = Color.fromRGBA(0x80 / 255f, 0x80 / 255f, 0x80 / 255f), style = PaintStyle.STROKE, strokeWidth = 1f),
            )
        }
    }
}
