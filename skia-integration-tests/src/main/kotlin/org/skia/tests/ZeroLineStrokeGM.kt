package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp` `DEF_SIMPLE_GM(zerolinestroke, …)`.
 *
 * Three zero-or-tiny-length strokes drawn with `kRound_Cap`, all of
 * which should render as identical-looking "lozenges" (round caps
 * connected by a flat segment). Tests:
 *  - 4 collinear points with a duplicated first point (top row),
 *  - `SkPath.Line` factory (middle row),
 *  - 3 collinear points with a duplicated middle (bottom row).
 *
 * Reference image: `zerolinestroke.png`, 90 × 120, default white BG.
 */
public class ZeroLineStrokeGM : GM() {

    override fun getName(): String = "zerolinestroke"
    override fun getISize(): SkISize = SkISize.Make(90, 120)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 20f
            isAntiAlias = true
            strokeCap = SkPaint.Cap.kRound_Cap
        }

        // 4 points with duplicate-first.
        c.drawPath(
            SkPathBuilder()
                .moveTo(30f, 90f)
                .lineTo(30f, 90f)
                .lineTo(60f, 90f)
                .lineTo(60f, 90f)
                .detach(),
            paint,
        )

        // SkPath.Line factory (already in our API).
        c.drawPath(SkPath.Line(Pair(30f, 30f), Pair(60f, 30f)), paint)

        // 3 points with duplicate-first.
        c.drawPath(
            SkPathBuilder()
                .moveTo(30f, 60f)
                .lineTo(30f, 60f)
                .lineTo(60f, 60f)
                .detach(),
            paint,
        )
    }
}
