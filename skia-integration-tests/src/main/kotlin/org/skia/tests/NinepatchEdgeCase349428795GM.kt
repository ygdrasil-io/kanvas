package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/lattice.cpp`
 * `DEF_SIMPLE_GM(ninepatch_edge_case_349428795, canvas, 500, 150)`.
 *
 * Regression test for b/349428795: a nine-patch should be able to have
 * zero-sized regions on **either** end (left/right or top/bottom).
 * Before the fix, only left/top zero-sized regions worked correctly,
 * leading to non-symmetric results.  Correct rendering: each row should
 * be left-right symmetric.
 *
 * The source is an 8×8 image filled with blue, with a 4×4 green centre
 * region.  Seven columns are rendered for each of the X and Y axis
 * variants (i from -1..5), using the `center` rect `MakeXYWH(i, 2, 4, 4)`
 * (X-axis row) and `MakeXYWH(2, i, 4, 4)` (Y-axis row).
 */
public class NinepatchEdgeCase349428795GM : GM() {

    override fun getName(): String = "ninepatch_edge_case_349428795"
    override fun getISize(): SkISize = SkISize.Make(500, 150)

    private fun makeSymmetryTestImage(): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(8, 8))
        val canvas = surface.canvas
        canvas.drawColor(SK_ColorBLUE)
        val p = SkPaint()
        p.color = SK_ColorGREEN
        canvas.drawRect(SkRect.MakeXYWH(2f, 2f, 4f, 4f), p)
        return surface.makeImageSnapshot()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val nine = makeSymmetryTestImage()

        for (i in -1 until 6) {
            // X-axis variant (top row)
            c.drawImageNine(
                nine,
                SkIRect.MakeXYWH(i, 2, 4, 4),
                SkRect.MakeXYWH(i * 70f + 80f, 10f, 64f, 64f),
                SkFilterMode.kLinear,
            )
            // Y-axis variant (bottom row)
            c.drawImageNine(
                nine,
                SkIRect.MakeXYWH(2, i, 4, 4),
                SkRect.MakeXYWH(i * 70f + 80f, 80f, 64f, 64f),
                SkFilterMode.kLinear,
            )
        }
    }
}
