package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/pathfill.cpp` `DEF_SIMPLE_GM(path_arcto_skbug_9077, …)`.
 *
 * Regression: 3 lineTo + close, then a tangent-`arcTo` (radius=60)
 * landing at `(180, 160)`. Stroke width 2, AA red. Tests that
 * `arcTo(p1, p2, radius)` plays nicely after a `close` — the
 * implicit moveTo behaviour after a close used to emit a stale
 * starting point.
 *
 * Reference image: `path_arcto_skbug_9077.png`, 200 × 200, default
 * white BG.
 */
public class PathArcToSkbug9077GM : GM() {

    override fun getName(): String = "path_arcto_skbug_9077"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = 0xFFFF0000.toInt()
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
        }
        val radius = 60f
        val path = SkPathBuilder()
            .moveTo(20f, 20f)
            .lineTo(100f, 20f)
            .lineTo(100f, 60f)
            .close()
            .arcTo(130f, 150f, 180f, 160f, radius)
            .detach()
        c.drawPath(path, p)
    }
}
