package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Shared base for `gm/circulararcs.cpp::circular_arcs_*` ports.
 *
 * Renders a 4-quadrant grid (`useCenter` × AA on/off) ; each quadrant
 * is an 8 × 8 grid of `drawArc(rect, start, sweep, useCenter, p)` calls
 * with the start ∈ `{0, 10, 30, 45, 90, 165, 180, 270}` and sweep ∈
 * `{1, 45, 90, 130, 180, 184, 300, 355}`. Each cell is drawn twice :
 * once in red with the requested sweep, once in blue with the
 * complement (`-(360 - sweep)`) so overlap shows as magenta.
 *
 * Subclasses supply [configurePaint] to swap fill / hairline / stroke
 * configuration into the red and blue paints.
 */
public abstract class CircularArcsGM(private val gmName: String) : GM() {

    override fun getName(): String = gmName
    override fun getISize(): SkISize = SkISize.Make(kW, kH)

    /**
     * Configure [paint] in place for this GM's rendering style. Called
     * twice per cell — once for each colour. Default `setStrokeWidth(15)`
     * is already applied ; override only what differs.
     */
    protected abstract fun configurePaint(paint: SkPaint)

    final override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawGrid(c, 0f, 0f, useCenter = false, aa = false)
        drawGrid(c, kGridW, 0f, useCenter = true, aa = false)
        drawGrid(c, 0f, kGridH, useCenter = false, aa = true)
        drawGrid(c, kGridW, kGridH, useCenter = true, aa = true)

        // Separator lines.
        val linePaint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLACK
        }
        c.drawLine(kGridW, 0f, kGridW, kH.toFloat(), linePaint)
        c.drawLine(0f, kGridH, kW.toFloat(), kGridH, linePaint)
    }

    private fun drawGrid(canvas: SkCanvas, x: Float, y: Float, useCenter: Boolean, aa: Boolean) {
        val pad = 20f
        val p0 = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = aa
            strokeWidth = 15f
        }
        val p1 = SkPaint().apply {
            color = SK_ColorBLUE
            isAntiAlias = aa
            strokeWidth = 15f
        }
        // Alpha so the overlap with the complement shows as magenta.
        val withAlpha = { p: SkPaint, color: Int ->
            // SkColor is non-premul ARGB ; mask the alpha to 100.
            p.color = (100 shl 24) or (color and 0x00FFFFFF)
        }
        withAlpha(p0, SK_ColorRED)
        withAlpha(p1, SK_ColorBLUE)
        configurePaint(p0)
        configurePaint(p1)

        canvas.save()
        canvas.translate(pad + x, pad + y)
        for (start in kStarts) {
            canvas.save()
            for (sweep in kSweeps) {
                canvas.drawArc(kRect, start, sweep, useCenter, p0)
                canvas.drawArc(kRect, start, -(360f - sweep), useCenter, p1)
                canvas.translate(kRect.width() + pad, 0f)
            }
            canvas.restore()
            canvas.translate(0f, kRect.height() + pad)
        }
        canvas.restore()
    }

    private companion object {
        const val kW: Int = 1000
        const val kH: Int = 1000
        const val kGridW: Float = (kW / 2).toFloat()
        const val kGridH: Float = (kH / 2).toFloat()
        const val kDiameter: Float = 40f

        val kStarts: FloatArray = floatArrayOf(0f, 10f, 30f, 45f, 90f, 165f, 180f, 270f)
        val kSweeps: FloatArray = floatArrayOf(1f, 45f, 90f, 130f, 180f, 184f, 300f, 355f)
        val kRect: SkRect = SkRect.MakeLTRB(0f, 0f, kDiameter, kDiameter)
    }
}

/** Filled `drawArc` grid. */
public class CircularArcsFillGM : CircularArcsGM("circular_arcs_fill") {
    override fun configurePaint(paint: SkPaint) {
        paint.style = SkPaint.Style.kFill_Style
    }
}

/** Hairline `drawArc` grid (`strokeWidth = 0`, `kStroke_Style`). */
public class CircularArcsHairlineGM : CircularArcsGM("circular_arcs_hairline") {
    override fun configurePaint(paint: SkPaint) {
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = 0f
    }
}

/** Stroked `drawArc` grid with `kButt_Cap`. Stroke width left at the
 *  base 15 px from `CircularArcsGM`. */
public class CircularArcsStrokeButtGM : CircularArcsGM("circular_arcs_stroke_butt") {
    override fun configurePaint(paint: SkPaint) {
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeCap = SkPaint.Cap.kButt_Cap
    }
}

/** Stroked `drawArc` grid with `kSquare_Cap`. */
public class CircularArcsStrokeSquareGM : CircularArcsGM("circular_arcs_stroke_square") {
    override fun configurePaint(paint: SkPaint) {
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeCap = SkPaint.Cap.kSquare_Cap
    }
}

/** Stroked `drawArc` grid with `kRound_Cap`. */
public class CircularArcsStrokeRoundGM : CircularArcsGM("circular_arcs_stroke_round") {
    override fun configurePaint(paint: SkPaint) {
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeCap = SkPaint.Cap.kRound_Cap
    }
}
