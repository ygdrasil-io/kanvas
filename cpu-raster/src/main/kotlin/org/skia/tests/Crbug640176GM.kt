package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/conicpaths.cpp` —
 * `DEF_SIMPLE_GM(crbug_640176, canvas, 250, 250)`.
 *
 * Repro for `crbug.com/640176`. A line / line / conic path with
 * coordinates near (0, 0) under translate(125, 125) and AA fill. The
 * conic weight is `0.965926f` (`cos(15°)`). The original bug was the
 * path interior subdivision producing a hole near the line→conic
 * transition.
 *
 * Coordinates are bit-exact (`Float.fromBits` for parity with upstream's
 * `SkBits2Float`) — every digit matters here.
 *
 * Reference image: `crbug_640176.png`, 250 × 250.
 *
 * Stresses :
 *  - Conic-with-weight-`cos(15°)` adjacent to two line segments;
 *  - the AA fill of a tiny self-overlapping triangle-with-arc near
 *    pixel boundaries.
 */
public class Crbug640176GM : GM() {

    override fun getName(): String = "crbug_640176"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val builder = SkPathBuilder()
        builder.moveTo(Float.fromBits(0x00000000), Float.fromBits(0x00000000))   // 0, 0
        builder.lineTo(Float.fromBits(0x42cfd89a), Float.fromBits(0xc2700000.toInt()))   // 103.923f, -60
        builder.lineTo(Float.fromBits(0x42cfd899), Float.fromBits(0xc2700006.toInt()))   // 103.923f, -60
        builder.conicTo(
            Float.fromBits(0x42f00000), Float.fromBits(0xc2009d9c.toInt()),
            Float.fromBits(0x42f00001), Float.fromBits(0x00000000),
            Float.fromBits(0x3f7746ea),   // 0.965926f
        )

        val paint = SkPaint().apply { isAntiAlias = true }
        c.translate(125f, 125f)
        c.drawPath(builder.detach(), paint)
    }
}
