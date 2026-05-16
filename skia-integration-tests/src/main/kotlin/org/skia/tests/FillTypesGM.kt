package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/filltypes.cpp::FillTypeGM` (835 × 840, BG = light
 * grey).
 *
 * Two overlapping circles `(50, 50, r=45)` and `(100, 100, r=45)` drawn
 * 4 × 2 × 2 = 16 cells :
 *   - 4 fill types (Winding / EvenOdd / InverseWinding / InverseEvenOdd).
 *   - 2 scales (1 ; 5/4).
 *   - 2 AA modes (false / true).
 *
 * Each cell is `200 × 200`, drawn into a clipped `150 × 150` window
 * after a `drawColor(WHITE)` background fill, then the path is centred
 * + scaled + drawn. The 2 × 2 subgrid layout (fill/even/inverse-fill/
 * inverse-even) is repeated 4 times — `(scale=1, AA=off)`,
 * `(scale=5/4, AA=off)`, `(scale=1, AA=on)`, `(scale=5/4, AA=on)`.
 */
public class FillTypesGM : GM() {

    init { setBGColor(0xFFDDDDDD.toInt()) }

    override fun getName(): String = "filltypes"
    override fun getISize(): SkISize = SkISize.Make(835, 840)

    private val path: SkPath = SkPathBuilder()
        .addCircle(50f, 50f, 45f)
        .addCircle(100f, 100f, 45f)
        .detach()

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(20f, 20f)

        val paint = SkPaint().apply { isAntiAlias = false }
        showFour(c, 1f, paint)
        c.translate(450f, 0f)
        showFour(c, 5f / 4f, paint)

        paint.isAntiAlias = true

        c.translate(-450f, 450f)
        showFour(c, 1f, paint)
        c.translate(450f, 0f)
        showFour(c, 5f / 4f, paint)
    }

    private fun showFour(canvas: SkCanvas, scale: Float, paint: SkPaint) {
        showPath(canvas, 0,   0,   SkPathFillType.kWinding, scale, paint)
        showPath(canvas, 200, 0,   SkPathFillType.kEvenOdd, scale, paint)
        showPath(canvas, 0,   200, SkPathFillType.kInverseWinding, scale, paint)
        showPath(canvas, 200, 200, SkPathFillType.kInverseEvenOdd, scale, paint)
    }

    private fun showPath(
        canvas: SkCanvas,
        x: Int, y: Int,
        ft: SkPathFillType,
        scale: Float,
        paint: SkPaint,
    ) {
        val r = SkRect.MakeWH(150f, 150f)
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(r)
        canvas.drawColor(SK_ColorWHITE)
        val typed = path.makeFillType(ft)
        canvas.translate(r.centerX(), r.centerY())
        canvas.scale(scale, scale)
        canvas.translate(-r.centerX(), -r.centerY())
        canvas.drawPath(typed, paint)
        canvas.restore()
    }
}
