package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/conicpaths.cpp` `DEF_SIMPLE_GM(crbug_640176, …)`.
 *
 * Regression test for [crbug.com/640176](https://crbug.com/640176): a
 * conic with a near-`cos(15°)` weight (`0.965926`) that rendered with a
 * gap. Three line segments + one conic, AA-filled.
 *
 * Reference image: `crbug_640176.png`, 250 × 250, default white BG.
 *
 * Coordinates use `Float.fromBits` to preserve the exact bit-pattern
 * upstream uses (matters for sub-ulp regressions).
 */
public class Crbug640176GM : GM() {

    override fun getName(): String = "crbug_640176"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .moveTo(Float.fromBits(0x00000000), Float.fromBits(0x00000000))   // 0, 0
            .lineTo(Float.fromBits(0x42cfd89a), Float.fromBits(0xc2700000.toInt()))   // 103.923f, -60
            .lineTo(Float.fromBits(0x42cfd899), Float.fromBits(0xc2700006.toInt()))   // 103.923f, -60.000023f
            .conicTo(
                Float.fromBits(0x42f00000), Float.fromBits(0xc2009d9c.toInt()),
                Float.fromBits(0x42f00001), Float.fromBits(0x00000000),
                Float.fromBits(0x3f7746ea),  // 120, -32.1539f, 120, 0, 0.965926f
            )
            .detach()

        val paint = SkPaint().apply { isAntiAlias = true }
        c.translate(125f, 125f)
        c.drawPath(path, paint)
    }
}
