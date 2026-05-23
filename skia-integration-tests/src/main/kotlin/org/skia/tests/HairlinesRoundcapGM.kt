package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/closedcappedhairlines.cpp::hairlines_roundcap` (250 × 250).
 *
 * Same layout as [HairlinesButtcapGM] (4 rows × 3 shapes, 1× + 4× renders,
 * pixel grid, endpoint highlights) but with [SkPaint.Cap.kRound_Cap]. Tests
 * that round caps do not leak beyond closed contours' implicit join.
 */
public class HairlinesRoundcapGM : GM() {

    override fun getName(): String = "hairlines_roundcap"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawHairlineContoursWithCaps(c, SkPaint.Cap.kRound_Cap)
    }
}
