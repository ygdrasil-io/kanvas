package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathEffect
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/inversepaths.cpp::inverse_paths` (800 × 1200).
 *
 * Stress test for inverse-fill paths under combinations of paint
 * style + stroke width + path-shape generators :
 *
 *  - **styles** : `kStroke`, `kStrokeAndFill`, `kFill`, `kStroke +
 *    DashPathEffect [4, 3]`.
 *  - **pathSizes** : 40 / 10 / 0 (the last one degenerate).
 *  - **strokeWidths** : 10 / 0 (the second is hairline).
 *  - **path generators** : square, rect-line, circle, line.
 *
 * Each cell clips to a 90×90 box, draws the path's inverse-fill in
 * green, and overlays the original path outline at hairline opacity
 * so the geometry is visible against the inverse-fill green field.
 */
public class InversePathsGM : GM() {

    override fun getName(): String = "inverse_paths"
    override fun getISize(): SkISize = SkISize.Make(800, 1200)

    private data class Style(val paintStyle: SkPaint.Style, val pathEffect: SkPathEffect? = null)

    private fun makeDash(): SkPathEffect = SkDashPathEffect.Make(floatArrayOf(4f, 3f), 0f)

    private val styles: Array<Style> by lazy {
        arrayOf(
            Style(SkPaint.Style.kStroke_Style),
            Style(SkPaint.Style.kStrokeAndFill_Style),
            Style(SkPaint.Style.kFill_Style),
            Style(SkPaint.Style.kStroke_Style, makeDash()),
        )
    }

    private val pathSizes = floatArrayOf(40f, 10f, 0f)
    private val strokeWidths = floatArrayOf(10f, 0f)
    private val paths: Array<(Float, Float, Float) -> SkPath> = arrayOf(
        ::generateSquare, ::generateRectLine, ::generateCircle, ::generateLine,
    )

    private fun generateSquare(cx: Float, cy: Float, w: Float): SkPath =
        SkPath.Rect(SkRect.MakeXYWH(cx - w / 2, cy - w / 2, w, w))

    private fun generateRectLine(cx: Float, cy: Float, l: Float): SkPath =
        SkPath.Rect(SkRect.MakeXYWH(cx - l / 2, cy, l, 0f))

    private fun generateCircle(cx: Float, cy: Float, d: Float): SkPath =
        SkPath.Circle(cx, cy, d / 2, SkPathDirection.kCW)

    private fun generateLine(cx: Float, cy: Float, l: Float): SkPath =
        SkPath.Line(cx - l / 2 to cy, cx + l / 2 to cy)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val slideWidth = 90f
        val slideHeight = 90f
        val slideBoundary = 5f

        val cx = slideWidth / 2 + slideBoundary
        val cy = slideHeight / 2 + slideBoundary
        val dx = slideWidth + 2 * slideBoundary
        val dy = slideHeight + 2 * slideBoundary

        val clipRect = SkRect.MakeLTRB(
            slideBoundary, slideBoundary,
            slideBoundary + slideWidth, slideBoundary + slideHeight,
        )
        val clipPaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
        }
        val outlinePaint = SkPaint().apply {
            color = 0x40000000
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }

        for (style in styles) {
            for (size in pathSizes) {
                c.save()
                for (sw in strokeWidths) {
                    val paint = SkPaint().apply {
                        color = 0xFF007000.toInt()
                        strokeWidth = sw
                        this.style = style.paintStyle
                        pathEffect = style.pathEffect
                    }
                    for (gen in paths) {
                        c.drawRect(clipRect, clipPaint)
                        c.save()
                        c.clipRect(clipRect)
                        val path = gen(cx, cy, size)
                        c.drawPath(path.makeFillType(SkPathFillType.kInverseWinding), paint)
                        c.drawPath(path.makeFillType(SkPathFillType.kWinding), outlinePaint)
                        c.restore()
                        c.translate(dx, 0f)
                    }
                }
                c.restore()
                c.translate(0f, dy)
            }
        }
    }
}
