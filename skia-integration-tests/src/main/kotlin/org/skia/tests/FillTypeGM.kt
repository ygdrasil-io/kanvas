package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/filltypes.cpp` :
 * `DEF_GM(return new FillTypeGM;)`.
 *
 * Same path as [FillTypePerspGM] (two overlapping circles) but
 * drawn **without perspective** — 4×4 grid of fill-type
 * permutations × scale × AA.
 */
public class FillTypeGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String = "filltypes"
    override fun getISize(): SkISize = SkISize.Make(835, 840)

    private lateinit var fPath: SkPath

    override fun onOnceBeforeDraw() {
        val radius = 45f
        fPath = SkPathBuilder()
            .addCircle(50f, 50f, radius)
            .addCircle(100f, 100f, radius)
            .detach()
    }

    private fun showPath(canvas: SkCanvas, x: Int, y: Int, ft: SkPathFillType,
                         scale: Float, paint: SkPaint) {
        val r = SkRect.MakeLTRB(0f, 0f, 150f, 150f)
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(r)
        canvas.drawColor(SK_ColorWHITE)
        val path = fPath.makeFillType(ft)
        canvas.translate(r.centerX(), r.centerY())
        canvas.scale(scale, scale)
        canvas.translate(-r.centerX(), -r.centerY())
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun showFour(canvas: SkCanvas, scale: Float, paint: SkPaint) {
        showPath(canvas,   0,   0, SkPathFillType.kWinding,        scale, paint)
        showPath(canvas, 200,   0, SkPathFillType.kEvenOdd,        scale, paint)
        showPath(canvas,   0, 200, SkPathFillType.kInverseWinding, scale, paint)
        showPath(canvas, 200, 200, SkPathFillType.kInverseEvenOdd, scale, paint)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(20f, 20f)

        val paint = SkPaint()
        val scale = 5f / 4f

        paint.isAntiAlias = false
        showFour(c, 1f, paint)
        c.translate(450f, 0f)
        showFour(c, scale, paint)

        paint.isAntiAlias = true
        c.translate(-450f, 450f)
        showFour(c, 1f, paint)
        c.translate(450f, 0f)
        showFour(c, scale, paint)
    }
}
