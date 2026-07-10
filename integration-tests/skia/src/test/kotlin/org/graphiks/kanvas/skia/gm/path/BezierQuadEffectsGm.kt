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
 * Port of Skia's `gm/beziereffects.cpp` — quad variant.
 * Tests quadratic Bezier curve rendering with control points and bounding rects.
 * @see https://github.com/google/skia/blob/main/gm/beziereffects.cpp
 */
class BezierQuadEffectsGm : SkiaGm {
    override val name = "bezier_quad_effects"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val ctrlPaint = Paint(color = Color.RED)
        val quads = listOf(
            listOf(30f, 50f, 100f, 20f, 180f, 80f),
            listOf(20f, 130f, 150f, 50f, 200f, 170f),
            listOf(40f, 200f, 80f, 120f, 220f, 230f),
        )

        for ((i, pts) in quads.withIndex()) {
            val x0 = pts[0]; val y0 = pts[1]
            val cx = pts[2]; val cy = pts[3]
            val x2 = pts[4]; val y2 = pts[5]

            canvas.drawCircle(x0, y0, 4f, ctrlPaint)
            canvas.drawCircle(cx, cy, 4f, ctrlPaint)
            canvas.drawCircle(x2, y2, 4f, ctrlPaint)

            val polyPath = Path { moveTo(x0, y0); lineTo(cx, cy); lineTo(x2, y2); close() }
            canvas.drawPath(polyPath, Paint(
                color = Color.fromRGBA(0xA0 / 255f, 0xA0 / 255f, 0xA0 / 255f),
                style = PaintStyle.STROKE,
                strokeWidth = 1f,
            ))

            val curvePath = Path { moveTo(x0, y0); quadTo(cx, cy, x2, y2) }
            canvas.drawPath(curvePath, Paint(color = Color.BLACK, style = PaintStyle.STROKE, strokeWidth = 2f))

            val boundsRect = Rect.fromLTRB(
                minOf(x0, cx, x2), minOf(y0, cy, y2),
                maxOf(x0, cx, x2), maxOf(y0, cy, y2),
            )
            canvas.drawRect(boundsRect, Paint(
                color = Color.fromRGBA(0x80 / 255f, 0x80 / 255f, 0x80 / 255f),
                style = PaintStyle.STROKE,
                strokeWidth = 1f,
            ))
        }
    }
}
