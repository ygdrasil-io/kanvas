package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathMeasure
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of upstream Skia's `gm/overstroke.cpp::OverStroke` GM.
 *
 * Stresses very wide stroke widths ("overstroke") on quad / cubic /
 * oval primitives. Each cell renders the source path at scale `0.2`
 * plus a rib pattern emitted by [PathMeasure] sampling.
 * @see https://github.com/google/skia/blob/main/gm/overstroke.cpp
 */
class OverStrokeGm : SkiaGm {
    override val name = "OverStroke"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val examples: List<(GmCanvas) -> Unit> = listOf(
            ::drawSmallQuad, ::drawStrokedQuad,
            ::drawSmallCubic, ::drawStrokedCubic,
            ::drawSmallOval, ::drawStrokedOval,
        )
        for (i in examples.indices) {
            val x = (i % 2).toFloat()
            val y = (i / 2).toFloat()
            canvas.save()
            canvas.translate(150f * x, 150f * y)
            canvas.scale(0.2f, 0.2f)
            canvas.translate(300f, 400f)
            examples[i](canvas)
            canvas.restore()
        }
    }

    private fun drawRibs(canvas: GmCanvas, path: Path) {
        val spacing = 5f
        var accum = 0f
        val meas = PathMeasure(path)
        val length = meas.length
        val pos = Point(0f, 0f)
        val tan = Point(0f, 0f)
        while (accum < length) {
            if (meas.getPosition(accum, pos, tan)) {
                val sx = tan.x * 250f
                val sy = tan.y * 250f
                val rx = -sy
                val ry = sx
                canvas.drawLine(pos.x + rx, pos.y + ry, pos.x - rx, pos.y - ry, ribsPaint)
            }
            accum += spacing
        }
    }

    private val normalPaint: Paint get() = Paint(
        color = Color.BLUE,
        antiAlias = true,
        style = PaintStyle.STROKE,
        strokeWidth = 3f,
    )

    private val overstrokePaint: Paint get() = Paint(
        antiAlias = true,
        style = PaintStyle.STROKE,
        strokeWidth = 500f,
    )

    private val ribsPaint: Paint get() = Paint(
        color = Color.GREEN,
        antiAlias = true,
        style = PaintStyle.STROKE,
        strokeWidth = 1f,
    )

    private fun quadPath(): Path = Path { moveTo(0f, 0f); lineTo(100f, 0f); quadTo(50f, -40f, 0f, 0f); close() }
    private fun cubicPath(): Path = Path { moveTo(0f, 0f); cubicTo(25f, 75f, 75f, -50f, 100f, 0f) }
    private fun ovalPath(): Path = Path { }.apply { addOval(Rect.fromXYWH(0f, -25f, 100f, 50f)) }

    private fun drawSmall(canvas: GmCanvas, path: Path) {
        drawRibs(canvas, path)
        canvas.drawPath(path, normalPaint)
    }

    private fun drawLarge(canvas: GmCanvas, path: Path) {
        canvas.drawPath(path, overstrokePaint)
        drawRibs(canvas, path)
    }

    private fun drawSmallQuad(canvas: GmCanvas) = drawSmall(canvas, quadPath())
    private fun drawLargeQuad(canvas: GmCanvas) = drawLarge(canvas, quadPath())
    private fun drawSmallCubic(canvas: GmCanvas) = drawSmall(canvas, cubicPath())
    private fun drawLargeCubic(canvas: GmCanvas) = drawLarge(canvas, cubicPath())
    private fun drawSmallOval(canvas: GmCanvas) = drawSmall(canvas, ovalPath())
    private fun drawLargeOval(canvas: GmCanvas) = drawLarge(canvas, ovalPath())

    private fun drawStrokedQuad(canvas: GmCanvas) {
        canvas.translate(400f, 0f)
        drawLargeQuad(canvas)
    }

    private fun drawStrokedCubic(canvas: GmCanvas) {
        canvas.translate(400f, 0f)
        drawLargeCubic(canvas)
    }

    private fun drawStrokedOval(canvas: GmCanvas) {
        canvas.translate(400f, 0f)
        drawLargeOval(canvas)
    }
}
