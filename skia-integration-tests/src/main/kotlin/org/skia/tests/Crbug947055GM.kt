package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_947055.cpp::crbug_947055` (DEF_SIMPLE_GM_BG,
 * 200 × 50, BG = blue).
 *
 * Reference green axis-aligned rect highlighting a red rect drawn under
 * a 3 × 3 perspective matrix (last row `[0, 0.0225, 1]`). Originally
 * exposed a Ganesh AA bug where extreme corner outsets under perspective
 * produced jagged edges.
 *
 * Unblocked by Phase 6m (perspective branch in `SkBitmapDevice.buildEdges`
 * — projects each control point through the full 3 × 3 CTM with
 * homogeneous divide before feeding the scanline-edge accumulator).
 */
public class Crbug947055GM : GM() {

    init { setBGColor(SK_ColorBLUE) }

    override fun getName(): String = "crbug_947055"
    override fun getISize(): SkISize = SkISize.Make(200, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorGREEN
        }
        c.drawRect(SkRect.MakeXYWH(19f, 7f, 180f, 10f), paint)

        paint.color = SK_ColorRED
        c.concat(SkMatrix.MakeAll(
            1f, 2.4520f, 19f,
            0f, 0.3528f, 9.5f,
            0f, 0.0225f, 1f,
        ))
        c.drawRect(SkRect.MakeWH(180f, 500f), paint)
    }
}
