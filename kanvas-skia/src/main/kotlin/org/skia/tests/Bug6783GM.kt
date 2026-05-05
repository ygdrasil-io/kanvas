package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/bug6783.cpp::bug6783` (DEF_SIMPLE_GM, 500 × 500).
 *
 * Reproduces `skbug.com/40037998` — a software-tiling bug where the
 * `kRepeat` / `kMirror` image samplers wrapped to `[0, limit)` with
 * `limit = ulp_before(actual_limit)`, producing jaggies on the
 * blue/yellow boundary.
 *
 * Pipeline :
 *  1. Off-screen 100 × 100 raster surface — yellow background, blue
 *     left half (`drawRect(0, 0, 50, 100)`).
 *  2. Snapshot → `SkImage`.
 *  3. Build a shader from the image with `tileX = kRepeat`,
 *     `tileY = kClamp`, `kLinear` filter, and a local matrix
 *     `Translate(25, 214) · Scale(2, 2) · Skew(0.5, 0.5)`.
 *  4. `drawPaint(paint)` on the 500 × 500 canvas — covers everything,
 *     showing repeated tile copies of the yellow/blue source under the
 *     skewed matrix.
 *
 * First port to use both [SkSurface.MakeRaster] and the `kRepeat` X
 * tile mode under non-uniform local matrix sampling.
 */
public class Bug6783GM : GM() {

    override fun getName(): String = "bug6783"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(100, 100))

        val p = SkPaint().apply { color = SK_ColorYELLOW }
        surface.canvas.drawPaint(p)
        p.color = SK_ColorBLUE
        surface.canvas.drawRect(SkRect.MakeWH(50f, 100f), p)

        val img = surface.makeImageSnapshot()

        // m = Translate(25, 214) · Scale(2, 2), then preSkew(0.5, 0.5).
        // C++ `T * S` means scale-then-translate ; our `preConcat` is
        // left-multiply (`T.preConcat(S) = T · S`).
        var m = SkMatrix.MakeTrans(25f, 214f).preConcat(SkMatrix.MakeScale(2f, 2f))
        m = m.preSkew(0.5f, 0.5f)

        val sampling = SkSamplingOptions(SkFilterMode.kLinear)
        p.shader = img.makeShader(SkTileMode.kRepeat, SkTileMode.kClamp, sampling, m)
        c.drawPaint(p)
    }
}
