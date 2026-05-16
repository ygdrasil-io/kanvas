package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.colorToRGB565
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/bigmatrix.cpp::bigmatrix` (DEF_SIMPLE_GM_BG, 50 × 50).
 *
 * Stresses the rasteriser with an extreme CTM:
 *
 *  `M = Translate(6000, -5000) · Scale(3000, 3000) · Rotate(33°)`
 *
 * Then draws three sub-pixel-sized primitives in *device* space (size
 * `1/500`) at three positions in *post-CTM* coordinates, mapped back
 * through `M.invert()` so that — once the CTM is applied — they each
 * end up roughly 6 px on screen:
 *
 *  - filled 6 px circle at (10, 10) post-CTM
 *  - filled 6 px square at (30, 10) post-CTM
 *  - shader-filled 6 px square at (30, 30) post-CTM, sampling a 2 × 2
 *    bitmap (red / green / 50 %-black / blue) under a `kRepeat /
 *    kRepeat` `(1/1000)` local matrix scale.
 *
 * Background is `color_to_565(0xFF66AA99)` to match upstream's
 * 565-quantised BG fill.
 */
public class BigMatrixGM : GM() {

    init {
        setBGColor(colorToRGB565(0xFF66AA99u.toInt()))
    }

    override fun getName(): String = "bigmatrix"
    override fun getISize(): SkISize = SkISize.Make(50, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // m = Rotate(33°) ; postScale(3000) ; postTranslate(6000, -5000)
        val m = SkMatrix.MakeRotate(33f)
            .postConcat(SkMatrix.MakeScale(3000f, 3000f))
            .postConcat(SkMatrix.MakeTrans(6000f, -5000f))
        c.concat(m)

        val paint = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
        }

        val inv = m.invert() ?: error("BigMatrixGM: m.invert() failed")
        val small = 1f / 500f

        var pt = inv.mapXY(10f, 10f)
        c.drawCircle(pt.first, pt.second, small, paint)

        pt = inv.mapXY(30f, 10f)
        c.drawRect(SkRect.MakeLTRB(pt.first - small, pt.second - small, pt.first + small, pt.second + small), paint)

        // 2×2 bitmap : red / green / 50%-black / blue.
        val bmp = SkBitmap(2, 2)
        bmp.setPixel(0, 0, SkColorSetARGB(0xFF, 0xFF, 0x00, 0x00))
        bmp.setPixel(1, 0, SkColorSetARGB(0xFF, 0x00, 0xFF, 0x00))
        bmp.setPixel(0, 1, SkColorSetARGB(0x80, 0x00, 0x00, 0x00))
        bmp.setPixel(1, 1, SkColorSetARGB(0xFF, 0x00, 0x00, 0xFF))

        pt = inv.mapXY(30f, 30f)
        val s = SkMatrix.MakeScale(1f / 1000f, 1f / 1000f)
        paint.shader = bmp.makeShader(
            SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions(SkFilterMode.kLinear), s,
        )
        paint.isAntiAlias = false
        c.drawRect(
            SkRect.MakeLTRB(pt.first - small, pt.second - small, pt.first + small, pt.second + small),
            paint,
        )
    }
}
