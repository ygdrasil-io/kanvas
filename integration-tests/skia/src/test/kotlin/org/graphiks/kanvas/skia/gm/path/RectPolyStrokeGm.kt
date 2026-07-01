package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/rect_poly_stroke.cpp::rect_poly_stroke`
 * (DEF_SIMPLE_GM, 1150 × 920).
 *
 * Compares stroked rect rasterization via two paths :
 *  1. `drawRect(rect, paint)` — the dedicated rect rasterizer.
 *  2. `drawPath(Path { addRect(rect) }, paint)` — the general path rasterizer.
 *
 * Both should produce identical outlines under the same stroke
 * settings. Drawn 3 × 4 × 2 × 2 = 48 cells (3 joins × 4 rect shapes ×
 * 2 rotations × 2 procs) plus a 0-width green hairline overlay per
 * cell to expose any geometry mismatch between the two procs.
 *
 * Rect shapes include `(0×H)` / `(W×0)` / `(0×0)` degenerate cases —
 * the dedicated rect rasterizer drops them entirely while the path
 * rasterizer may emit a stroked line / point.
 */
class RectPolyStrokeGm : SkiaGm {
    override val name = "rect_poly_stroke"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1150
    override val height = 920

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val W = 100f
        val H = 80f
        val SPACING = 150f
        val THICKNESS = 20f

        val rects = arrayOf(
            Rect.fromLTRB(0f, 0f, W, H),
            Rect.fromLTRB(0f, 0f, W, 0f),
            Rect.fromLTRB(0f, 0f, 0f, H),
            Rect.fromLTRB(0f, 0f, 0f, 0f),
        )
        val degrees = floatArrayOf(0f, -30f)
        val joins = arrayOf(StrokeJoin.MITER, StrokeJoin.ROUND, StrokeJoin.BEVEL)
        val procs: Array<Pair<(GmCanvas, Rect, Paint) -> Unit, Color>> = arrayOf(
            { c: GmCanvas, r: Rect, p: Paint -> c.drawRect(r, p) } to Color.BLACK,
            { c: GmCanvas, r: Rect, p: Paint -> c.drawPath(Path { }.apply { addRect(r) }, p) } to Color.fromRGBA(0f, 0f, 0x88f / 255f, 1f),
        )

        canvas.translate(30f, 50f)
        val basePaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = THICKNESS,
        )
        var paint = basePaint
        for (j in joins) {
            paint = paint.copy(strokeJoin = j)

            canvas.save()
            for (r in rects) {
                for (angle in degrees) {
                    canvas.save()
                    for ((proc, color) in procs) {
                        canvas.save()
                        val pivot = Matrix33.translate(r.center.x, r.center.y) * Matrix33.rotate(angle) * Matrix33.translate(-r.center.x, -r.center.y)
                        canvas.concat(pivot)
                        paint = paint.copy(strokeWidth = THICKNESS, color = color)
                        proc(canvas, r, paint)

                        paint = paint.copy(strokeWidth = 0f, color = Color.GREEN)
                        proc(canvas, r, paint)
                        canvas.restore()
                        canvas.translate(0f, SPACING)
                    }
                    canvas.restore()
                    canvas.translate(SPACING, 0f)
                }
            }
            canvas.restore()
            canvas.translate(0f, 2f * SPACING)
        }
    }
}
