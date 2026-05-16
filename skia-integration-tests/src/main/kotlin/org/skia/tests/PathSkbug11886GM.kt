package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/pathfill.cpp` `DEF_SIMPLE_GM(path_skbug_11886, …)`.
 *
 * One AA-filled cubic-Bézier path starting at `(0, 770)` (near the
 * bottom of a `256 × 256` canvas) with control points pulling
 * sharply upward. Tests numerical stability at large path
 * coordinates (`y` jumps by ~1500).
 *
 * Reference image: `path_skbug_11886.png`, 256 × 256, default white BG.
 */
public class PathSkbug11886GM : GM() {

    override fun getName(): String = "path_skbug_11886"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val mx = 0f
        val my = 770f
        val path = SkPathBuilder()
            .moveTo(mx, my)
            .cubicTo(
                mx + 0f, my + 1f,
                mx + 20f, my - 750f,
                mx + 83f, my - 746f,
            )
            .detach()

        val paint = SkPaint().apply { isAntiAlias = true }
        c.drawPath(path, paint)
    }
}
