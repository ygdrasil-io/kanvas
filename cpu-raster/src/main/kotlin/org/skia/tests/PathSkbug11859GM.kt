package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/pathfill.cpp` `DEF_SIMPLE_GM(path_skbug_11859, …)`.
 *
 * Two-subpath red fill under `scale(2, 2)` — exposes a clipping
 * regression where coordinates near the bitmap edge (`-2`) interact
 * badly with the rasterizer's edge arithmetic.
 *
 * Reference image: `path_skbug_11859.png`, 512 × 512, default white BG.
 */
public class PathSkbug11859GM : GM() {

    override fun getName(): String = "path_skbug_11859"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            color = 0xFFFF0000.toInt()
            isAntiAlias = true
        }
        val path = SkPathBuilder()
            .moveTo(258f, -2f)
            .lineTo(258f, 258f)
            .lineTo(237f, 258f)
            .lineTo(240f, -2f)
            .lineTo(258f, -2f)
            .moveTo(-2f, -2f)
            .lineTo(240f, -2f)
            .lineTo(238f, 131f)
            .lineTo(-2f, 131f)
            .lineTo(-2f, -2f)
            .detach()

        c.scale(2f, 2f)
        c.drawPath(path, paint)
    }
}
