package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/closedcappedhairlines.cpp::hairlines_squarecap` (250 × 250).
 *
 * Same layout as [HairlinesButtcapGM] (4 rows × 3 shapes, 1× + 4× renders,
 * pixel grid, endpoint highlights) but with [SkPaint.Cap.kSquare_Cap]. Tests
 * that square caps (which extend 0.5 px past the endpoint) do not produce
 * visible artefacts on closed contours where no cap should appear.
 */
public class HairlinesSquarecapGM : GM() {

    override fun getName(): String = "hairlines_squarecap"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawHairlineContoursWithCaps(c, SkPaint.Cap.kSquare_Cap)
    }
}
