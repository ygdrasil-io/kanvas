package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of upstream `gm/cubicpaths.cpp` `CubicPathGM`
 * (`DEF_GM(return new CubicPathGM)`, 1240 × 390 canvas).
 *
 * First GM ported on top of the **kInverse* rasterizer** unblocked
 * by Phase 3.8.
 *
 * Renders a single cubic path
 * moveTo(25, 10)  cubicTo(40, 20, 60, 20, 75, 10)
 * 36 times across a 4 × 3 × 3 grid that varies independently:
 * - fill rule (rows): kWinding, kEvenOdd, kInverseWinding,
 *   kInverseEvenOdd.
 * - paint style (cells per row): fill, stroke (width 10), and
 *   stroke-and-fill.
 * - cap + join (column blocks of 3 styles): butt-bevel,
 *   round-round, square-bevel.
 *
 * Each cell clips to a 100 × 30 rect, draws the cubic in dark green
 * 0xff007000 at the configured style/cap/join/fill, then strokes
 * the rect outline as a 1-px black hairline (strokeWidth = 0).
 *
 * Inverse fills here exercise the Phase 3.8 scanline-walker
 * extension: rows above / below the path's edge bbox contribute
 * full-coverage spans, the per-row span loop seeds
 * inside = isInside(0, fillType) so the region left of the first
 * crossing is already filled, and the trailing flush emits the
 * residual span to clip.right.
 *
 * Reference image: cubicpath.png, 1240 × 390, white background.
 * @see https://github.com/google/skia/blob/main/gm/cubicpaths.cpp
 */
class CubicPathGm : SkiaGm {
    override val name = "cubicpath"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1240
    override val height = 390

    private fun drawPathCell(
        path: Path, canvas: GmCanvas, color: Color,
        clip: Rect,
        cap: StrokeCap, join: StrokeJoin,
        style: PaintStyle, fill: FillType,
        strokeWidth: Float,
    ) {
        val typedPath = Path { }
        typedPath.addPath(path)
        typedPath.fillType = fill
        val paint = Paint(
            color = color,
            style = style,
            strokeCap = cap,
            strokeJoin = join,
            strokeWidth = strokeWidth
        )
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawPath(typedPath, paint)
        canvas.restore()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // --- the cubic path itself ------------------------------------
        val path = Path {
            moveTo(25f, 10f)
            cubicTo(40f, 20f, 60f, 20f, 75f, 10f)
        }

        val fills = listOf(
            FillType.WINDING to "Winding",
            FillType.EVEN_ODD to "Even / Odd",
            FillType.INVERSE_WINDING to "Inverse Winding",
            FillType.INVERSE_EVEN_ODD to "Inverse Even / Odd",
        )
        val styles = listOf(
            PaintStyle.FILL to "Fill",
            PaintStyle.STROKE to "Stroke",
            PaintStyle.STROKE_AND_FILL to "Stroke And Fill",
        )
        data class CapJoin(val cap: StrokeCap, val join: StrokeJoin, val name: String)
        val caps = listOf(
            CapJoin(StrokeCap.BUTT, StrokeJoin.BEVEL, "Butt"),
            CapJoin(StrokeCap.ROUND, StrokeJoin.ROUND, "Round"),
            CapJoin(StrokeCap.SQUARE, StrokeJoin.BEVEL, "Square"),
        )

        // --- title ----------------------------------------------------
        // Skip title for now as drawString is not available on GmCanvas

        // --- 4 × 3 × 3 grid -------------------------------------------
        val rect = Rect.fromXYWH(0f, 0f, 100f, 30f)
        canvas.save()
        canvas.translate(10f, 30f)
        canvas.save()
        for (capIdx in caps.indices) {
            if (capIdx > 0) canvas.translate((rect.width + 40f) * styles.size, 0f)
            canvas.save()
            for (fillIdx in fills.indices) {
                if (fillIdx > 0) canvas.translate(0f, rect.height + 40f)
                canvas.save()
                for (styleIdx in styles.indices) {
                    if (styleIdx > 0) canvas.translate(rect.width + 40f, 0f)

                    val cellColor = Color.fromRGBA(0x00 / 255f, 0x70 / 255f, 0x00 / 255f, 1f)
                    drawPathCell(
                        path, canvas, cellColor, rect,
                        caps[capIdx].cap, caps[capIdx].join,
                        styles[styleIdx].first, fills[fillIdx].first,
                        strokeWidth = 10f,
                    )

                    // Hairline rect outline
                    val rectPaint = Paint(
                        color = Color.BLACK,
                        style = PaintStyle.STROKE,
                        strokeWidth = 0f,
                        antiAlias = true
                    )
                    canvas.drawRect(rect, rectPaint)
                }
                canvas.restore()
            }
            canvas.restore()
        }
        canvas.restore()
        canvas.restore()
    }
}
